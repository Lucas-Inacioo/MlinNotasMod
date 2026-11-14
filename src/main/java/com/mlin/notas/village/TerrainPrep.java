package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongPredicate;

/**
 * Preparo do terreno de um lote antes da obra (logica pura). O lote inteiro fica na altura alvo (a mediana do chao):
 * o que sobra acima e cortado, o que falta e aterrado com terra (pedra no fundo) e, na borda de aterro alto, muro de
 * arrimo. Em volta do lote, numa faixa de {@link #BAND} blocos, o chao desce/sobe 1 por bloco ate encontrar o
 * natural (rampa andavel). O acesso da porta ate a rua tambem e acertado para nunca ter degrau maior que 1. Colunas
 * com qualquer coisa de jogador (ou {@code locked}, como outros lotes) nunca sao tocadas.
 */
public final class TerrainPrep {
	public static final int BAND = 3;
	public static final int SOIL_DEPTH = 3;

	/** Altura alvo, blocos de terraplenagem e a altura nova de cada coluna mexida. */
	public record Result(int target, List<Placement> placements, Map<Long, Integer> heights) {
		/** Chao depois do preparo. */
		public int ground(Terrain terrain, int x, int z) {
			Integer h = heights.get(VillageLayout.col(x, z));
			return h != null ? h : terrain.ground(x, z);
		}
	}

	private TerrainPrep() {
	}

	/** Mediana do chao do retangulo (a altura que corta e aterra menos). */
	public static int target(Terrain terrain, VillageState.Rect lot) {
		int[] heights = new int[(lot.x1() - lot.x0() + 1) * (lot.z1() - lot.z0() + 1)];
		int i = 0;
		for (int x = lot.x0(); x <= lot.x1(); x++) {
			for (int z = lot.z0(); z <= lot.z1(); z++) {
				heights[i++] = terrain.ground(x, z);
			}
		}
		Arrays.sort(heights);
		return heights[heights.length / 2];
	}

	public static Result prepare(VillageState.Rect lot, List<int[]> access, Terrain terrain, LongPredicate locked) {
		int target = target(terrain, lot);
		Map<Long, Integer> desired = new LinkedHashMap<>();
		for (int x = lot.x0(); x <= lot.x1(); x++) {
			for (int z = lot.z0(); z <= lot.z1(); z++) {
				desired.put(VillageLayout.col(x, z), target);
			}
		}
		// faixa em volta: 1 de desnivel por bloco ate o chao natural
		for (int x = lot.x0() - BAND; x <= lot.x1() + BAND; x++) {
			for (int z = lot.z0() - BAND; z <= lot.z1() + BAND; z++) {
				if (lot.contains(x, z)) {
					continue;
				}
				int k = Math.max(Math.max(lot.x0() - x, x - lot.x1()), Math.max(lot.z0() - z, z - lot.z1()));
				int g = terrain.ground(x, z);
				int h = Math.max(target - k, Math.min(target + k, g));
				long key = VillageLayout.col(x, z);
				if (h != g && terrain.free(x, z) && !locked.test(key)) {
					desired.put(key, h);
				}
			}
		}
		// acesso: degraus de no maximo 1 da porta ate a rua
		int prev = target;
		for (int[] c : access) {
			long key = VillageLayout.col(c[0], c[1]);
			int g = terrain.ground(c[0], c[1]);
			int h = desired.getOrDefault(key, g);
			if (!terrain.free(c[0], c[1]) || locked.test(key)) {
				prev = g;
				continue;
			}
			int clamped = Math.max(prev - 1, Math.min(prev + 1, h));
			if (clamped != g) {
				desired.put(key, clamped);
			} else {
				desired.remove(key);
			}
			prev = clamped;
		}
		List<Placement> out = new ArrayList<>();
		for (Map.Entry<Long, Integer> e : desired.entrySet()) {
			int x = VillageLayout.colX(e.getKey());
			int z = VillageLayout.colZ(e.getKey());
			int g = terrain.ground(x, z);
			int h = e.getValue();
			if (h < g) {
				for (int y = h + 1; y <= g + 1; y++) {
					out.add(new Placement(x, y, z, VPart.AIR, null, false));
				}
				out.add(new Placement(x, h, z, VPart.TURF, null, false));
			} else if (h > g) {
				boolean wall = h - g > 1 && (edge(lot, x, z) || dropsOff(desired, terrain, x, z, h));
				for (int y = g + 1; y < h; y++) {
					VPart part = wall ? VPart.RETAIN : h - y <= SOIL_DEPTH ? VPart.EARTH : VPart.ROCK;
					out.add(new Placement(x, y, z, part, null, false));
				}
				out.add(new Placement(x, h, z, VPart.TURF, null, false));
				out.add(new Placement(x, h + 1, z, VPart.AIR, null, false));
			}
		}
		return new Result(target, out, desired);
	}

	private static boolean edge(VillageState.Rect lot, int x, int z) {
		return lot.contains(x, z) && (x == lot.x0() || x == lot.x1() || z == lot.z0() || z == lot.z1());
	}

	/** Coluna aterrada com vizinho natural 2+ abaixo: vira muro de arrimo para a borda nao ficar de terra solta. */
	private static boolean dropsOff(Map<Long, Integer> desired, Terrain terrain, int x, int z, int h) {
		for (Facing f : Facing.values()) {
			long k = VillageLayout.col(x + f.dx, z + f.dz);
			int n = desired.containsKey(k) ? desired.get(k) : terrain.ground(x + f.dx, z + f.dz);
			if (n <= h - 2) {
				return true;
			}
		}
		return false;
	}
}
