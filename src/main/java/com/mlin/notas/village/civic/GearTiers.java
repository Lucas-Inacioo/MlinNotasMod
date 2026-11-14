package com.mlin.notas.village.civic;

import java.util.Optional;

/**
 * Nivel (0 = madeira .. 11 = celestio, a ordem de {@code tier/Tier}) e tipo de um equipamento pelo id do item (logica
 * pura): ferramenta (picareta, machado, pa, enxada, vara de pesca), arma (espada, lanca, as classes do combate, arco,
 * besta) ou peca de armadura.
 */
public final class GearTiers {
	public enum Kind {
		TOOL, WEAPON, HELMET, CHESTPLATE, LEGGINGS, BOOTS
	}

	public record Gear(Kind kind, int tier) {
	}

	private static final String[] PREFIXES = {"wooden", "stone", "copper", "iron", "golden", "cobalt", "diamond", "netherite", "infernite",
			"endite", "astralite", "celestium"};
	private static final String[] WEAPONS = {"sword", "spear", "greatsword", "dagger", "warhammer", "glaive", "rapier"};
	private static final String[] TOOLS = {"pickaxe", "axe", "shovel", "hoe"};

	private GearTiers() {
	}

	/** Equipamento do item ({@code minecraft:iron_pickaxe}, {@code mlinnotas:cobalt_helmet}...), vazio se nao for. */
	public static Optional<Gear> parse(String itemId) {
		int colon = itemId.indexOf(':');
		String name = colon >= 0 ? itemId.substring(colon + 1) : itemId;
		switch (name) {
			case "fishing_rod", "shears":
				return Optional.of(new Gear(Kind.TOOL, 3));
			case "bow", "crossbow":
				return Optional.of(new Gear(Kind.WEAPON, 3));
			case "turtle_helmet":
				return Optional.of(new Gear(Kind.HELMET, 3));
			default:
				break;
		}
		int us = name.indexOf('_');
		if (us < 0) {
			return Optional.empty();
		}
		String prefix = name.substring(0, us);
		String suffix = name.substring(us + 1);
		int tier = -1;
		for (int i = 0; i < PREFIXES.length; i++) {
			if (PREFIXES[i].equals(prefix)) {
				tier = i;
			}
		}
		if (prefix.equals("leather")) {
			tier = 0;
		} else if (prefix.equals("chainmail")) {
			tier = 3;
		}
		if (tier < 0) {
			return Optional.empty();
		}
		Kind kind = switch (suffix) {
			case "helmet" -> Kind.HELMET;
			case "chestplate" -> Kind.CHESTPLATE;
			case "leggings" -> Kind.LEGGINGS;
			case "boots" -> Kind.BOOTS;
			default -> null;
		};
		if (kind == null) {
			for (String w : WEAPONS) {
				if (w.equals(suffix)) {
					kind = Kind.WEAPON;
				}
			}
			for (String t : TOOLS) {
				if (t.equals(suffix)) {
					kind = Kind.TOOL;
				}
			}
		}
		return kind == null ? Optional.empty() : Optional.of(new Gear(kind, tier));
	}
}
