package com.mlin.notas.compass;

import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillServer;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Lado servidor das bussolas: monta a lista (biomas possiveis da dimensao; estruturas dos structure sets que o gerador
 * da dimensao usa, agrupadas e com os covis travados pela Mineracao) e roda as buscas aos poucos no fim de cada tick,
 * sem travar o servidor:
 * <ul>
 *   <li>bioma: espiral de colunas a cada 32 blocos ate 6400 (como o {@code /locate biome}), varias alturas por coluna,
 *   com orcamento de tempo por tick;</li>
 *   <li>estrutura: {@code ChunkGenerator.findNearestMapStructure} com o raio (em celulas da grade da estrutura)
 *   crescendo 1 por tick ate {@link #STRUCTURE_RADIUS}; os aneis de dentro ja ficam no cache do StructureCheck.</li>
 * </ul>
 */
public final class CompassServer {
	public static final int BIOME_RADIUS = 6400;
	public static final int BIOME_STEP = 32;
	public static final int BIOME_VERTICAL_STEP = 64;
	public static final int STRUCTURE_RADIUS = 32;
	/** Orcamento de CPU por tick para todas as buscas de bioma juntas. */
	private static final long BUDGET_NANOS = 4_000_000L;

	private static final Map<UUID, Job> JOBS = new LinkedHashMap<>();

	private CompassServer() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(CompassPayloads.Open.TYPE, CompassPayloads.Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(CompassPayloads.Select.TYPE, CompassPayloads.Select.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(CompassPayloads.Select.TYPE, (payload, context) -> select(context.player(), payload));
		ServerTickEvents.END_SERVER_TICK.register(CompassServer::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> JOBS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> JOBS.clear());
	}

	// ---- lista ----

	/** Entradas da bussola {@code kind} na dimensao de {@code level}, ja filtradas pela trava de Mineracao. */
	public static List<String> entries(ServerLevel level, CompassKind kind) {
		if (kind == CompassKind.BIOME) {
			List<String> out = new ArrayList<>();
			for (Holder<Biome> biome : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
				biome.unwrapKey().ifPresent(key -> out.add(key.identifier().toString()));
			}
			out.sort(String::compareTo);
			return out;
		}
		int mining = SkillServer.progress(level.getServer()).level(SkillBranch.MINING);
		return CompassCatalog.structureEntries(structures(level).keySet(), mining);
	}

	/** Estruturas que o gerador desta dimensao pode colocar (id -> holder). */
	private static Map<String, Holder<Structure>> structures(ServerLevel level) {
		Map<String, Holder<Structure>> out = new HashMap<>();
		for (Holder<StructureSet> set : level.getChunkSource().getGeneratorState().possibleStructureSets()) {
			for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
				entry.structure().unwrapKey().ifPresent(key -> out.put(key.identifier().toString(), entry.structure()));
			}
		}
		return out;
	}

	public static void open(ServerPlayer player, CompassKind kind, ItemStack stack) {
		if (player.connection == null) {
			return;
		}
		CompassTarget current = stack.get(Compasses.TARGET);
		ServerPlayNetworking.send(player, new CompassPayloads.Open(kind.id(), entries(player.level(), kind), current == null ? "" : current.id()));
	}

	private static void select(ServerPlayer player, CompassPayloads.Select payload) {
		CompassKind kind = CompassKind.byId(payload.kind());
		ItemStack stack = findCompass(player, kind);
		if (stack != null && entries(player.level(), kind).contains(payload.id())) {
			startSearch(player, stack, kind, payload.id());
		}
	}

	/** A bussola do tipo pedido: mao principal, mao secundaria ou o primeiro no inventario. */
	private static ItemStack findCompass(ServerPlayer player, CompassKind kind) {
		for (InteractionHand hand : InteractionHand.values()) {
			ItemStack stack = player.getItemInHand(hand);
			if (stack.getItem() instanceof ExplorerCompassItem item && item.kind() == kind) {
				return stack;
			}
		}
		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (stack.getItem() instanceof ExplorerCompassItem item && item.kind() == kind) {
				return stack;
			}
		}
		return null;
	}

	// ---- busca ----

	/** Comeca (ou recomeca) a busca de {@code id} a partir da posicao atual do jogador; substitui a busca anterior dele. */
	public static boolean startSearch(ServerPlayer player, ItemStack stack, CompassKind kind, String id) {
		ServerLevel level = player.level();
		Job job = kind == CompassKind.BIOME ? biomeJob(level, player.blockPosition(), id) : structureJob(level, player.blockPosition(), id);
		if (job == null) {
			stack.set(Compasses.TARGET, new CompassTarget(id, CompassTarget.NOT_FOUND));
			stack.remove(DataComponents.LODESTONE_TRACKER);
			message(player, Component.translatable("compass.mlinnotas.not_found", ExplorerCompassItem.targetName(kind, id)).withStyle(ChatFormatting.RED));
			return false;
		}
		job.player = player.getUUID();
		job.stack = stack;
		job.kind = kind;
		job.id = id;
		stack.set(Compasses.TARGET, new CompassTarget(id, CompassTarget.SEARCHING));
		stack.remove(DataComponents.LODESTONE_TRACKER);
		JOBS.put(player.getUUID(), job);
		message(player, Component.translatable("compass.mlinnotas.searching", ExplorerCompassItem.targetName(kind, id)).withStyle(ChatFormatting.YELLOW));
		return true;
	}

	/** Busca em andamento do jogador (para testes). */
	public static boolean searching(UUID player) {
		return JOBS.containsKey(player);
	}

	private static Job biomeJob(ServerLevel level, BlockPos origin, String id) {
		Holder<Biome> target = null;
		for (Holder<Biome> biome : level.getChunkSource().getGenerator().getBiomeSource().possibleBiomes()) {
			if (biome.unwrapKey().map(k -> k.identifier().toString().equals(id)).orElse(false)) {
				target = biome;
			}
		}
		if (target == null) {
			return null;
		}
		BiomeJob job = new BiomeJob();
		job.level = level;
		job.origin = origin;
		job.biome = target;
		job.resolver = level.getChunkSource().getGenerator().getBiomeSource().createCachingResolver(level.getChunkSource().randomState());
		job.spiral = new SpiralSearch(origin.getX(), origin.getZ(), BIOME_RADIUS, BIOME_STEP);
		job.ys = Mth.outFromOrigin(origin.getY(), level.getMinY() + 1, level.getMaxY() + 1, BIOME_VERTICAL_STEP).toArray();
		return job;
	}

	private static Job structureJob(ServerLevel level, BlockPos origin, String id) {
		Map<String, Holder<Structure>> all = structures(level);
		List<Holder<Structure>> wanted = new ArrayList<>();
		for (String structureId : CompassCatalog.structuresOf(id, all.keySet())) {
			wanted.add(all.get(structureId));
		}
		if (wanted.isEmpty()) {
			return null;
		}
		StructureJob job = new StructureJob();
		job.level = level;
		job.origin = origin;
		job.structures = HolderSet.direct(wanted);
		return job;
	}

	private static void tick(MinecraftServer server) {
		if (JOBS.isEmpty()) {
			return;
		}
		long deadline = System.nanoTime() + BUDGET_NANOS;
		Iterator<Map.Entry<UUID, Job>> it = JOBS.entrySet().iterator();
		while (it.hasNext()) {
			Job job = it.next().getValue();
			ServerPlayer player = server.getPlayerList().getPlayer(job.player);
			CompassTarget target = job.stack.get(Compasses.TARGET);
			if (player == null || target == null || !target.id().equals(job.id)) {
				it.remove();
				continue;
			}
			BlockPos found;
			try {
				found = job.step(deadline);
			} catch (RuntimeException e) {
				com.mlin.notas.MlinNotas.LOGGER.warn("[compass] busca de {} falhou", job.id, e);
				found = null;
				job.done = true;
			}
			if (found != null) {
				finish(player, job, found);
				it.remove();
			} else if (job.done) {
				job.stack.set(Compasses.TARGET, target.withStatus(CompassTarget.NOT_FOUND));
				message(player, Component.translatable("compass.mlinnotas.not_found", ExplorerCompassItem.targetName(job.kind, job.id))
						.withStyle(ChatFormatting.RED));
				it.remove();
			} else if (++job.ticks % 40 == 0) {
				message(player, Component.translatable("compass.mlinnotas.searching", ExplorerCompassItem.targetName(job.kind, job.id)).withStyle(ChatFormatting.YELLOW));
			}
		}
	}

	private static void finish(ServerPlayer player, Job job, BlockPos found) {
		job.stack.set(Compasses.TARGET, new CompassTarget(job.id, CompassTarget.FOUND));
		job.stack.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(Optional.of(GlobalPos.of(job.level.dimension(), found)), false));
		int distance = CompassCatalog.horizontalDistance(player.getX(), player.getZ(), found.getX() + 0.5, found.getZ() + 0.5);
		String dir = CompassCatalog.direction(player.getX(), player.getZ(), found.getX() + 0.5, found.getZ() + 0.5);
		Component name = ExplorerCompassItem.targetName(job.kind, job.id);
		message(player, (dir.equals("here") ? Component.translatable("compass.mlinnotas.found_here", name)
				: Component.translatable("compass.mlinnotas.found", name, distance, Component.translatable("compass.mlinnotas.dir." + dir)))
				.withStyle(ChatFormatting.GREEN));
	}

	private static void message(ServerPlayer player, Component text) {
		if (player.connection != null) {
			player.sendOverlayMessage(text);
		}
	}

	private abstract static class Job {
		UUID player;
		ItemStack stack;
		CompassKind kind;
		String id;
		ServerLevel level;
		BlockPos origin;
		int ticks;
		boolean done;

		/** Avanca a busca; devolve a posicao achada, ou null (veja {@link #done}). */
		abstract BlockPos step(long deadline);
	}

	private static final class BiomeJob extends Job {
		Holder<Biome> biome;
		BiomeResolver resolver;
		SpiralSearch spiral;
		int[] ys;

		@Override
		BlockPos step(long deadline) {
			int columns = 0;
			while (true) {
				if (!spiral.next()) {
					done = true;
					return null;
				}
				int qx = QuartPos.fromBlock(spiral.x());
				int qz = QuartPos.fromBlock(spiral.z());
				for (int y : ys) {
					if (resolver.getNoiseBiome(qx, QuartPos.fromBlock(y), qz).is(biome)) {
						return new BlockPos(spiral.x(), y, spiral.z());
					}
				}
				// confere o relogio a cada 16 colunas; pelo menos 16 colunas por tick mesmo com o orcamento estourado
				if (++columns % 16 == 0 && System.nanoTime() > deadline) {
					return null;
				}
			}
		}
	}

	private static final class StructureJob extends Job {
		HolderSet<Structure> structures;
		int radius;

		@Override
		BlockPos step(long deadline) {
			Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator()
					.findNearestMapStructure(level, structures, origin, radius, false);
			if (result != null) {
				return result.getFirst();
			}
			if (++radius > STRUCTURE_RADIUS) {
				done = true;
			}
			return null;
		}
	}
}
