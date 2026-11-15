package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Muralha de pedra da vila (logica pura, Vilas 3.0 fase 4) lida do campo de alturas ({@link HeightField#wallPoints},
 * {@link HeightField#gates}), que nunca muda depois de calculado: a muralha nao fica enterrada nem afunda.
 *
 * <ul>
 * <li>Cortina: cada celula da linha do octogono (meia-largura {@link VillagePlanner#WALL_HALF}) desce ate o menor chao
 * entre a linha, o lado de dentro e o de fora (alicerce que enche o que faltar) e sobe {@link #HEIGHT} acima do maior,
 * mais ameia; nos dois lados o topo fica pelo menos {@link #HEIGHT} acima do chao do campo.</li>
 * <li>Torres 5x5 nos 8 cantos: do menor ao maior chao da area, {@link #TOWER} acima do maior.</li>
 * <li>Portao: onde uma avenida cruza a linha, a rua e plana (o campo garante 3 de cada lado). A passagem fica livre ate
 * {@link #CLEARANCE} blocos acima da rua (ar, nada dentro: nem poste, nem lanterna), o arco de tijolo comeca em
 * {@code nivel + CLEARANCE + 1} e dois pilares altos guardam a passagem.</li>
 * <li>Colunas que nao sao livres ({@link Terrain#free}: casa de jogador, agua, rua que nao e portao) ficam abertas.</li>
 * </ul>
 *
 * <p>Referencias de forma: muralhas medievais com torres e portao em arco (ChoiceTheorem's Overhauled Village, Planet
 * Minecraft); escrito do zero.
 */
public final class StoneWallPlanner {
	/** Blocos de pedra da cortina acima do maior chao (o parapeito vem por cima). */
	public static final int HEIGHT = 4;
	/** Altura das torres acima do maior chao. */
	public static final int TOWER = 7;
	/** Ar livre garantido acima da rua do portao. */
	public static final int CLEARANCE = 4;

	private StoneWallPlanner() {
	}

	/** O que a obra faz: blocos e onde pôr o golem (x, y, z) ou null. */
	public record Result(List<Placement> placements, List<int[]> golems) {
	}

	/** Celulas (relativas) da passagem dos portoes de pedra. */
	public static Set<Long> passage(HeightField field) {
		Set<Long> out = new HashSet<>();
		for (HeightField.WallPoint p : field.wallPoints(HeightField.Wall.STONE)) {
			if (p.gate()) {
				out.add(VillageLayout.col(p.x(), p.z()));
			}
		}
		return out;
	}

	public static Result plan(HeightField field, Terrain terrain) {
		List<Placement> out = new ArrayList<>();
		List<HeightField.WallPoint> points = field.wallPoints(HeightField.Wall.STONE);
		Set<Long> gateCells = new HashSet<>();
		Map<Long, Integer> gateLevel = new HashMap<>();
		for (HeightField.WallPoint p : points) {
			if (p.gate()) {
				long key = VillageLayout.col(p.x(), p.z());
				gateCells.add(key);
				gateLevel.put(key, p.ground());
			}
		}
		// torres primeiro (as celulas delas saem da cortina)
		Set<Long> towerCells = new HashSet<>();
		int h = VillagePlanner.WALL_HALF;
		int d = VillagePlanner.WALL_DIAGONAL - h;
		int[][] vertices = {{h, d}, {d, h}, {-h, d}, {-d, h}, {h, -d}, {d, -h}, {-h, -d}, {-d, -h}};
		for (int[] v : vertices) {
			tower(out, field, terrain, v[0] - Integer.signum(v[0]), v[1] - Integer.signum(v[1]), gateCells, towerCells);
		}
		for (HeightField.WallPoint p : points) {
			long key = VillageLayout.col(p.x(), p.z());
			if (towerCells.contains(key)) {
				continue;
			}
			int low = Math.min(p.ground(), Math.min(p.inner(), p.outer()));
			int high = Math.max(p.ground(), Math.max(p.inner(), p.outer()));
			if (p.gate()) {
				gate(out, p, high);
				continue;
			}
			if (!terrain.free(p.x(), p.z())) {
				continue;
			}
			// o chao de agora (vila antiga ainda nao aplainada ali) tambem conta: nunca enterrada, nunca com o pe no ar
			low = Math.min(low, terrain.ground(p.x(), p.z()));
			high = Math.max(high, terrain.ground(p.x(), p.z()));
			int pillarLevel = adjacentGate(gateLevel, p.x(), p.z());
			if (pillarLevel != Integer.MIN_VALUE) {
				int top = Math.max(high + HEIGHT, pillarLevel + CLEARANCE + 3);
				out.add(new Placement(p.x(), low, p.z(), VPart.FOUNDATION, null, true));
				for (int y = low + 1; y <= top; y++) {
					out.add(new Placement(p.x(), y, p.z(), VPart.BRICK, null, false));
				}
				out.add(new Placement(p.x(), top + 1, p.z(), VPart.STONE_SLAB, null, false));
				if (Math.floorMod(p.x() + p.z(), 2) == 0) {
					out.add(new Placement(p.x(), top + 2, p.z(), VPart.LANTERN, null, false));
				}
				continue;
			}
			out.add(new Placement(p.x(), low, p.z(), VPart.FOUNDATION, null, true));
			int top = high + HEIGHT;
			for (int y = low + 1; y <= top; y++) {
				VPart part = y == low + 1 || y == p.ground() + 1 ? VPart.BRICK
						: Math.floorMod(p.x() * 7 + p.z() * 11 + y * 3, 6) == 0 ? VPart.STONE_ALT : VPart.STONE;
				out.add(new Placement(p.x(), y, p.z(), part, null, false));
			}
			out.add(new Placement(p.x(), top + 1, p.z(), Math.floorMod(p.x() + p.z(), 2) == 0 ? VPart.BRICK : VPart.STONE_SLAB, null, false));
		}
		List<int[]> golems = new ArrayList<>();
		for (HeightField.Gate g : field.gates()) {
			if (g.wall() == HeightField.Wall.STONE) {
				double len = Math.max(1, Math.hypot(g.cx(), g.cz()));
				int gx = (int) Math.round(g.cx() - g.cx() / len * 4);
				int gz = (int) Math.round(g.cz() - g.cz() / len * 4);
				golems.add(new int[] {gx, field.heightRel(gx, gz) + 1, gz});
			}
		}
		return new Result(VillagePlanner.sort(VillagePlanner.dedupe(out)), golems);
	}

	/** Nivel do portao de uma celula de portao vizinha (8 direcoes), ou MIN_VALUE. */
	private static int adjacentGate(Map<Long, Integer> gateLevel, int x, int z) {
		int best = Integer.MIN_VALUE;
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				Integer l = gateLevel.get(VillageLayout.col(x + dx, z + dz));
				if (l != null) {
					best = Math.max(best, l);
				}
			}
		}
		return best;
	}

	/**
	 * Uma celula da passagem: ar livre ate {@link #CLEARANCE} acima da rua (a rua em si nao e tocada: calcamento, caminho
	 * e meio-fio sao da avenida), arco de tijolo em cima e pedra ate o topo da cortina.
	 */
	private static void gate(List<Placement> out, HeightField.WallPoint p, int high) {
		int level = p.ground();
		// chao da passagem no nivel (so troca terreno natural: calcamento da avenida nunca e sobrescrito)
		out.add(new Placement(p.x(), level, p.z(), VPart.PATH, null, true));
		for (int y = level + 1; y <= level + CLEARANCE; y++) {
			out.add(new Placement(p.x(), y, p.z(), VPart.AIR, null, false));
		}
		int top = Math.max(high + HEIGHT, level + CLEARANCE + 2);
		for (int y = level + CLEARANCE + 1; y <= top; y++) {
			out.add(new Placement(p.x(), y, p.z(), y == level + CLEARANCE + 1 || y == top ? VPart.BRICK : VPart.STONE, null, false));
		}
		if (Math.floorMod(p.x() + p.z(), 2) == 0) {
			out.add(new Placement(p.x(), top + 1, p.z(), VPart.BRICK, null, false));
		}
	}

	/** Torre 5x5 macica com ameias e lanterna; so as celulas livres e fora da passagem. */
	private static void tower(List<Placement> out, HeightField field, Terrain terrain, int cx, int cz, Set<Long> gateCells, Set<Long> cells) {
		List<int[]> mine = new ArrayList<>();
		int low = Integer.MAX_VALUE;
		int high = Integer.MIN_VALUE;
		for (int x = cx - 2; x <= cx + 2; x++) {
			for (int z = cz - 2; z <= cz + 2; z++) {
				int g = field.heightRel(x, z);
				if (gateCells.contains(VillageLayout.col(x, z)) || g == Integer.MIN_VALUE || !terrain.free(x, z)) {
					continue;
				}
				mine.add(new int[] {x, z});
				low = Math.min(low, g);
				high = Math.max(high, g);
			}
		}
		if (mine.size() < 20) {
			return;
		}
		int top = high + TOWER;
		for (int[] c : mine) {
			int x = c[0];
			int z = c[1];
			cells.add(VillageLayout.col(x, z));
			boolean edge = Math.abs(x - cx) == 2 || Math.abs(z - cz) == 2;
			boolean corner = Math.abs(x - cx) == 2 && Math.abs(z - cz) == 2;
			out.add(new Placement(x, low, z, VPart.FOUNDATION, null, true));
			for (int y = low + 1; y <= top; y++) {
				VPart part = corner ? VPart.BRICK : y == low + 1 || y == top && edge ? VPart.BRICK
						: Math.floorMod(x * 3 + y * 5 + z, 7) == 0 ? VPart.STONE_ALT : VPart.STONE;
				out.add(new Placement(x, y, z, part, null, false));
			}
			if (edge && (corner || Math.floorMod(x + z, 2) == 0)) {
				out.add(new Placement(x, top + 1, z, VPart.BRICK, null, false));
			}
		}
		if (cells.contains(VillageLayout.col(cx, cz))) {
			out.add(new Placement(cx, top + 1, cz, VPart.LANTERN, null, false));
		}
	}
}
