package com.mlin.notas.accessories;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Onde cada artefato e reliquia aparece: tabela de loot -> chance de uma rolagem extra e a lista sorteada (peso igual).
 * Os baus de ruinas, masmorras, estruturas vanilla refeitas, cidadela e covis ganham um pool a mais; os chefes soltam a
 * propria reliquia sempre. Aplicado sem reescrever os JSONs gerados (evento de loot da Fabric). Logica pura.
 */
public final class ArtifactLoot {
	/** Um pool extra: com {@code chance}, sorteia um item de {@code items} (ids sem namespace, do mod). */
	public record Pool(double chance, List<String> items) {
	}

	/** Chance de um mini-chefe de masmorra soltar o Selo do Campeao. */
	public static final double CHAMPION_SEAL_CHANCE = 0.08;

	private static final Map<String, Pool> POOLS = new LinkedHashMap<>();

	static {
		// ruinas de superficie
		pool("mlinnotas:chests/ruin_supplies", 0.10, "pocket_watch", "feather_charm", "traveler_belt", "lucky_horseshoe", "rabbit_foot_charm");
		pool("mlinnotas:chests/ruin_mine", 0.15, "miner_lantern", "prospector_glove", "climbing_glove");
		// masmorras medias
		pool("mlinnotas:chests/dungeon_common", 0.04, "thorn_necklace", "frost_ring", "anchor_belt", "duelist_glove");
		pool("mlinnotas:chests/dungeon_armory", 0.12, "duelist_glove", "anchor_belt", "thorn_necklace");
		pool("mlinnotas:chests/dungeon_library", 0.10, "pocket_watch", "sun_pendant", "life_amulet");
		pool("mlinnotas:chests/dungeon_secret", 0.25, "vampire_fang", "magnet_ring", "life_amulet", "lucky_horseshoe");
		pool("mlinnotas:chests/dungeon_final", 0.40, "life_amulet", "vampire_fang", "sun_pendant", "frost_ring", "magnet_ring");
		pool("mlinnotas:chests/dungeon_vault", 0.30, "life_amulet", "vampire_fang", "sun_pendant", "frost_ring", "magnet_ring");
		// estruturas vanilla refeitas (tabelas vanilla + as do mod)
		pool("mlinnotas:chests/desert_tomb", 0.30, "sun_pendant", "lucky_horseshoe", "life_amulet");
		pool("minecraft:chests/desert_pyramid", 0.08, "sun_pendant", "feather_charm");
		pool("mlinnotas:chests/desert_storage", 0.05, "sun_pendant", "traveler_belt");
		pool("minecraft:chests/jungle_temple", 0.25, "climbing_glove", "rabbit_foot_charm", "vampire_fang");
		pool("mlinnotas:chests/jungle_storage", 0.10, "climbing_glove", "rabbit_foot_charm");
		pool("minecraft:chests/abandoned_mineshaft", 0.04, "miner_lantern", "prospector_glove", "magnet_ring");
		pool("mlinnotas:chests/mineshaft_workshop", 0.12, "miner_lantern", "prospector_glove", "magnet_ring");
		pool("minecraft:chests/pillager_outpost", 0.10, "anchor_belt", "duelist_glove");
		pool("mlinnotas:chests/outpost_armory", 0.15, "anchor_belt", "duelist_glove", "thorn_necklace");
		// oceano
		pool("minecraft:chests/buried_treasure", 0.30, "diver_ring");
		pool("minecraft:chests/shipwreck_treasure", 0.08, "diver_ring", "pocket_watch");
		pool("minecraft:chests/underwater_ruin_big", 0.10, "diver_ring");
		// estruturas do mar e pequenas refeitas
		pool("mlinnotas:chests/monument_treasury", 0.30, "diver_ring", "life_amulet", "frost_ring");
		pool("mlinnotas:chests/witch_hut", 0.20, "vampire_fang", "lucky_horseshoe", "sun_pendant");
		pool("minecraft:chests/igloo_chest", 0.15, "frost_ring", "pocket_watch");
		pool("minecraft:chests/ruined_portal", 0.05, "feather_charm", "anchor_belt");
		// Nether refeito: fortaleza e bastiao
		pool("minecraft:chests/nether_bridge", 0.06, "thorn_necklace", "anchor_belt", "duelist_glove");
		pool("minecraft:chests/bastion_treasure", 0.30, "vampire_fang", "life_amulet", "magnet_ring");
		pool("minecraft:chests/bastion_bridge", 0.08, "traveler_belt", "duelist_glove");
		// covis dos chefes e cidadela do End
		pool("mlinnotas:chests/cobalt_shrine", 0.15, "life_amulet", "duelist_glove", "anchor_belt");
		pool("mlinnotas:chests/abyssal_sanctum", 0.20, "miner_lantern", "thorn_necklace", "life_amulet");
		pool("mlinnotas:chests/colossus_forge", 0.20, "vampire_fang", "frost_ring", "traveler_belt");
		pool("minecraft:chests/end_city_treasure", 0.20, "feather_charm", "rabbit_foot_charm", "life_amulet", "magnet_ring");
		// reliquias dos chefes (sempre)
		pool("mlinnotas:entities/cobalt_golem", 1.0, "resonant_pendant");
		pool("mlinnotas:entities/abyssal_guardian", 1.0, "abyssal_shell");
		pool("mlinnotas:entities/igneous_colossus", 1.0, "colossus_girdle");
		pool("mlinnotas:chests/dragon_hoard", 1.0, "dragonscale_charm");
		pool("mlinnotas:entities/astral_herald", 1.0, "herald_star");
		pool("minecraft:entities/wither", 1.0, "wither_ring");
	}

	private ArtifactLoot() {
	}

	private static void pool(String table, double chance, String... items) {
		POOLS.put(table, new Pool(chance, List.of(items)));
	}

	/** Tabela de loot ({@code "ns:path"}) -> pool extra. */
	public static Map<String, Pool> pools() {
		return Collections.unmodifiableMap(POOLS);
	}

	public static Pool poolFor(String table) {
		return POOLS.get(table);
	}
}
