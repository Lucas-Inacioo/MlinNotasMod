package com.mlin.notas.village;

import com.mlin.notas.MlinNotas;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * Todas as vilas do mundo e as obras em andamento, salvas no armazenamento do Overworld
 * ({@code data/mlinnotas/villages.dat}). A logica fica em {@link VillageState}; aqui so o formato salvo. Campos novos
 * (plano, itens pagos, registro dos blocos de cada obra, lugar da vila inicial) sao todos opcionais: o arquivo antigo
 * carrega com eles vazios e o servidor calcula o plano depois.
 */
public class VillageData extends SavedData {
	private static final Codec<Map<String, Integer>> INT_MAP = Codec.unboundedMap(Codec.STRING, Codec.INT);

	/** Lista de inteiros opcional (vazia se faltar); cada leitura ganha um fluxo novo (o padrao compartilhado so serve uma vez). */
	private static com.mojang.serialization.MapCodec<IntStream> ints(String name) {
		return Codec.INT_STREAM.optionalFieldOf(name).xmap(o -> o.orElseGet(IntStream::empty), Optional::of);
	}

	private static com.mojang.serialization.MapCodec<LongStream> longs(String name) {
		return Codec.LONG_STREAM.optionalFieldOf(name).xmap(o -> o.orElseGet(LongStream::empty), Optional::of);
	}

	public static final Codec<Bounty> BOUNTY_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("kind").forGetter(b -> b.kind().id()),
			Codec.STRING.fieldOf("target").forGetter(Bounty::target),
			Codec.INT.fieldOf("count").forGetter(Bounty::count),
			Codec.INT.fieldOf("emeralds").forGetter(Bounty::emeralds),
			Codec.STRING.fieldOf("reward").forGetter(Bounty::rewardItem),
			Codec.INT.fieldOf("reward_count").forGetter(Bounty::rewardCount),
			Codec.INT.fieldOf("prosperity").forGetter(Bounty::prosperity)
	).apply(i, (kind, target, count, emeralds, reward, rewardCount, prosperity) ->
			new Bounty(Bounty.Kind.byId(kind), target, count, emeralds, reward, rewardCount, prosperity)));

	private record Core(String dimension, int x, int y, int z, long seed, String style, String name, boolean starter) {
	}

	private static final Codec<Core> CORE_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("dimension").forGetter(Core::dimension),
			Codec.INT.fieldOf("x").forGetter(Core::x),
			Codec.INT.fieldOf("y").forGetter(Core::y),
			Codec.INT.fieldOf("z").forGetter(Core::z),
			Codec.LONG.fieldOf("seed").forGetter(Core::seed),
			Codec.STRING.fieldOf("style").forGetter(Core::style),
			Codec.STRING.fieldOf("name").forGetter(Core::name),
			Codec.BOOL.optionalFieldOf("starter", false).forGetter(Core::starter)
	).apply(i, Core::new));

	private record Progress(int level, int prosperity, Map<String, Integer> built, Map<String, Map<String, Integer>> deposits, List<String> queue,
			List<Integer> lots, long bountyDay, List<Bounty> bounties, List<Integer> bountyProgress, List<Boolean> bountyDone,
			Map<String, Map<String, Integer>> paid, List<Map<String, Integer>> queuePaid) {
	}

	private static final Codec<Progress> PROGRESS_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("level", 0).forGetter(Progress::level),
			Codec.INT.optionalFieldOf("prosperity", 0).forGetter(Progress::prosperity),
			INT_MAP.optionalFieldOf("built", Map.of()).forGetter(Progress::built),
			Codec.unboundedMap(Codec.STRING, INT_MAP).optionalFieldOf("deposits", Map.of()).forGetter(Progress::deposits),
			Codec.STRING.listOf().optionalFieldOf("queue", List.of()).forGetter(Progress::queue),
			Codec.INT.listOf().optionalFieldOf("lots", List.of()).forGetter(Progress::lots),
			Codec.LONG.optionalFieldOf("bounty_day", Long.MIN_VALUE).forGetter(Progress::bountyDay),
			BOUNTY_CODEC.listOf().optionalFieldOf("bounties", List.of()).forGetter(Progress::bounties),
			Codec.INT.listOf().optionalFieldOf("bounty_progress", List.of()).forGetter(Progress::bountyProgress),
			Codec.BOOL.listOf().optionalFieldOf("bounty_done", List.of()).forGetter(Progress::bountyDone),
			Codec.unboundedMap(Codec.STRING, INT_MAP).optionalFieldOf("paid", Map.of()).forGetter(Progress::paid),
			INT_MAP.listOf().optionalFieldOf("queue_paid", List.of()).forGetter(Progress::queuePaid)
	).apply(i, Progress::new));

	private static final Codec<VillageLayout.Lot> LOT_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("id").forGetter(VillageLayout.Lot::id),
			Codec.STRING.optionalFieldOf("kind", "").forGetter(VillageLayout.Lot::kind),
			Codec.INT.fieldOf("x").forGetter(VillageLayout.Lot::x),
			Codec.INT.fieldOf("z").forGetter(VillageLayout.Lot::z),
			Codec.INT.optionalFieldOf("rot", 0).forGetter(VillageLayout.Lot::rot),
			Codec.INT.fieldOf("cw").forGetter(VillageLayout.Lot::cw),
			Codec.INT.fieldOf("cd").forGetter(VillageLayout.Lot::cd),
			Codec.INT.optionalFieldOf("door", 0).forGetter(VillageLayout.Lot::door),
			Codec.LONG.optionalFieldOf("seed", 0L).forGetter(VillageLayout.Lot::seed),
			Codec.BOOL.optionalFieldOf("built", false).forGetter(VillageLayout.Lot::built),
			Codec.BOOL.optionalFieldOf("legacy", false).forGetter(VillageLayout.Lot::legacy),
			Codec.BOOL.optionalFieldOf("preferred", false).forGetter(VillageLayout.Lot::preferred),
			ints("access").forGetter(l -> l.access().stream().flatMapToInt(IntStream::of)),
			INT_MAP.optionalFieldOf("paid", Map.of()).forGetter(VillageLayout.Lot::paid),
			Codec.INT.optionalFieldOf("parcel", -1).forGetter(VillageLayout.Lot::parcel)
	).apply(i, (id, kind, x, z, rot, cw, cd, door, seed, built, legacy, preferred, access, paid, parcel) ->
			VillageLayout.restoreLot(id, kind, x, z, rot, cw, cd, door, seed, built, legacy, preferred, pairs(access.toArray()), paid, parcel)));

	/**
	 * Plano diretor: grade (pares valor/repeticoes), anel de cada celula, altura das ruas, quarteiroes (id, anel, alvo,
	 * celulas, cx, cz, pronto), lotes (id, quarteirao, x0, z0, x1, z1, frente, tamanho, zona, jogador) e recortes.
	 */
	public static final Codec<MasterPlan> MASTER_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("version", MasterPlan.VERSION_SPARSE).forGetter(MasterPlan::version),
			Codec.LONG.optionalFieldOf("seed", 0L).forGetter(MasterPlan::seed),
			Codec.INT.fieldOf("extent").forGetter(MasterPlan::extent),
			Codec.INT.fieldOf("plaza_y").forGetter(MasterPlan::plazaY),
			Codec.INT_STREAM.fieldOf("grid").forGetter(m -> IntStream.of(m.gridRle())),
			Codec.INT_STREAM.fieldOf("rings").forGetter(m -> IntStream.of(m.ringRle())),
			ints("heights").forGetter(m -> IntStream.of(m.streetHeights())),
			ints("blocks").forGetter(m -> m.blocks().stream().flatMapToInt(b -> IntStream.of(b.id(),
					b.ring(), b.target(), b.cells(), b.cx(), b.cz(), b.prepared() ? 1 : 0))),
			ints("parcels").forGetter(m -> m.parcels().stream().flatMapToInt(p -> IntStream.of(p.id(),
					p.block(), p.rect().x0(), p.rect().z0(), p.rect().x1(), p.rect().z1(), p.front().ordinal(), p.size().ordinal(), p.zone().ordinal(),
					p.flags()))),
			ints("player_areas").forGetter(m -> rects(m.playerAreas())),
			ints("fixed_areas").forGetter(m -> rects(m.fixedAreas()))
	).apply(i, (version, seed, extent, plazaY, grid, ring, heights, blocks, parcels, player, fixed) -> {
		int[] b = blocks.toArray();
		List<MasterPlan.Block> blockList = new ArrayList<>();
		for (int k = 0; k + 6 < b.length; k += 7) {
			blockList.add(new MasterPlan.Block(b[k], b[k + 1], b[k + 2], b[k + 3], b[k + 4], b[k + 5], b[k + 6] != 0));
		}
		int[] p = parcels.toArray();
		List<MasterPlan.Parcel> parcelList = new ArrayList<>();
		for (int k = 0; k + 9 < p.length; k += 10) {
			MasterPlan.Parcel parcel = new MasterPlan.Parcel(p[k], p[k + 1], new VillageState.Rect(p[k + 2], p[k + 3], p[k + 4], p[k + 5]),
					Facing.values()[Math.floorMod(p[k + 6], 4)], SizeClass.byOrdinal(p[k + 7]), Zone.byOrdinal(p[k + 8]), false);
			parcel.restoreFlags(p[k + 9]);
			parcelList.add(parcel);
		}
		MasterPlan restored = MasterPlan.restore(seed, extent, plazaY, grid.toArray(), ring.toArray(), heights.toArray(), blockList, parcelList,
				rects(player.toArray()), rects(fixed.toArray()));
		restored.setVersion(version);
		return restored;
	}));

	private static final Codec<RingWork> RING_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("ring").forGetter(RingWork::ring),
			Codec.INT.optionalFieldOf("state", 0).forGetter(w -> w.state().ordinal()),
			Codec.INT.optionalFieldOf("emeralds", 0).forGetter(RingWork::emeralds),
			Codec.INT.optionalFieldOf("fill", 0).forGetter(RingWork::fill),
			Codec.INT.optionalFieldOf("dep_emeralds", 0).forGetter(RingWork::depositedEmeralds),
			Codec.INT.optionalFieldOf("dep_fill", 0).forGetter(RingWork::depositedFill),
			Codec.INT.optionalFieldOf("columns", 0).forGetter(RingWork::columns),
			Codec.INT.optionalFieldOf("ready", 0).forGetter(RingWork::ready),
			Codec.INT.optionalFieldOf("cut", 0).forGetter(RingWork::cut),
			Codec.INT.optionalFieldOf("filled", 0).forGetter(RingWork::filled),
			INT_MAP.optionalFieldOf("paid", Map.of()).forGetter(RingWork::paid)
	).apply(i, (ring, state, emeralds, fill, depE, depF, columns, ready, cut, filled, paid) -> {
		RingWork w = new RingWork(ring);
		w.restore(RingWork.State.byOrdinal(state), emeralds, fill, depE, depF, columns, ready, cut, filled, paid);
		return w;
	}));

	private record TerrainPart(List<RingWork> rings, int stockpile, int playerHouses) {
	}

	private static final Codec<TerrainPart> TERRAIN_CODEC = RecordCodecBuilder.create(i -> i.group(
			RING_CODEC.listOf().optionalFieldOf("rings", List.of()).forGetter(TerrainPart::rings),
			Codec.INT.optionalFieldOf("stockpile", 0).forGetter(TerrainPart::stockpile),
			Codec.INT.optionalFieldOf("player_houses", 0).forGetter(TerrainPart::playerHouses)
	).apply(i, TerrainPart::new));

	private static List<int[]> pairs(int[] flat) {
		List<int[]> out = new ArrayList<>();
		for (int i = 0; i + 1 < flat.length; i += 2) {
			out.add(new int[] {flat[i], flat[i + 1]});
		}
		return out;
	}

	private static IntStream rects(List<VillageState.Rect> rects) {
		return rects.stream().flatMapToInt(r -> IntStream.of(r.x0(), r.z0(), r.x1(), r.z1()));
	}

	private static List<VillageState.Rect> rects(int[] flat) {
		List<VillageState.Rect> out = new ArrayList<>();
		for (int i = 0; i + 3 < flat.length; i += 4) {
			out.add(new VillageState.Rect(flat[i], flat[i + 1], flat[i + 2], flat[i + 3]));
		}
		return out;
	}

	/** Plano da vila: raio, lotes, ruas (colunas empacotadas), postes (x, z, construido) e areas protegidas. */
	public static final Codec<VillageLayout> LAYOUT_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("radius", 0).forGetter(VillageLayout::radius),
			Codec.INT.optionalFieldOf("next_id", 0).forGetter(VillageLayout::nextId),
			LOT_CODEC.listOf().optionalFieldOf("lots", List.of()).forGetter(VillageLayout::lots),
			longs("roads").forGetter(l -> l.roads().stream().mapToLong(Long::longValue)),
			ints("lamps").forGetter(l -> l.lamps().stream()
					.flatMapToInt(m -> IntStream.of(m.x(), m.z(), m.built() ? 1 : 0))),
			ints("protect").forGetter(l -> rects(l.protectedAreas())),
			MASTER_CODEC.optionalFieldOf("master").forGetter(l -> Optional.ofNullable(l.master()))
	).apply(i, (radius, nextId, lots, roads, lamps, protect, master) -> {
		VillageLayout layout = new VillageLayout();
		int[] flat = lamps.toArray();
		List<VillageLayout.Lamp> lampList = new ArrayList<>();
		for (int k = 0; k + 2 < flat.length; k += 3) {
			lampList.add(new VillageLayout.Lamp(flat[k], flat[k + 1], flat[k + 2] != 0));
		}
		layout.restore(radius, nextId, lots, roads.boxed().toList(), lampList, rects(protect.toArray()));
		layout.setMaster(master.orElse(null));
		return layout;
	}));

	/** Uma vila inteira (usado no arquivo e no pacote da tela). */
	public static final Codec<VillageState> VILLAGE_CODEC = RecordCodecBuilder.create(i -> i.group(
			CORE_CODEC.fieldOf("core").forGetter(VillageData::core),
			PROGRESS_CODEC.fieldOf("progress").forGetter(VillageData::progress),
			LAYOUT_CODEC.optionalFieldOf("layout").forGetter(v -> Optional.ofNullable(v.layout())),
			TERRAIN_CODEC.optionalFieldOf("terrain").forGetter(v -> v.ringWorks().isEmpty() && v.stockpile() == 0 && v.playerHouses() == 0
					? Optional.empty() : Optional.of(new TerrainPart(v.ringWorks(), v.stockpile(), v.playerHouses()))),
			BuildingsCodec.CODEC.optionalFieldOf("buildings").forGetter(v -> v.buildingsState().isEmpty() ? Optional.empty()
					: Optional.of(v.buildingsState()))
	).apply(i, (core, progress, layout, terrain, buildings) -> {
		VillageState v = fromParts(core, progress, layout);
		terrain.ifPresent(t -> v.restoreTerrain(t.rings(), t.stockpile(), t.playerHouses()));
		buildings.ifPresent(b -> BuildingsCodec.copy(b, v.buildingsState()));
		return v;
	}));

	/** Blocos que uma obra pronta colocou (demolir tira so os que ainda estao la). */
	public static final class BuildRecord {
		public final long[] positions;
		public final int[] blocks;
		public final List<String> palette;
		public final int ground;

		public BuildRecord(long[] positions, int[] blocks, List<String> palette, int ground) {
			this.positions = positions;
			this.blocks = blocks;
			this.palette = palette;
			this.ground = ground;
		}

		public int size() {
			return positions.length;
		}

		/** Bloco (id) colocado no indice {@code i}. */
		public String block(int i) {
			int b = blocks[i];
			return b >= 0 && b < palette.size() ? palette.get(b) : "";
		}
	}

	private record RecordEntry(String village, int lot, BuildRecord record) {
	}

	private static final Codec<RecordEntry> RECORD_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("village").forGetter(RecordEntry::village),
			Codec.INT.fieldOf("lot").forGetter(RecordEntry::lot),
			Codec.INT.optionalFieldOf("ground", 0).forGetter(e -> e.record().ground),
			Codec.LONG_STREAM.fieldOf("positions").forGetter(e -> LongStream.of(e.record().positions)),
			Codec.INT_STREAM.fieldOf("blocks").forGetter(e -> IntStream.of(e.record().blocks)),
			Codec.STRING.listOf().fieldOf("palette").forGetter(e -> e.record().palette)
	).apply(i, (village, lot, ground, positions, blocks, palette) -> new RecordEntry(village, lot,
			new BuildRecord(positions.toArray(), blocks.toArray(), palette, ground))));

	/** Obra em andamento: blocos que faltam (posicao, parte), aldeoes e golens que chegam no fim. */
	public static final class Job {
		public final String village;
		public final VillageProject project;
		public final long[] positions;
		public final int[] parts;
		public final int[] spawns;
		public final int[] golems;
		public int index;
		/** Lote do plano onde a obra vai (-1 = sem lote: praca, postes, muralha). */
		public final int lot;
		/** Altura do chao da obra (para devolver o chao natural ao demolir). */
		public final int ground;
		private final List<Long> recordPositions = new ArrayList<>();
		private final List<Integer> recordBlocks = new ArrayList<>();
		private final List<String> recordPalette = new ArrayList<>();
		/** Coluna (x, z empacotados) onde a obra parou porque o plato foi mexido; {@link Long#MIN_VALUE} = andando. */
		public long pausedAt = Long.MIN_VALUE;
		/** Melhoria no lugar: nivel que a obra do lote ganha no fim (0 = obra nova). */
		public int upgradeTo;
		/** Colunas do lote ja conferidas nesta execucao (nao salvo; as com bloco registrado contam como conferidas). */
		private final java.util.Set<Long> verified = new java.util.HashSet<>();

		public boolean paused() {
			return pausedAt != Long.MIN_VALUE;
		}


		public boolean isVerified(long col) {
			return verified.contains(col);
		}

		public void markVerified(long col) {
			verified.add(col);
		}

		/** Colunas (x, z absolutos empacotados como col) com bloco registrado: ja comecadas antes de reiniciar. */
		public java.util.Set<Long> recordedColumns() {
			java.util.Set<Long> out = new java.util.HashSet<>();
			for (long pos : recordPositions) {
				out.add(VillageLayout.col(net.minecraft.core.BlockPos.getX(pos), net.minecraft.core.BlockPos.getZ(pos)));
			}
			return out;
		}

		public Job(String village, VillageProject project, long[] positions, int[] parts, int[] spawns, int[] golems, int index) {
			this(village, project, positions, parts, spawns, golems, index, -1, 0);
		}

		public Job(String village, VillageProject project, long[] positions, int[] parts, int[] spawns, int[] golems, int index, int lot, int ground) {
			this.village = village;
			this.project = project;
			this.positions = positions;
			this.parts = parts;
			this.spawns = spawns;
			this.golems = golems;
			this.index = index;
			this.lot = lot;
			this.ground = ground;
		}

		/** Variante de telhado da obra (estavel entre salvar e carregar). */
		public int variant() {
			return positions.length == 0 ? 0 : VillageBuilder.variant(positions[0]);
		}

		public int total() {
			return positions.length;
		}

		public boolean done() {
			return index >= positions.length;
		}

		/** Bloco {@code i} da obra. */
		public Placement placement(int i) {
			long key = positions[i];
			int packed = parts[i];
			int x = (int) (key >>> 40) - 2048;
			int y = (int) ((key >>> 20) & 0xFFFFF) - 2048;
			int z = (int) (key & 0xFFFFF) - 2048;
			VPart part = VPart.values()[packed >>> 4];
			int dir = packed & 7;
			return new Placement(x, y, z, part, dir == 0 ? null : Facing.values()[dir - 1], (packed & 8) != 0);
		}

		/** Anota um bloco que a obra colocou (posicao absoluta empacotada e id do bloco). */
		public void record(long pos, String block) {
			int idx = recordPalette.indexOf(block);
			if (idx < 0) {
				idx = recordPalette.size();
				recordPalette.add(block);
			}
			recordPositions.add(pos);
			recordBlocks.add(idx);
		}

		public BuildRecord buildRecord() {
			return new BuildRecord(recordPositions.stream().mapToLong(Long::longValue).toArray(), recordBlocks.stream().mapToInt(Integer::intValue)
					.toArray(), List.copyOf(recordPalette), ground);
		}

		public static Job create(String village, VillagePlanner.Planned planned) {
			List<Placement> list = planned.placements();
			long[] positions = new long[list.size()];
			int[] parts = new int[list.size()];
			for (int i = 0; i < list.size(); i++) {
				Placement p = list.get(i);
				positions[i] = p.key();
				parts[i] = (p.part().ordinal() << 4) | (p.fill() ? 8 : 0) | (p.dir() == null ? 0 : p.dir().ordinal() + 1);
			}
			return new Job(village, planned.project(), positions, parts, flatten(planned.spawns()), flatten(planned.golems()), 0, planned.lotId(),
					planned.lot() == null ? 0 : planned.lot().groundY());
		}

		private static int[] flatten(List<int[]> points) {
			return points.stream().flatMapToInt(IntStream::of).toArray();
		}
	}

	private static final Codec<Job> JOB_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("village").forGetter(j -> j.village),
			Codec.STRING.fieldOf("project").forGetter(j -> j.project.id()),
			Codec.LONG_STREAM.fieldOf("positions").forGetter(j -> LongStream.of(j.positions)),
			Codec.INT_STREAM.fieldOf("parts").forGetter(j -> IntStream.of(j.parts)),
			ints("spawns").forGetter(j -> IntStream.of(j.spawns)),
			ints("golems").forGetter(j -> IntStream.of(j.golems)),
			Codec.INT.fieldOf("index").forGetter(j -> j.index),
			Codec.INT.optionalFieldOf("lot", -1).forGetter(j -> j.lot),
			Codec.INT.optionalFieldOf("ground", 0).forGetter(j -> j.ground),
			longs("rec_positions").forGetter(j -> j.recordPositions.stream().mapToLong(Long::longValue)),
			ints("rec_blocks").forGetter(j -> j.recordBlocks.stream().mapToInt(Integer::intValue)),
			Codec.STRING.listOf().optionalFieldOf("rec_palette", List.of()).forGetter(j -> j.recordPalette),
			Codec.LONG.optionalFieldOf("paused_at", Long.MIN_VALUE).forGetter(j -> j.pausedAt),
			Codec.INT.optionalFieldOf("upgrade_to", 0).forGetter(j -> j.upgradeTo)
	).apply(i, (village, project, positions, parts, spawns, golems, index, lot, ground, recPos, recBlocks, recPalette, paused, upgradeTo) -> {
		Job job = new Job(village, VillageProject.byId(project), positions.toArray(), parts.toArray(), spawns.toArray(), golems.toArray(), index, lot,
				ground);
		job.pausedAt = paused;
		job.upgradeTo = upgradeTo;
		long[] rp = recPos.toArray();
		int[] rb = recBlocks.toArray();
		job.recordPalette.addAll(recPalette);
		for (int k = 0; k < Math.min(rp.length, rb.length); k++) {
			job.recordPositions.add(rp[k]);
			job.recordBlocks.add(rb[k]);
		}
		return job;
	}));

	/**
	 * Terraplenagem de um anel em andamento: a vila, o anel e a proxima coluna (a lista de colunas sai do plano diretor,
	 * sempre na mesma ordem; cada coluna e lida do mundo na hora). Colunas acabadas: estacas nos cantos dos lotes.
	 */
	public static final class TerrainJob {
		public final String village;
		public final int ring;
		public int index;

		public TerrainJob(String village, int ring, int index) {
			this.village = village;
			this.ring = ring;
			this.index = index;
		}
	}

	private static final Codec<TerrainJob> TERRAIN_JOB_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("village").forGetter(j -> j.village),
			Codec.INT.fieldOf("ring").forGetter(j -> j.ring),
			Codec.INT.optionalFieldOf("index", 0).forGetter(j -> j.index)
	).apply(i, TerrainJob::new));


	/** Chave do campo de alturas da vila inicial calculado antes de a vila existir (lugar escolhido, quadro ainda nao). */
	public static final String PENDING_FIELD = "";

	private record FieldEntry(String village, HeightField field) {
	}

	/**
	 * Campo de alturas (Vilas 3.0) guardado a parte da vila: o {@link #VILLAGE_CODEC} tambem vai no pacote da tela e o
	 * campo nao precisa ir para o cliente. Campo de formato desconhecido some (o servidor recalcula).
	 */
	private static final Codec<Optional<FieldEntry>> FIELD_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("village").forGetter(e -> e.map(FieldEntry::village).orElse("")),
			Codec.INT_STREAM.fieldOf("header").forGetter(e -> IntStream.of(e.map(x -> x.field().header()).orElse(new int[0]))),
			Codec.BYTE_BUFFER.fieldOf("heights").forGetter(e -> java.nio.ByteBuffer.wrap(e.map(x -> x.field().encodeHeights()).orElse(new byte[0]))),
			Codec.BYTE_BUFFER.fieldOf("kinds").forGetter(e -> java.nio.ByteBuffer.wrap(e.map(x -> x.field().encodeKinds()).orElse(new byte[0])))
	).apply(i, (village, header, heights, kinds) -> {
		try {
			HeightField field = HeightField.decode(header.toArray(), bytes(heights), bytes(kinds));
			return field == null ? Optional.empty() : Optional.of(new FieldEntry(village, field));
		} catch (RuntimeException e) {
			MlinNotas.LOGGER.warn("[{}] campo de alturas de {} ilegivel; sera recalculado", MlinNotas.MOD_ID, village, e);
			return Optional.empty();
		}
	}));

	private static byte[] bytes(java.nio.ByteBuffer buffer) {
		java.nio.ByteBuffer b = buffer.duplicate();
		byte[] out = new byte[b.remaining()];
		b.get(out);
		return out;
	}

	private final Map<String, HeightField> fields = new LinkedHashMap<>();

	/** Campo de alturas da vila (ou {@link #PENDING_FIELD}); null se ainda nao foi calculado. */
	public HeightField field(String village) {
		return fields.get(village);
	}

	public void putField(String village, HeightField field) {
		fields.put(village, field);
		setDirty();
	}

	public void removeField(String village) {
		if (fields.remove(village) != null) {
			setDirty();
		}
	}
	private final Map<String, TerrainJob> terrainJobs = new LinkedHashMap<>();

	public TerrainJob terrainJob(String village) {
		return terrainJobs.get(village);
	}

	public Collection<TerrainJob> terrainJobs() {
		return terrainJobs.values();
	}

	public void startTerrainJob(TerrainJob job) {
		terrainJobs.put(job.village, job);
		setDirty();
	}

	public void endTerrainJob(String village) {
		terrainJobs.remove(village);
		setDirty();
	}

	public static final Codec<VillageData> CODEC = RecordCodecBuilder.create(i -> i.group(
			VILLAGE_CODEC.listOf().optionalFieldOf("villages", List.of()).forGetter(d -> List.copyOf(d.villages.values())),
			JOB_CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(d -> List.copyOf(d.jobs.values())),
			Codec.BOOL.optionalFieldOf("starter_checked", false).forGetter(d -> d.starterChecked),
			RECORD_CODEC.listOf().optionalFieldOf("records", List.of()).forGetter(VillageData::recordList),
			ints("starter_site").forGetter(d -> d.starterSite == null ? IntStream.empty()
					: IntStream.of(d.starterSite)),
			// so lido (formato antigo: areas protegidas do reparo de mundo antigo); nao e mais usado nem gravado
			ints("starter_protect").forGetter(d -> IntStream.empty()),
			TERRAIN_JOB_CODEC.listOf().optionalFieldOf("terrain_jobs", List.of()).forGetter(d -> List.copyOf(d.terrainJobs.values())),
			FIELD_CODEC.listOf().optionalFieldOf("height_fields", List.of()).forGetter(d -> d.fields.entrySet().stream()
					.map(e -> Optional.of(new FieldEntry(e.getKey(), e.getValue()))).toList())
	).apply(i, (villages, jobs, checked, records, site, protect, terrainJobs, fields) -> {
		VillageData data = new VillageData(villages, jobs, checked);
		for (TerrainJob job : terrainJobs) {
			if (data.villages.containsKey(job.village)) {
				data.terrainJobs.put(job.village, job);
			}
		}
		for (RecordEntry e : records) {
			data.records.computeIfAbsent(e.village(), k -> new LinkedHashMap<>()).put(e.lot(), e.record());
		}
		int[] s = site.toArray();
		data.starterSite = s.length >= 2 ? new int[] {s[0], s[1]} : null;
		for (Optional<FieldEntry> e : fields) {
			e.ifPresent(entry -> {
				if (entry.village().equals(PENDING_FIELD) || data.villages.containsKey(entry.village())) {
					data.fields.put(entry.village(), entry.field());
				}
			});
		}
		return data;
	}));

	public static final SavedDataType<VillageData> TYPE = new SavedDataType<>(MlinNotas.id("villages"), VillageData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<String, VillageState> villages = new LinkedHashMap<>();
	private final Map<String, Job> jobs = new LinkedHashMap<>();
	private final Map<String, Map<Integer, BuildRecord>> records = new LinkedHashMap<>();
	private boolean starterChecked;
	/** Centro (x, z) escolhido para a vila inicial antes de gerar os chunks (vilas vanilla perto sao suprimidas). */
	private int[] starterSite;

	public VillageData() {
	}

	private VillageData(List<VillageState> villages, List<Job> jobs, boolean starterChecked) {
		for (VillageState village : villages) {
			this.villages.put(village.key(), village);
		}
		for (Job job : jobs) {
			if (job.project != null && this.villages.containsKey(job.village)) {
				this.jobs.put(job.village, job);
			}
		}
		this.starterChecked = starterChecked;
	}

	public static VillageData get(MinecraftServer server) {
		return server.overworld().getDataStorage().computeIfAbsent(TYPE);
	}

	public Collection<VillageState> villages() {
		return villages.values();
	}

	public VillageState village(String key) {
		return villages.get(key);
	}

	public void add(VillageState village) {
		villages.put(village.key(), village);
		setDirty();
	}

	public void remove(String key) {
		villages.remove(key);
		jobs.remove(key);
		records.remove(key);
		terrainJobs.remove(key);
		fields.remove(key);
		setDirty();
	}

	public Job job(String village) {
		return jobs.get(village);
	}

	public Collection<Job> jobs() {
		return jobs.values();
	}

	public void startJob(Job job) {
		jobs.put(job.village, job);
		setDirty();
	}

	public void endJob(String village) {
		jobs.remove(village);
		setDirty();
	}

	public boolean starterChecked() {
		return starterChecked;
	}

	public void setStarterChecked() {
		starterChecked = true;
		setDirty();
	}

	/** Volta a procurar a vila inicial no proximo inicio (reparo de mundo). */
	public void resetStarter() {
		starterChecked = false;
		starterSite = null;
		setDirty();
	}

	public int[] starterSite() {
		return starterSite == null ? null : starterSite.clone();
	}

	public void setStarterSite(int x, int z) {
		starterSite = new int[] {x, z};
		setDirty();
	}

	public BuildRecord record(String village, int lot) {
		Map<Integer, BuildRecord> map = records.get(village);
		return map == null ? null : map.get(lot);
	}

	public void putRecord(String village, int lot, BuildRecord record) {
		records.computeIfAbsent(village, k -> new LinkedHashMap<>()).put(lot, record);
		setDirty();
	}

	public void removeRecord(String village, int lot) {
		Map<Integer, BuildRecord> map = records.get(village);
		if (map != null) {
			map.remove(lot);
		}
		setDirty();
	}

	private List<RecordEntry> recordList() {
		List<RecordEntry> out = new ArrayList<>();
		records.forEach((village, map) -> map.forEach((lot, record) -> out.add(new RecordEntry(village, lot, record))));
		return out;
	}

	/** Marca para salvar depois de mudar uma vila. */
	public void changed() {
		setDirty();
	}

	// ---- conversao do estado puro ----

	private static Core core(VillageState v) {
		return new Core(v.dimension(), v.x(), v.y(), v.z(), v.seed(), v.style().id(), v.name(), v.starter());
	}

	private static Progress progress(VillageState v) {
		Map<String, Integer> built = new LinkedHashMap<>();
		v.builtMap().forEach((p, n) -> built.put(p.id(), n));
		Map<String, Map<String, Integer>> deposits = new LinkedHashMap<>();
		v.depositsMap().forEach((p, m) -> deposits.put(p.id(), m));
		List<Integer> lots = new ArrayList<>();
		for (VillageState.Rect r : v.lots()) {
			lots.add(r.x0());
			lots.add(r.z0());
			lots.add(r.x1());
			lots.add(r.z1());
		}
		List<Integer> progress = new ArrayList<>();
		List<Boolean> done = new ArrayList<>();
		for (int i = 0; i < BountyBoard.MAX_PER_DAY; i++) {
			progress.add(v.bountyProgress(i));
			done.add(v.bountyDone(i));
		}
		Map<String, Map<String, Integer>> paid = new LinkedHashMap<>();
		v.paidMap().forEach((p, m) -> paid.put(p.id(), m));
		return new Progress(v.level().ordinal(), v.prosperity(), built, deposits, v.queue().stream().map(VillageProject::id).toList(), lots,
				v.bountyDay(), v.bounties(), progress, done, paid, v.queuePaid());
	}

	private static VillageState fromParts(Core core, Progress p, Optional<VillageLayout> layout) {
		VillageState v = new VillageState(core.dimension(), core.x(), core.y(), core.z(), core.seed(), VillageStyle.byId(core.style()), core.name());
		v.setStarter(core.starter());
		Map<VillageProject, Integer> built = new EnumMap<>(VillageProject.class);
		p.built().forEach((id, n) -> {
			VillageProject project = VillageProject.byId(id);
			if (project != null) {
				built.put(project, n);
			}
		});
		Map<VillageProject, Map<String, Integer>> deposits = new EnumMap<>(VillageProject.class);
		p.deposits().forEach((id, m) -> {
			VillageProject project = VillageProject.byId(id);
			if (project != null) {
				deposits.put(project, m);
			}
		});
		List<VillageProject> queue = new ArrayList<>();
		List<Map<String, Integer>> queuePaid = new ArrayList<>();
		for (int k = 0; k < p.queue().size(); k++) {
			VillageProject project = VillageProject.byId(p.queue().get(k));
			if (project != null) {
				queue.add(project);
				queuePaid.add(k < p.queuePaid().size() ? p.queuePaid().get(k) : Map.of());
			}
		}
		List<VillageState.Rect> lots = new ArrayList<>();
		for (int i = 0; i + 3 < p.lots().size(); i += 4) {
			lots.add(new VillageState.Rect(p.lots().get(i), p.lots().get(i + 1), p.lots().get(i + 2), p.lots().get(i + 3)));
		}
		int[] progress = p.bountyProgress().stream().mapToInt(Integer::intValue).toArray();
		boolean[] done = new boolean[p.bountyDone().size()];
		for (int i = 0; i < done.length; i++) {
			done[i] = p.bountyDone().get(i);
		}
		v.restore(VillageLevel.byOrdinal(p.level()), p.prosperity(), built, deposits, queue, lots, p.bountyDay(), p.bounties(), progress, done);
		Map<VillageProject, Map<String, Integer>> paid = new EnumMap<>(VillageProject.class);
		p.paid().forEach((id, m) -> {
			VillageProject project = VillageProject.byId(id);
			if (project != null) {
				paid.put(project, m);
			}
		});
		v.restorePaid(paid, queuePaid);
		v.setLayout(layout.orElse(null));
		return v;
	}
}
