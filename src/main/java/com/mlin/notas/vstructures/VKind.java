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
	MINESHAFT_MESA("mineshaft_mesa", true, 0);

	private final String id;
	private final boolean underground;
	private final int maxSlope;

	VKind(String id, boolean underground, int maxSlope) {
		this.id = id;
		this.underground = underground;
		this.maxSlope = maxSlope;
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
		};
	}
}
