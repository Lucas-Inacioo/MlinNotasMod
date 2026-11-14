package com.mlin.notas.village.works;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.boss.BossMob;
import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.VillageBuilder;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.WorldTerrain;
import com.mlin.notas.vstructures.VanillaRemakePiece;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleBuilder;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRule;
import net.minecraft.world.level.gamerules.GameRuleCategory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Obras publicas gratuitas da vila (ponte com o jogo): a cada nivel entram na fila das obras da vila (ruas, praca, arvores,
 * jardins, decoracao, estacas e placas dos lotes vagos, pontes, hortas e pomares dos arredores, estradas externas, fases da
 * muralha) e o servidor as faz aos poucos, so quando nao ha obra paga nem terraplenagem rodando e so em chao ja aplainado.
 * O planejamento e puro ({@code StreetWorks}, {@code PlazaWorks}, {@code ParcelWorks}, {@code BridgeWorks}, {@code WallWorks},
 * {@code RoadWorks}); aqui ficam a fila salva ({@link VillageWorksData}), a regra de posse (so troca chao natural ou o que
 * a propria obra colocou, nunca bloco de jogador) e as grades de ferro dos portoes do Capital.
 */
public final class VillageWorks {
	/** Liga e desliga todas as obras publicas do mundo. */
	public static final GameRule<Boolean> ENABLED = GameRuleBuilder.forBoolean(true).category(GameRuleCategory.MISC)
			.buildAndRegister(MlinNotas.id("village_works"));
	/** Blocos colocados por tick (como as obras pagas). */
	public static final int BLOCKS_PER_TICK = 6;
	/** Posicoes examinadas por tick (as que ja estao certas ou nao servem nao contam como colocadas). */
	public static final int EXAMINE_PER_TICK = 64;

	private record Cached(List<WBlock> blocks) {
	}

	private static final Map<String, Cached> CACHE = new HashMap<>();
	private static final Map<String, Integer> PLACED = new HashMap<>();

	private VillageWorks() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(WorksPayloads.Sync.TYPE, WorksPayloads.Sync.CODEC);
		ServerTickEvents.END_SERVER_TICK.register(VillageWorks::tick);
		ServerLivingEntityEvents.AFTER_DEATH.register(VillageWorks::onDeath);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
			CACHE.clear();
			PLACED.clear();
		});
	}

	// ---------------------------------------------------------------- contexto do mundo

	static boolean enabled(MinecraftServer server) {
		return server.getGameRules().get(ENABLED);
	}

	/** Contexto de planejamento lido do mundo agora (alturas do plano, o que atrapalha, lotes, destinos). */
	static WorkContext context(ServerLevel level, VillageState village, VillageWorksData data, VillageWorksData.State st) {
		VillageLayout layout = village.layout();
		MasterPlan plan = layout.master();
		java.util.Set<Integer> reserved = new java.util.HashSet<>();
		List<VillageState.Rect> rects = new ArrayList<>();
		java.util.Set<Long> keep = new java.util.HashSet<>();
		for (VillageLayout.Lot lot : layout.lots()) {
			if (lot.parcel() >= 0) {
				reserved.add(lot.parcel());
			}
			rects.add(lot.rect());
			for (int[] c : lot.access()) {
				keep.add(VillageLayout.col(c[0], c[1]));
			}
		}
		for (MasterPlan.Parcel p : plan.parcels()) {
			if (p.player()) {
				reserved.add(p.id());
			}
		}
		for (VillageLayout.Lamp lamp : layout.lamps()) {
			keep.add(VillageLayout.col(lamp.x(), lamp.z()));
		}
		for (long road : layout.roads()) {
			keep.add(road);
		}
		BlockPos center = VillageServer.center(village);
		WorldTerrain terrain = new WorldTerrain(level, center);
		Map<Long, Boolean> blockedCache = new HashMap<>();
		BiPredicate<Integer, Integer> blocked = (x, z) -> blockedCache.computeIfAbsent(VillageLayout.col(x, z), k -> blocked(level, village, st, plan, x, z));
		List<WorkContext.Destination> destinations = destinations(level, village);
		java.util.function.BiFunction<Integer, Integer, Integer> top = (x, z) -> columnTop(level, village, plan, x, z);
		int open = Math.min(MasterPlan.RINGS, village.level().rings());
		return new WorkContext(plan, village.style(), village.seed(), open, blocked, reserved, rects, keep, destinations, data.lastBoss, terrain, top);
	}

	/** Coluna com bloco de fora (jogador, obra paga) logo acima do chao planejado. */
	static boolean blocked(ServerLevel level, VillageState village, VillageWorksData.State st, MasterPlan plan, int x, int z) {
		int h = plan.plannedHeight(x, z);
		if (h == Integer.MIN_VALUE) {
			return false;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dy = 1; dy <= 3; dy++) {
			pos.set(village.x() + x, h + dy, village.z() + z);
			if (!level.isLoaded(pos)) {
				return false;
			}
			BlockState s = level.getBlockState(pos);
			if (s.isAir() || WorldTerrain.clearable(s)) {
				continue;
			}
			if (!st.owned.owns(WBlock.above(x, h + dy, z, "").key(), id(s))) {
				return true;
			}
		}
		return false;
	}

	static int columnTop(ServerLevel level, VillageState village, MasterPlan plan, int x, int z) {
		int h = plan.plannedHeight(x, z);
		if (h == Integer.MIN_VALUE) {
			h = village.y() - 1;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int y = h + 16; y >= h - 2; y--) {
			pos.set(village.x() + x, y, village.z() + z);
			if (!level.isLoaded(pos)) {
				return Integer.MIN_VALUE;
			}
			if (!level.getBlockState(pos).isAir() && !WorldTerrain.clearable(level.getBlockState(pos))) {
				return y;
			}
		}
		return Integer.MIN_VALUE;
	}

	static List<WorkContext.Destination> destinations(ServerLevel level, VillageState village) {
		List<WorkContext.Destination> out = new ArrayList<>();
		String dimension = VillageServer.dimension(level);
		for (com.mlin.notas.waystone.WaystoneInfo w : com.mlin.notas.waystone.WaystoneData.get(level.getServer()).registry().all()) {
			if (w.dimension().equals(dimension)) {
				out.add(new WorkContext.Destination(w.x() - village.x(), w.z() - village.z(), "waystone", w.name()));
			}
		}
		for (VillageState other : VillageData.get(level.getServer()).villages()) {
			if (other != village && other.dimension().equals(village.dimension())) {
				out.add(new WorkContext.Destination(other.x() - village.x(), other.z() - village.z(), "village", other.name()));
			}
		}
		return out;
	}

	static String id(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	// ---------------------------------------------------------------- planejamento por obra

	static Blueprint plan(WorkSpec spec, WorkContext ctx, VillageState village) {
		return switch (spec.kind()) {
			case "streets" -> StreetWorks.plan(ctx, spec.argInt());
			case "plaza" -> PlazaWorks.plan(ctx, spec.argInt());
			case "trees" -> ParcelWorks.trees(ctx);
			case "gardens" -> ParcelWorks.gardens(ctx);
			case "decor" -> ParcelWorks.decor(ctx);
			case "stakes" -> ParcelWorks.stakes(ctx, spec.argInt());
			case "bridges" -> BridgeWorks.plan(ctx, spec.argInt());
			case "rural" -> ParcelWorks.rural(ctx);
			case "roads" -> RoadWorks.plan(ctx);
			case "fields" -> spec.arg().equals("hedge") ? CrownWorks.hedge(ctx) : CrownWorks.lamps(ctx);
			case "wall" -> switch (spec.arg()) {
				case "palisade" -> WallWorks.palisade(ctx);
				case "gates" -> WallWorks.woodenGates(ctx);
				case "historic" -> CrownWorks.promenade(ctx);
				case "barbican" -> CrownWorks.barbican(ctx);
				default -> WallWorks.battlements(ctx);
			};
			default -> new Blueprint();
		};
	}

	/** A muralha de pedra (paga) esta feita, na fila ou sendo feita: a palicada e os portoes de madeira saem de cena. */
	static boolean stoneWall(VillageState village, VillageData data) {
		VillageData.Job job = data.job(village.key());
		return village.built(VillageProject.WALL) > 0 || village.queue().contains(VillageProject.WALL) || job != null && job.project == VillageProject.WALL;
	}

	/** Assinatura que reabre a obra quando muda: aneis prontos (bits) ou, nas estradas, quantos destinos existem. */
	static int signature(WorkSpec spec, MasterPlan plan, ServerLevel level, VillageState village) {
		if (spec.category() == WorkCategory.ROADS) {
			return destinations(level, village).size() + 1;
		}
		int mask = 0;
		for (int ring = 1; ring <= MasterPlan.RINGS; ring++) {
			if (plan.ringPrepared(ring)) {
				mask |= 1 << (ring - 1);
			}
		}
		return mask;
	}

	// ---------------------------------------------------------------- tick

	private static void tick(MinecraftServer server) {
		if (!enabled(server)) {
			return;
		}
		long now = server.getTickCount();
		VillageData data = VillageData.get(server);
		VillageWorksData works = VillageWorksData.get(server);
		for (VillageState village : List.copyOf(data.villages())) {
			if (village.layout() == null || village.layout().master() == null) {
				continue;
			}
			ServerLevel level = VillageServer.levelOf(server, village);
			if (level == null || !level.isLoaded(VillageServer.center(village))) {
				continue;
			}
			VillageWorksData.State st = works.state(village.key());
			MasterPlan plan = village.layout().master();
			plan.setOwnedColumns(st.owned.columns());
			if (now % 20 == 0) {
				enqueue(works, st, village);
				reopen(works, st, plan, level, village);
				gates(level, village, st, works);
			}
			if (data.job(village.key()) != null || data.terrainJob(village.key()) != null) {
				continue;
			}
			run(server, level, village, data, works, st, false);
		}
	}

	/** Obras que entram no nivel atual (e nos anteriores, se a vila e anterior a este sistema). */
	static void enqueue(VillageWorksData works, VillageWorksData.State st, VillageState village) {
		int current = village.level().ordinal();
		if (st.lastLevel >= current) {
			// obra nova de um nivel que a vila ja passou (versao nova do mod: anel rural, centro historico...)
			boolean added = false;
			for (WorkSpec spec : WorkSpec.upTo(village.level())) {
				if (st.entry(spec.id()) == null) {
					st.entries.add(new VillageWorksData.Entry(spec.id(), 0, 0, 0, 0));
					added = true;
				}
			}
			if (added) {
				works.changed();
			}
			return;
		}
		for (int l = st.lastLevel + 1; l <= current; l++) {
			for (WorkSpec spec : WorkSpec.introducedAt(VillageLevel.byOrdinal(l))) {
				if (st.entry(spec.id()) == null) {
					st.entries.add(new VillageWorksData.Entry(spec.id(), 0, 0, 0, 0));
				}
			}
		}
		st.lastLevel = current;
		works.changed();
	}

	/** Obra pronta cuja assinatura mudou (anel novo aplainado, destino novo) volta para a fila. */
	static void reopen(VillageWorksData works, VillageWorksData.State st, MasterPlan plan, ServerLevel level, VillageState village) {
		for (VillageWorksData.Entry e : st.entries) {
			if (e.state != 2) {
				continue;
			}
			WorkSpec spec = WorkSpec.byId(e.id);
			if (spec == null || spec.category() == WorkCategory.WALL && spec.arg().equals("battlements")) {
				continue;
			}
			int sig = signature(spec, plan, level, village);
			boolean changed = spec.category() == WorkCategory.ROADS ? sig != e.ringMask : (sig & ~e.ringMask) != 0;
			if (changed) {
				e.state = 0;
				e.index = 0;
				CACHE.remove(cacheKey(village, e.id));
				works.changed();
			}
		}
	}

	static String cacheKey(VillageState village, String id) {
		return village.key() + "|" + id;
	}

	/**
	 * Faz a proxima obra da fila: ate {@link #BLOCKS_PER_TICK} blocos. {@code instant}: tudo de uma vez (testes).
	 * Devolve true se ainda ha obra pendente.
	 */
	static boolean run(MinecraftServer server, ServerLevel level, VillageState village, VillageData data, VillageWorksData works,
			VillageWorksData.State st, boolean instant) {
		for (VillageWorksData.Entry e : st.entries) {
			if (e.state >= 2) {
				continue;
			}
			WorkSpec spec = WorkSpec.byId(e.id);
			if (spec == null || !st.enabled(spec.category())) {
				continue;
			}
			if (spec.category() == WorkCategory.WALL && (spec.arg().equals("palisade") || spec.arg().equals("gates")) && stoneWall(village, data)) {
				e.state = 3;
				works.changed();
				continue;
			}
			if (spec.category() == WorkCategory.WALL && spec.arg().equals("battlements") && village.built(VillageProject.WALL) == 0) {
				continue;
			}
			if (spec.id().equals("wall:historic") && village.built(VillageProject.WALL) == 0
					|| spec.id().equals("wall:barbican") && village.built(VillageProject.OUTER_WALL) == 0) {
				continue;
			}
			Cached cached = CACHE.get(cacheKey(village, e.id));
			if (cached == null) {
				WorkContext ctx = context(level, village, works, st);
				Blueprint bp = plan(spec, ctx, village);
				cached = new Cached(bp.ordered());
				CACHE.put(cacheKey(village, e.id), cached);
				e.total = cached.blocks().size();
				e.index = 0;
				PLACED.put(cacheKey(village, e.id), 0);
			}
			e.state = 1;
			boolean force = spec.category() == WorkCategory.ROADS || instant;
			int budget = instant ? Integer.MAX_VALUE : BLOCKS_PER_TICK;
			int examined = instant ? Integer.MAX_VALUE : EXAMINE_PER_TICK;
			List<WBlock> blocks = cached.blocks();
			boolean waiting = false;
			while (e.index < blocks.size() && budget > 0 && examined > 0) {
				Result r = place(level, village, st, blocks.get(e.index), spec, force);
				if (r == Result.WAIT) {
					waiting = true;
					break;
				}
				e.index++;
				examined--;
				if (r == Result.PLACED) {
					budget--;
					PLACED.merge(cacheKey(village, e.id), 1, Integer::sum);
				}
			}
			works.changed();
			if (!waiting && e.index >= blocks.size()) {
				finish(server, level, village, works, st, e, spec);
				return instant;
			}
			return true;
		}
		return false;
	}

	static void finish(MinecraftServer server, ServerLevel level, VillageState village, VillageWorksData works, VillageWorksData.State st,
			VillageWorksData.Entry e, WorkSpec spec) {
		e.state = 2;
		e.ringMask = signature(spec, village.layout().master(), level, village);
		int placed = PLACED.getOrDefault(cacheKey(village, e.id), 0);
		CACHE.remove(cacheKey(village, e.id));
		PLACED.remove(cacheKey(village, e.id));
		if (spec.category() == WorkCategory.WALL && spec.arg().equals("battlements")) {
			registerGates(level, village, st);
		}
		if (spec.id().equals("wall:historic")) {
			CrownServer.openHistoricGates(level, village, st);
		}
		works.changed();
		if (placed > 0) {
			Component message = Component.translatable("message.mlinnotas.village.work_done", village.name(), Component.translatable(spec.langKey()));
			BlockPos center = VillageServer.center(village);
			for (ServerPlayer player : level.players()) {
				if (player.blockPosition().closerThan(center, 100)) {
					player.sendSystemMessage(message);
				}
			}
			level.playSound(null, center, SoundEvents.VILLAGER_WORK_MASON, SoundSource.NEUTRAL, 0.8F, 1.0F);
		}
	}

	/** Termina na hora toda a fila da vila (testes, comando de operador). Devolve quantas obras terminaram. */
	public static int runAll(ServerLevel level, VillageState village) {
		MinecraftServer server = level.getServer();
		VillageData data = VillageData.get(server);
		VillageWorksData works = VillageWorksData.get(server);
		VillageWorksData.State st = works.state(village.key());
		MasterPlan plan = village.layout().master();
		plan.setOwnedColumns(st.owned.columns());
		enqueue(works, st, village);
		reopen(works, st, plan, level, village);
		int done = 0;
		for (int guard = 0; guard < 200; guard++) {
			int before = (int) st.entries.stream().filter(e -> e.state >= 2).count();
			if (!run(server, level, village, data, works, st, true)) {
				break;
			}
			done += (int) st.entries.stream().filter(e -> e.state >= 2).count() - before;
		}
		return done;
	}

	// ---------------------------------------------------------------- colocar um bloco

	enum Result {
		PLACED, SAME, SKIPPED, WAIT
	}

	/** Chao que uma obra pode trocar: natural, caminho de terra, vazio e, para pontes e chafariz, agua. */
	static boolean surfaceFree(BlockState cur, WorkSpec spec, WBlock b) {
		if (cur.isAir() || WorldTerrain.replaceable(cur) || cur.is(Blocks.DIRT_PATH) || cur.is(Blocks.FARMLAND)) {
			return true;
		}
		boolean water = cur.getFluidState().is(net.minecraft.tags.FluidTags.WATER);
		if (water && (spec.category() == WorkCategory.BRIDGES || spec.category() == WorkCategory.PLAZA || spec.category() == WorkCategory.RURAL)) {
			return true;
		}
		if (spec.category() == WorkCategory.PLAZA) {
			// calcamento da praca paga (tijolos, pedregulho, arenito) pode ser trocado pelo desenho novo
			String id = id(cur);
			return id.endsWith("stone_bricks") || id.endsWith("cobblestone") || id.endsWith("sandstone") || id.endsWith("gravel") || id.endsWith("andesite");
		}
		return false;
	}

	static Result place(ServerLevel level, VillageState village, VillageWorksData.State st, WBlock b, WorkSpec spec, boolean force) {
		BlockPos pos = new BlockPos(village.x() + b.x(), b.y(), village.z() + b.z());
		if (!level.isLoaded(pos)) {
			if (!force) {
				return Result.WAIT;
			}
			level.getChunk(pos);
		}
		BlockState cur = level.getBlockState(pos);
		BlockState target = VanillaRemakePiece.parse(b.state());
		if (cur == target) {
			return Result.SAME;
		}
		boolean mine = st.owned.owns(b.key(), id(cur));
		boolean ok = mine || (b.kind() == WBlock.Kind.SURFACE ? surfaceFree(cur, spec, b) : cur.isAir() || WorldTerrain.clearable(cur));
		if (!ok) {
			return Result.SKIPPED;
		}
		Block block = target.getBlock();
		if (block instanceof CrossCollisionBlock || block instanceof WallBlock || block instanceof StairBlock || block instanceof FenceGateBlock) {
			target = Block.updateFromNeighbourShapes(target, level, pos);
		}
		if (b.kind() == WBlock.Kind.SURFACE && target.getFluidState().isEmpty() && !target.isAir()) {
			// planta em cima do chao novo sai (caminho e pedra nao convivem com mato)
			BlockPos up = pos.above();
			BlockState above = level.getBlockState(up);
			if (!above.isAir() && WorldTerrain.clearable(above) && !st.owned.recorded(WBlock.above(b.x(), b.y() + 1, b.z(), "").key())) {
				level.setBlock(up, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
		level.setBlock(pos, target, Block.UPDATE_ALL);
		if (b.text() != null) {
			BlockEntity entity = level.getBlockEntity(pos);
			if (entity instanceof SignBlockEntity sign) {
				List<Component> lines = new ArrayList<>();
				for (int i = 0; i < SignText.LINES; i++) {
					String t = i < b.text().length ? b.text()[i] : "";
					lines.add(t.startsWith("tr:") ? Component.translatable(t.substring(3)) : Component.literal(t));
				}
				sign.setText(new SignText(lines, lines, DyeColor.BLACK, false), SignTextSlot.FRONT);
				sign.setChanged();
				level.sendBlockUpdated(pos, target, target, Block.UPDATE_ALL);
			}
		}
		st.owned.record(b, id(target), spec.id());
		if (level.getRandom().nextInt(6) == 0) {
			VillageBuilder.effects(level, pos, b.y());
		}
		return Result.PLACED;
	}

	// ---------------------------------------------------------------- ganchos das obras pagas

	/** Uma obra paga vai comecar no lote: tira o que as obras publicas puseram dentro do lote (estacas, placa, jardim, horta). */
	public static void releaseLot(ServerLevel level, VillageState village, int lotId) {
		VillageLayout layout = village.layout();
		if (layout == null || layout.master() == null || lotId < 0 || layout.lot(lotId) == null) {
			return;
		}
		MasterPlan plan = layout.master();
		MasterPlan.Parcel parcel = plan.parcel(layout.lot(lotId).parcel());
		VillageState.Rect r = layout.lot(lotId).rect();
		int x0 = r.x0() - 1;
		int z0 = r.z0() - 1;
		int x1 = r.x1() + 1;
		int z1 = r.z1() + 1;
		if (parcel != null && !parcel.retired()) {
			x0 = Math.min(x0, parcel.rect().x0() - 1);
			z0 = Math.min(z0, parcel.rect().z0() - 1);
			x1 = Math.max(x1, parcel.rect().x1() + 1);
			z1 = Math.max(z1, parcel.rect().z1() + 1);
		}
		VillageWorksData works = VillageWorksData.get(level.getServer());
		VillageWorksData.State st = works.peek(village.key());
		if (st == null) {
			return;
		}
		clear(level, village, plan, st, st.owned.inRect(x0, z0, x1, z1));
		works.changed();
	}

	/** Um lote livre do plano foi aposentado por um recorte sob demanda: tira o que as obras publicas puseram nele. */
	public static void releaseArea(ServerLevel level, VillageState village, VillageState.Rect r) {
		if (village.layout() == null || village.layout().master() == null) {
			return;
		}
		VillageWorksData works = VillageWorksData.get(level.getServer());
		VillageWorksData.State st = works.peek(village.key());
		if (st == null) {
			return;
		}
		clear(level, village, village.layout().master(), st, st.owned.inRect(r.x0() - 1, r.z0() - 1, r.x1() + 1, r.z1() + 1));
		works.changed();
	}

	/** A muralha de pedra vai comecar: palicada e portoes de madeira saem (a obra de pedra faz as proprias passagens). */
	public static void beforeWall(ServerLevel level, VillageState village) {
		VillageWorksData works = VillageWorksData.get(level.getServer());
		VillageWorksData.State st = works.peek(village.key());
		if (st == null || village.layout() == null || village.layout().master() == null) {
			return;
		}
		List<long[]> mine = new ArrayList<>(st.owned.ofWork("wall:palisade"));
		mine.addAll(st.owned.ofWork("wall:gates"));
		clear(level, village, village.layout().master(), st, mine);
		for (VillageWorksData.Entry e : st.entries) {
			if (e.id.equals("wall:palisade") || e.id.equals("wall:gates")) {
				e.state = 3;
				CACHE.remove(cacheKey(village, e.id));
			}
		}
		works.changed();
	}

	/** Tira os blocos anotados que ainda sao os mesmos; o chao volta a grama. */
	static void clear(ServerLevel level, VillageState village, MasterPlan plan, VillageWorksData.State st, List<long[]> blocks) {
		blocks.sort(java.util.Comparator.<long[]>comparingLong(k -> -((k[0] >> 20) & 0xFFFFF)));
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (long[] k : blocks) {
			int x = (int) (k[0] >> 40) - 2048;
			int y = (int) ((k[0] >> 20) & 0xFFFFF) - 2048;
			int z = (int) (k[0] & 0xFFFFF) - 2048;
			pos.set(village.x() + x, y, village.z() + z);
			level.getChunk(pos);
			BlockState cur = level.getBlockState(pos);
			if (id(cur).equals(st.owned.blockId((int) k[1]))) {
				int ground = plan.plannedHeight(x, z);
				BlockState replacement = y == ground ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState();
				level.setBlock(pos, replacement, Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
			}
			st.owned.forgetAt(k[0]);
		}
	}

	// ---------------------------------------------------------------- chefes derrotados

	private static void onDeath(LivingEntity entity, DamageSource source) {
		String boss = entity instanceof BossMob mob ? mob.kind().id() : entity instanceof EnderDragon ? "ender_dragon" : entity instanceof WitherBoss ? "wither" : null;
		if (boss != null && entity.level() instanceof ServerLevel level) {
			VillageWorksData works = VillageWorksData.get(level.getServer());
			works.lastBoss = boss;
			works.changed();
		}
	}

	// ---------------------------------------------------------------- grades de ferro dos portoes

	/** Registra as grades dos portoes (uma vez, quando as ameias terminam) e poe as alavancas. */
	static void registerGates(ServerLevel level, VillageState village, VillageWorksData.State st) {
		if (!st.gates.isEmpty()) {
			return;
		}
		WorkContext ctx = context(level, village, VillageWorksData.get(level.getServer()), st);
		for (WallWorks.Portcullis p : WallWorks.portcullises(ctx)) {
			int[] bars = new int[p.bars().size() * 3];
			for (int i = 0; i < p.bars().size(); i++) {
				bars[i * 3] = p.bars().get(i)[0];
				bars[i * 3 + 1] = p.bars().get(i)[1];
				bars[i * 3 + 2] = p.bars().get(i)[2];
			}
			st.gates.add(new VillageWorksData.GateState(bars, p.lever(), p.leverState(), false));
			BlockPos lever = new BlockPos(village.x() + p.lever()[0], p.lever()[1], village.z() + p.lever()[2]);
			level.getChunk(lever);
			if (level.getBlockState(lever).isAir()) {
				level.setBlock(lever, VanillaRemakePiece.parse(p.leverState()), Block.UPDATE_ALL);
			}
		}
	}

	/** Fecha as grades a noite e em ataque; a alavanca ligada mantem aberto. */
	static void gates(ServerLevel level, VillageState village, VillageWorksData.State st, VillageWorksData works) {
		for (VillageWorksData.GateState g : st.gates) {
			BlockPos lever = new BlockPos(village.x() + g.lever[0], g.lever[1], village.z() + g.lever[2]);
			if (!level.isLoaded(lever)) {
				continue;
			}
			BlockState leverState = level.getBlockState(lever);
			boolean opened = leverState.getBlock() instanceof LeverBlock && leverState.getValue(LeverBlock.POWERED);
			BlockPos center = new BlockPos(village.x() + g.bars[0], g.bars[1], village.z() + g.bars[2]);
			boolean danger = level.isDarkOutside() || level.getRaids().getNearbyRaid(center, 96 * 96) != null;
			boolean close = danger && !opened;
			if (close == g.closed) {
				continue;
			}
			g.closed = close;
			BlockState bars = close ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState();
			for (int i = 0; i + 2 < g.bars.length; i += 3) {
				BlockPos pos = new BlockPos(village.x() + g.bars[i], g.bars[i + 1], village.z() + g.bars[i + 2]);
				BlockState now = level.getBlockState(pos);
				if (close && (now.isAir() || WorldTerrain.clearable(now))) {
					level.setBlock(pos, bars, Block.UPDATE_ALL);
				} else if (!close && now.is(Blocks.IRON_BARS)) {
					level.setBlock(pos, bars, Block.UPDATE_ALL);
				}
			}
			level.playSound(null, center, close ? SoundEvents.IRON_DOOR_CLOSE : SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0F, 0.8F);
			works.changed();
		}
	}

	// ---------------------------------------------------------------- tela do quadro

	/** Acao do quadro: {@code list} manda a fila; {@code toggle:<categoria>} liga/desliga uma categoria. */
	public static void action(ServerPlayer player, VillageState village, String arg) {
		MinecraftServer server = player.level().getServer();
		VillageWorksData works = VillageWorksData.get(server);
		VillageWorksData.State st = works.state(village.key());
		if (arg.startsWith("toggle:")) {
			WorkCategory category = WorkCategory.byId(arg.substring(7));
			if (category != null) {
				st.disabled ^= category.bit();
				works.changed();
			}
		}
		if (arg.equals("list") || arg.startsWith("toggle:")) {
			send(player, village, st);
		}
	}

	static void send(ServerPlayer player, VillageState village, VillageWorksData.State st) {
		VillageData data = VillageData.get(player.level().getServer());
		List<WorksPayloads.Row> rows = new ArrayList<>();
		for (VillageWorksData.Entry e : st.entries) {
			rows.add(new WorksPayloads.Row(e.id, e.state, e.index, e.total));
		}
		boolean paid = data.job(village.key()) != null || data.terrainJob(village.key()) != null;
		ServerPlayNetworking.send(player, new WorksPayloads.Sync(village.key(), rows, st.disabled, paid));
	}
}
