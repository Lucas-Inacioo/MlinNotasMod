package com.mlin.notas.village;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Plano do espaco da vila (logica pura), calculado ao fundar e estendido a cada nivel: praca, lotes reservados para
 * cada tipo de projeto (tamanho tirado da planta em {@link Buildings}), ruas e postes. Coordenadas x/z relativas ao
 * quadro.
 *
 * <p>Ruas: quatro avenidas de 3 de largura saindo da praca nos eixos, e um acesso curto (busca em largura) da frente
 * de cada lote ate a rua ou praca mais perto. Lotes: o mais perto do centro que caiba sem encostar em outro lote (2 de
 * folga), sem pisar em rua (1 de calcada), sem coluna de jogador nem agua e com ate {@link #MAX_SPREAD} de desnivel
 * (o construtor aplaina o resto). O que ja foi planejado nunca muda ao subir de nivel: o nivel novo so acrescenta
 * lotes mais longe, avenidas mais compridas e postes onde ainda nao ha. Postes ficam na calcada das ruas, a pelo
 * menos {@link #LAMP_SPACING} blocos um do outro, nunca dentro de lote.
 *
 * <p>Referencias de experiencia: a ferramenta de construcao do MineColonies (fantasma da planta que segue a mira,
 * girar e confirmar) e os planejadores de vila de Settlements / Minecraft Comes Alive (lotes reservados ao longo das
 * ruas). Codigo escrito do zero.
 */
public final class VillageLayout {
	/** Desnivel maximo dentro de um lote (corte e aterro cuidam do resto). */
	public static final int MAX_SPREAD = 6;
	public static final int LOT_GAP = 2;
	public static final int ROAD_GAP = 1;
	public static final int LAMP_SPACING = 8;
	/** Nenhum lote chega mais perto do quadro que isso (praca). */
	public static final int LOT_CLEARANCE = VillagePlanner.PLAZA_RADIUS + 2;
	public static final int MAX_ACCESS = 40;
	public static final String CAMPFIRE = "campfire";
	/** Projetos que ocupam lote, na ordem em que os lotes sao reservados (um de cada por vez). */
	public static final List<VillageProject> LOT_PROJECTS = java.util.stream.Stream.concat(
			List.of(VillageProject.HOUSE, VillageProject.FARM, VillageProject.SMITHY, VillageProject.MARKET, VillageProject.INN,
					VillageProject.LIBRARY, VillageProject.TOWN_HALL, VillageProject.GRANARY, VillageProject.MILL, VillageProject.BAKERY,
					VillageProject.ORCHARD, VillageProject.PEN, VillageProject.APIARY, VillageProject.BUTCHER, VillageProject.WINERY,
					VillageProject.SAWMILL, VillageProject.ENCHANTER, VillageProject.CHURCH, VillageProject.TAVERN,
					VillageProject.TAILOR, VillageProject.FLETCHER, VillageProject.QUARRY, VillageProject.TANNERY, VillageProject.GARDEN,
					VillageProject.EMPTY_LOT).stream(),
			com.mlin.notas.village.civic.CivicBuildings.PROJECTS.stream()).toList();
	/** Postes que a praca ja coloca (os da rua guardam distancia deles). */
	static final int[][] PLAZA_LAMPS = {{5, 2}, {-5, 2}, {2, 5}, {2, -5}};

	public enum Problem {
		OUTSIDE, PLAZA, OVERLAP, TERRAIN, STEEP, NO_ACCESS,
		/** Lote do plano diretor de outra zona (ex.: mercado fora do centro). */
		ZONE,
		/** Anel ainda fechado no nivel da vila. */
		CLOSED,
		/** Lote de jogador. */
		PLAYER
	}

	/** Um lote reservado. {@code cw}/{@code cd}: tamanho da planta sem girar; {@code door}: coluna da porta na planta. */
	public static final class Lot {
		private final int id;
		private final String kind;
		private int x;
		private int z;
		private int rot;
		private int cw;
		private int cd;
		private int door;
		private final long seed;
		private boolean built;
		private boolean legacy;
		private boolean preferred;
		private final List<int[]> access = new ArrayList<>();
		private final Map<String, Integer> paid = new LinkedHashMap<>();
		/** Lote do plano diretor onde a obra fica (-1 = lote de antes do plano). */
		private int parcel = -1;

		/** Lote do plano diretor ({@link MasterPlan.Parcel#id()}) ou -1. */
		public int parcel() {
			return parcel;
		}

		void setParcel(int parcel) {
			this.parcel = parcel;
		}

		public Lot(int id, String kind, int x, int z, int rot, int cw, int cd, int door, long seed) {
			this.id = id;
			this.kind = kind;
			this.x = x;
			this.z = z;
			this.rot = Math.floorMod(rot, 4);
			this.cw = cw;
			this.cd = cd;
			this.door = door;
			this.seed = seed;
		}

		public int id() {
			return id;
		}

		/** Id do projeto ({@link VillageProject#id()}), {@link #CAMPFIRE} ou vazio (obra antiga sem tipo). */
		public String kind() {
			return kind;
		}

		public VillageProject project() {
			return VillageProject.byId(kind);
		}

		public int x() {
			return x;
		}

		public int z() {
			return z;
		}

		public int rot() {
			return rot;
		}

		public int cw() {
			return cw;
		}

		public int cd() {
			return cd;
		}

		public int door() {
			return door;
		}

		public long seed() {
			return seed;
		}

		public int w() {
			return rot % 2 == 0 ? cw : cd;
		}

		public int d() {
			return rot % 2 == 0 ? cd : cw;
		}

		public boolean built() {
			return built;
		}

		public void setBuilt(boolean built) {
			this.built = built;
		}

		/** Obra de antes do plano (sem registro dos blocos: nao da para demolir). */
		public boolean legacy() {
			return legacy;
		}

		public void setLegacy(boolean legacy) {
			this.legacy = legacy;
		}

		/** Escolhido pelo jogador: a proxima obra do tipo vai aqui. */
		public boolean preferred() {
			return preferred;
		}

		public void setPreferred(boolean preferred) {
			this.preferred = preferred;
		}

		/** Acesso da frente do lote ate a rua (celulas x, z). */
		public List<int[]> access() {
			return Collections.unmodifiableList(access);
		}

		/** Itens pagos pela obra que esta no lote (devolucao ao demolir). */
		public Map<String, Integer> paid() {
			return Collections.unmodifiableMap(paid);
		}

		public void setPaid(Map<String, Integer> items) {
			paid.clear();
			paid.putAll(items);
		}

		public VillageState.Rect rect() {
			return new VillageState.Rect(x, z, x + w() - 1, z + d() - 1);
		}

		/** Celula logo na frente da porta, fora do lote. */
		public int[] front() {
			return front(x, z, rot, cw, cd, door);
		}

		/** Frente da porta de um lote no canto (x, z) girado {@code rot}. */
		public static int[] frontOf(int x, int z, int rot, int cw, int cd, int door) {
			return front(x, z, rot, cw, cd, door);
		}

		static int[] front(int x, int z, int rot, int cw, int cd, int door) {
			int[] r = VillagePlanner.rotate(door, cd, cw, cd, rot);
			return new int[] {x + r[0], z + r[1]};
		}

		void move(int x, int z, int rot, List<int[]> access) {
			this.x = x;
			this.z = z;
			this.rot = Math.floorMod(rot, 4);
			this.access.clear();
			this.access.addAll(access);
		}

		void setAccess(List<int[]> access) {
			this.access.clear();
			this.access.addAll(access);
		}
	}

	/** Poste planejado (x, z) e se ja foi construido. */
	public static final class Lamp {
		private final int x;
		private final int z;
		private boolean built;

		public Lamp(int x, int z, boolean built) {
			this.x = x;
			this.z = z;
			this.built = built;
		}

		public int x() {
			return x;
		}

		public int z() {
			return z;
		}

		public boolean built() {
			return built;
		}

		public void setBuilt(boolean built) {
			this.built = built;
		}
	}

	private int radius;
	private int nextId;
	private final List<Lot> lots = new ArrayList<>();
	private final LinkedHashSet<Long> roads = new LinkedHashSet<>();
	private final List<Lamp> lamps = new ArrayList<>();
	private final List<VillageState.Rect> protect = new ArrayList<>();
	/** Plano diretor (ruas, quarteiroes, lotes, alturas); null = plano antigo (so lotes e ruas nos eixos). */
	private MasterPlan master;

	public VillageLayout() {
	}

	// ---------------------------------------------------------------- acesso

	public MasterPlan master() {
		return master;
	}

	public void setMaster(MasterPlan master) {
		this.master = master;
	}

	public int radius() {
		return radius;
	}

	public int nextId() {
		return nextId;
	}

	public List<Lot> lots() {
		return Collections.unmodifiableList(lots);
	}

	public Lot lot(int id) {
		for (Lot lot : lots) {
			if (lot.id == id) {
				return lot;
			}
		}
		return null;
	}

	public Set<Long> roads() {
		return Collections.unmodifiableSet(roads);
	}

	public boolean isRoad(int x, int z) {
		return roads.contains(col(x, z)) || (master != null && master.ground(x, z));
	}

	public List<Lamp> lamps() {
		return Collections.unmodifiableList(lamps);
	}

	/** Areas onde a vila nunca planeja nada (construcoes de jogador que ja existiam, com folga). */
	public List<VillageState.Rect> protectedAreas() {
		return Collections.unmodifiableList(protect);
	}

	public void addProtected(VillageState.Rect rect) {
		protect.add(rect);
		roads.removeIf(k -> rect.contains(colX(k), colZ(k)));
	}

	public static long col(int x, int z) {
		return ((long) x << 32) | (z & 0xFFFFFFFFL);
	}

	public static int colX(long key) {
		return (int) (key >> 32);
	}

	public static int colZ(long key) {
		return (int) key;
	}

	public List<Lot> lotsOf(String kind) {
		List<Lot> out = new ArrayList<>();
		for (Lot lot : lots) {
			if (lot.kind.equals(kind)) {
				out.add(lot);
			}
		}
		return out;
	}

	/** Lote (o primeiro) que contem a coluna, ou null. */
	public Lot lotAt(int x, int z) {
		for (Lot lot : lots) {
			if (lot.rect().contains(x, z)) {
				return lot;
			}
		}
		return null;
	}

	// ---------------------------------------------------------------- restauracao (codec)

	public void restore(int radius, int nextId, List<Lot> lots, List<Long> roads, List<Lamp> lamps, List<VillageState.Rect> protect) {
		this.radius = radius;
		this.nextId = nextId;
		this.lots.clear();
		this.lots.addAll(lots);
		this.roads.clear();
		this.roads.addAll(roads);
		this.lamps.clear();
		this.lamps.addAll(lamps);
		this.protect.clear();
		this.protect.addAll(protect);
		for (Lot lot : lots) {
			this.nextId = Math.max(this.nextId, lot.id + 1);
		}
	}

	/** Restaura um lote salvo (codec). */
	public static Lot restoreLot(int id, String kind, int x, int z, int rot, int cw, int cd, int door, long seed, boolean built, boolean legacy,
			boolean preferred, List<int[]> access, Map<String, Integer> paid) {
		return restoreLot(id, kind, x, z, rot, cw, cd, door, seed, built, legacy, preferred, access, paid, -1);
	}

	public static Lot restoreLot(int id, String kind, int x, int z, int rot, int cw, int cd, int door, long seed, boolean built, boolean legacy,
			boolean preferred, List<int[]> access, Map<String, Integer> paid, int parcel) {
		Lot lot = new Lot(id, kind, x, z, rot, cw, cd, door, seed);
		lot.built = built;
		lot.legacy = legacy;
		lot.preferred = preferred;
		lot.access.addAll(access);
		lot.paid.putAll(paid);
		lot.parcel = parcel;
		return lot;
	}

	// ---------------------------------------------------------------- criacao e crescimento

	/** Planta (sem girar) que vai no lote do tipo {@code kind}. */
	public static Canvas canvas(String kind, VillageStyle style, long seed) {
		if (CAMPFIRE.equals(kind)) {
			return Buildings.campfire(style);
		}
		VillageProject project = VillageProject.byId(kind);
		return Buildings.plan(project, style, seed);
	}

	/**
	 * Plano novo para a vila: avenidas e lotes do nivel atual. Obras que ja existiam (vila de antes do plano) entram
	 * como lotes ja construidos sem tipo, para nada ser planejado em cima delas.
	 */
	public static VillageLayout create(VillageState state, Terrain terrain) {
		VillageLayout layout = new VillageLayout();
		for (VillageState.Rect r : state.lots()) {
			Lot legacy = new Lot(layout.nextId++, "", r.x0(), r.z0(), 0, r.x1() - r.x0() + 1, r.z1() - r.z0() + 1, 0, 0);
			legacy.built = true;
			legacy.legacy = true;
			layout.lots.add(legacy);
		}
		layout.planMaster(state, terrain);
		layout.extend(state, terrain, state.level());
		return layout;
	}

	/**
	 * Plano antigo (sem plano diretor) passa para o plano diretor: as obras prontas ficam onde estao e viram recortes
	 * fixos (as ruas passam em volta), as ruas e postes antigos contam como da vila, as avenidas seguem retas nos eixos
	 * ate depois do ultimo lote existente e os lotes reservados ainda vazios sao refeitos nos lotes do plano.
	 */
	public void migrate(VillageState state, Terrain terrain) {
		lots.removeIf(lot -> !lot.built);
		for (Lot lot : lots) {
			lot.preferred = false;
		}
		planMaster(state, terrain);
		extend(state, terrain, state.level());
	}

	/** Calcula o plano diretor em volta do que ja existe (lotes prontos, areas protegidas, ruas e postes antigos). */
	void planMaster(VillageState state, Terrain terrain) {
		List<VillageState.Rect> fixed = new ArrayList<>();
		int reach = 0;
		for (Lot lot : lots) {
			if (lot.built) {
				VillageState.Rect r = lot.rect();
				fixed.add(r);
				for (int[] c : new int[][] {{r.x0(), r.z0()}, {r.x1(), r.z0()}, {r.x0(), r.z1()}, {r.x1(), r.z1()}}) {
					reach = Math.max(reach, (int) Math.ceil(Math.hypot(c[0], c[1])));
				}
			}
		}
		Set<Long> villageCells = new java.util.HashSet<>(roads);
		for (Lamp lamp : lamps) {
			villageCells.add(col(lamp.x, lamp.z));
		}
		for (Lot lot : lots) {
			lot.access.forEach(c -> villageCells.add(col(c[0], c[1])));
		}
		master = MasterPlan.create(state.seed(), terrain, state.y() - 1, fixed, List.copyOf(protect), villageCells, fixed.isEmpty() ? 0 : reach + 4);
		// postes antigos que ainda nao foram feitos saem (o plano novo poe os seus)
		lamps.removeIf(lamp -> !lamp.built);
	}

	/** Lotes que faltam reservar para o tipo no nivel (conta obras antigas sem lote). */
	int missing(VillageState state, VillageProject project, VillageLevel level) {
		List<Lot> mine = lotsOf(project.id());
		int builtInLots = (int) mine.stream().filter(Lot::built).count();
		int orphan = Math.max(0, state.built(project) - builtInLots);
		return Math.max(0, project.maxBuilds(level) - mine.size() - orphan);
	}

	/**
	 * Cresce o plano ate o raio do nivel: avenidas mais compridas, lotes que faltam para cada tipo (os de antes ficam
	 * onde estao) e postes novos. Devolve os lotes acrescentados.
	 */
	public List<Lot> extend(VillageState state, Terrain terrain, VillageLevel level) {
		if (master != null) {
			if (level.ordinal() >= RuralRing.OPENS.ordinal() && !master.rural()) {
				// Cidade: o plano ganha o anel rural fora da muralha externa (o que ja existe nao muda)
				master = RuralRing.expand(master, terrain);
			}
			return extendMaster(state, level);
		}
		radius = Math.max(radius, level.radius());
		addAvenues();
		Random random = new Random(state.seed() ^ 0x5EED_1A70L ^ ((long) level.ordinal() * 7919L));
		Map<VillageProject, Integer> need = new LinkedHashMap<>();
		for (VillageProject project : LOT_PROJECTS) {
			need.put(project, FoodPlans.handles(project) ? 0 : missing(state, project, level));
		}
		List<Lot> added = new ArrayList<>();
		boolean progress = true;
		while (progress) {
			progress = false;
			for (VillageProject project : LOT_PROJECTS) {
				int n = need.get(project);
				if (n <= 0) {
					continue;
				}
				Lot lot = addLot(project.id(), state.style(), state.seed(), terrain, random);
				if (lot == null) {
					need.put(project, 0);
					continue;
				}
				added.add(lot);
				need.put(project, n - 1);
				progress = true;
			}
		}
		addLamps();
		return added;
	}

	/** Abre o plano ate o raio sem reservar lotes (vila inicial: a fogueira vem antes das casas). */
	public void ensureRadius(int r) {
		radius = Math.max(radius, r);
		if (master == null) {
			addAvenues();
		}
	}

	/** O plano diretor ainda nao tem o anel rural e a vila ja chegou no nivel dele (mundo de antes da fase 8). */
	public boolean needsRural(VillageLevel level) {
		return master != null && !master.rural() && level.ordinal() >= RuralRing.OPENS.ordinal();
	}

	// ---------------------------------------------------------------- plano diretor

	/** Aneis do plano diretor abertos com o alcance atual (o do maior nivel ja alcancado). */
	public int openRings() {
		int out = 1;
		for (VillageLevel level : VillageLevel.values()) {
			if (level.radius() <= radius) {
				out = Math.max(out, level.rings());
			}
		}
		return out;
	}

	/**
	 * Tipos com lote agrupados pela classe, maiores primeiro (L, M, S, T...; dentro do grupo na ordem de
	 * {@link #LOT_PROJECTS}). Os lotes grandes precisam de frente comprida e contigua; reservados depois das casas, so
	 * sobrariam pedacos.
	 */
	static List<List<VillageProject>> reserveGroups(java.util.function.Predicate<VillageProject> filter) {
		Map<Integer, List<VillageProject>> byArea = new java.util.TreeMap<>(Comparator.reverseOrder());
		for (VillageProject p : LOT_PROJECTS) {
			if (!filter.test(p)) {
				continue;
			}
			SizeClass s = LotRules.of(p.id()).size();
			byArea.computeIfAbsent(s.width() * s.depth(), k -> new ArrayList<>()).add(p);
		}
		return new ArrayList<>(byArea.values());
	}

	/**
	 * Tipos essenciais: casa, plantacao e toda obra que algum nivel da vila exige ({@link VillageLevel#required}). Eles
	 * reservam primeiro (e guardam o espaco dos proximos niveis); os outros oficios ficam com o que sobra do plano e,
	 * sem lugar, o projeto aparece como "sem lugar no plano" ({@link VillageState.Status#NO_ROOM}).
	 */
	public static boolean essential(VillageProject project) {
		if (project == VillageProject.HOUSE || project == VillageProject.FARM) {
			return true;
		}
		for (VillageLevel level : VillageLevel.values()) {
			if (level.required().contains(project)) {
				return true;
			}
		}
		return false;
	}

	/** Reserva antes dos outros: essencial ou ja paga e esperando na fila (a fila nunca fica sem lote por causa de um oficio novo). */
	static boolean first(VillageState state, VillageProject project) {
		return essential(project) || state.queued(project) > 0;
	}

	/** Lotes do plano guardados (so durante {@link #extendMaster}) para obras dos proximos niveis. */
	private final java.util.Set<Integer> held = new java.util.HashSet<>();

	/**
	 * Reserva os lotes que faltam no nivel nos lotes do plano diretor (os de antes ficam) e os postes novos. Com todos
	 * os aneis de lotes comuns abertos (da Cidade em diante), o espaco das obras dos niveis seguintes e guardado na mesma
	 * passada (classe por classe, maiores primeiro): assim o museu e o quartel da Capital ainda acham frente comprida
	 * depois das obras da Cidade. O que foi guardado nao vira lote (nada aparece para o jogador); so impede que uma obra
	 * menor parta aquele espaco agora. Os essenciais ({@link #essential}) e os ja pagos reservam todos antes dos outros
	 * oficios: num terreno dificil (lagos, encostas) o plano tem bem menos lotes e quem fica sem lugar e um oficio.
	 */
	List<Lot> extendMaster(VillageState state, VillageLevel level) {
		radius = Math.max(radius, level.radius());
		int open = openRings();
		VillageLevel top = VillageLevel.values()[VillageLevel.values().length - 1];
		Map<VillageProject, Integer> now = new LinkedHashMap<>();
		Map<VillageProject, Integer> later = new LinkedHashMap<>();
		for (VillageProject project : LOT_PROJECTS) {
			int n = missing(state, project, level);
			now.put(project, n);
			later.put(project, open >= 5 && first(state, project) ? Math.max(0, missing(state, project, top) - n) : 0);
		}
		List<Lot> added = new ArrayList<>();
		held.clear();
		try {
			for (boolean essentials : new boolean[] {true, false}) {
				for (List<VillageProject> group : reserveGroups(p -> first(state, p) == essentials)) {
					roundRobin(group, now, project -> {
						Lot lot = reserve(project.id(), state.style(), state.seed(), open);
						if (lot != null) {
							added.add(lot);
						}
						return lot != null;
					});
					roundRobin(group, later, project -> {
						SizeClass cls = LotRules.of(project.id()).size();
						MasterPlan.Parcel p = pickParcel(project.id(), cls.width(), cls.depth(), open, -1, false);
						if (p != null) {
							held.add(p.id());
						}
						return p != null;
					});
				}
			}
		} finally {
			held.clear();
		}
		addMasterLamps(open);
		return added;
	}

	/** Uma unidade de cada tipo do grupo por volta, ate acabar a conta ou o lugar ({@code take} devolve false). */
	private static void roundRobin(List<VillageProject> group, Map<VillageProject, Integer> need,
			java.util.function.Predicate<VillageProject> take) {
		boolean progress = true;
		while (progress) {
			progress = false;
			for (VillageProject project : group) {
				int n = need.get(project);
				if (n <= 0) {
					continue;
				}
				if (!take.test(project)) {
					need.put(project, 0);
					continue;
				}
				need.put(project, n - 1);
				progress = true;
			}
		}
	}

	/**
	 * Plano de antes da subdivisao densa passa para a versao nova: quarteirao sem lote reservado e subdividido de novo,
	 * os outros so ganham lotes no espaco que sobrou (os reservados e os prontos nunca mudam). Postes ja feitos e
	 * caminhos de porta ficam livres. Devolve os lotes aposentados (para tirar as estacas velhas).
	 */
	public List<MasterPlan.Parcel> densifyMaster() {
		if (master == null || master.version() >= MasterPlan.VERSION) {
			return List.of();
		}
		java.util.Set<Integer> reserved = new java.util.HashSet<>();
		List<int[]> keep = new ArrayList<>();
		for (Lot lot : lots) {
			if (lot.parcel >= 0) {
				reserved.add(lot.parcel);
			}
			keep.addAll(lot.access);
		}
		for (Lamp lamp : lamps) {
			if (lamp.built) {
				keep.add(new int[] {lamp.x, lamp.z});
			}
		}
		List<MasterPlan.Parcel> retired = BlockSubdivider.densify(master, reserved, keep).retired();
		lamps.removeIf(lamp -> !lamp.built && master.parcelAt(lamp.x, lamp.z) != null);
		addMasterLamps(openRings());
		return retired;
	}

	/** Reserva um lote do plano diretor para o tipo (null = nenhum serve). */
	public Lot reserve(String kind, VillageStyle style, long villageSeed, int open) {
		int id = nextId;
		long seed = lotSeed(villageSeed, id);
		Canvas canvas = canvas(kind, style, seed);
		MasterPlan.Parcel parcel = pickParcel(kind, canvas.w(), canvas.d(), open, -1, false);
		if (parcel == null) {
			return null;
		}
		nextId++;
		Lot lot = new Lot(id, kind, 0, 0, 0, canvas.w(), canvas.d(), canvas.doorX(), seed);
		placeIn(lot, parcel);
		lots.add(lot);
		return lot;
	}

	/**
	 * Melhor lote do plano para o tipo: zona preferida primeiro, depois o tamanho que menos sobra e o mais perto do
	 * centro. {@code prepared}: so quarteiroes ja aplainados. Quando nenhum lote do plano serve bem (zona de reserva,
	 * sobra grande ou nenhum), o espaco livre dos quarteiroes e recortado sob demanda ({@link #carveParcel}): dois lotes
	 * pequenos vizinhos viram um medio, um grande se parte. Lotes reservados, prontos e de jogador nunca mudam.
	 */
	public MasterPlan.Parcel pickParcel(String kind, int cw, int cd, int open, int exclude, boolean prepared) {
		LotRules.Rule rule = LotRules.of(kind);
		MasterPlan.Parcel best = null;
		double bestScore = Double.MAX_VALUE;
		for (MasterPlan.Parcel p : master.parcels()) {
			if (parcelProblem(kind, p, cw, cd, open, exclude) != null) {
				continue;
			}
			MasterPlan.Block block = master.block(p.block());
			if (prepared && !block.prepared()) {
				continue;
			}
			double score = parcelScore(kind, rule, p.zone(), p.rect(), p.along() * p.deep(), block.prepared());
			if (score < bestScore) {
				bestScore = score;
				best = p;
			}
		}
		boolean perfect = best != null && rule.preference(best.zone()) == 0
				&& best.along() * best.deep() - rule.size().width() * rule.size().depth() <= CARVE_WASTE;
		if (!perfect) {
			MasterPlan.Parcel carved = carveParcel(kind, cw, cd, open, prepared, bestScore);
			if (carved != null) {
				return carved;
			}
		}
		return best;
	}

	/** Sobra (em celulas) que um lote do plano pode ter sem o recorte sob demanda tentar algo mais justo (0 = sempre tenta: empacota melhor). */
	static final int CARVE_WASTE = 0;
	/** O recorte so ganha de um lote existente com nota pelo menos esta melhor (0 = qualquer melhora; medido no VillageCapacityTest). */
	static final double CARVE_MARGIN = 0;

	private double parcelScore(String kind, LotRules.Rule rule, Zone zone, VillageState.Rect r, int area, boolean prepared) {
		double dist = Math.hypot((r.x0() + r.x1()) / 2.0, (r.z0() + r.z1()) / 2.0);
		if (LotRules.prefersOuter(kind)) {
			dist = -dist;
		}
		double waste = area - rule.size().width() * rule.size().depth();
		return rule.preference(zone) * 1000 + waste * 0.5 + dist + (prepared ? 0 : 500);
	}

	/** Lotes do plano aposentados por recortes sob demanda, esperando o servidor tirar as estacas deles do mundo. */
	private final List<MasterPlan.Parcel> retiredPending = new ArrayList<>();

	/** Lotes aposentados desde a ultima chamada (o servidor tira as estacas e o que as obras publicas puseram neles). */
	public List<MasterPlan.Parcel> drainRetired() {
		List<MasterPlan.Parcel> out = List.copyOf(retiredPending);
		retiredPending.clear();
		return out;
	}

	/**
	 * Recorta um lote novo da classe do tipo no espaco livre de um quarteirao (sobre lotes do plano ainda livres) se a
	 * nota dele for melhor que {@code beatScore - CARVE_MARGIN}. null = nenhum lugar (o plano nao muda).
	 */
	MasterPlan.Parcel carveParcel(String kind, int cw, int cd, int open, boolean prepared, double beatScore) {
		LotRules.Rule rule = LotRules.of(kind);
		SizeClass cls = rule.size();
		if (master == null || cls == SizeClass.W || cls == SizeClass.WL || !cls.fits(cw, cd)) {
			return null;
		}
		java.util.Set<Integer> fixed = new java.util.HashSet<>();
		fixed.addAll(held);
		boolean[] keep = BlockSubdivider.emptyTaken(master);
		for (Lot lot : lots) {
			if (lot.parcel >= 0) {
				fixed.add(lot.parcel);
			}
			BlockSubdivider.occupy(master, keep, lot.rect());
			for (int[] c : lot.access) {
				BlockSubdivider.occupy(master, keep, new VillageState.Rect(c[0], c[1], c[0], c[1]));
			}
		}
		for (MasterPlan.Parcel p : master.parcels()) {
			if (!p.retired() && (fixed.contains(p.id()) || p.player())) {
				BlockSubdivider.occupy(master, keep, p.rect());
			}
		}
		for (Lamp lamp : lamps) {
			if (lamp.built) {
				BlockSubdivider.occupy(master, keep, new VillageState.Rect(lamp.x, lamp.z, lamp.x, lamp.z));
			}
		}
		int[][] bounds = BlockSubdivider.blockBounds(master);
		BlockSubdivider.Carving best = null;
		double bestScore = beatScore - CARVE_MARGIN;
		for (MasterPlan.Block block : master.blocks()) {
			if (!MasterPlan.isOpen(block.ring(), open) || block.zone() == null || (prepared && !block.prepared())
					|| !LotRules.accepts(rule, block.zone(), open >= 3, open >= 5)) {
				continue;
			}
			double floor = rule.preference(block.zone()) * 1000 + (block.prepared() ? 0 : 500) - RuralRing.EXTENT * 1.5;
			if (floor >= bestScore) {
				continue;
			}
			BlockSubdivider.Carving c = BlockSubdivider.carve(master, block, bounds[block.id()], cls, keep);
			if (c == null) {
				continue;
			}
			MasterPlan.Parcel probe = new MasterPlan.Parcel(-2, block.id(), c.rect(), c.front(), cls, block.zone(), false);
			if (parcelProblem(kind, probe, cw, cd, open, -1) != null) {
				continue;
			}
			double score = parcelScore(kind, rule, block.zone(), c.rect(), probe.along() * probe.deep(), block.prepared());
			if (score < bestScore) {
				bestScore = score;
				best = c;
			}
		}
		if (best == null) {
			return null;
		}
		List<MasterPlan.Parcel> retired = new ArrayList<>();
		MasterPlan.Parcel made = BlockSubdivider.applyCarving(master, best, keep, fixed, retired);
		retiredPending.addAll(retired);
		lamps.removeIf(lamp -> !lamp.built && master.parcelAt(lamp.x, lamp.z) != null);
		return made;
	}

	/** O lote do plano comporta uma construcao da classe {@code need}: pela classe ou pelo retangulo real (lotes crescidos). */
	static boolean holds(MasterPlan.Parcel p, SizeClass need) {
		if (p.size().holds(need)) {
			return true;
		}
		boolean wall = p.size() == SizeClass.W || p.size() == SizeClass.WL;
		boolean needWall = need == SizeClass.W || need == SizeClass.WL;
		return !wall && !needWall && p.along() >= need.width() && p.deep() >= need.depth();
	}

	/** O lote {@code p} serve para o tipo agora? null = serve. {@code exclude}: lote da propria obra (ao mudar). */
	public Problem parcelProblem(String kind, MasterPlan.Parcel p, int cw, int cd, int open, int exclude) {
		if (master == null || p == null || p.retired()) {
			return Problem.OUTSIDE;
		}
		if (p.player()) {
			return Problem.PLAYER;
		}
		if (held.contains(p.id())) {
			return Problem.OVERLAP;
		}
		for (Lot lot : lots) {
			if (lot.parcel == p.id() && lot.id != exclude) {
				return Problem.OVERLAP;
			}
		}
		VillageState.Rect pr = p.rect();
		int nx = Math.max(pr.x0(), Math.min(0, pr.x1()));
		int nz = Math.max(pr.z0(), Math.min(0, pr.z1()));
		if (nx * nx + nz * nz <= LOT_CLEARANCE * LOT_CLEARANCE) {
			return Problem.PLAZA;
		}
		MasterPlan.Block block = master.block(p.block());
		if (block == null || !MasterPlan.isOpen(block.ring(), open)) {
			return Problem.CLOSED;
		}
		LotRules.Rule rule = LotRules.of(kind);
		if (!LotRules.accepts(rule, p.zone(), open >= 3, open >= 5)) {
			return Problem.ZONE;
		}
		if (!holds(p, rule.size()) || fit(p, cw, cd) == null) {
			return Problem.OUTSIDE;
		}
		return null;
	}

	/**
	 * Canto e giro de uma planta {@code cw} x {@code cd} dentro do lote, com a porta virada para a rua: centrada ao longo
	 * da frente e com 1 de recuo quando cabe. {x, z, giro}; null = nao cabe.
	 */
	public static int[] fit(MasterPlan.Parcel p, int cw, int cd) {
		int along = p.along();
		int deep = p.deep();
		if (cw > along || cd > deep) {
			return null;
		}
		int back = Math.min(1, deep - cd);
		int side = (along - cw) / 2;
		VillageState.Rect r = p.rect();
		int rot = VillagePlanner.rotationFor(p.front());
		return switch (p.front()) {
			case SOUTH -> new int[] {r.x0() + side, r.z1() - back - cd + 1, rot};
			case NORTH -> new int[] {r.x0() + side, r.z0() + back, rot};
			case EAST -> new int[] {r.x1() - back - cd + 1, r.z0() + side, rot};
			case WEST -> new int[] {r.x0() + back, r.z0() + side, rot};
		};
	}

	/** Coloca a obra no lote do plano: canto, giro e o acesso da porta ate a rua. */
	/**
	 * Troca a planta de um lote pronto pela de outro nivel (melhoria no lugar): no lote do plano diretor a planta e
	 * encaixada de novo com a porta para a rua; lote antigo so aceita planta que caiba no mesmo retangulo, no mesmo canto.
	 * Devolve false se nao cabe (nada muda).
	 */
	public boolean resize(Lot lot, int cw, int cd, int door) {
		MasterPlan.Parcel p = master == null ? null : master.parcel(lot.parcel);
		if (p != null) {
			if (fit(p, cw, cd) == null) {
				return false;
			}
			lot.cw = cw;
			lot.cd = cd;
			lot.door = door;
			placeIn(lot, p);
			return true;
		}
		if (cw > lot.cw || cd > lot.cd) {
			return false;
		}
		lot.cw = cw;
		lot.cd = cd;
		lot.door = door;
		return true;
	}

	void placeIn(Lot lot, MasterPlan.Parcel p) {
		int[] f = fit(p, lot.cw, lot.cd);
		int[] front = Lot.front(f[0], f[1], f[2], lot.cw, lot.cd, lot.door);
		lot.move(f[0], f[1], f[2], accessTo(front, p.front()));
		lot.parcel = p.id();
	}

	/** Celulas da frente da porta ate a rua, andando para {@code dir} (a primeira e a da frente da porta). */
	List<int[]> accessTo(int[] front, Facing dir) {
		List<int[]> out = new ArrayList<>();
		int x = front[0];
		int z = front[1];
		out.add(new int[] {x, z});
		for (int k = 0; k < 4 && !master.ground(x, z) && !master.plaza(x, z); k++) {
			x += dir.dx;
			z += dir.dz;
			if (master.ground(x, z) || master.plaza(x, z)) {
				break;
			}
			out.add(new int[] {x, z});
		}
		return out;
	}

	/** Postes na beira das ruas dos aneis abertos, fora dos lotes, a {@link #LAMP_SPACING}+ uns dos outros. */
	void addMasterLamps(int open) {
		List<int[]> placed = new ArrayList<>();
		for (Lamp lamp : lamps) {
			placed.add(new int[] {lamp.x, lamp.z});
		}
		for (int[] p : PLAZA_LAMPS) {
			placed.add(p);
		}
		List<int[]> candidates = new ArrayList<>();
		int e = master.extent();
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				MasterPlan.Block block = master.blockAt(x, z);
				if (block == null || !MasterPlan.isOpen(block.ring(), open) || master.parcelAt(x, z) != null || lotAt(x, z) != null) {
					continue;
				}
				boolean byStreet = false;
				for (Facing f : Facing.values()) {
					byStreet |= master.ground(x + f.dx, z + f.dz);
				}
				if (byStreet) {
					candidates.add(new int[] {x, z});
				}
			}
		}
		candidates.sort(Comparator.<int[]>comparingInt(c -> c[0] * c[0] + c[1] * c[1]).thenComparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
		for (int[] c : candidates) {
			boolean far = true;
			for (int[] p : placed) {
				int dx = p[0] - c[0];
				int dz = p[1] - c[1];
				if (dx * dx + dz * dz < LAMP_SPACING * LAMP_SPACING * 2) {
					far = false;
					break;
				}
			}
			if (far) {
				placed.add(c);
				lamps.add(new Lamp(c[0], c[1], false));
			}
		}
	}

	private void addAvenues() {
		for (int t = VillagePlanner.PLAZA_RADIUS + 1; t <= radius; t++) {
			for (int w = -1; w <= 1; w++) {
				for (int[] c : new int[][] {{t, w}, {-t, w}, {w, t}, {w, -t}}) {
					if (!protectedAt(c[0], c[1]) && lotAt(c[0], c[1]) == null) {
						roads.add(col(c[0], c[1]));
					}
				}
			}
		}
	}

	private boolean protectedAt(int x, int z) {
		for (VillageState.Rect r : protect) {
			if (r.contains(x, z)) {
				return true;
			}
		}
		return false;
	}

	/** Semente da planta do lote {@code id} (a casa sorteia chale ou sobrado por ela). */
	static long lotSeed(long villageSeed, int id) {
		long h = villageSeed * 0x9E3779B97F4A7C15L + id * 0xBF58476D1CE4E5B9L;
		return h ^ (h >>> 29);
	}

	/** Reserva um lote novo do tipo no melhor lugar livre (null = nao coube). */
	public Lot addLot(String kind, VillageStyle style, long villageSeed, Terrain terrain, Random random) {
		return addNear(kind, style, villageSeed, terrain, random, 0);
	}

	/** Lugar achado: canto, giro, acesso ate a rua e desnivel. */
	record Spot(int x, int z, int rot, List<int[]> access, int spread, double score) {
	}

	/** Grade de ocupacao com soma acumulada para testar retangulos em O(1). */
	private final class Grid {
		final int r;
		final int size;
		final int[] sum;

		Grid(int exclude) {
			r = radius + 2;
			size = 2 * r + 1;
			boolean[] blocked = new boolean[size * size];
			for (long k : roads) {
				mark(blocked, colX(k), colZ(k), ROAD_GAP);
			}
			for (Lot lot : lots) {
				if (lot.id == exclude) {
					continue;
				}
				VillageState.Rect rect = lot.rect();
				for (int x = rect.x0(); x <= rect.x1(); x++) {
					for (int z = rect.z0(); z <= rect.z1(); z++) {
						mark(blocked, x, z, LOT_GAP);
					}
				}
			}
			for (Lamp lamp : lamps) {
				mark(blocked, lamp.x, lamp.z, 0);
			}
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					if (x * x + z * z <= LOT_CLEARANCE * LOT_CLEARANCE || protectedAt(x, z)) {
						blocked[idx(x, z)] = true;
					}
				}
			}
			sum = new int[(size + 1) * (size + 1)];
			for (int i = 0; i < size; i++) {
				for (int j = 0; j < size; j++) {
					sum[(i + 1) * (size + 1) + j + 1] = (blocked[i * size + j] ? 1 : 0) + sum[i * (size + 1) + j + 1]
							+ sum[(i + 1) * (size + 1) + j] - sum[i * (size + 1) + j];
				}
			}
		}

		private int idx(int x, int z) {
			return (x + r) * size + (z + r);
		}

		private void mark(boolean[] blocked, int x, int z, int gap) {
			for (int dx = -gap; dx <= gap; dx++) {
				for (int dz = -gap; dz <= gap; dz++) {
					int gx = x + dx;
					int gz = z + dz;
					if (Math.abs(gx) <= r && Math.abs(gz) <= r) {
						blocked[idx(gx, gz)] = true;
					}
				}
			}
		}

		/** Algum bloqueio no retangulo (inclusivo)? Fora da grade conta como bloqueado. */
		boolean any(int x0, int z0, int x1, int z1) {
			if (x0 < -r || z0 < -r || x1 > r || z1 > r) {
				return true;
			}
			int i0 = x0 + r;
			int j0 = z0 + r;
			int i1 = x1 + r + 1;
			int j1 = z1 + r + 1;
			int s = size + 1;
			return sum[i1 * s + j1] - sum[i0 * s + j1] - sum[i1 * s + j0] + sum[i0 * s + j0] > 0;
		}
	}

	/** Problema geometrico (sem olhar o terreno) de um lote em (x, z) girado {@code rot}; null = cabe. */
	public Problem geometry(int x, int z, int rot, int cw, int cd, int exclude) {
		return geometry(new Grid(exclude), x, z, rot, cw, cd);
	}

	private Problem geometry(Grid grid, int x, int z, int rot, int cw, int cd) {
		int w = rot % 2 == 0 ? cw : cd;
		int d = rot % 2 == 0 ? cd : cw;
		int x1 = x + w - 1;
		int z1 = z + d - 1;
		for (int[] c : new int[][] {{x, z}, {x1, z}, {x, z1}, {x1, z1}}) {
			if (c[0] * c[0] + c[1] * c[1] > radius * radius) {
				return Problem.OUTSIDE;
			}
		}
		int nx = Math.max(x, Math.min(0, x1));
		int nz = Math.max(z, Math.min(0, z1));
		if (nx * nx + nz * nz <= LOT_CLEARANCE * LOT_CLEARANCE) {
			return Problem.PLAZA;
		}
		if (grid.any(x, z, x1, z1)) {
			return Problem.OVERLAP;
		}
		return null;
	}

	/** Problema de terreno do lote: coluna ocupada/agua ou desnivel demais; null = da para aplainar e construir. */
	public static Problem terrain(Terrain terrain, int x, int z, int w, int d) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int i = x; i < x + w; i++) {
			for (int j = z; j < z + d; j++) {
				if (!terrain.free(i, j)) {
					return Problem.TERRAIN;
				}
				int g = terrain.ground(i, j);
				min = Math.min(min, g);
				max = Math.max(max, g);
			}
		}
		return max - min > MAX_SPREAD ? Problem.STEEP : null;
	}

	private static int spread(Terrain terrain, int x, int z, int w, int d) {
		int min = Integer.MAX_VALUE;
		int max = Integer.MIN_VALUE;
		for (int i = x; i < x + w; i++) {
			for (int j = z; j < z + d; j++) {
				int g = terrain.ground(i, j);
				min = Math.min(min, g);
				max = Math.max(max, g);
			}
		}
		return max - min;
	}

	/**
	 * Melhor lugar para uma planta {@code cw}x{@code cd}: geometria livre, terreno aplainavel e acesso a pe ate a rua.
	 * Prefere perto do centro, plano e com acesso curto, com um pouco de sorteio.
	 */
	Spot findSpot(int cw, int cd, int door, Terrain terrain, Random random, int minR, int exclude) {
		Grid grid = new Grid(exclude);
		List<Spot> candidates = new ArrayList<>();
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				for (int rot = 0; rot < 4; rot++) {
					if (geometry(grid, x, z, rot, cw, cd) != null) {
						continue;
					}
					int w = rot % 2 == 0 ? cw : cd;
					int d = rot % 2 == 0 ? cd : cw;
					double cx = x + w / 2.0;
					double cz = z + d / 2.0;
					double dist = Math.sqrt(cx * cx + cz * cz);
					if (dist < minR) {
						continue;
					}
					candidates.add(new Spot(x, z, rot, List.of(), 0, dist + random.nextDouble() * 4));
				}
			}
		}
		candidates.sort(Comparator.comparingDouble(Spot::score));
		Spot best = null;
		int found = 0;
		int tried = 0;
		for (Spot c : candidates) {
			if (found >= 6 || tried >= 5000) {
				break;
			}
			int w = c.rot % 2 == 0 ? cw : cd;
			int d = c.rot % 2 == 0 ? cd : cw;
			tried++;
			if (terrain(terrain, c.x, c.z, w, d) != null) {
				continue;
			}
			List<int[]> access = access(c.x, c.z, w, d, c.rot, cw, cd, door, terrain, exclude);
			if (access == null) {
				continue;
			}
			int spread = spread(terrain, c.x, c.z, w, d);
			double score = c.score + spread * 1.5 + access.size() * 0.8;
			found++;
			if (best == null || score < best.score) {
				best = new Spot(c.x, c.z, c.rot, access, spread, score);
			}
		}
		return best;
	}

	/**
	 * Caminho da frente do lote ate a rua ou a praca mais perto (busca em largura), so por colunas livres ou de rua,
	 * fora de lotes, postes e areas protegidas. A primeira celula e a da frente da porta. Null = sem acesso.
	 */
	List<int[]> access(int x, int z, int w, int d, int rot, int cw, int cd, int door, Terrain terrain, int exclude) {
		int[] front = Lot.front(x, z, rot, cw, cd, door);
		VillageState.Rect self = new VillageState.Rect(x, z, x + w - 1, z + d - 1);
		if (isRoad(front[0], front[1]) || !passable(front[0], front[1], self, terrain, exclude)) {
			// a porta nao abre direto na rua (o lote ficaria colado nela)
			return null;
		}
		if (goal(front[0], front[1])) {
			return List.of(front);
		}
		Map<Long, Long> parent = new HashMap<>();
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		long start = col(front[0], front[1]);
		parent.put(start, start);
		queue.add(new int[] {front[0], front[1], 0});
		while (!queue.isEmpty()) {
			int[] p = queue.poll();
			if (p[2] >= MAX_ACCESS) {
				continue;
			}
			for (Facing f : Facing.values()) {
				int nx = p[0] + f.dx;
				int nz = p[1] + f.dz;
				long k = col(nx, nz);
				if (parent.containsKey(k) || nx * nx + nz * nz > (radius + 1) * (radius + 1)) {
					continue;
				}
				if (goal(nx, nz)) {
					List<int[]> path = new ArrayList<>();
					long cur = col(p[0], p[1]);
					path.add(new int[] {p[0], p[1]});
					while (cur != start) {
						cur = parent.get(cur);
						path.add(new int[] {colX(cur), colZ(cur)});
					}
					Collections.reverse(path);
					return path;
				}
				if (!passable(nx, nz, self, terrain, exclude)) {
					continue;
				}
				parent.put(k, col(p[0], p[1]));
				queue.add(new int[] {nx, nz, p[2] + 1});
			}
		}
		return null;
	}

	private boolean goal(int x, int z) {
		return isRoad(x, z) || x * x + z * z <= VillagePlanner.PLAZA_RADIUS * VillagePlanner.PLAZA_RADIUS;
	}

	private boolean passable(int x, int z, VillageState.Rect self, Terrain terrain, int exclude) {
		if (self.contains(x, z) || protectedAt(x, z)) {
			return false;
		}
		for (Lot lot : lots) {
			if (lot.id != exclude && lot.rect().contains(x, z)) {
				return false;
			}
		}
		for (Lamp lamp : lamps) {
			if (lamp.x == x && lamp.z == z) {
				return false;
			}
		}
		return isRoad(x, z) || terrain.free(x, z);
	}

	/** Postes novos na calcada das ruas, longe dos que ja existem. */
	void addLamps() {
		List<int[]> placed = new ArrayList<>();
		for (Lamp lamp : lamps) {
			placed.add(new int[] {lamp.x, lamp.z});
		}
		for (int[] p : PLAZA_LAMPS) {
			placed.add(p);
		}
		List<int[]> candidates = new ArrayList<>();
		Set<Long> seen = new java.util.HashSet<>();
		for (long k : roads) {
			int rx = colX(k);
			int rz = colZ(k);
			for (Facing f : Facing.values()) {
				int x = rx + f.dx;
				int z = rz + f.dz;
				if (!seen.add(col(x, z)) || isRoad(x, z) || lotAt(x, z) != null || protectedAt(x, z)) {
					continue;
				}
				int d2 = x * x + z * z;
				if (d2 <= (VillagePlanner.PLAZA_RADIUS + 1) * (VillagePlanner.PLAZA_RADIUS + 1) || d2 > (radius - 1) * (radius - 1)) {
					continue;
				}
				candidates.add(new int[] {x, z});
			}
		}
		candidates.sort(Comparator.<int[]>comparingInt(c -> c[0] * c[0] + c[1] * c[1]).thenComparingInt(c -> c[0]).thenComparingInt(c -> c[1]));
		for (int[] c : candidates) {
			boolean far = true;
			for (int[] p : placed) {
				int dx = p[0] - c[0];
				int dz = p[1] - c[1];
				if (dx * dx + dz * dz < LAMP_SPACING * LAMP_SPACING) {
					far = false;
					break;
				}
			}
			if (far) {
				placed.add(c);
				lamps.add(new Lamp(c[0], c[1], false));
			}
		}
	}

	// ---------------------------------------------------------------- escolha do jogador

	/** Lotes do tipo ainda vazios, o preferido primeiro, depois do mais antigo (mais perto) para o mais novo. */
	public List<Lot> freeLots(String kind) {
		List<Lot> out = new ArrayList<>();
		for (Lot lot : lots) {
			if (lot.kind.equals(kind) && !lot.built) {
				out.add(lot);
			}
		}
		out.sort(Comparator.comparing((Lot l) -> !l.preferred).thenComparingInt(l -> l.id));
		return out;
	}

	/** O jogador escolheu um lote ja planejado: a proxima obra do tipo vai nele. */
	public boolean prefer(int id) {
		Lot target = lot(id);
		if (target == null || target.built) {
			return false;
		}
		for (Lot lot : lots) {
			if (lot.kind.equals(target.kind)) {
				lot.preferred = lot == target;
			}
		}
		return true;
	}

	/**
	 * Muda um lote vazio do tipo {@code kind} (o preferido, ou o primeiro) para (x, z, rot) escolhido pelo jogador e o
	 * marca como preferido. Devolve o problema (null = mudou).
	 */
	public Problem relocate(String kind, int x, int z, int rot, Terrain terrain) {
		List<Lot> free = freeLots(kind);
		if (free.isEmpty()) {
			return Problem.OVERLAP;
		}
		Lot lot = free.getFirst();
		if (master != null) {
			// plano diretor: so lotes do plano; a obra vai no lote com a porta para a rua
			MasterPlan.Parcel parcel = master.parcelAt(x, z);
			Problem problem = parcelProblem(kind, parcel, lot.cw, lot.cd, openRings(), lot.id);
			if (problem != null) {
				return problem;
			}
			placeIn(lot, parcel);
			prefer(lot.id);
			return null;
		}
		Problem geo = geometry(x, z, rot, lot.cw, lot.cd, lot.id);
		if (geo != null) {
			return geo;
		}
		int w = rot % 2 == 0 ? lot.cw : lot.cd;
		int d = rot % 2 == 0 ? lot.cd : lot.cw;
		Problem ground = terrain(terrain, x, z, w, d);
		if (ground != null) {
			return ground;
		}
		List<int[]> path = access(x, z, w, d, rot, lot.cw, lot.cd, lot.door, terrain, lot.id);
		if (path == null) {
			return Problem.NO_ACCESS;
		}
		// o acesso antigo deixa de ser rua se ninguem mais usa
		for (int[] c : lot.access) {
			if (!usedByOther(lot, c)) {
				roads.remove(col(c[0], c[1]));
			}
		}
		lot.move(x, z, rot, path);
		for (int[] c : path) {
			roads.add(col(c[0], c[1]));
		}
		prefer(lot.id);
		return null;
	}

	private boolean usedByOther(Lot self, int[] cell) {
		if (Math.abs(cell[0]) <= 1 || Math.abs(cell[1]) <= 1) {
			return true;
		}
		for (Lot lot : lots) {
			if (lot != self) {
				for (int[] c : lot.access) {
					if (c[0] == cell[0] && c[1] == cell[1]) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/** Lote vazio do tipo onde da para construir agora (o preferido primeiro); null = nenhum. */
	public Lot buildable(String kind, Terrain terrain) {
		if (master != null) {
			// so em quarteirao ja aplainado (nada e construido em terreno cru); se o lote reservado ainda nao esta,
			// a obra muda para um lote pronto do mesmo tipo
			List<Lot> free = freeLots(kind);
			for (Lot lot : free) {
				MasterPlan.Parcel p = master.parcel(lot.parcel);
				if (p != null && !p.player() && master.block(p.block()).prepared()) {
					return lot;
				}
			}
			for (Lot lot : free) {
				MasterPlan.Parcel alt = pickParcel(kind, lot.cw, lot.cd, openRings(), lot.id, true);
				if (alt != null) {
					placeIn(lot, alt);
					return lot;
				}
			}
			return null;
		}
		for (Lot lot : freeLots(kind)) {
			if (terrain(terrain, lot.x, lot.z, lot.w(), lot.d()) == null) {
				int[] f = lot.front();
				if (terrain.free(f[0], f[1]) || isRoad(f[0], f[1])) {
					return lot;
				}
			}
		}
		return null;
	}

	/**
	 * Caminho de ruas do lote ate a praca: o acesso do lote e depois as ruas planejadas (busca em largura so por rua)
	 * ate a borda da praca.
	 */
	public List<int[]> routeToPlaza(Lot lot) {
		List<int[]> out = new ArrayList<>(lot.access);
		if (out.isEmpty()) {
			return out;
		}
		int[] last = out.getLast();
		Map<Long, Long> parent = new HashMap<>();
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		// comeca nas ruas vizinhas da ultima celula do acesso
		for (Facing f : Facing.values()) {
			int nx = last[0] + f.dx;
			int nz = last[1] + f.dz;
			long k = col(nx, nz);
			if (isRoad(nx, nz) && !parent.containsKey(k)) {
				parent.put(k, k);
				queue.add(new int[] {nx, nz});
			}
		}
		long end = Long.MIN_VALUE;
		while (!queue.isEmpty()) {
			int[] p = queue.poll();
			if (p[0] * p[0] + p[1] * p[1] <= (VillagePlanner.PLAZA_RADIUS + 1) * (VillagePlanner.PLAZA_RADIUS + 1)) {
				end = col(p[0], p[1]);
				break;
			}
			for (Facing f : Facing.values()) {
				int nx = p[0] + f.dx;
				int nz = p[1] + f.dz;
				long k = col(nx, nz);
				if (isRoad(nx, nz) && !parent.containsKey(k)) {
					parent.put(k, col(p[0], p[1]));
					queue.add(new int[] {nx, nz});
				}
			}
		}
		if (end == Long.MIN_VALUE) {
			return out;
		}
		List<int[]> tail = new ArrayList<>();
		long cur = end;
		while (true) {
			tail.add(new int[] {colX(cur), colZ(cur)});
			long prev = parent.get(cur);
			if (prev == cur) {
				break;
			}
			cur = prev;
		}
		Collections.reverse(tail);
		Set<Long> have = new java.util.HashSet<>();
		for (int[] c : out) {
			have.add(col(c[0], c[1]));
		}
		for (int[] c : tail) {
			if (have.add(col(c[0], c[1]))) {
				out.add(c);
			}
		}
		return out;
	}

	/** Lote vazio do tipo mais parecido com o pedido (para o cliente encaixar o fantasma). */
	public Lot firstFree(String kind) {
		List<Lot> free = freeLots(kind);
		return free.isEmpty() ? null : free.getFirst();
	}

	/** Reserva sob medida (vila inicial: fogueira perto do quadro). */
	public Lot addNear(String kind, VillageStyle style, long villageSeed, Terrain terrain, Random random, int minR) {
		if (master != null) {
			return reserve(kind, style, villageSeed, openRings());
		}
		int id = nextId;
		long seed = lotSeed(villageSeed, id);
		Canvas canvas = canvas(kind, style, seed);
		Spot spot = findSpot(canvas.w(), canvas.d(), canvas.doorX(), terrain, random, minR, -1);
		if (spot == null) {
			return null;
		}
		nextId++;
		Lot lot = new Lot(id, kind, spot.x, spot.z, spot.rot, canvas.w(), canvas.d(), canvas.doorX(), seed);
		lot.setAccess(spot.access);
		lots.add(lot);
		for (int[] c : spot.access) {
			roads.add(col(c[0], c[1]));
		}
		return lot;
	}
}
