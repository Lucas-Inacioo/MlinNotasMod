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
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * Todas as vilas do mundo e as obras em andamento, salvas no armazenamento do Overworld
 * ({@code data/mlinnotas/villages.dat}). A logica fica em {@link VillageState}; aqui so o formato salvo.
 */
public class VillageData extends SavedData {
	private static final Codec<Map<String, Integer>> INT_MAP = Codec.unboundedMap(Codec.STRING, Codec.INT);

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
			List<Integer> lots, long bountyDay, List<Bounty> bounties, List<Integer> bountyProgress, List<Boolean> bountyDone) {
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
			Codec.BOOL.listOf().optionalFieldOf("bounty_done", List.of()).forGetter(Progress::bountyDone)
	).apply(i, Progress::new));

	/** Uma vila inteira (usado no arquivo e no pacote da tela). */
	public static final Codec<VillageState> VILLAGE_CODEC = RecordCodecBuilder.create(i -> i.group(
			CORE_CODEC.fieldOf("core").forGetter(VillageData::core),
			PROGRESS_CODEC.fieldOf("progress").forGetter(VillageData::progress)
	).apply(i, VillageData::fromParts));

	/** Obra em andamento: blocos que faltam (posicao, parte), aldeoes e golens que chegam no fim. */
	public static final class Job {
		public final String village;
		public final VillageProject project;
		public final long[] positions;
		public final int[] parts;
		public final int[] spawns;
		public final int[] golems;
		public int index;

		public Job(String village, VillageProject project, long[] positions, int[] parts, int[] spawns, int[] golems, int index) {
			this.village = village;
			this.project = project;
			this.positions = positions;
			this.parts = parts;
			this.spawns = spawns;
			this.golems = golems;
			this.index = index;
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

		public static Job create(String village, VillagePlanner.Planned planned) {
			List<Placement> list = planned.placements();
			long[] positions = new long[list.size()];
			int[] parts = new int[list.size()];
			for (int i = 0; i < list.size(); i++) {
				Placement p = list.get(i);
				positions[i] = p.key();
				parts[i] = (p.part().ordinal() << 4) | (p.fill() ? 8 : 0) | (p.dir() == null ? 0 : p.dir().ordinal() + 1);
			}
			return new Job(village, planned.project(), positions, parts, flatten(planned.spawns()), flatten(planned.golems()), 0);
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
			Codec.INT_STREAM.optionalFieldOf("spawns", IntStream.empty()).forGetter(j -> IntStream.of(j.spawns)),
			Codec.INT_STREAM.optionalFieldOf("golems", IntStream.empty()).forGetter(j -> IntStream.of(j.golems)),
			Codec.INT.fieldOf("index").forGetter(j -> j.index)
	).apply(i, (village, project, positions, parts, spawns, golems, index) -> new Job(village, VillageProject.byId(project),
			positions.toArray(), parts.toArray(), spawns.toArray(), golems.toArray(), index)));

	public static final Codec<VillageData> CODEC = RecordCodecBuilder.create(i -> i.group(
			VILLAGE_CODEC.listOf().optionalFieldOf("villages", List.of()).forGetter(d -> List.copyOf(d.villages.values())),
			JOB_CODEC.listOf().optionalFieldOf("jobs", List.of()).forGetter(d -> List.copyOf(d.jobs.values())),
			Codec.BOOL.optionalFieldOf("starter_checked", false).forGetter(d -> d.starterChecked)
	).apply(i, VillageData::new));

	public static final SavedDataType<VillageData> TYPE = new SavedDataType<>(MlinNotas.id("villages"), VillageData::new, CODEC,
			DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

	private final Map<String, VillageState> villages = new LinkedHashMap<>();
	private final Map<String, Job> jobs = new LinkedHashMap<>();
	private boolean starterChecked;

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
		for (int i = 0; i < BountyBoard.PER_DAY; i++) {
			progress.add(v.bountyProgress(i));
			done.add(v.bountyDone(i));
		}
		return new Progress(v.level().ordinal(), v.prosperity(), built, deposits, v.queue().stream().map(VillageProject::id).toList(), lots,
				v.bountyDay(), v.bounties(), progress, done);
	}

	private static VillageState fromParts(Core core, Progress p) {
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
		for (String id : p.queue()) {
			VillageProject project = VillageProject.byId(id);
			if (project != null) {
				queue.add(project);
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
		return v;
	}
}
