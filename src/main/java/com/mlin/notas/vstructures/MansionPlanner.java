package com.mlin.notas.vstructures;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Mansao da floresta (woodland mansion) refeita: casarao de carvalho escuro e pedregulho de 57x41 blocos com tres
 * andares. Terreo e primeiro andar ocupam a planta inteira (7x5 celulas de 8 blocos); o segundo andar ocupa as tres
 * fileiras do meio e o resto vira terraco com parapeito e lanternas. Em cada andar um salao corrido no meio (passadeira,
 * lustres) com salas dos dois lados; escadas de um lance (terreo -> 1o andar no fundo do meio, 1o -> 2o no canto do
 * fundo) e telhado de quatro aguas de degraus de carvalho escuro com chamines e sotao.
 *
 * <p>Salas sorteadas por tamanho, como na vanilla: biblioteca, salao de jantar, arena com allays presas e estatua de
 * vex, sala de xadrez, sala do X, sala das aranhas (teias), quartos, sala de mapas, deposito, cozinha, sala de
 * obsidiana ("portal do End falso"), estatuas de galinha e de gato, arvore de la, sala de flores, estudo e galeria.
 * Dois segredos: o cofre atras de uma parede de estantes no salao do 2o andar (marcador {@link #MARKER_SECRET_WALL},
 * quebre as estantes) e o sotao, pela escada de mao do armario. Vindicadores e evocadores nascem com a estrutura. Baus
 * com {@code woodland_mansion}. Entrada na frente (-z) com alpendre, avenida e postes. No jardim palido (variante 1) a
 * madeira vira carvalho palido. Logica pura.
 *
 * <p>Referencias (so ideia de forma, camadas e paleta; nada copiado): a mansao vanilla (salas tematicas, planta em
 * grade, carvalho escuro com pedregulho e vidro), Mansion Redux (formas e salas mais variadas), DnT Woodland Mansion
 * Overhaul (caminho saindo da mansao), Fancier Mansions do Planet Minecraft (paleta mais escura, telhados altos) e
 * casaroes vitorianos de galerias de builds (fachada simetrica, alpendre de colunas, chamines, terraco com parapeito).
 */
public final class MansionPlanner extends PlanBuilder {
	public static final String LOOT = "minecraft:chests/woodland_mansion";
	public static final String MARKER_SECRET_WALL = "secret_wall";
	public static final String MARKER_VAULT = "vault";
	public static final String MARKER_ATTIC = "attic";
	public static final String MARKER_ARENA = "arena";
	public static final String MARKER_HALL = "hall";
	public static final int PALE = 1;
	static final int CELL = 8;
	static final int OX = -28;
	static final int OZ = -20;
	static final int STOREY = 7;
	static final int ROOF = 20;

	enum Type {
		ENTRANCE, STAIRS, STAIRS_TOP, STAIRS2, STAIRS2_TOP, LIBRARY, DINING, ARENA, CHESS, XROOM, SPIDER, BEDROOM, MAP, STORAGE, KITCHEN, OBSIDIAN,
		SMALL_BEDROOM, CHICKEN, CAT, FLOWERS, TREE, CLOSET, VAULT, STUDY, GALLERY
	}

	/** Sala: celulas [i1..i2] x [j1..j2] no andar {@code floor}; {@code hall} = lado do salao (N ou S). */
	private static final class Room {
		final int floor;
		final int i1;
		final int i2;
		final int j1;
		final int j2;
		final String hall;
		Type type;
		final Set<Long> keep = new HashSet<>();

		Room(int floor, int i1, int i2, int j1, int j2, String hall) {
			this.floor = floor;
			this.i1 = i1;
			this.i2 = i2;
			this.j1 = j1;
			this.j2 = j2;
			this.hall = hall;
		}

		int x1() {
			return CELL * i1 + 1;
		}

		int x2() {
			return CELL * (i2 + 1) - 1;
		}

		int z1() {
			return CELL * j1 + 1;
		}

		int z2() {
			return CELL * (j2 + 1) - 1;
		}

		int y() {
			return STOREY * floor;
		}

		int width() {
			return i2 - i1 + 1;
		}

		int depth() {
			return j2 - j1 + 1;
		}

		/** Linha da parede do lado do salao. */
		int hallWall() {
			return hall.equals(S) ? z2() + 1 : z1() - 1;
		}

		/** Fileira do fundo (oposta ao salao). */
		int backZ() {
			return hall.equals(S) ? z1() : z2();
		}

		/** Fileira encostada no salao. */
		int frontZ() {
			return hall.equals(S) ? z2() : z1();
		}

		/** Um passo para o fundo. */
		int inward() {
			return hall.equals(S) ? -1 : 1;
		}

		int midX() {
			return (x1() + x2()) / 2;
		}

		int midZ() {
			return (z1() + z2()) / 2;
		}
	}

	private final List<Room> rooms = new ArrayList<>();
	private int secrets;
	private boolean evoker;

	private MansionPlanner(Random random) {
		super(new VPlan(false), random);
	}

	public static VPlan plan(long seed, int variant) {
		MansionPlanner b = new MansionPlanner(new Random(seed * 7919L + 4243));
		b.build();
		if (variant == PALE) {
			b.pale();
		}
		return b.p;
	}

	// --- coordenadas locais (0..56 x 0..40) ---

	private void b(int x, int y, int z, String state) {
		p.set(x + OX, y, z + OZ, state);
	}

	private void b(int x, int y, int z, String state, int flag) {
		p.set(x + OX, y, z + OZ, state, flag);
	}

	private String at(int x, int y, int z) {
		return p.get(x + OX, y, z + OZ);
	}

	private void box(int x1, int y1, int z1, int x2, int y2, int z2, String state) {
		for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++) {
			for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
				for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) {
					b(x, y, z, state);
				}
			}
		}
	}

	private void chest(int x, int y, int z, String facing) {
		p.loot(x + OX, y, z + OZ, s("chest", "facing=" + facing), LOOT);
	}

	private void mob(String type, int x, int y, int z) {
		p.entity(type, x + OX + 0.5, y, z + OZ + 0.5, "persistent");
		evoker |= type.endsWith("evoker");
	}

	private void mark(String name, int x, int y, int z) {
		p.mark(name, x + OX, y, z + OZ);
	}

	private boolean put(Room room, int x, int y, int z, String state) {
		if (room.keep.contains(VPlan.key(x, y, z))) {
			return false;
		}
		b(x, y, z, state);
		return true;
	}

	private boolean free(Room room, int x, int y, int z) {
		String s = at(x, y, z);
		return !room.keep.contains(VPlan.key(x, y, z)) && s != null && BlockTraits.isAir(s);
	}

	private static boolean inF2(int z) {
		return z >= 8 && z <= 32;
	}

	// --- montagem ---

	private void build() {
		grounds();
		storeys();
		layout();
		for (Room room : rooms) {
			partition(room);
		}
		for (Room room : rooms) {
			doorway(room);
		}
		exterior();
		stairs();
		terraces();
		for (Room room : rooms) {
			decorate(room);
		}
		halls();
		roof();
		towers();
		porch();
		clearAbove();
		p.removeFloating();
		mark(VPlan.MARKER_ENTRANCE, 28, 0, -8);
	}

	/** Jardim em volta: caminho de pedregulho, grama com flores e arbustos, avenida com postes ate o alpendre. */
	private void grounds() {
		for (int x = -8; x <= 64; x++) {
			for (int z = -12; z <= 48; z++) {
				int d = Math.max(Math.max(-x, x - 56), Math.max(-z, z - 40));
				if (d <= 0) {
					b(x, -2, z, "minecraft:cobblestone", VPlan.FILL_DOWN);
					continue;
				}
				boolean avenue = z < 0 && x >= 25 && x <= 31;
				String ground = avenue || d <= 4 ? pick("minecraft:cobblestone", "minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:gravel")
						: "minecraft:grass_block";
				if (avenue && x > 25 && x < 31) {
					ground = "minecraft:dirt_path";
				}
				b(x, -1, z, ground, VPlan.FILL_SOIL);
				if (ground.equals("minecraft:grass_block")) {
					double v = r.nextDouble();
					if (v < 0.05) {
						b(x, 0, z, pick("minecraft:poppy", "minecraft:dandelion", "minecraft:oxeye_daisy", "minecraft:azure_bluet", "minecraft:lily_of_the_valley"));
					} else if (v < 0.12) {
						b(x, 0, z, "minecraft:short_grass");
					} else if (v < 0.14 && d >= 6) {
						b(x, 0, z, s("dark_oak_leaves", "persistent=true"));
					}
				}
			}
		}
		// postes de lanterna ao longo da avenida
		for (int z = -10; z <= -7; z += 3) {
			for (int x : new int[] {24, 32}) {
				b(x, -1, z, "minecraft:cobblestone", VPlan.FILL_DOWN);
				b(x, 0, z, "minecraft:cobblestone");
				b(x, 1, z, "minecraft:polished_andesite");
				b(x, 2, z, s("lantern", "hanging=false"));
			}
		}
	}

	/** Ar acima do jardim e da casa onde nada foi colocado: corta o morro. */
	private void clearAbove() {
		for (int x = -8; x <= 64; x++) {
			for (int z = -12; z <= 48; z++) {
				int d = Math.max(Math.max(-x, x - 56), Math.max(-z, z - 40));
				int top = d <= 0 ? ROOF + 16 : 14;
				for (int y = 0; y <= top; y++) {
					if (at(x, y, z) == null) {
						b(x, y, z, VPlan.AIR);
					}
				}
			}
		}
	}

	/** Pisos e casca externa dos tres andares, com colunas de tronco a cada celula. */
	private void storeys() {
		for (int f = 0; f <= 2; f++) {
			int y = STOREY * f;
			int z1 = f == 2 ? 8 : 0;
			int z2 = f == 2 ? 32 : 40;
			for (int x = 0; x <= 56; x++) {
				for (int z = z1; z <= z2; z++) {
					boolean edge = x == 0 || x == 56 || z == z1 || z == z2;
					b(x, y - 1, z, edge ? "minecraft:cobblestone" : f == 1 ? "minecraft:birch_planks" : "minecraft:dark_oak_planks");
					for (int k = 0; k <= 5; k++) {
						if (edge) {
							boolean post = (z == z1 || z == z2) ? x % CELL == 0 : z % CELL == 0;
							String wall = post ? axis("minecraft:dark_oak_log", "y") : f == 0 && k == 0 ? "minecraft:cobblestone" : "minecraft:dark_oak_planks";
							b(x, y + k, z, wall);
						} else {
							b(x, y + k, z, VPlan.AIR);
						}
					}
				}
			}
		}
	}

	/** Divide cada andar em salas dos dois lados do salao do meio. */
	private void layout() {
		split(0, new int[] {0, 1, 2}, 0, 1, S);
		addRoom(0, 3, 3, 0, 1, S, Type.ENTRANCE);
		split(0, new int[] {4, 5, 6}, 0, 1, S);
		split(0, new int[] {0, 1, 2}, 3, 4, N);
		addRoom(0, 3, 3, 3, 4, N, Type.STAIRS);
		split(0, new int[] {4, 5, 6}, 3, 4, N);

		split(1, new int[] {0, 1, 2, 3, 4, 5, 6}, 0, 1, S);
		split(1, new int[] {0, 1, 2}, 3, 4, N);
		addRoom(1, 3, 3, 3, 4, N, Type.STAIRS_TOP);
		addRoom(1, 4, 4, 3, 4, N, null);
		addRoom(1, 5, 6, 3, 4, N, Type.STAIRS2);

		// 2o andar: so as fileiras 1..3 (7 e 5 celulas: sempre sobra sala de 1)
		split(2, new int[] {0, 1, 2, 3, 4, 5, 6}, 1, 1, S);
		split(2, new int[] {0, 1, 2, 3, 4}, 3, 3, N);
		addRoom(2, 5, 6, 3, 3, N, Type.STAIRS2_TOP);

		List<Room> topSmall = new ArrayList<>();
		for (Room room : rooms) {
			if (room.floor == 2 && room.type == null && room.width() == 1) {
				topSmall.add(room);
			}
		}
		Collections.shuffle(topSmall, r);
		topSmall.get(0).type = Type.CLOSET;
		topSmall.get(1).type = Type.VAULT;

		List<Type> big = shuffled(Type.ARENA, Type.LIBRARY, Type.DINING, Type.CHESS, Type.XROOM, Type.SPIDER);
		big.remove(Type.ARENA);
		big.addFirst(Type.ARENA);
		List<Type> tall = shuffled(Type.BEDROOM, Type.MAP, Type.STORAGE, Type.KITCHEN, Type.OBSIDIAN, Type.BEDROOM, Type.STORAGE, Type.MAP,
				Type.BEDROOM, Type.KITCHEN, Type.OBSIDIAN, Type.BEDROOM);
		List<Type> small = shuffled(Type.SMALL_BEDROOM, Type.CHICKEN, Type.CAT, Type.FLOWERS, Type.TREE, Type.SMALL_BEDROOM, Type.FLOWERS);
		List<Type> wide = shuffled(Type.STUDY, Type.GALLERY, Type.STUDY, Type.GALLERY);
		List<Room> bigRooms = new ArrayList<>();
		for (Room room : rooms) {
			if (room.type == null && room.width() == 2 && room.depth() == 2) {
				bigRooms.add(room);
			}
		}
		Collections.shuffle(bigRooms, r);
		for (Room room : bigRooms) {
			room.type = big.removeFirst();
			if (big.isEmpty()) {
				big = shuffled(Type.LIBRARY, Type.DINING, Type.CHESS, Type.XROOM);
			}
		}
		for (Room room : rooms) {
			if (room.type != null) {
				continue;
			}
			List<Type> from = room.depth() == 2 ? tall : room.width() == 2 ? wide : small;
			if (from.isEmpty()) {
				from.addAll(room.depth() == 2 ? shuffled(Type.BEDROOM, Type.STORAGE, Type.MAP, Type.KITCHEN)
						: room.width() == 2 ? shuffled(Type.STUDY, Type.GALLERY) : shuffled(Type.SMALL_BEDROOM, Type.FLOWERS, Type.CAT, Type.CHICKEN));
			}
			room.type = from.removeFirst();
		}
	}

	private List<Type> shuffled(Type... types) {
		List<Type> out = new ArrayList<>(List.of(types));
		Collections.shuffle(out, r);
		return out;
	}

	/** Reparte as celulas (em sequencia) em salas de 1 ou 2 de largura. */
	private void split(int floor, int[] cells, int j1, int j2, String hall) {
		int k = 0;
		while (k < cells.length) {
			int w = cells.length - k == 1 ? 1 : chance(0.55) ? 2 : 1;
			addRoom(floor, cells[k], cells[k] + w - 1, j1, j2, hall, null);
			k += w;
		}
	}

	private void addRoom(int floor, int i1, int i2, int j1, int j2, String hall, Type type) {
		Room room = new Room(floor, i1, i2, j1, j2, hall);
		room.type = type;
		rooms.add(room);
	}

	/** Paredes da sala (do lado do salao e entre salas) e piso. */
	private void partition(Room room) {
		int y = room.y();
		String wall = room.floor == 1 ? "minecraft:dark_oak_planks" : "minecraft:birch_planks";
		for (int k = 0; k <= 5; k++) {
			int hz = room.hallWall();
			for (int x = room.x1() - 1; x <= room.x2() + 1; x++) {
				if (x > 0 && x < 56) {
					b(x, y + k, hz, x % CELL == 0 ? axis("minecraft:dark_oak_log", "y") : "minecraft:dark_oak_planks");
				}
			}
			for (int z = room.z1(); z <= room.z2(); z++) {
				for (int x : new int[] {room.x1() - 1, room.x2() + 1}) {
					if (x > 0 && x < 56) {
						b(x, y + k, z, z % CELL == 0 ? axis("minecraft:stripped_dark_oak_log", "y") : wall);
					}
				}
			}
			for (int x = room.x1(); x <= room.x2(); x++) {
				for (int z = room.z1(); z <= room.z2(); z++) {
					b(x, y + k, z, VPlan.AIR);
				}
			}
		}
		for (int x = room.x1(); x <= room.x2(); x++) {
			for (int z = room.z1(); z <= room.z2(); z++) {
				b(x, y - 1, z, room.floor == 1 ? "minecraft:birch_planks" : "minecraft:dark_oak_planks");
			}
		}
	}

	/** Porta da sala para o salao (a do cofre e uma parede de estantes). */
	private void doorway(Room room) {
		int y = room.y();
		int z = room.hallWall();
		int mid = switch (room.type == null ? Type.BEDROOM : room.type) {
			case STAIRS, STAIRS_TOP -> 28;
			case STAIRS2, STAIRS2_TOP -> 48;
			default -> room.midX();
		};
		int half = room.width() == 2 || room.type == Type.STAIRS || room.type == Type.STAIRS_TOP || room.type == Type.ENTRANCE ? 1 : 0;
		for (int x = mid - half; x <= mid + half; x++) {
			for (int k = 0; k <= 1 + half; k++) {
				b(x, y + k, z, VPlan.AIR);
			}
			for (int d = 1; d <= 2; d++) {
				for (int k = 0; k <= 2; k++) {
					room.keep.add(VPlan.key(x, y + k, z + d * room.inward()));
				}
			}
		}
		if (half == 1) {
			for (int x = mid - 2; x <= mid + 2; x++) {
				b(x, y + 3, z, axis("minecraft:dark_oak_log", "x"));
			}
			return;
		}
		if (room.type == Type.VAULT) {
			b(mid, y, z, "minecraft:bookshelf");
			b(mid, y + 1, z, "minecraft:bookshelf");
			mark(MARKER_SECRET_WALL + (secrets++), mid, y, z);
			return;
		}
		String facing = room.hall.equals(S) ? N : S;
		b(mid, y, z, s("dark_oak_door", "facing=" + facing, "half=lower", "hinge=left", "open=false", "powered=false"));
		b(mid, y + 1, z, s("dark_oak_door", "facing=" + facing, "half=upper", "hinge=left", "open=false", "powered=false"));
	}

	/** Janelas com verga de tronco e a porta da frente. */
	private void exterior() {
		for (int f = 0; f <= 2; f++) {
			int y = STOREY * f;
			int z1 = f == 2 ? 8 : 0;
			int z2 = f == 2 ? 32 : 40;
			for (int x = 0; x <= 56; x++) {
				for (int z = z1; z <= z2; z++) {
					boolean edge = x == 0 || x == 56 || z == z1 || z == z2;
					boolean corner = (x == 0 || x == 56) && (z == z1 || z == z2);
					if (!edge || corner) {
						continue;
					}
					boolean frontBack = z == z1 || z == z2;
					int off = Math.floorMod(frontBack ? x : z, CELL);
					if (off >= 3 && off <= 5) {
						b(x, y + 1, z, "minecraft:glass_pane");
						b(x, y + 2, z, "minecraft:glass_pane");
						b(x, y + 3, z, off == 4 ? "minecraft:dark_oak_planks" : "minecraft:glass_pane");
						b(x, y + 4, z, axis("minecraft:dark_oak_log", frontBack ? "x" : "z"));
					}
				}
			}
		}
		for (int x = 27; x <= 29; x++) {
			for (int k = 0; k <= 2; k++) {
				b(x, k, 0, VPlan.AIR);
			}
			b(x, 3, 0, axis("minecraft:dark_oak_log", "x"));
		}
		b(26, 3, 0, "minecraft:chiseled_stone_bricks");
		b(30, 3, 0, "minecraft:chiseled_stone_bricks");
	}

	/** Escadas de um lance: terreo -> 1o andar (meio, fundo) e 1o -> 2o (canto do fundo), com vao guardado por cerca. */
	private void stairs() {
		for (int k = 0; k <= 6; k++) {
			int z = 27 + k;
			for (int x = 27; x <= 29; x++) {
				for (int y = 0; y < k; y++) {
					b(x, y, z, "minecraft:dark_oak_planks");
				}
				b(x, k, z, stairs("minecraft:dark_oak_stairs", S));
			}
		}
		for (int z = 28; z <= 32; z++) {
			for (int x = 27; x <= 29; x++) {
				b(x, 6, z, VPlan.AIR);
			}
			b(26, 7, z, "minecraft:dark_oak_fence");
			b(30, 7, z, "minecraft:dark_oak_fence");
		}
		for (int x = 26; x <= 30; x++) {
			b(x, 7, 27, "minecraft:dark_oak_fence");
		}
		b(28, 12, 30, s("iron_chain", "axis=y"));
		b(28, 11, 30, s("lantern", "hanging=true"));
		for (int k = 0; k <= 6; k++) {
			int x = 43 + k;
			for (int z = 27; z <= 29; z++) {
				for (int y = 7; y < 7 + k; y++) {
					b(x, y, z, "minecraft:dark_oak_planks");
				}
				b(x, 7 + k, z, stairs("minecraft:dark_oak_stairs", E));
			}
		}
		for (int x = 44; x <= 48; x++) {
			for (int z = 27; z <= 29; z++) {
				b(x, 13, z, VPlan.AIR);
			}
			b(x, 14, 26, "minecraft:dark_oak_fence");
			b(x, 14, 30, "minecraft:dark_oak_fence");
		}
		for (int z = 26; z <= 30; z++) {
			b(43, 14, z, "minecraft:dark_oak_fence");
		}
	}

	private void decorate(Room room) {
		switch (room.type) {
			case ENTRANCE -> entrance(room);
			case LIBRARY -> library(room);
			case DINING -> dining(room);
			case ARENA -> arena(room);
			case CHESS -> chess(room);
			case XROOM -> xroom(room);
			case SPIDER -> spider(room);
			case BEDROOM -> bedroom(room, true);
			case SMALL_BEDROOM -> bedroom(room, false);
			case MAP -> mapRoom(room);
			case STORAGE -> storage(room);
			case KITCHEN -> kitchen(room);
			case OBSIDIAN -> obsidian(room);
			case CHICKEN -> chicken(room);
			case CAT -> cat(room);
			case FLOWERS -> flowers(room);
			case TREE -> tree(room);
			case CLOSET -> closet(room);
			case VAULT -> vault(room);
			case STUDY -> study(room);
			case GALLERY -> gallery(room);
			case STAIRS2 -> stairHall(room);
			default -> {
			}
		}
		if (room.type != Type.VAULT && room.type != Type.CLOSET && room.type != Type.STAIRS && room.type != Type.STAIRS_TOP
				&& room.type != Type.TREE && room.type != Type.SPIDER) {
			lantern(room, room.midX(), room.midZ());
		}
	}

	private void lantern(Room room, int x, int z) {
		int top = room.y() + 5;
		if (free(room, x, top, z) && free(room, x, top - 1, z)) {
			b(x, top, z, s("iron_chain", "axis=y"));
			b(x, top - 1, z, s("lantern", "hanging=true"));
		}
	}

	private void carpet(Room room, String color, int inset) {
		for (int x = room.x1() + inset; x <= room.x2() - inset; x++) {
			for (int z = room.z1() + inset; z <= room.z2() - inset; z++) {
				if (free(room, x, room.y(), z)) {
					b(x, room.y(), z, "minecraft:" + color + "_carpet");
				}
			}
		}
	}

	/** Tem vidro na parede de fora ao lado desse ponto (nao cobrir a janela). */
	private boolean windowBehind(int x, int y, int z) {
		for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
			String s = at(x + d[0], y, z + d[1]);
			if (s != null && VPlan.id(s).equals("glass_pane")) {
				return true;
			}
		}
		return false;
	}

	// --- salas ---

	private void entrance(Room room) {
		int y = room.y();
		for (int z = room.z1(); z <= room.z2(); z++) {
			for (int x = room.x1(); x <= room.x2(); x++) {
				b(x, y - 1, z, (x + z) % 2 == 0 ? "minecraft:polished_andesite" : "minecraft:cobblestone");
			}
			if (z % 4 == 2) {
				b(room.x1(), y + 2, z, wallTorch(E));
				b(room.x2(), y + 2, z, wallTorch(W));
			}
		}
		for (int z = room.z1() + 1; z <= room.z2() - 3; z++) {
			b(28, y, z, "minecraft:red_carpet");
		}
		for (int x : new int[] {room.x1(), room.x2()}) {
			for (int z : new int[] {room.z1() + 4, room.z2() - 4}) {
				b(x, y, z, "minecraft:potted_dark_oak_sapling");
			}
		}
		lantern(room, 28, room.z1() + 4);
		lantern(room, 28, room.z2() - 4);
		mob("minecraft:vindicator", 26, y, 8);
	}

	private void library(Room room) {
		int y = room.y();
		for (int x = room.x1(); x <= room.x2(); x++) {
			for (int z = room.z1(); z <= room.z2(); z++) {
				boolean edge = x == room.x1() || x == room.x2() || z == room.z1() || z == room.z2();
				if (!edge) {
					continue;
				}
				for (int k = 0; k <= 3; k++) {
					if (!windowBehind(x, y + k, z)) {
						put(room, x, y + k, z, k == 3 ? slabTop("minecraft:dark_oak_slab") : "minecraft:bookshelf");
					}
				}
			}
		}
		int cx = room.midX();
		int cz = room.midZ();
		for (int dx = -4; dx <= 4; dx += 8) {
			for (int z = cz - 3; z <= cz + 3; z++) {
				put(room, cx + dx, y, z, "minecraft:bookshelf");
				put(room, cx + dx, y + 1, z, z == cz ? "minecraft:chiseled_bookshelf" : "minecraft:bookshelf");
			}
		}
		for (int x = cx - 1; x <= cx + 1; x++) {
			b(x, y, cz, slabTop("minecraft:dark_oak_slab"));
			b(x, y, cz - 1, stairs("minecraft:dark_oak_stairs", S));
			b(x, y, cz + 1, stairs("minecraft:dark_oak_stairs", N));
		}
		b(cx, y + 1, cz, s("candle", "candles=3", "lit=true"));
		put(room, cx - 2, y, cz - 3, s("lectern", "facing=south"));
		put(room, cx + 2, y, cz + 3, s("lectern", "facing=north"));
		chest(cx - 2, y, cz + 3, N);
		if (chance(0.5)) {
			chest(cx + 2, y, cz - 3, S);
		}
		mob("minecraft:evoker", cx - 2, y, cz);
	}

	private void dining(Room room) {
		int y = room.y();
		int cz = room.midZ();
		carpet(room, "red", 2);
		for (int x = room.x1() + 3; x <= room.x2() - 3; x++) {
			b(x, y, cz, slabTop("minecraft:dark_oak_slab"));
			b(x, y, cz + 1, slabTop("minecraft:dark_oak_slab"));
			if (x % 2 == 0) {
				b(x, y, cz - 1, stairs("minecraft:dark_oak_stairs", S));
				b(x, y, cz + 2, stairs("minecraft:dark_oak_stairs", N));
			}
			if (x % 3 == 0) {
				b(x, y + 1, cz, s("candle", "candles=" + between(1, 3), "lit=true"));
			}
		}
		b(room.midX(), y + 1, cz + 1, "minecraft:cake");
		b(room.x1() + 2, y, cz, stairs("minecraft:dark_oak_stairs", E));
		b(room.x2() - 2, y, cz, stairs("minecraft:dark_oak_stairs", W));
		for (int z = room.z1() + 2; z <= room.z2() - 2; z += 4) {
			put(room, room.x1(), y, z, s("barrel", "facing=east"));
			put(room, room.x2(), y, z, s("barrel", "facing=west"));
		}
		lantern(room, room.x1() + 4, cz);
		lantern(room, room.x2() - 4, cz);
		mob("minecraft:vindicator", room.x1() + 2, y, room.z1() + 2);
	}

	/** Arena: anel de pedregulho, jaulas de grade com allays e a estatua de vex no meio. */
	private void arena(Room room) {
		int y = room.y();
		int cx = room.midX();
		int cz = room.midZ();
		for (int x = room.x1(); x <= room.x2(); x++) {
			for (int z = room.z1(); z <= room.z2(); z++) {
				int d = Math.max(Math.abs(x - cx), Math.abs(z - cz));
				b(x, y - 1, z, d == 5 ? "minecraft:mossy_cobblestone" : d < 5 ? "minecraft:cobblestone" : "minecraft:dark_oak_planks");
			}
		}
		b(cx, y, cz, "minecraft:polished_andesite");
		b(cx, y + 1, cz, "minecraft:light_gray_wool");
		b(cx, y + 2, cz, "minecraft:skeleton_skull");
		b(cx - 1, y + 1, cz, "minecraft:iron_bars");
		b(cx + 1, y + 1, cz, "minecraft:iron_bars");
		b(cx, y + 1, cz + 1, s("iron_chain", "axis=z"));
		int cages = 0;
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				int gx = cx + 5 * sx;
				int gz = cz + 5 * sz;
				boolean ok = true;
				for (int x = gx - 1; x <= gx + 1; x++) {
					for (int z = gz - 1; z <= gz + 1; z++) {
						for (int k = 0; k <= 2; k++) {
							ok &= !room.keep.contains(VPlan.key(x, y + k, z));
						}
					}
				}
				if (!ok || cages >= 3) {
					continue;
				}
				for (int x = gx - 1; x <= gx + 1; x++) {
					for (int z = gz - 1; z <= gz + 1; z++) {
						boolean wall = x != gx || z != gz;
						b(x, y, z, wall ? "minecraft:iron_bars" : VPlan.AIR);
						b(x, y + 1, z, wall ? "minecraft:iron_bars" : VPlan.AIR);
						b(x, y + 2, z, "minecraft:dark_oak_slab");
					}
				}
				p.entity("minecraft:allay", gx + OX + 0.5, y + 0.2, gz + OZ + 0.5, "persistent");
				cages++;
			}
		}
		mark(MARKER_ARENA, cx, y, cz);
		mob("minecraft:evoker", cx + 2, y, cz + 2);
		mob("minecraft:vindicator", cx - 3, y, cz + 2);
	}

	private void chess(Room room) {
		int y = room.y();
		int cx = room.midX();
		int cz = room.midZ();
		for (int x = cx - 4; x <= cx + 3; x++) {
			for (int z = cz - 4; z <= cz + 3; z++) {
				b(x, y - 1, z, (x + z) % 2 == 0 ? "minecraft:black_wool" : "minecraft:white_wool");
			}
		}
		for (int x = cx - 4; x <= cx + 3; x++) {
			for (int side = 0; side <= 1; side++) {
				String color = side == 0 ? "white" : "black";
				int back = side == 0 ? cz - 4 : cz + 3;
				int pawn = side == 0 ? cz - 3 : cz + 2;
				if (chance(0.7)) {
					b(x, y, pawn, "minecraft:" + color + "_carpet");
				}
				double off = Math.abs(x - cx + 0.5);
				int rank = off < 1 ? 3 : off < 3 ? 2 : 1;
				for (int k = 0; k < rank; k++) {
					b(x, y + k, back, "minecraft:" + color + "_wool");
				}
				b(x, y + rank, back, color.equals("white") ? "minecraft:skeleton_skull" : "minecraft:wither_skeleton_skull");
			}
		}
		chest(room.x1(), y, room.midZ(), E);
		mob("minecraft:vindicator", room.x2() - 1, y, room.midZ());
	}

	/** Sala do X: um X de la vermelha no chao, pilares de tronco nas pontas e velas no meio. */
	private void xroom(Room room) {
		int y = room.y();
		int cx = room.midX();
		int cz = room.midZ();
		for (int d = -6; d <= 6; d++) {
			b(cx + d, y - 1, cz + d, "minecraft:red_wool");
			b(cx + d, y - 1, cz - d, "minecraft:red_wool");
		}
		for (int sx = -1; sx <= 1; sx += 2) {
			for (int sz = -1; sz <= 1; sz += 2) {
				for (int k = 0; k <= 5; k++) {
					put(room, cx + 6 * sx, y + k, cz + 6 * sz, k == 0 ? "minecraft:cobblestone" : axis("minecraft:dark_oak_log", "y"));
				}
			}
		}
		b(cx, y, cz, "minecraft:red_wool");
		b(cx, y + 1, cz, s("candle", "candles=4", "lit=true"));
		chest(cx + 1, y, cz, W);
	}

	private void spider(Room room) {
		int y = room.y();
		for (int x = room.x1(); x <= room.x2(); x++) {
			for (int z = room.z1(); z <= room.z2(); z++) {
				for (int k = 2; k <= 5; k++) {
					if (free(room, x, y + k, z) && chance(k >= 4 ? 0.5 : 0.18)) {
						b(x, y + k, z, "minecraft:cobweb");
					}
				}
				if (free(room, x, y, z) && chance(0.06)) {
					b(x, y, z, "minecraft:cobweb");
				}
			}
		}
		chest(room.midX(), y, room.backZ(), room.hall);
		b(room.midX() + 1, y, room.backZ(), "minecraft:skeleton_skull");
	}

	private void bedroom(Room room, boolean big) {
		int y = room.y();
		int bz = room.backZ();
		int bx = room.midX();
		String head = room.hall.equals(S) ? N : S;
		for (int x = bx - (big ? 1 : 0); x <= bx; x++) {
			b(x, y, bz, s("red_bed", "facing=" + head, "part=head"));
			b(x, y, bz - room.inward(), s("red_bed", "facing=" + head, "part=foot"));
		}
		put(room, bx + 1, y, bz, s("barrel", "facing=up"));
		put(room, bx + 1, y + 1, bz, s("candle", "candles=2", "lit=true"));
		chest(bx - (big ? 2 : 1), y, bz, room.hall);
		if (big) {
			carpet(room, "light_gray", 1);
			put(room, room.x1(), y, room.frontZ() + room.inward() * 3, "minecraft:bookshelf");
			put(room, room.x1(), y + 1, room.frontZ() + room.inward() * 3, "minecraft:flower_pot");
			put(room, room.x2(), y, room.midZ(), s("dark_oak_trapdoor", "facing=west", "half=top", "open=false"));
		} else {
			put(room, room.x2(), y, room.frontZ() + room.inward() * 2, "minecraft:potted_red_tulip");
		}
	}

	private void mapRoom(Room room) {
		int y = room.y();
		int cx = room.midX();
		carpet(room, "brown", 1);
		for (int z = room.z1() + 3; z <= room.z2() - 3; z += 3) {
			put(room, cx, y, z, "minecraft:cartography_table");
		}
		put(room, room.x1(), y, room.backZ(), s("lectern", "facing=east"));
		chest(room.x2(), y, room.backZ(), W);
		for (int z = room.z1(); z <= room.z2(); z++) {
			if (!windowBehind(room.x1(), y + 2, z)) {
				put(room, room.x1(), y + 2, z, s("white_wall_banner", "facing=east"));
			}
		}
	}

	private void storage(Room room) {
		int y = room.y();
		int chests = 0;
		for (int z = room.z1(); z <= room.z2(); z++) {
			for (int x : new int[] {room.x1(), room.x2()}) {
				if (room.keep.contains(VPlan.key(x, y, z))) {
					continue;
				}
				double v = r.nextDouble();
				if (v < 0.15 && chests < 2) {
					chest(x, y, z, x == room.x1() ? E : W);
					chests++;
				} else if (v < 0.5) {
					b(x, y, z, s("barrel", "facing=up"));
				} else if (v < 0.7) {
					b(x, y, z, pick("minecraft:pumpkin", "minecraft:hay_block", "minecraft:melon"));
				}
			}
		}
		if (chests == 0) {
			chest(room.midX(), y, room.backZ(), room.hall);
		}
		put(room, room.midX(), y, room.midZ(), "minecraft:crafting_table");
	}

	private void kitchen(Room room) {
		int y = room.y();
		int bz = room.backZ();
		for (int x = room.x1(); x <= room.x2(); x++) {
			put(room, x, y, bz, x % 2 == 0 ? s("smoker", "facing=" + room.hall) : s("furnace", "facing=" + room.hall));
		}
		put(room, room.x1(), y, room.midZ(), s("water_cauldron", "level=3"));
		put(room, room.x2(), y, room.midZ(), s("barrel", "facing=up"));
		for (int z = room.midZ() - 1; z <= room.midZ() + 1; z++) {
			put(room, room.midX(), y, z, slabTop("minecraft:spruce_slab"));
		}
		put(room, room.midX(), y + 1, room.midZ(), "minecraft:cake");
		chest(room.x2(), y, room.midZ() + room.inward() * 3, W);
	}

	/** "Portal do End falso": moldura de obsidiana com cantos de obsidiana chorona e o meio negro, cercado de velas. */
	private void obsidian(Room room) {
		int y = room.y();
		int cx = room.midX();
		int cz = room.midZ();
		for (int x = cx - 2; x <= cx + 2; x++) {
			for (int z = cz - 2; z <= cz + 2; z++) {
				int d = Math.max(Math.abs(x - cx), Math.abs(z - cz));
				boolean corner = Math.abs(x - cx) == 2 && Math.abs(z - cz) == 2;
				b(x, y - 1, z, d < 2 ? "minecraft:black_concrete" : corner ? "minecraft:crying_obsidian" : "minecraft:obsidian");
				if (d == 2 && !corner && (x == cx || z == cz)) {
					b(x, y, z, s("candle", "candles=1", "lit=true"));
				}
			}
		}
		for (int z = room.z1(); z <= room.z2(); z += 2) {
			put(room, room.x1(), y, z, "minecraft:obsidian");
			put(room, room.x2(), y, z, "minecraft:obsidian");
		}
	}

	private void chicken(Room room) {
		int y = room.y();
		int cx = room.midX();
		int cz = room.midZ();
		box(cx - 1, y, cz - 1, cx + 1, y + 1, cz + 1, "minecraft:white_wool");
		b(cx, y + 2, cz, "minecraft:white_wool");
		b(cx, y + 2, cz - 1, "minecraft:white_wool");
		b(cx, y + 3, cz - 1, "minecraft:red_wool");
		b(cx, y + 2, cz - 2, "minecraft:orange_wool");
		b(cx - 1, y, cz - 1, "minecraft:orange_wool");
		b(cx + 1, y, cz - 1, "minecraft:orange_wool");
		put(room, room.x1(), y, room.backZ(), "minecraft:hay_block");
	}

	private void cat(Room room) {
		int y = room.y();
		int cx = room.midX();
		int cz = room.midZ();
		box(cx - 1, y, cz, cx + 1, y, cz, "minecraft:orange_wool");
		b(cx - 1, y + 1, cz, "minecraft:orange_wool");
		b(cx - 1, y + 2, cz, "minecraft:black_wool");
		b(cx + 1, y + 1, cz, "minecraft:orange_carpet");
		put(room, room.x2(), y, room.backZ(), "minecraft:potted_cactus");
		put(room, room.x1(), y, room.backZ(), s("barrel", "facing=up"));
	}

	private void flowers(Room room) {
		int y = room.y();
		for (int x = room.x1(); x <= room.x2(); x++) {
			for (int z = room.z1(); z <= room.z2(); z++) {
				boolean edge = x == room.x1() || x == room.x2() || z == room.z1() || z == room.z2();
				if (edge && free(room, x, y, z) && chance(0.6)) {
					b(x, y - 1, z, "minecraft:grass_block");
					b(x, y, z, pick("minecraft:poppy", "minecraft:dandelion", "minecraft:allium", "minecraft:azure_bluet", "minecraft:cornflower",
							"minecraft:lily_of_the_valley", "minecraft:fern"));
				}
			}
		}
		put(room, room.midX(), y, room.midZ(), "minecraft:moss_block");
		put(room, room.midX(), y + 1, room.midZ(), "minecraft:potted_dark_oak_sapling");
	}

	/** Arvore de la branca no meio da sala. */
	private void tree(Room room) {
		int y = room.y();
		int cx = room.midX();
		int cz = room.midZ();
		for (int k = 0; k <= 3; k++) {
			b(cx, y + k, cz, "minecraft:white_wool");
		}
		for (int x = cx - 2; x <= cx + 2; x++) {
			for (int z = cz - 2; z <= cz + 2; z++) {
				if (Math.abs(x - cx) + Math.abs(z - cz) <= 3) {
					b(x, y + 4, z, "minecraft:white_wool");
				}
			}
		}
		b(cx, y + 5, cz, "minecraft:white_wool");
	}

	/** Armario: escada de mao ate o sotao, onde tem um bau. */
	private void closet(Room room) {
		int y = room.y();
		// a escada fica do lado do meio da casa, onde o telhado e alto
		int in = room.i1 < 3 ? -1 : 1;
		int lx = in > 0 ? room.x1() : room.x2();
		int lz = room.frontZ();
		for (int k = 0; k <= 6; k++) {
			b(lx, y + k, lz, ladder(in > 0 ? E : W));
		}
		b(lx - in, y + 6, lz, "minecraft:dark_oak_planks");
		chest(lx + 2 * in, y + 7, lz, in > 0 ? W : E);
		b(lx + 3 * in, y + 7, lz, s("lantern", "hanging=false"));
		b(lx + in, y + 9, lz, "minecraft:cobweb");
		mark(MARKER_ATTIC, lx + in, y + 7, lz);
		int far = in > 0 ? room.x2() : room.x1();
		put(room, far, y, room.backZ(), s("barrel", "facing=up"));
		put(room, far, y + 2, room.backZ(), "minecraft:cobweb");
	}

	/** Cofre secreto: bau, blocos de lapis e velas, atras das estantes do salao. */
	private void vault(Room room) {
		int y = room.y();
		chest(room.midX(), y, room.backZ(), room.hall);
		b(room.x1(), y, room.backZ(), "minecraft:lapis_block");
		b(room.x2(), y, room.backZ(), "minecraft:lapis_block");
		b(room.x1(), y + 1, room.backZ(), s("candle", "candles=3", "lit=false"));
		b(room.x2(), y + 1, room.backZ(), s("candle", "candles=3", "lit=false"));
		for (int x = room.x1(); x <= room.x2(); x++) {
			b(x, y + 4, room.midZ(), "minecraft:cobweb");
		}
		mark(MARKER_VAULT, room.midX(), y, room.backZ());
	}

	private void study(Room room) {
		int y = room.y();
		int bz = room.backZ();
		for (int x = room.x1(); x <= room.x2(); x++) {
			if (!windowBehind(x, y + 1, bz)) {
				put(room, x, y, bz, "minecraft:bookshelf");
				put(room, x, y + 1, bz, "minecraft:bookshelf");
			}
		}
		int cx = room.midX();
		int z = room.midZ();
		for (int x = cx - 1; x <= cx + 1; x++) {
			put(room, x, y, z, slabTop("minecraft:dark_oak_slab"));
		}
		put(room, cx, y, z - room.inward(), stairs("minecraft:dark_oak_stairs", room.hall.equals(S) ? N : S));
		put(room, cx, y + 1, z, s("candle", "candles=2", "lit=true"));
		chest(room.x1(), y, z, E);
		mob("minecraft:vindicator", room.x2() - 1, y, z);
	}

	private void gallery(Room room) {
		int y = room.y();
		carpet(room, "gray", 1);
		for (int x = room.x1() + 1; x <= room.x2() - 1; x += 3) {
			put(room, x, y, room.backZ(), "minecraft:polished_andesite");
			put(room, x, y + 1, room.backZ(), pick("minecraft:decorated_pot", "minecraft:flower_pot", "minecraft:skeleton_skull", "minecraft:creeper_head"));
		}
		for (int x = room.x1() + 2; x <= room.x2() - 2; x += 4) {
			if (free(room, x, y + 1, room.midZ())) {
				p.entity("minecraft:armor_stand", x + OX + 0.5, y, room.midZ() + OZ + 0.5, "");
			}
		}
	}

	private void stairHall(Room room) {
		int y = room.y();
		for (int x = room.x1(); x <= room.x2(); x += 2) {
			put(room, x, y, room.backZ(), "minecraft:bookshelf");
		}
		put(room, room.x2(), y, room.z1() + 6, "minecraft:potted_fern");
		mob("minecraft:vindicator", 52, y, 35);
	}

	// --- saloes, terracos, telhado, alpendre ---

	private void halls() {
		for (int f = 0; f <= 2; f++) {
			int y = STOREY * f;
			for (int x = 1; x <= 55; x++) {
				for (int z = 17; z <= 23; z++) {
					b(x, y - 1, z, z == 17 || z == 23 ? "minecraft:dark_oak_planks" : f == 1 ? "minecraft:birch_planks" : "minecraft:dark_oak_planks");
				}
				if (x >= 2 && x <= 54) {
					b(x, y, 20, "minecraft:red_carpet");
					b(x, y, 19, x % 2 == 0 ? "minecraft:white_carpet" : "minecraft:red_carpet");
					b(x, y, 21, x % 2 == 0 ? "minecraft:white_carpet" : "minecraft:red_carpet");
				}
				if (x % CELL == 4 && x > 4 && x < 52) {
					b(x, y + 5, 20, s("iron_chain", "axis=y"));
					b(x, y + 4, 20, s("lantern", "hanging=true"));
				}
			}
			for (int x = 6; x <= 50; x += 11) {
				for (int z : new int[] {17, 23}) {
					String wall = at(x, y, z == 17 ? 16 : 24);
					if (BlockTraits.isAir(at(x, y, z)) && wall != null && !BlockTraits.isAir(wall) && !VPlan.id(wall).endsWith("door")
							&& !VPlan.id(wall).equals("bookshelf")) {
						b(x, y, z, "minecraft:potted_azure_bluet");
					}
				}
			}
			mob("minecraft:vindicator", 12, y, 20);
			mob("minecraft:vindicator", 44, y, 20);
			mark(MARKER_HALL + f, 28, y, 20);
		}
		if (!evoker) {
			mob("minecraft:evoker", 28, STOREY, 20);
		}
	}

	/** Terracos sobre o 1o andar (frente e fundo) com parapeito e lanternas; uma porta de cada lado do 2o andar. */
	private void terraces() {
		for (int x = 0; x <= 56; x++) {
			for (int z = 0; z <= 40; z++) {
				if (inF2(z)) {
					continue;
				}
				boolean edge = x == 0 || x == 56 || z == 0 || z == 40;
				b(x, 13, z, edge ? "minecraft:cobblestone" : "minecraft:dark_oak_planks");
				if (edge) {
					boolean post = x % CELL == 0 && (z == 0 || z == 40) || z % CELL == 0 && (x == 0 || x == 56);
					b(x, 14, z, post ? "minecraft:cobblestone" : "minecraft:cobblestone_wall");
					if (post) {
						b(x, 15, z, s("lantern", "hanging=false"));
					}
				}
			}
		}
		for (int side = 0; side <= 1; side++) {
			int z = side == 0 ? 8 : 32;
			int inside = side == 0 ? 9 : 31;
			for (Room room : rooms) {
				if (room.floor != 2 || room.type == Type.VAULT || room.type == Type.CLOSET || room.type == Type.STAIRS2_TOP
						|| room.j1 != (side == 0 ? 1 : 3)) {
					continue;
				}
				int x = room.x1() + 1;
				String facing = side == 0 ? N : S;
				b(x, 14, z, s("dark_oak_door", "facing=" + facing, "half=lower", "hinge=left", "open=false", "powered=false"));
				b(x, 15, z, s("dark_oak_door", "facing=" + facing, "half=upper", "hinge=left", "open=false", "powered=false"));
				for (int k = 0; k <= 2; k++) {
					room.keep.add(VPlan.key(x, 14 + k, inside));
				}
				break;
			}
		}
	}

	/** Telhado de quatro aguas sobre o 2o andar, com beiral, cumeeira e duas chamines. */
	/** Telhado de quatro aguas de degraus sobre o retangulo (com beiral), tabuas por baixo; devolve a altura da cumeeira. */
	private int hipRoof(int x1, int z1, int x2, int z2, int base) {
		int max = Math.min((x2 - x1) / 2, (z2 - z1) / 2);
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				int dxe = Math.min(x - x1, x2 - x);
				int dze = Math.min(z - z1, z2 - z);
				int d = Math.min(dxe, dze);
				int y = base + d;
				if (d >= 1) {
					b(x, y - 1, z, "minecraft:dark_oak_planks");
				}
				if (d == max) {
					b(x, y, z, "minecraft:dark_oak_planks");
					b(x, y + 1, z, "minecraft:dark_oak_slab");
					continue;
				}
				String facing;
				if (dze <= dxe) {
					facing = z - z1 < z2 - z ? S : N;
				} else {
					facing = x - x1 < x2 - x ? E : W;
				}
				b(x, y, z, stairs("minecraft:dark_oak_stairs", facing));
			}
		}
		return max;
	}

	private boolean nearAttic(int x, int z) {
		for (Room room : rooms) {
			if (room.type == Type.CLOSET && Math.abs(x - room.midX()) <= 6 && Math.abs(z - room.frontZ()) <= 3) {
				return true;
			}
		}
		return false;
	}

	/** Torres nos cantos e no meio da frente, saindo dos terracos, com telhado proprio e porta para o terraco. */
	private void towers() {
		int[][] cells = {{0, 0, 19}, {6, 0, 19}, {0, 4, 19}, {6, 4, 19}, {3, 0, 26}};
		for (int[] c : cells) {
			int x0 = CELL * c[0];
			int z0 = CELL * c[1];
			int top = c[2];
			for (int x = x0; x <= x0 + 8; x++) {
				for (int z = z0; z <= z0 + 8; z++) {
					boolean edge = x == x0 || x == x0 + 8 || z == z0 || z == z0 + 8;
					boolean corner = (x == x0 || x == x0 + 8) && (z == z0 || z == z0 + 8);
					for (int y = 14; y <= top; y++) {
						if (!edge) {
							b(x, y, z, VPlan.AIR);
							continue;
						}
						String was = at(x, y, z);
						if (was != null && VPlan.id(was).endsWith("_door")) {
							continue;
						}
						int off = (x == x0 || x == x0 + 8) ? z - z0 : x - x0;
						boolean window = !corner && off >= 3 && off <= 5 && (y - 14) % 7 >= 1 && (y - 14) % 7 <= 2;
						b(x, y, z, corner ? axis("minecraft:dark_oak_log", "y") : window ? "minecraft:glass_pane"
								: (y - 14) % 7 == 6 ? "minecraft:cobblestone" : "minecraft:dark_oak_planks");
					}
				}
			}
			if (top > 19) {
				for (int x = x0 + 1; x <= x0 + 7; x++) {
					for (int z = z0 + 1; z <= z0 + 7; z++) {
						b(x, 20, z, "minecraft:dark_oak_planks");
					}
				}
				b(x0 + 1, 20, z0 + 2, VPlan.AIR);
				for (int y = 14; y <= 20; y++) {
					b(x0 + 1, y, z0 + 2, ladder(E));
				}
			}
			// porta(s) para o terraco, do lado de dentro da planta
			int zDoor = z0 + 4;
			for (int x : new int[] {x0, x0 + 8}) {
				if (x == 0 || x == 56) {
					continue;
				}
				b(x, 14, zDoor, VPlan.AIR);
				b(x, 15, zDoor, VPlan.AIR);
			}
			b(x0 + 4, top, z0 + 4, s("iron_chain", "axis=y"));
			b(x0 + 4, top - 1, z0 + 4, s("lantern", "hanging=true"));
			hipRoof(x0 - 1, z0 - 1, x0 + 9, z0 + 9, top + 1);
			for (int x = x0 + 1; x <= x0 + 7; x++) {
				for (int z = z0 + 1; z <= z0 + 7; z++) {
					b(x, top + 1, z, "minecraft:dark_oak_planks");
				}
			}
		}
	}

	private void roof() {
		int max = hipRoof(-1, 7, 57, 33, ROOF);
		for (int x = 0; x <= 56; x++) {
			for (int z = 8; z <= 32; z++) {
				String here = at(x, ROOF, z);
				if (x == 0 || x == 56 || z == 8 || z == 32) {
					b(x, ROOF, z, "minecraft:cobblestone");
				} else if (here == null || !VPlan.id(here).equals("ladder")) {
					b(x, ROOF, z, "minecraft:dark_oak_planks");
				}
			}
		}
		// tralha no sotao, longe da escada do armario
		for (int i = 0; i < 30; i++) {
			int x = between(10, 46);
			int z = between(14, 26);
			String here = at(x, ROOF + 1, z);
			if (here != null || nearAttic(x, z)) {
				continue;
			}
			if (chance(0.15)) {
				b(x, ROOF + 1, z, s("lantern", "hanging=false"));
				continue;
			}
			if (chance(0.5)) {
				b(x, ROOF + 1, z, s("barrel", "facing=up"));
			} else {
				b(x, ROOF + 1, z, "minecraft:cobweb");
			}
		}
		for (int cx : new int[] {12, 44}) {
			int top = ROOF + max + 2;
			for (int y = ROOF + 1; y <= top; y++) {
				b(cx, y, 14, "minecraft:cobblestone");
				b(cx + 1, y, 14, y == top ? "minecraft:mossy_cobblestone" : "minecraft:cobblestone");
			}
			b(cx, top + 1, 14, "minecraft:cobblestone_wall");
		}
	}

	/** Alpendre na frente: colunas de tronco, telhadinho de lajes, vasos. */
	private void porch() {
		for (int x = 24; x <= 32; x++) {
			for (int z = -4; z <= -1; z++) {
				b(x, -1, z, (x + z) % 2 == 0 ? "minecraft:cobblestone" : "minecraft:polished_andesite", VPlan.FILL_DOWN);
				boolean rim = x == 24 || x == 32 || z == -4;
				b(x, 4, z, rim ? "minecraft:dark_oak_planks" : slabTop("minecraft:dark_oak_slab"));
				b(x, 5, z, rim ? "minecraft:dark_oak_slab" : VPlan.AIR);
			}
		}
		for (int x : new int[] {24, 32}) {
			for (int z : new int[] {-4, -1}) {
				for (int y = 0; y <= 3; y++) {
					b(x, y, z, axis("minecraft:dark_oak_log", "y"));
				}
			}
		}
		b(28, 3, -4, s("lantern", "hanging=true"));
		b(26, 0, -1, "minecraft:potted_red_tulip");
		b(30, 0, -1, "minecraft:potted_red_tulip");
	}

	/** Variante do jardim palido: a madeira escura vira carvalho palido. */
	private void pale() {
		for (Map.Entry<Long, String> e : new ArrayList<>(p.blocks().entrySet())) {
			String s = e.getValue();
			if (!s.contains("dark_oak")) {
				continue;
			}
			long k = e.getKey();
			int x = VPlan.keyX(k);
			int y = VPlan.keyY(k);
			int z = VPlan.keyZ(k);
			int flag = p.flags(x, y, z);
			String table = p.lootTables().get(k);
			p.set(x, y, z, s.replace("dark_oak", "pale_oak"), flag);
			if (table != null) {
				p.loot(x, y, z, p.get(x, y, z), table);
			}
		}
	}
}
