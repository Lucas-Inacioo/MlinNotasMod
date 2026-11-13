package com.mlin.notas.dynlight;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regras puras da luz dinamica (so cliente): quanta luz cada item/entidade da, como ela cai com a distancia e quais
 * secoes de chunk (16x16x16) precisam ser refeitas quando as fontes mudam.
 *
 * <p>Abordagem de "lightmap" inspirada na descricao publica do LambDynamicLights (LambdAurora; HOW_DOES_IT_WORK.md):
 * a luz nao entra no motor de luz do mundo, so e somada na hora de desenhar (malha dos chunks e entidades), caindo
 * linearmente ate {@link #RANGE} blocos. Codigo escrito do zero.
 */
public final class DynamicLightRules {
	/** Alcance da luz em blocos: limita a reconstrucao a no maximo 2x2x2 secoes por fonte. */
	public static final double RANGE = 7.75;
	/** Entidade pegando fogo. */
	public static final int BURNING = 12;

	/** Itens que nao sao blocos (ou blocos que nao emitem no estado padrao) mas brilham na mao. */
	private static final Map<String, Integer> ITEMS = new HashMap<>();
	/** Itens que apagam debaixo d'agua. */
	private static final Set<String> WATER_SENSITIVE = new HashSet<>();
	/** Mobs que brilham sozinhos. */
	private static final Map<String, Integer> ENTITIES = new HashMap<>();

	static {
		ITEMS.put("minecraft:lava_bucket", 15);
		ITEMS.put("minecraft:glow_berries", 14);
		ITEMS.put("minecraft:nether_star", 12);
		ITEMS.put("minecraft:blaze_rod", 10);
		ITEMS.put("minecraft:fire_charge", 10);
		ITEMS.put("minecraft:blaze_powder", 8);
		ITEMS.put("minecraft:glowstone_dust", 8);
		ITEMS.put("minecraft:glow_ink_sac", 8);
		ITEMS.put("minecraft:spectral_arrow", 8);
		ITEMS.put("minecraft:magma_cream", 6);
		ITEMS.put("minecraft:echo_shard", 4);
		ITEMS.put("minecraft:amethyst_shard", 4);
		ITEMS.put("minecraft:prismarine_crystals", 6);
		ITEMS.put("minecraft:end_crystal", 12);
		ITEMS.put("mlinnotas:ether_crystal_shard", 7);
		ITEMS.put("mlinnotas:colossus_ember", 12);
		ITEMS.put("mlinnotas:astral_fragment", 10);

		for (String id : new String[] {"minecraft:torch", "minecraft:soul_torch", "minecraft:copper_torch", "minecraft:campfire",
				"minecraft:soul_campfire", "minecraft:fire_charge", "minecraft:blaze_powder", "minecraft:lava_bucket", "minecraft:jack_o_lantern"}) {
			WATER_SENSITIVE.add(id);
		}

		ENTITIES.put("minecraft:blaze", 10);
		ENTITIES.put("minecraft:magma_cube", 8);
		ENTITIES.put("minecraft:glow_squid", 6);
		ENTITIES.put("minecraft:allay", 5);
		ENTITIES.put("mlinnotas:magma_slug", 8);
		ENTITIES.put("mlinnotas:igneous_colossus", 12);
		ENTITIES.put("mlinnotas:void_jelly", 6);
	}

	private DynamicLightRules() {
	}

	/** Uma fonte de luz ja encaixada no bloco (a luz sai do centro do bloco). */
	public record Source(int x, int y, int z, int luminance) {
		public double distanceSq(double px, double py, double pz) {
			double dx = x + 0.5 - px;
			double dy = y + 0.5 - py;
			double dz = z + 0.5 - pz;
			return dx * dx + dy * dy + dz * dz;
		}
	}

	/**
	 * Luz de um item: a tabela propria tem prioridade; senao, a luz que o bloco do item emite no estado padrao
	 * ({@code blockEmission}, 0 para itens que nao sao bloco). Tocha, fogueira e balde de lava apagam na agua.
	 */
	public static int itemLuminance(String itemId, int blockEmission, boolean underwater) {
		Integer own = ITEMS.get(itemId);
		int light = own != null ? own : Math.max(0, Math.min(15, blockEmission));
		if (underwater && WATER_SENSITIVE.contains(itemId)) {
			return 0;
		}
		return light;
	}

	/** Luz propria de um mob (0 = nenhuma). */
	public static int entityLuminance(String entityId) {
		return ENTITIES.getOrDefault(entityId, 0);
	}

	/** Nivel de luz (0..15, com fracao) a {@code distance} blocos de uma fonte de brilho {@code luminance}. */
	public static double levelAt(double distance, int luminance) {
		if (luminance <= 0 || distance >= RANGE) {
			return 0.0;
		}
		return luminance * (1.0 - distance / RANGE);
	}

	/** Secoes (x, y, z em coordenadas de secao, empacotadas por {@link #packSection}) que a fonte alcanca. */
	public static List<Long> sectionsOf(Source source) {
		List<Long> out = new ArrayList<>(8);
		if (source.luminance() <= 0) {
			return out;
		}
		double cx = source.x() + 0.5;
		double cy = source.y() + 0.5;
		double cz = source.z() + 0.5;
		int x0 = Math.floorDiv((int) Math.floor(cx - RANGE), 16);
		int x1 = Math.floorDiv((int) Math.floor(cx + RANGE), 16);
		int y0 = Math.floorDiv((int) Math.floor(cy - RANGE), 16);
		int y1 = Math.floorDiv((int) Math.floor(cy + RANGE), 16);
		int z0 = Math.floorDiv((int) Math.floor(cz - RANGE), 16);
		int z1 = Math.floorDiv((int) Math.floor(cz + RANGE), 16);
		for (int sx = x0; sx <= x1; sx++) {
			for (int sy = y0; sy <= y1; sy++) {
				for (int sz = z0; sz <= z1; sz++) {
					out.add(packSection(sx, sy, sz));
				}
			}
		}
		return out;
	}

	/**
	 * Secoes a refazer quando as fontes passam de {@code before} para {@code after}: so as das fontes que mudaram
	 * (sairam, entraram, andaram um bloco ou mudaram de brilho). Fonte parada nao refaz nada.
	 */
	public static Set<Long> changedSections(Collection<Source> before, Collection<Source> after) {
		Set<Source> old = new HashSet<>(before);
		Set<Source> now = new HashSet<>(after);
		Set<Long> out = new HashSet<>();
		for (Source s : old) {
			if (!now.contains(s)) {
				out.addAll(sectionsOf(s));
			}
		}
		for (Source s : now) {
			if (!old.contains(s)) {
				out.addAll(sectionsOf(s));
			}
		}
		return out;
	}

	/** As {@code max} fontes mais perto de (px, py, pz); com fontes no mesmo bloco fica so a mais forte. */
	public static List<Source> select(Collection<Source> candidates, double px, double py, double pz, int max) {
		Map<List<Integer>, Source> byBlock = new HashMap<>();
		for (Source s : candidates) {
			if (s.luminance() <= 0) {
				continue;
			}
			List<Integer> key = List.of(s.x(), s.y(), s.z());
			Source existing = byBlock.get(key);
			if (existing == null || existing.luminance() < s.luminance()) {
				byBlock.put(key, s);
			}
		}
		List<Source> sorted = new ArrayList<>(byBlock.values());
		sorted.sort(Comparator.comparingDouble((Source s) -> s.distanceSq(px, py, pz)).thenComparingInt(Source::x).thenComparingInt(Source::y)
				.thenComparingInt(Source::z));
		return sorted.size() > max ? new ArrayList<>(sorted.subList(0, Math.max(0, max))) : sorted;
	}

	/** Empacota (x, y, z) em 64 bits (22 bits por eixo, com sinal). */
	public static long packSection(int x, int y, int z) {
		return ((long) (x & 0x3FFFFF) << 42) | ((long) (y & 0xFFFFF) << 22) | (z & 0x3FFFFF);
	}

	public static int sectionX(long packed) {
		return (int) (packed >> 42) << 10 >> 10;
	}

	public static int sectionY(long packed) {
		return (int) ((packed >> 22) & 0xFFFFF) << 12 >> 12;
	}

	public static int sectionZ(long packed) {
		return (int) (packed & 0x3FFFFF) << 10 >> 10;
	}
}
