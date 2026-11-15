package com.mlin.notas.village;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Plano diretor da vila (logica pura): calculado uma vez ao fundar, ja no tamanho final (muralha externa, meia-largura
 * 74), e salvo. Os niveis so abrem e constroem o proximo anel; nada que ja foi planejado muda de lugar.
 *
 * <p>Uma grade de celulas centrada no quadro guarda o que e cada coluna: praca, rua (avenida, anel, travessa ou
 * contorno; com a altura final de cada celula), quarteirao (com a altura do plato), lago fundo ou area recortada
 * (construcao de jogador, obra antiga ou area protegida, nunca mexida). Cada quarteirao e um plato com altura alvo e
 * pertence a um dos seis aneis de terraplenagem; os lotes ({@link Parcel}) tem frente para uma rua, tamanho
 * ({@link SizeClass}) e zona ({@link Zone}). A rampa entre platos fica sempre na rua, nunca dentro de lote.
 *
 * <p>Fases seguintes: obras publicas leem {@link #street}/{@link #streetKind}, os aneis de terraplenagem sao
 * {@link #columns}, e tipos novos de construcao registram classe e zonas em {@link LotRules}.
 */
public final class MasterPlan {
	public static final int VERSION = 2;
	/** Versao do plano de antes da subdivisao densa (lotes {@link SizeClass#T}, faixa da muralha maior). */
	public static final int VERSION_SPARSE = 1;
	/** Aneis de terraplenagem: 1..6 dentro da muralha externa e o 7 = anel rural fora dela ({@link RuralRing}). */
	public static final int RINGS = 7;
	/** Esmeraldas fixas da terraplenagem de cada anel (1..7). */
	public static final int[] RING_EMERALDS = {5, 10, 15, 20, 30, 40, 30};
	/** Agua mais funda que isso vira lago (nunca lote); ate isso e aterrada. */
	public static final int SHALLOW = 2;
	/** Diferenca maxima de altura entre quarteiroes vizinhos (a rua entre eles leva a rampa). */
	public static final int MAX_STEP = 4;

	public static final int OUT = 0;
	public static final int PLAZA = 1;
	public static final int LAKE = 2;
	public static final int CARVED = 3;
	/** Faixa da muralha externa (fora do plano de 74, ate o anel rural): muralha, torres e barbacas; nunca lote. */
	public static final int WALL_BAND = 4;
	/** Borda do anel rural (cerca viva e porteiras). */
	public static final int RURAL_EDGE = 5;
	/** Rua: {@code STREET + tipo} ({@link StreetPlanner#AVENUE}...); ponte sobre agua funda: {@code BRIDGE + tipo}. */
	public static final int STREET = 10;
	public static final int BRIDGE = 20;
	public static final int BLOCK = 100;

	/** Um quarteirao: plato de altura {@code target} no anel {@code ring}. */
	public static final class Block {
		private final int id;
		private final int ring;
		private int target;
		private final int cells;
		private final int cx;
		private final int cz;
		private boolean prepared;

		public Block(int id, int ring, int target, int cells, int cx, int cz, boolean prepared) {
			this.id = id;
			this.ring = ring;
			this.target = target;
			this.cells = cells;
			this.cx = cx;
			this.cz = cz;
			this.prepared = prepared;
		}

		public int id() {
			return id;
		}

		/** Anel de terraplenagem (1..6). */
		public int ring() {
			return ring;
		}

		/** Altura do topo do plato (o bloco de grama). */
		public int target() {
			return target;
		}

		void setTarget(int target) {
			this.target = target;
		}

		public int cells() {
			return cells;
		}

		/** Centro aproximado (para a etiqueta do contorno). */
		public int cx() {
			return cx;
		}

		public int cz() {
			return cz;
		}

		/** Terreno ja na altura (terraplenagem pronta ou o jogador acertou a mao). */
		public boolean prepared() {
			return prepared;
		}

		public void setPrepared(boolean prepared) {
			this.prepared = prepared;
		}

		public Zone zone() {
			return Zone.ofRing(ring);
		}
	}

	/** Lote do plano: retangulo com frente ({@code front} = direcao do lote para a rua), tamanho e zona. */
	public static final class Parcel {
		private final int id;
		private final int block;
		private final VillageState.Rect rect;
		private final Facing front;
		private final SizeClass size;
		private final Zone zone;
		private boolean player;
		private boolean plot;
		private boolean terraformPending;
		private int floor = Integer.MIN_VALUE;

		public Parcel(int id, int block, VillageState.Rect rect, Facing front, SizeClass size, Zone zone, boolean player) {
			this.id = id;
			this.block = block;
			this.rect = rect;
			this.front = front;
			this.size = size;
			this.zone = zone;
			this.player = player;
		}

		public int id() {
			return id;
		}

		public int block() {
			return block;
		}

		public VillageState.Rect rect() {
			return rect;
		}

		public Facing front() {
			return front;
		}

		public SizeClass size() {
			return size;
		}

		public Zone zone() {
			return zone;
		}

		/** Lote aposentado pela subdivisao nova (retangulo vazio): o indice fica, ninguem usa. */
		public boolean retired() {
			return rect.x1() < rect.x0() || rect.z1() < rect.z0();
		}

		static Parcel retiredOf(Parcel old) {
			return new Parcel(old.id, old.block, new VillageState.Rect(0, 0, -1, -1), old.front, old.size, old.zone, false);
		}

		/** "Lote do jogador": apareceu construcao de jogador nele, ou e um terreno do jogador ({@link #plot}); a vila nunca mexe nem constroi aqui. */
		public boolean player() {
			return player || plot;
		}

		/** So o "lote do jogador" achado por construcao (o terreno reservado fica de fora; e o que a varredura mexe). */
		public boolean builtByPlayer() {
			return player;
		}

		/** Terreno do jogador (Vilas 3.0 fase 5): reservado para o jogador construir, nunca usado pela vila. */
		public boolean plot() {
			return plot;
		}

		public void setPlot(boolean plot) {
			this.plot = plot;
		}

		/** Terreno do jogador numa vila que ja existia: falta nivelar em volta (o servidor refaz do zero se o mundo reiniciar). */
		public boolean terraformPending() {
			return terraformPending;
		}

		public void setTerraformPending(boolean pending) {
			this.terraformPending = pending;
		}

		/** Chao do terreno (Y do topo, como o campo de alturas); MIN_VALUE = o do quarteirao. */
		public int floor() {
			return floor;
		}

		public void setFloor(int floor) {
			this.floor = floor;
		}

		/** Estado salvo: bit 1 jogador, 2 terreno, 4 nivelar; acima, chao + 1025 (so terreno). Valores antigos (0/1) continuam validos. */
		public int flags() {
			int v = (player ? 1 : 0) | (plot ? 2 : 0) | (terraformPending ? 4 : 0);
			return plot && floor != Integer.MIN_VALUE ? v | ((floor + 1025) << 3) : v;
		}

		public void restoreFlags(int v) {
			player = (v & 1) != 0;
			plot = (v & 2) != 0;
			terraformPending = (v & 4) != 0;
			floor = plot && (v >> 3) > 0 ? (v >> 3) - 1025 : Integer.MIN_VALUE;
		}

		public void setPlayer(boolean player) {
			this.player = player;
		}

		/** Largura ao longo da frente e fundo do lote. */
		public int along() {
			return front.alongX() ? rect.z1() - rect.z0() + 1 : rect.x1() - rect.x0() + 1;
		}

		public int deep() {
			return front.alongX() ? rect.x1() - rect.x0() + 1 : rect.z1() - rect.z0() + 1;
		}

		/** Estacas dos cantos (colunas x, z). */
		public List<int[]> corners() {
			return List.of(new int[] {rect.x0(), rect.z0()}, new int[] {rect.x1(), rect.z0()}, new int[] {rect.x0(), rect.z1()},
					new int[] {rect.x1(), rect.z1()});
		}
	}

	private final long seed;
	private final int extent;
	private final int size;
	private final int plazaY;
	final int[] grid;
	final byte[] rings;
	final int[] heights;
	private final List<Block> blocks;
	private final List<Parcel> parcels;
	private final List<VillageState.Rect> playerAreas;
	private final List<VillageState.Rect> fixedAreas;
	private int[] parcelIndex;
	private int version = VERSION;
	/** Colunas com decoracao das obras publicas (nao contam como construcao de jogador); preenchido pelo servidor, nao salvo. */
	private java.util.Set<Long> ownedColumns = java.util.Set.of();

	public MasterPlan(long seed, int extent, int plazaY, int[] grid, byte[] rings, int[] heights, List<Block> blocks, List<Parcel> parcels,
			List<VillageState.Rect> playerAreas, List<VillageState.Rect> fixedAreas) {
		this.seed = seed;
		this.extent = extent;
		this.size = 2 * extent + 1;
		this.plazaY = plazaY;
		this.grid = grid;
		this.rings = rings;
		this.heights = heights;
		this.blocks = new ArrayList<>(blocks);
		this.parcels = new ArrayList<>(parcels);
		this.playerAreas = new ArrayList<>(playerAreas);
		this.fixedAreas = new ArrayList<>(fixedAreas);
	}

	// ---------------------------------------------------------------- consultas

	public void setOwnedColumns(java.util.Set<Long> columns) {
		this.ownedColumns = columns;
	}

	/** Versao do plano (1 = subdivisao esparsa de antes das obras publicas). */
	public int version() {
		return version;
	}

	public void setVersion(int version) {
		this.version = version;
	}

	/** Aposenta o lote (a lista guarda o indice). */
	void retireParcel(int id) {
		parcels.set(id, Parcel.retiredOf(parcels.get(id)));
		parcelIndex = null;
	}

	/** Acrescenta um lote (o id e o indice na lista). */
	void addParcel(Parcel parcel) {
		parcels.add(parcel);
		parcelIndex = null;
	}

	public long seed() {
		return seed;
	}

	public int extent() {
		return extent;
	}

	/** Y do chao da praca (o bloco embaixo do quadro). */
	public int plazaY() {
		return plazaY;
	}

	int idx(int x, int z) {
		return (x + extent) * size + (z + extent);
	}

	public boolean in(int x, int z) {
		return Math.abs(x) <= extent && Math.abs(z) <= extent;
	}

	public int code(int x, int z) {
		return in(x, z) ? grid[idx(x, z)] : OUT;
	}

	/** Rua (inclusive ponte). */
	public boolean street(int x, int z) {
		int c = code(x, z);
		return c >= STREET && c < BLOCK;
	}

	/** Rua de chao (sem ponte): da para andar e entra na terraplenagem. */
	public boolean ground(int x, int z) {
		int c = code(x, z);
		return c >= STREET && c < BRIDGE;
	}

	/** Tipo da rua ({@link StreetPlanner#AVENUE}...) ou 0. */
	public int streetKind(int x, int z) {
		int c = code(x, z);
		return c >= BRIDGE && c < BLOCK ? c - BRIDGE : c >= STREET && c < BRIDGE ? c - STREET : 0;
	}

	public boolean plaza(int x, int z) {
		return code(x, z) == PLAZA;
	}

	/** Anel de terraplenagem da celula (1..6, 0 = fora do plano). */
	public int ring(int x, int z) {
		return in(x, z) ? rings[idx(x, z)] : 0;
	}

	/**
	 * Anel {@code ring} aberto com {@code open} aneis comuns abertos (1..6, {@link VillageLevel#rings}): o anel rural (7)
	 * abre junto com os arredores (anel 5, Cidade), antes da muralha externa.
	 */
	public static boolean isOpen(int ring, int open) {
		return ring == RuralRing.RING ? open >= RuralRing.OPENS_WITH : ring >= 1 && ring <= open;
	}

	/** O plano ja tem o anel rural (expandido ate {@link RuralRing#EXTENT}). */
	public boolean rural() {
		return extent >= RuralRing.EXTENT;
	}

	/** Altura final da rua na celula (Integer.MIN_VALUE se nao e rua de chao). */
	public int streetHeight(int x, int z) {
		return ground(x, z) ? heights[idx(x, z)] : Integer.MIN_VALUE;
	}

	public Block blockAt(int x, int z) {
		int c = code(x, z);
		return c >= BLOCK ? blocks.get(c - BLOCK) : null;
	}

	public Block block(int id) {
		return id >= 0 && id < blocks.size() ? blocks.get(id) : null;
	}

	public List<Block> blocks() {
		return Collections.unmodifiableList(blocks);
	}

	public List<Parcel> parcels() {
		return Collections.unmodifiableList(parcels);
	}

	public Parcel parcel(int id) {
		return id >= 0 && id < parcels.size() ? parcels.get(id) : null;
	}

	public Parcel parcelAt(int x, int z) {
		if (!in(x, z)) {
			return null;
		}
		if (parcelIndex == null) {
			int[] index = new int[size * size];
			Arrays.fill(index, -1);
			for (Parcel p : parcels) {
				VillageState.Rect r = p.rect;
				if (p.retired()) {
					continue;
				}
				for (int px = r.x0(); px <= r.x1(); px++) {
					for (int pz = r.z0(); pz <= r.z1(); pz++) {
						if (in(px, pz)) {
							index[idx(px, pz)] = p.id;
						}
					}
				}
			}
			parcelIndex = index;
		}
		int id = parcelIndex[idx(x, z)];
		return id < 0 ? null : parcels.get(id);
	}

	/** Areas com construcao de jogador achadas ao planejar (recortadas: nunca mexidas). */
	public List<VillageState.Rect> playerAreas() {
		return Collections.unmodifiableList(playerAreas);
	}

	/** Obras antigas e areas protegidas (recortadas). */
	public List<VillageState.Rect> fixedAreas() {
		return Collections.unmodifiableList(fixedAreas);
	}

	/** Altura planejada do chao na celula (plato, rua ou praca); Integer.MIN_VALUE onde o plano nao mexe. */
	public int plannedHeight(int x, int z) {
		int c = code(x, z);
		if (c >= BLOCK) {
			return blocks.get(c - BLOCK).target;
		}
		if (c >= STREET && c < BRIDGE) {
			return heights[idx(x, z)];
		}
		if (c == PLAZA) {
			return plazaY;
		}
		return Integer.MIN_VALUE;
	}

	/**
	 * Colunas da terraplenagem do anel {@code ring}: as dos quarteiroes do anel (quarteirao por quarteirao) e depois as
	 * ruas de chao do anel. Ordem estavel (a obra salva so o indice).
	 */
	public List<Long> columns(int ring) {
		List<Long> out = new ArrayList<>();
		for (Block b : blocks) {
			if (b.ring != ring) {
				continue;
			}
			for (int x = -extent; x <= extent; x++) {
				for (int z = -extent; z <= extent; z++) {
					if (grid[idx(x, z)] == BLOCK + b.id) {
						out.add(VillageLayout.col(x, z));
					}
				}
			}
		}
		for (int x = -extent; x <= extent; x++) {
			for (int z = -extent; z <= extent; z++) {
				int i = idx(x, z);
				if (rings[i] == ring && grid[i] >= STREET && grid[i] < BRIDGE) {
					out.add(VillageLayout.col(x, z));
				}
			}
		}
		if (ring == 1) {
			// a praca tambem fica na altura do quadro (sem arvores)
			for (int x = -extent; x <= extent; x++) {
				for (int z = -extent; z <= extent; z++) {
					if (grid[idx(x, z)] == PLAZA) {
						out.add(VillageLayout.col(x, z));
					}
				}
			}
		}
		return out;
	}

	/** Todos os quarteiroes do anel estao prontos. */
	public boolean ringPrepared(int ring) {
		for (Block b : blocks) {
			if (b.ring == ring && !b.prepared) {
				return false;
			}
		}
		return true;
	}

	public void setRingPrepared(int ring) {
		for (Block b : blocks) {
			if (b.ring == ring) {
				b.prepared = true;
			}
		}
	}

	public boolean insidePlayerArea(int x, int z) {
		for (VillageState.Rect r : playerAreas) {
			if (r.contains(x, z)) {
				return true;
			}
		}
		return insidePlot(x, z);
	}

	/** A coluna esta dentro de um terreno do jogador. */
	public boolean insidePlot(int x, int z) {
		for (Parcel p : parcels) {
			if (p.plot && !p.retired() && p.rect.contains(x, z)) {
				return true;
			}
		}
		return false;
	}

	/** Terrenos do jogador (reservados na criacao ou registrados depois). */
	public List<Parcel> plots() {
		List<Parcel> out = new ArrayList<>();
		for (Parcel p : parcels) {
			if (p.plot && !p.retired()) {
				out.add(p);
			}
		}
		return out;
	}

	/** Chao do terreno: o registrado, ou o plato do quarteirao. */
	public int plotFloor(Parcel p) {
		if (p.floor != Integer.MIN_VALUE) {
			return p.floor;
		}
		Block b = block(p.block);
		return b != null ? b.target : plazaY;
	}

	/** Recorte novo (terreno do jogador sobre quarteirao): as celulas de quarteirao viram area recortada, nunca aplainadas. */
	void carveCells(VillageState.Rect r) {
		for (int x = r.x0(); x <= r.x1(); x++) {
			for (int z = r.z0(); z <= r.z1(); z++) {
				if (in(x, z) && grid[idx(x, z)] >= BLOCK) {
					grid[idx(x, z)] = CARVED;
				}
			}
		}
	}

	/** Celula onde o plano espera construcao da vila ou do jogador (nao conta como "coluna de jogador nova"). */
	public boolean known(int x, int z) {
		if (!ownedColumns.isEmpty() && ownedColumns.contains(VillageLayout.col(x, z))) {
			return true;
		}
		int c = code(x, z);
		if (c == CARVED || c == PLAZA || c == WALL_BAND || c == RURAL_EDGE || (c >= STREET && c < BLOCK)) {
			return true;
		}
		Parcel p = parcelAt(x, z);
		if (p != null) {
			for (int[] k : p.corners()) {
				if (k[0] == x && k[1] == z) {
					return true;
				}
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- formato salvo

	/** Grade em pares (valor, repeticoes). */
	public int[] gridRle() {
		return rle(grid);
	}

	public int[] ringRle() {
		int[] values = new int[rings.length];
		for (int i = 0; i < rings.length; i++) {
			values[i] = rings[i];
		}
		return rle(values);
	}

	/** Alturas das ruas na ordem da grade (so as celulas de rua de chao). */
	public int[] streetHeights() {
		int n = 0;
		for (int c : grid) {
			if (c >= STREET && c < BRIDGE) {
				n++;
			}
		}
		int[] out = new int[n];
		int k = 0;
		for (int i = 0; i < grid.length; i++) {
			if (grid[i] >= STREET && grid[i] < BRIDGE) {
				out[k++] = heights[i];
			}
		}
		return out;
	}

	static int[] rle(int[] values) {
		List<Integer> out = new ArrayList<>();
		int i = 0;
		while (i < values.length) {
			int v = values[i];
			int j = i;
			while (j < values.length && values[j] == v) {
				j++;
			}
			out.add(v);
			out.add(j - i);
			i = j;
		}
		return out.stream().mapToInt(Integer::intValue).toArray();
	}

	static int[] unrle(int[] pairs, int length) {
		int[] out = new int[length];
		int k = 0;
		for (int i = 0; i + 1 < pairs.length && k < length; i += 2) {
			for (int n = 0; n < pairs[i + 1] && k < length; n++) {
				out[k++] = pairs[i];
			}
		}
		return out;
	}

	/** Remonta o plano salvo. */
	public static MasterPlan restore(long seed, int extent, int plazaY, int[] gridRle, int[] ringRle, int[] streetHeights, List<Block> blocks,
			List<Parcel> parcels, List<VillageState.Rect> playerAreas, List<VillageState.Rect> fixedAreas) {
		int size = 2 * extent + 1;
		int[] grid = unrle(gridRle, size * size);
		int[] ringValues = unrle(ringRle, size * size);
		byte[] rings = new byte[size * size];
		for (int i = 0; i < rings.length; i++) {
			rings[i] = (byte) ringValues[i];
		}
		int[] heights = new int[size * size];
		int k = 0;
		for (int i = 0; i < grid.length; i++) {
			if (grid[i] >= STREET && grid[i] < BRIDGE) {
				heights[i] = k < streetHeights.length ? streetHeights[k++] : plazaY;
			}
		}
		return new MasterPlan(seed, extent, plazaY, grid, rings, heights, blocks, parcels, playerAreas, fixedAreas);
	}

	// ---------------------------------------------------------------- criacao

	/**
	 * Calcula o plano. {@code plazaY}: chao da praca; {@code fixed}: obras antigas (relativas, viram recortes e as ruas
	 * passam em volta); {@code protect}: areas onde a vila nunca mexe; {@code villageCells}: colunas que ja sao da vila
	 * (ruas e postes antigos, nao contam como jogador); {@code straightUntil}: avenidas retas ate esse raio (vila antiga).
	 */
	public static MasterPlan create(long seed, Terrain terrain, int plazaY, List<VillageState.Rect> fixed, List<VillageState.Rect> protect,
			Set<Long> villageCells, int straightUntil) {
		int extent = StreetPlanner.OUTER + 2;
		int size = 2 * extent + 1;
		int[] bed = new int[size * size];
		int[] water = new int[size * size];
		boolean[] player = new boolean[size * size];
		for (int x = -extent; x <= extent; x++) {
			for (int z = -extent; z <= extent; z++) {
				int i = (x + extent) * size + (z + extent);
				water[i] = terrain.water(x, z);
				bed[i] = terrain.ground(x, z) - water[i];
				player[i] = x * x + z * z > 7.5 * 7.5 && terrain.playerBuilt(x, z) && !inside(fixed, x, z) && !inside(protect, x, z)
						&& !villageCells.contains(VillageLayout.col(x, z));
			}
		}
		// construcao compacta (casa, curral): area recortada com a rua em volta; coisa espalhada (trilha, tocha
		// solta): so a propria coluna sai do plano, a rua passa por cima sem mexer nela
		boolean[] sparse = new boolean[size * size];
		List<VillageState.Rect> playerAreas = clusters(player, extent, sparse);
		List<VillageState.Rect> obstacles = new ArrayList<>(playerAreas);
		obstacles.addAll(fixed);
		obstacles.addAll(protect);
		StreetPlanner.Field field = new StreetPlanner.Field() {
			@Override
			public int bed(int x, int z) {
				return Math.abs(x) <= extent && Math.abs(z) <= extent ? bed[(x + extent) * size + (z + extent)] : plazaY;
			}

			@Override
			public int water(int x, int z) {
				return Math.abs(x) <= extent && Math.abs(z) <= extent ? water[(x + extent) * size + (z + extent)] : 0;
			}

			@Override
			public boolean blocked(int x, int z) {
				return insideGrown(obstacles, x, z, 1);
			}
		};
		int diagonals = straightUntil > 0 || fixed.size() > 0 ? 0 : (int) Math.floorMod(seed >>> 7, 3L);
		StreetPlanner.Result streets = StreetPlanner.plan(seed, field, diagonals, straightUntil);
		for (VillageState.Rect area : obstacles) {
			StreetPlanner.loop(streets, area, (x, z) -> x * x + z * z <= StreetPlanner.PLAZA * StreetPlanner.PLAZA || otherObstacle(obstacles, area, x, z));
		}
		int[] grid = new int[size * size];
		byte[] rings = new byte[size * size];
		for (int x = -extent; x <= extent; x++) {
			for (int z = -extent; z <= extent; z++) {
				int i = (x + extent) * size + (z + extent);
				if (!streets.inside(x, z)) {
					grid[i] = OUT;
					continue;
				}
				rings[i] = (byte) streets.terrainRing(x, z);
				int kind = streets.kind[streets.idx(x, z)];
				if (x * x + z * z <= StreetPlanner.PLAZA * StreetPlanner.PLAZA) {
					grid[i] = PLAZA;
				} else if (kind != 0) {
					grid[i] = (water[i] > SHALLOW ? BRIDGE : STREET) + kind;
				} else if (insideGrown(obstacles, x, z, 1) || sparse[i]) {
					grid[i] = CARVED;
				} else if (water[i] > SHALLOW) {
					grid[i] = LAKE;
				} else {
					grid[i] = -1;
				}
			}
		}
		List<Block> blocks = label(grid, rings, bed, extent, 0);
		int[] heights = new int[size * size];
		List<VillageState.Rect> fixedAll = new ArrayList<>(fixed);
		fixedAll.addAll(protect);
		MasterPlan plan = new MasterPlan(seed, extent, plazaY, grid, rings, heights, blocks, List.of(), playerAreas, fixedAll);
		PlateauGrading.grade(plan, bed);
		plan.parcels.addAll(BlockSubdivider.subdivide(plan, seed));
		return plan;
	}

	private static boolean otherObstacle(List<VillageState.Rect> obstacles, VillageState.Rect self, int x, int z) {
		for (VillageState.Rect r : obstacles) {
			if (r != self && r.x0() - 1 <= x && x <= r.x1() + 1 && r.z0() - 1 <= z && z <= r.z1() + 1) {
				return true;
			}
		}
		return false;
	}

	static boolean inside(List<VillageState.Rect> rects, int x, int z) {
		for (VillageState.Rect r : rects) {
			if (r.contains(x, z)) {
				return true;
			}
		}
		return false;
	}

	static boolean insideGrown(List<VillageState.Rect> rects, int x, int z, int grow) {
		for (VillageState.Rect r : rects) {
			if (r.x0() - grow <= x && x <= r.x1() + grow && r.z0() - grow <= z && z <= r.z1() + grow) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Agrupa colunas de jogador proximas (folga de 2). Grupo compacto (ocupa 1/4+ da propria caixa: casa, curral) vira
	 * um retangulo com 1 de margem; grupo espalhado (trilha, tochas soltas) marca so as proprias colunas em
	 * {@code sparse}.
	 */
	static List<VillageState.Rect> clusters(boolean[] player, int extent, boolean[] sparse) {
		int size = 2 * extent + 1;
		boolean[] seen = new boolean[player.length];
		List<VillageState.Rect> out = new ArrayList<>();
		for (int start = 0; start < player.length; start++) {
			if (!player[start] || seen[start]) {
				continue;
			}
			int x0 = Integer.MAX_VALUE;
			int z0 = Integer.MAX_VALUE;
			int x1 = Integer.MIN_VALUE;
			int z1 = Integer.MIN_VALUE;
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			List<Integer> members = new ArrayList<>();
			queue.add(start);
			seen[start] = true;
			while (!queue.isEmpty()) {
				int i = queue.poll();
				members.add(i);
				int x = i / size - extent;
				int z = i % size - extent;
				x0 = Math.min(x0, x);
				z0 = Math.min(z0, z);
				x1 = Math.max(x1, x);
				z1 = Math.max(z1, z);
				for (int dx = -2; dx <= 2; dx++) {
					for (int dz = -2; dz <= 2; dz++) {
						int nx = x + dx;
						int nz = z + dz;
						if (Math.abs(nx) > extent || Math.abs(nz) > extent) {
							continue;
						}
						int j = (nx + extent) * size + (nz + extent);
						if (player[j] && !seen[j]) {
							seen[j] = true;
							queue.add(j);
						}
					}
				}
			}
			int area = (x1 - x0 + 1) * (z1 - z0 + 1);
			if (members.size() * 4 >= area) {
				out.add(new VillageState.Rect(x0 - 1, z0 - 1, x1 + 1, z1 + 1));
			} else {
				for (int i : members) {
					sparse[i] = true;
				}
			}
		}
		// areas que se encostam viram uma so
		boolean merged = true;
		while (merged) {
			merged = false;
			outer:
			for (int a = 0; a < out.size(); a++) {
				for (int b = a + 1; b < out.size(); b++) {
					if (out.get(a).overlaps(out.get(b), 2)) {
						VillageState.Rect ra = out.get(a);
						VillageState.Rect rb = out.get(b);
						out.set(a, new VillageState.Rect(Math.min(ra.x0(), rb.x0()), Math.min(ra.z0(), rb.z0()), Math.max(ra.x1(), rb.x1()),
								Math.max(ra.z1(), rb.z1())));
						out.remove(b);
						merged = true;
						break outer;
					}
				}
			}
		}
		return out;
	}

	/** Separa os quarteiroes (celulas -1 ligadas, mesmo anel) e grava {@code BLOCK + id}; alvo inicial = mediana. */
	static List<Block> label(int[] grid, byte[] rings, int[] bed, int extent, int firstId) {
		int size = 2 * extent + 1;
		List<Block> out = new ArrayList<>();
		for (int start = 0; start < grid.length; start++) {
			if (grid[start] != -1) {
				continue;
			}
			int id = firstId + out.size();
			int ring = rings[start];
			List<Integer> cells = new ArrayList<>();
			ArrayDeque<Integer> queue = new ArrayDeque<>();
			queue.add(start);
			grid[start] = BLOCK + id;
			long sx = 0;
			long sz = 0;
			while (!queue.isEmpty()) {
				int i = queue.poll();
				cells.add(i);
				int x = i / size - extent;
				int z = i % size - extent;
				sx += x;
				sz += z;
				for (Facing f : Facing.values()) {
					int nx = x + f.dx;
					int nz = z + f.dz;
					if (Math.abs(nx) > extent || Math.abs(nz) > extent) {
						continue;
					}
					int j = (nx + extent) * size + (nz + extent);
					if (grid[j] == -1 && rings[j] == ring) {
						grid[j] = BLOCK + id;
						queue.add(j);
					}
				}
			}
			int[] h = new int[cells.size()];
			for (int k = 0; k < h.length; k++) {
				h[k] = bed[cells.get(k)];
			}
			Arrays.sort(h);
			out.add(new Block(id, ring, h[h.length / 2], cells.size(), (int) (sx / cells.size()), (int) (sz / cells.size()), false));
		}
		return out;
	}
}
