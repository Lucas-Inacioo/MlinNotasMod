package com.mlin.notas.village;

import java.util.List;
import java.util.Locale;

/**
 * Boatos da taverna (logica pura): quatro categorias com preco em esmeraldas (4 a 16). Cada compra revela a posicao de
 * uma estrutura da categoria que ainda nao foi gerada (ninguem foi ate la) e que ainda nao foi contada. Os covis de
 * chefe respeitam a mesma trava de Mineracao da Bussola de Estruturas ({@code CompassCatalog}).
 */
public final class Rumors {
	public enum Category {
		/** Ruinas de superficie do mod (torre, acampamento, poco, capela, mina). */
		RUIN(4, List.of("mlinnotas:ruined_watchtower", "mlinnotas:abandoned_camp", "mlinnotas:old_well", "mlinnotas:ruined_chapel",
				"mlinnotas:disused_mine")),
		/** Estruturas vanilla refeitas (templos, posto, mina, mansao, monumento...). */
		VANILLA(8, List.of("minecraft:desert_pyramid", "minecraft:jungle_pyramid", "minecraft:pillager_outpost", "minecraft:mineshaft",
				"minecraft:igloo", "minecraft:swamp_hut", "minecraft:shipwreck", "minecraft:trail_ruins", "minecraft:monument", "minecraft:mansion",
				"minecraft:stronghold", "minecraft:ruined_portal", "minecraft:ocean_ruin_warm", "minecraft:ocean_ruin_cold")),
		/** Masmorras medias do mod. */
		DUNGEON(12, List.of("mlinnotas:forgotten_crypt", "mlinnotas:sunken_hold", "mlinnotas:drowned_temple", "mlinnotas:sand_catacomb")),
		/** Covis de chefe do Overworld (travados pela Mineracao do mundo). */
		LAIR(16, List.of("mlinnotas:cobalt_shrine", "mlinnotas:abyssal_sanctum"));

		private final int price;
		private final List<String> structures;

		Category(int price, List<String> structures) {
			this.price = price;
			this.structures = structures;
		}

		public int price() {
			return price;
		}

		public List<String> structures() {
			return structures;
		}

		public String id() {
			return name().toLowerCase(Locale.ROOT);
		}
	}

	private Rumors() {
	}

	public static Category byId(String id) {
		for (Category c : Category.values()) {
			if (c.id().equals(id)) {
				return c;
			}
		}
		return null;
	}

	/** Estruturas da categoria que o mundo (Mineracao {@code mining}) ja pode ouvir falar. */
	public static List<String> candidates(Category category, int mining) {
		return category.structures().stream().filter(id -> com.mlin.notas.compass.CompassCatalog.unlocked(com.mlin.notas.compass.CompassCatalog
				.structureGroup(id), mining)).toList();
	}

	/** Estado do limite diario guardado como {@code dia * 10 + contagem}. */
	public static int usedToday(long stored, long today) {
		return Math.floorDiv(stored, 10L) == today ? (int) Math.floorMod(stored, 10L) : 0;
	}

	public static long afterBuying(long stored, long today) {
		return today * 10L + usedToday(stored, today) + 1;
	}

	/** Pode comprar mais um boato hoje com a taverna no nivel {@code tavernLevel}? */
	public static boolean mayBuy(long stored, long today, int tavernLevel) {
		return usedToday(stored, today) < CraftsRules.rumorsPerDay(tavernLevel);
	}

	/** Nome para o waypoint: prefixo fixo para o livro de quests reconhecer. */
	public static final String WAYPOINT_PREFIX = "Boato: ";
}
