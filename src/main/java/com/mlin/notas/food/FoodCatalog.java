package com.mlin.notas.food;

import com.mlin.notas.season.CropSeasons;
import com.mlin.notas.season.Season;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tudo o que a cozinha do mod registra, como dados puros: as 10 culturas (com perfil de estacao, semente e onde
 * crescem selvagens) e as comidas (fome, saturacao, tipo, recipiente e se estragam). O registro
 * ({@link FoodRegistry}), o ContentGen ({@code scripts/FoodContent.java}) e os testes leem daqui.
 *
 * <p>Desenho: poucas culturas, cada uma com estacao e uso claro (inspirado no Farmer's Delight, sem a lista gigante
 * do Pam's HarvestCraft). O ciclo do ano: plantar na estacao certa, colher em quantidade, cozinhar e guardar em
 * conserva para o inverno (quando quase nada cresce ao ar livre).</p>
 */
public final class FoodCatalog {
	/** Onde a forma selvagem nasce no mundo. */
	public enum WildHabitat {
		/** Planicies, prados e florestas temperadas. */
		TEMPERATE,
		/** Florestas (inclui betulas, flores e cerejeiras). */
		FOREST,
		/** Savanas e selvas. */
		TROPICAL,
		/** Pantanos e manguezais. */
		WETLAND,
		/** Taigas e colinas frias. */
		COLD
	}

	/**
	 * Uma cultura. {@code seed} = item plantado (igual ao {@code produce} quando se planta o proprio produto, como
	 * cebola e alho).
	 */
	public record Crop(String name, String produce, String seed, CropSeasons.Profile profile, WildHabitat habitat) {
		/** Id do bloco plantado ({@code mlinnotas:<nome>_crop}). */
		public String blockName() {
			return name + "_crop";
		}

		/** Id do bloco selvagem ({@code mlinnotas:wild_<nome>}). */
		public String wildName() {
			return "wild_" + name;
		}

		public boolean hasSeparateSeed() {
			return !seed.equals(produce);
		}

		/** Estacao em que mais cresce (empate = a primeira do ano). */
		public Season bestSeason() {
			return FoodCatalog.bestSeason(profile);
		}
	}

	/** Tipo de comida: decide o efeito e o texto da dica. */
	public enum Kind {
		/** Produto cru da horta. */
		PRODUCE,
		/** Lanche ou prato simples. */
		SNACK,
		/** Salada fresca (estraga como a horta). */
		SALAD,
		/** Refeicao farta: Bem Alimentado I. */
		MEAL,
		/** Banquete: Bem Alimentado II. */
		FEAST,
		/** Doce. */
		DESSERT,
		/** Conserva: nao estraga. */
		PRESERVE,
		/** Ingrediente que nao se come (farinha, arroz...). */
		INGREDIENT
	}

	/** Recipiente devolvido ao comer. */
	public enum Container {
		NONE,
		BOWL,
		BOTTLE
	}

	/**
	 * Uma comida ou ingrediente. {@code saturation} e o modificador vanilla (saturacao = fome x mod x 2).
	 * {@code perishable} = envelhece com as estacoes (ver {@link FoodRules#freshness}).
	 */
	public record Food(String id, int nutrition, float saturation, Kind kind, Container container, boolean perishable, int stack) {
		public boolean edible() {
			return kind != Kind.INGREDIENT;
		}
	}

	// perfis: primavera, verao, outono, inverno
	public static final CropSeasons.Profile LETTUCE = new CropSeasons.Profile(1.35F, 0.7F, 1.0F, 0.1F);
	public static final CropSeasons.Profile ONION = new CropSeasons.Profile(1.3F, 0.9F, 1.0F, 0.15F);
	public static final CropSeasons.Profile STRAWBERRY = new CropSeasons.Profile(1.35F, 1.0F, 0.6F, 0.05F);
	public static final CropSeasons.Profile TOMATO = new CropSeasons.Profile(0.7F, 1.4F, 0.7F, 0.0F);
	public static final CropSeasons.Profile CORN = new CropSeasons.Profile(0.8F, 1.4F, 0.8F, 0.0F);
	public static final CropSeasons.Profile BELL_PEPPER = new CropSeasons.Profile(0.6F, 1.35F, 0.9F, 0.0F);
	public static final CropSeasons.Profile RICE = new CropSeasons.Profile(0.9F, 1.35F, 0.7F, 0.0F);
	public static final CropSeasons.Profile BEANS = new CropSeasons.Profile(0.9F, 1.15F, 1.3F, 0.05F);
	public static final CropSeasons.Profile GARLIC = new CropSeasons.Profile(0.8F, 0.6F, 1.35F, 0.35F);
	/** Repolho: o unico que segue crescendo (devagar) no inverno ao ar livre. */
	public static final CropSeasons.Profile CABBAGE = new CropSeasons.Profile(1.1F, 0.6F, 1.3F, 0.5F);

	public static final List<Crop> CROPS = List.of(
			new Crop("lettuce", "lettuce", "lettuce_seeds", LETTUCE, WildHabitat.TEMPERATE),
			new Crop("onion", "onion", "onion", ONION, WildHabitat.TEMPERATE),
			new Crop("strawberry", "strawberry", "strawberry_seeds", STRAWBERRY, WildHabitat.FOREST),
			new Crop("tomato", "tomato", "tomato_seeds", TOMATO, WildHabitat.TROPICAL),
			new Crop("corn", "corn", "corn_seeds", CORN, WildHabitat.TROPICAL),
			new Crop("bell_pepper", "bell_pepper", "bell_pepper_seeds", BELL_PEPPER, WildHabitat.TROPICAL),
			new Crop("rice", "rice", "rice", RICE, WildHabitat.WETLAND),
			new Crop("beans", "beans", "beans", BEANS, WildHabitat.TROPICAL),
			new Crop("garlic", "garlic", "garlic", GARLIC, WildHabitat.COLD),
			new Crop("cabbage", "cabbage", "cabbage_seeds", CABBAGE, WildHabitat.COLD));

	private static Food f(String id, int nutrition, float saturation, Kind kind, Container container, boolean perishable, int stack) {
		return new Food(id, nutrition, saturation, kind, container, perishable, stack);
	}

	private static Food produce(String id, int nutrition, float saturation, boolean perishable) {
		return f(id, nutrition, saturation, Kind.PRODUCE, Container.NONE, perishable, 64);
	}

	private static Food ingredient(String id) {
		return f(id, 0, 0, Kind.INGREDIENT, Container.NONE, false, 64);
	}

	/** Produtos e comidas, na ordem da aba criativa (sementes ficam a parte). */
	public static final List<Food> FOODS = List.of(
			// horta (cebola, alho, arroz e feijao secos nao estragam)
			produce("lettuce", 2, 0.3F, true),
			produce("cabbage", 2, 0.4F, true),
			produce("onion", 1, 0.3F, false),
			produce("tomato", 3, 0.3F, true),
			produce("corn", 2, 0.3F, true),
			produce("bell_pepper", 2, 0.3F, true),
			produce("strawberry", 2, 0.3F, true),
			ingredient("garlic"),
			ingredient("rice"),
			ingredient("beans"),
			ingredient("flour"),
			ingredient("cornmeal"),
			// lanches e pratos simples
			f("roasted_corn", 5, 0.6F, Kind.SNACK, Container.NONE, false, 64),
			f("popcorn", 3, 0.3F, Kind.SNACK, Container.NONE, false, 64),
			f("corn_bread", 5, 0.6F, Kind.SNACK, Container.NONE, false, 64),
			f("cheese_bread", 3, 0.6F, Kind.SNACK, Container.NONE, false, 64),
			f("omelette", 6, 0.6F, Kind.SNACK, Container.NONE, false, 16),
			f("fresh_cheese", 3, 0.5F, Kind.SNACK, Container.NONE, true, 64),
			// saladas
			f("garden_salad", 6, 0.6F, Kind.SALAD, Container.BOWL, true, 16),
			f("fruit_salad", 6, 0.5F, Kind.SALAD, Container.BOWL, true, 16),
			// refeicoes fartas
			f("rice_and_beans", 10, 0.7F, Kind.MEAL, Container.BOWL, false, 16),
			f("vegetable_soup", 9, 0.6F, Kind.MEAL, Container.BOWL, false, 16),
			f("pumpkin_soup", 8, 0.6F, Kind.MEAL, Container.BOWL, false, 16),
			f("beef_stew", 11, 0.8F, Kind.MEAL, Container.BOWL, false, 16),
			f("galinhada", 10, 0.8F, Kind.MEAL, Container.BOWL, false, 16),
			f("pasta", 10, 0.7F, Kind.MEAL, Container.BOWL, false, 16),
			f("polenta", 8, 0.6F, Kind.MEAL, Container.BOWL, false, 16),
			f("feijoada", 14, 0.9F, Kind.FEAST, Container.BOWL, false, 16),
			// doces
			f("strawberry_pie", 8, 0.4F, Kind.DESSERT, Container.NONE, false, 64),
			f("corn_cake", 6, 0.5F, Kind.DESSERT, Container.NONE, false, 64),
			// conservas
			f("strawberry_jam", 5, 0.4F, Kind.PRESERVE, Container.BOTTLE, false, 16),
			f("tomato_sauce", 3, 0.3F, Kind.PRESERVE, Container.BOTTLE, false, 16),
			f("pickles", 4, 0.5F, Kind.PRESERVE, Container.BOTTLE, false, 16),
			f("sauerkraut", 4, 0.5F, Kind.PRESERVE, Container.NONE, false, 64),
			f("aged_cheese", 5, 0.8F, Kind.PRESERVE, Container.NONE, false, 64),
			f("charque", 6, 0.8F, Kind.PRESERVE, Container.NONE, false, 64),
			f("dried_fish", 5, 0.7F, Kind.PRESERVE, Container.NONE, false, 64));

	/** Itens de comida (vanilla e do mod) que vem de uma cultura sazonal: produto -> bloco da cultura. */
	private static final Map<String, String> PRODUCE_CROP = new LinkedHashMap<>();

	static {
		for (Crop crop : CROPS) {
			PRODUCE_CROP.put("mlinnotas:" + crop.produce(), "mlinnotas:" + crop.blockName());
		}
		PRODUCE_CROP.put("minecraft:carrot", "minecraft:carrots");
		PRODUCE_CROP.put("minecraft:potato", "minecraft:potatoes");
		PRODUCE_CROP.put("minecraft:beetroot", "minecraft:beetroots");
		PRODUCE_CROP.put("minecraft:melon_slice", "minecraft:melon_stem");
		PRODUCE_CROP.put("minecraft:sweet_berries", "minecraft:sweet_berry_bush");
	}

	private FoodCatalog() {
	}

	public static Food food(String id) {
		for (Food food : FOODS) {
			if (food.id().equals(id)) {
				return food;
			}
		}
		return null;
	}

	public static Crop crop(String name) {
		for (Crop crop : CROPS) {
			if (crop.name().equals(name)) {
				return crop;
			}
		}
		return null;
	}

	/** Cultura cujo item (produto ou semente, id {@code mlinnotas:x}) e este; null se nenhuma. */
	public static Crop cropOfItem(String itemId) {
		for (Crop crop : CROPS) {
			if (itemId.equals("mlinnotas:" + crop.produce()) || itemId.equals("mlinnotas:" + crop.seed())) {
				return crop;
			}
		}
		return null;
	}

	/** Bloco da cultura que produz este item de comida (vanilla ou do mod), ou null. */
	public static String cropBlockOfProduce(String itemId) {
		return PRODUCE_CROP.get(itemId);
	}

	/** Sementes vanilla -> bloco da cultura (para a dica de estacao). */
	private static final Map<String, String> VANILLA_SEEDS = Map.of(
			"minecraft:wheat_seeds", "minecraft:wheat",
			"minecraft:beetroot_seeds", "minecraft:beetroots",
			"minecraft:pumpkin_seeds", "minecraft:pumpkin_stem",
			"minecraft:melon_seeds", "minecraft:melon_stem",
			"minecraft:sugar_cane", "minecraft:sugar_cane",
			"minecraft:torchflower_seeds", "minecraft:torchflower_crop",
			"minecraft:pitcher_pod", "minecraft:pitcher_crop");

	/** Bloco da cultura ligada ao item (produto ou semente, vanilla ou do mod), ou null. */
	public static String cropBlockOfItem(String itemId) {
		String produce = PRODUCE_CROP.get(itemId);
		if (produce != null) {
			return produce;
		}
		Crop crop = cropOfItem(itemId);
		if (crop != null) {
			return "mlinnotas:" + crop.blockName();
		}
		return VANILLA_SEEDS.get(itemId);
	}

	/** Perfis das culturas do mod por id de bloco, para o {@link CropSeasons}. */
	public static Map<String, CropSeasons.Profile> cropProfiles() {
		Map<String, CropSeasons.Profile> map = new LinkedHashMap<>();
		for (Crop crop : CROPS) {
			map.put("mlinnotas:" + crop.blockName(), crop.profile());
		}
		return map;
	}

	public static Season bestSeason(CropSeasons.Profile profile) {
		Season best = Season.SPRING;
		for (Season season : Season.values()) {
			if (value(profile, season) > value(profile, best)) {
				best = season;
			}
		}
		return best;
	}

	/** Cresce ao ar livre no inverno (devagar): multiplicador de inverno de pelo menos 0,3. */
	public static boolean winterHardy(CropSeasons.Profile profile) {
		return profile.winter() >= 0.3F;
	}

	static float value(CropSeasons.Profile profile, Season season) {
		return switch (season) {
			case SPRING -> profile.spring();
			case SUMMER -> profile.summer();
			case AUTUMN -> profile.autumn();
			case WINTER -> profile.winter();
		};
	}
}
