package com.mlin.notas.end;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Formas das decoracoes dos biomas do End (logica pura, sem Minecraft): cada metodo devolve as celulas relativas a
 * origem (pe da forma, no nivel do chao) com o papel de cada uma. As features em {@code EndFeatures} so traduzem
 * {@link Part} em blocos e colocam no mundo.
 */
public final class EndShapes {
	private EndShapes() {
	}

	/** Papel de uma celula. */
	public enum Part {
		/** Tronco / corpo principal (tronco do vazio, cristal, basalto, osso). */
		SOLID,
		/** Copa (folhas do vazio). */
		LEAVES,
		/** Ponta (aglomerado de cristal). */
		TIP,
		/** Casca externa (calcita do geodo, borda da cratera). */
		SHELL,
		/** Abrir espaco (ar). */
		AIR,
		/** Agua. */
		WATER
	}

	public record Cell(int x, int y, int z, Part part) {
	}

	/**
	 * Arvore do vazio: tronco de 4 a 7 blocos que entorta um pouco perto do topo e copa achatada em forma de guarda-
	 * chuva (raio 3, duas camadas), turquesa.
	 */
	public static List<Cell> voidTree(Random random) {
		List<Cell> cells = new ArrayList<>();
		int height = 4 + random.nextInt(4);
		int dx = 0;
		int dz = 0;
		int bendAt = height - 2;
		int bendX = random.nextInt(3) - 1;
		int bendZ = bendX == 0 ? (random.nextBoolean() ? 1 : -1) : 0;
		for (int y = 0; y < height; y++) {
			if (y == bendAt) {
				dx += bendX;
				dz += bendZ;
			}
			cells.add(new Cell(dx, y, dz, Part.SOLID));
		}
		int top = height;
		for (int y = 0; y <= 1; y++) {
			int radius = y == 0 ? 3 : 2;
			for (int x = -radius; x <= radius; x++) {
				for (int z = -radius; z <= radius; z++) {
					int d2 = x * x + z * z;
					if (d2 > radius * radius + 1 || (d2 >= radius * radius && random.nextInt(3) == 0)) {
						continue;
					}
					cells.add(new Cell(dx + x, top - 1 + y, dz + z, x == 0 && z == 0 && y == 0 ? Part.SOLID : Part.LEAVES));
				}
			}
		}
		// franja pendurada na borda da copa
		for (int i = 0; i < 6; i++) {
			double angle = random.nextDouble() * Math.PI * 2;
			int x = (int) Math.round(Math.cos(angle) * 3);
			int z = (int) Math.round(Math.sin(angle) * 3);
			cells.add(new Cell(dx + x, top - 2, dz + z, Part.LEAVES));
		}
		return dedupe(cells);
	}

	/** Arbusto: um toco de tronco e uma bola de folhas de raio 1-2. */
	public static List<Cell> bush(Random random) {
		List<Cell> cells = new ArrayList<>();
		cells.add(new Cell(0, 0, 0, Part.SOLID));
		int radius = 1 + random.nextInt(2);
		for (int x = -radius; x <= radius; x++) {
			for (int y = 0; y <= radius; y++) {
				for (int z = -radius; z <= radius; z++) {
					if (x * x + y * y + z * z <= radius * radius + 1 && !(x == 0 && y == 0 && z == 0)) {
						cells.add(new Cell(x, y, z, Part.LEAVES));
					}
				}
			}
		}
		return cells;
	}

	/**
	 * Espinho gigante de cristal: altura dada (6 a 14), raio da base 1,5 a 2,5 afinando ate a ponta, levemente
	 * inclinado; enterrado 2 blocos; aglomerado na ponta.
	 */
	public static List<Cell> crystalSpike(Random random, int height) {
		List<Cell> cells = new ArrayList<>();
		double base = 1.5 + random.nextDouble();
		double leanX = (random.nextDouble() - 0.5) * 0.35;
		double leanZ = (random.nextDouble() - 0.5) * 0.35;
		int tipX = 0;
		int tipZ = 0;
		for (int y = -2; y < height; y++) {
			double t = Math.max(0, y) / (double) height;
			double radius = base * (1.0 - t) + 0.3;
			int cx = (int) Math.round(leanX * Math.max(0, y));
			int cz = (int) Math.round(leanZ * Math.max(0, y));
			int r = (int) Math.ceil(radius);
			for (int x = -r; x <= r; x++) {
				for (int z = -r; z <= r; z++) {
					if (x * x + z * z <= radius * radius) {
						cells.add(new Cell(cx + x, y, cz + z, Part.SOLID));
					}
				}
			}
			tipX = cx;
			tipZ = cz;
		}
		cells.add(new Cell(tipX, height, tipZ, Part.TIP));
		return dedupe(cells);
	}

	/**
	 * Geodo de cristal aberto em cima: esfera de raio {@code radius} com casca de calcita, camada de cristal e miolo
	 * vazio, cortada acima de {@code radius - 2} para ficar exposta. Centro em (0, 0, 0).
	 */
	public static List<Cell> geode(int radius) {
		List<Cell> cells = new ArrayList<>();
		int cut = radius - 2;
		for (int x = -radius; x <= radius; x++) {
			for (int y = -radius; y <= cut; y++) {
				for (int z = -radius; z <= radius; z++) {
					double d = Math.sqrt(x * x + y * y + z * z);
					if (d > radius + 0.5) {
						continue;
					}
					Part part = d > radius - 0.5 ? Part.SHELL : d > radius - 1.5 ? Part.SOLID : Part.AIR;
					cells.add(new Cell(x, y, z, part));
				}
			}
		}
		return cells;
	}

	/** Coluna da cratera: quanto cavar ({@code dig}) e se a coluna e da borda levantada ({@code rim}). */
	public record CraterColumn(int x, int z, int dig, boolean rim) {
	}

	/** Cratera em tigela: profundidade maxima no centro, borda levantada irregular. */
	public static List<CraterColumn> crater(Random random, int radius, int depth) {
		List<CraterColumn> columns = new ArrayList<>();
		for (int x = -radius - 1; x <= radius + 1; x++) {
			for (int z = -radius - 1; z <= radius + 1; z++) {
				double r = Math.sqrt(x * x + z * z);
				if (r <= radius) {
					int dig = (int) Math.round(depth * (1.0 - (r / radius) * (r / radius)));
					columns.add(new CraterColumn(x, z, Math.max(dig, 1), false));
				} else if (r <= radius + 1.2 && random.nextInt(3) != 0) {
					columns.add(new CraterColumn(x, z, 0, true));
				}
			}
		}
		return columns;
	}

	/**
	 * Fossil de criatura gigante ao longo do eixo X: coluna vertebral de {@code length} blocos a altura 2, costelas em
	 * arco a cada 2 blocos (maiores no meio) e um cranio no fim. Base em y = 0.
	 */
	public static List<Cell> fossil(Random random, int length) {
		List<Cell> cells = new ArrayList<>();
		int spineY = 4;
		for (int x = 0; x < length; x++) {
			cells.add(new Cell(x, spineY, 0, Part.SOLID));
		}
		for (int x = 2; x < length - 3; x += 2) {
			double t = (double) x / length;
			int ribRadius = 2 + (int) Math.round(2 * Math.sin(Math.PI * t));
			for (int side = -1; side <= 1; side += 2) {
				for (int a = 0; a <= 6; a++) {
					double angle = Math.PI / 2 * a / 6.0;
					int z = side * (int) Math.round(Math.sin(angle) * ribRadius);
					int y = spineY - (int) Math.round((1 - Math.cos(angle)) * ribRadius);
					cells.add(new Cell(x, y, z, Part.SOLID));
				}
			}
		}
		// cranio 3x3x3 com orbitas vazias
		for (int x = length; x < length + 3; x++) {
			for (int y = spineY - 1; y <= spineY + 1; y++) {
				for (int z = -1; z <= 1; z++) {
					boolean eye = x == length + 1 && y == spineY && z != 0;
					cells.add(new Cell(x, y, z, eye ? Part.AIR : Part.SOLID));
				}
			}
		}
		if (random.nextBoolean()) {
			// presa
			cells.add(new Cell(length + 3, spineY - 1, 0, Part.SOLID));
		}
		return dedupe(cells);
	}

	/** Pilares de basalto do vazio: 1 a 4 colunas juntas, a do centro mais alta (3 a 10 blocos). */
	public static List<Cell> basaltPillars(Random random) {
		List<Cell> cells = new ArrayList<>();
		int count = 1 + random.nextInt(4);
		int[][] offsets = {{0, 0}, {1, 0}, {0, 1}, {-1, 0}, {0, -1}};
		for (int i = 0; i < count; i++) {
			int height = i == 0 ? 6 + random.nextInt(5) : 3 + random.nextInt(4);
			for (int y = -1; y < height; y++) {
				cells.add(new Cell(offsets[i][0], y, offsets[i][1], Part.SOLID));
			}
		}
		return dedupe(cells);
	}

	/** Lago raso: disco irregular de raio 3 a 5, 1 bloco de agua (2 no miolo). Centro em (0, 0, 0) = superficie. */
	public static List<Cell> pond(Random random) {
		List<Cell> cells = new ArrayList<>();
		int radius = 3 + random.nextInt(3);
		double wobble = random.nextDouble() * Math.PI * 2;
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				double angle = Math.atan2(z, x);
				double edge = radius - 0.8 + 0.8 * Math.sin(angle * 3 + wobble);
				double r = Math.sqrt(x * x + z * z);
				if (r <= edge) {
					cells.add(new Cell(x, -1, z, Part.WATER));
					if (r <= edge - 2) {
						cells.add(new Cell(x, -2, z, Part.WATER));
					}
				}
			}
		}
		return cells;
	}

	/** Mancha de superficie irregular de raio ~{@code radius} (colunas x, z). */
	public static List<Cell> patch(Random random, int radius) {
		List<Cell> cells = new ArrayList<>();
		for (int x = -radius; x <= radius; x++) {
			for (int z = -radius; z <= radius; z++) {
				double r = Math.sqrt(x * x + z * z);
				if (r <= radius - 1 || (r <= radius + 0.5 && random.nextBoolean())) {
					cells.add(new Cell(x, 0, z, Part.SOLID));
				}
			}
		}
		return cells;
	}

	private static List<Cell> dedupe(List<Cell> cells) {
		java.util.Map<Long, Cell> byPos = new java.util.LinkedHashMap<>();
		for (Cell cell : cells) {
			long key = ((long) (cell.x() & 0x1FFFFF) << 42) | ((long) (cell.y() & 0x1FFFFF) << 21) | (cell.z() & 0x1FFFFF);
			Cell old = byPos.get(key);
			// tronco/corpo ganha das folhas na mesma celula
			if (old == null || (old.part() == Part.LEAVES && cell.part() != Part.LEAVES)) {
				byPos.put(key, cell);
			}
		}
		return new ArrayList<>(byPos.values());
	}
}
