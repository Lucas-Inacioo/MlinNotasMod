package com.mlin.notas.vstructures;

import java.util.Locale;

/**
 * Estruturas vanilla refeitas pelo mod. O JSON vanilla ({@code data/minecraft/worldgen/structure/<id>.json}) e
 * substituido por um do tipo {@code mlinnotas:vanilla_remake} com o campo {@code kind}: o id da estrutura continua o
 * vanilla, entao {@code /locate}, mapas de explorador e conquistas seguem funcionando. Logica pura.
 */
public enum VKind {
	DESERT_PYRAMID("desert_pyramid", false, 9),
	JUNGLE_TEMPLE("jungle_pyramid", false, 12),
	PILLAGER_OUTPOST("pillager_outpost", false, 8),
	MINESHAFT("mineshaft", true, 0),
	MINESHAFT_MESA("mineshaft_mesa", true, 0),
	// estruturas do mar e pequenas (ver Placement)
	MONUMENT("monument", 0, Placement.MONUMENT),
	SHIPWRECK("shipwreck", 6, Placement.SEAFLOOR),
	SHIPWRECK_BEACHED("shipwreck_beached", 5, Placement.SHORE),
	OCEAN_RUIN_WARM("ocean_ruin_warm", 6, Placement.SEAFLOOR),
	OCEAN_RUIN_COLD("ocean_ruin_cold", 6, Placement.SEAFLOOR),
	BURIED_TREASURE("buried_treasure", 99, Placement.BURIED),
	IGLOO("igloo", 4, Placement.LAND),
	SWAMP_HUT("swamp_hut", 5, Placement.SHORE),
	RUINED_PORTAL("ruined_portal", 6, Placement.LAND),
	RUINED_PORTAL_DESERT("ruined_portal_desert", 6, Placement.LAND),
	RUINED_PORTAL_JUNGLE("ruined_portal_jungle", 6, Placement.LAND),
	RUINED_PORTAL_SWAMP("ruined_portal_swamp", 5, Placement.SHORE),
	RUINED_PORTAL_MOUNTAIN("ruined_portal_mountain", 10, Placement.LAND),
	RUINED_PORTAL_OCEAN("ruined_portal_ocean", 8, Placement.SEAFLOOR),
	RUINED_PORTAL_NETHER("ruined_portal_nether", 5, Placement.NETHER),
	TRAIL_RUINS("trail_ruins", 8, Placement.LAND);

	/**
	 * Como a estrutura acha o lugar. {@code LAND}/{@code UNDERGROUND}: as regras antigas (superficie seca / subsolo).
	 * {@code SEAFLOOR}: no fundo do mar, com agua por cima. {@code SHORE}: no chao, com ou sem agua rasa por cima.
	 * {@code MONUMENT}: no mar profundo, topo logo abaixo do nivel do mar, pilares ate o fundo. {@code BURIED}: no bloco
	 * (9, 9) do chunk, onde o mapa do tesouro aponta. {@code NETHER}: no chao do Nether, longe da lava.
	 */
	public enum Placement {
		LAND, UNDERGROUND, SEAFLOOR, SHORE, MONUMENT, BURIED, NETHER;

		/** Ar do plano abaixo do nivel do mar vira agua e blocos que aceitam ficam alagados. */
		public boolean aquatic() {
			return this == SEAFLOOR || this == SHORE || this == MONUMENT;
		}
	}

	private final String id;
	private final boolean underground;
	private final int maxSlope;
	private final Placement placement;

	VKind(String id, boolean underground, int maxSlope) {
		this(id, maxSlope, underground ? Placement.UNDERGROUND : Placement.LAND);
	}

	VKind(String id, int maxSlope, Placement placement) {
		this.id = id;
		this.underground = placement == Placement.UNDERGROUND;
		this.maxSlope = maxSlope;
		this.placement = placement;
	}

	public Placement placement() {
		return placement;
	}

	/** Id da estrutura vanilla ({@code minecraft:<id>}). */
	public String id() {
		return id;
	}

	public boolean underground() {
		return underground;
	}

	/** Maior desnivel do terreno (blocos) aceito sob a estrutura de superficie. */
	public int maxSlope() {
		return maxSlope;
	}

	public String serializedName() {
		return name().toLowerCase(Locale.ROOT);
	}

	public static VKind byName(String name) {
		for (VKind kind : values()) {
			if (kind.serializedName().equals(name) || kind.id.equals(name)) {
				return kind;
			}
		}
		return DESERT_PYRAMID;
	}

	/** Plano da estrutura para a semente; {@code variant} = estilo do bioma (ver cada planner). */
	public VPlan plan(long seed, int variant) {
		return switch (this) {
			case DESERT_PYRAMID -> DesertPyramidPlanner.plan(seed, variant);
			case JUNGLE_TEMPLE -> JungleTemplePlanner.plan(seed, variant);
			case PILLAGER_OUTPOST -> OutpostPlanner.plan(seed, variant);
			case MINESHAFT -> MineshaftPlanner.plan(seed, variant, false);
			case MINESHAFT_MESA -> MineshaftPlanner.plan(seed, variant, true);
			case MONUMENT -> MonumentPlanner.plan(seed, variant);
			case SHIPWRECK -> ShipwreckPlanner.plan(seed, variant, false);
			case SHIPWRECK_BEACHED -> ShipwreckPlanner.plan(seed, variant, true);
			case OCEAN_RUIN_WARM -> OceanRuinPlanner.plan(seed, variant, true);
			case OCEAN_RUIN_COLD -> OceanRuinPlanner.plan(seed, variant, false);
			case BURIED_TREASURE -> BuriedTreasurePlanner.plan(seed, variant);
			case IGLOO -> IglooPlanner.plan(seed, variant);
			case SWAMP_HUT -> SwampHutPlanner.plan(seed, variant);
			case RUINED_PORTAL, RUINED_PORTAL_DESERT, RUINED_PORTAL_JUNGLE, RUINED_PORTAL_SWAMP, RUINED_PORTAL_MOUNTAIN, RUINED_PORTAL_OCEAN,
					RUINED_PORTAL_NETHER -> RuinedPortalPlanner.plan(seed, variant, this);
			case TRAIL_RUINS -> TrailRuinsPlanner.plan(seed, variant);
		};
	}
}
