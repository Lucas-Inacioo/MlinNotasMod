package com.mlin.notas.vstructures;

import java.util.Set;

/**
 * O que importa de cada bloco para andar e apoiar coisas, so pelo texto de estado (logica pura, aproximada o
 * bastante para os testes dos planos): se e ar, se da para atravessar, se da para ficar em pe em cima, se e
 * armadilha e de que lado precisa de apoio.
 */
public final class BlockTraits {
	private static final Set<String> PASSABLE = Set.of("air", "cave_air", "torch", "wall_torch", "soul_torch", "soul_wall_torch", "redstone_torch",
			"redstone_wall_torch", "redstone_wire", "rail", "powered_rail", "detector_rail", "activator_rail", "tripwire", "tripwire_hook", "lever",
			"ladder", "vine", "cobweb", "short_grass", "tall_grass", "fern", "large_fern", "dead_bush", "bush", "short_dry_grass", "tall_dry_grass",
			"water", "lava", "sugar_cane", "glow_lichen", "hanging_roots", "spore_blossom", "cave_vines", "cave_vines_plant", "seagrass", "kelp",
			"kelp_plant", "brown_mushroom", "red_mushroom", "fire", "snow", "pink_petals", "leaf_litter", "wildflowers", "lily_pad", "poppy", "dandelion",
			"cornflower", "oxeye_daisy", "azure_bluet", "allium", "blue_orchid", "orange_tulip", "red_tulip", "white_tulip", "pink_tulip",
			"lily_of_the_valley", "torchflower", "jungle_sapling", "oak_sapling", "cocoa", "twisting_vines", "weeping_vines", "big_dripleaf_stem",
			"small_dripleaf", "moss_carpet", "pale_moss_carpet", "firefly_bush", "sweet_berry_bush", "cactus_flower", "light", "structure_void");

	private BlockTraits() {
	}

	public static boolean isAir(String state) {
		String id = VPlan.id(state);
		return id.equals("air") || id.equals("cave_air");
	}

	/** Da para atravessar (ar, tochas, trilhos, plantas, tapetes, placas, teia, agua...). */
	public static boolean passable(String state) {
		String id = VPlan.id(state);
		return PASSABLE.contains(id) || id.endsWith("_carpet") || id.endsWith("pressure_plate") || id.endsWith("_button")
				|| id.endsWith("_sign") || id.endsWith("_banner") || id.endsWith("_candle") || id.equals("candle") || id.endsWith("_sapling")
				|| id.endsWith("_tulip") || id.endsWith("_coral_fan")
				|| ("true".equals(VPlan.prop(state, "open")) && (id.endsWith("_trapdoor") || id.endsWith("_door") || id.endsWith("_fence_gate")));
	}

	public static boolean obstacle(String state) {
		return !passable(state);
	}

	/** Da para ficar em pe em cima (bloco cheio, laje, degrau, bau...; cerca, muro, grade e lanterna nao). */
	public static boolean standable(String state) {
		if (passable(state)) {
			return false;
		}
		String id = VPlan.id(state);
		return !(id.endsWith("_fence") || id.endsWith("_wall") || id.endsWith("fence_gate") || id.equals("iron_bars") || id.endsWith("_pane")
				|| hangingLantern(id) || id.endsWith("chain") || id.equals("pointed_dripstone") || id.endsWith("_bars") || id.equals("end_rod")
				|| id.equals("lightning_rod"));
	}

	/** Bloco cheio e firme (segura tocha de parede, escada de mao, alavanca). */
	public static boolean sturdy(String state) {
		if (!standable(state)) {
			return false;
		}
		String id = VPlan.id(state);
		return !(id.endsWith("_slab") && !"double".equals(VPlan.prop(state, "type"))) && !id.endsWith("_stairs") && !id.equals("chest")
				&& !id.endsWith("trapdoor") && !id.equals("decorated_pot") && !id.equals("flower_pot") && !id.startsWith("potted_")
				&& !id.equals("lectern") && !id.equals("anvil") && !id.equals("chipped_anvil") && !id.equals("damaged_anvil") && !id.equals("bell")
				&& !id.equals("cauldron") && !id.equals("water_cauldron") && !id.equals("brewing_stand") && !id.equals("grindstone")
				&& !id.endsWith("candle_cake") && !id.equals("cake") && !id.endsWith("_head") && !id.endsWith("_skull") && !id.equals("piston_head")
				&& !id.equals("campfire") && !id.equals("soul_campfire") && !id.endsWith("_bed") && !id.equals("dirt_path") && !id.equals("farmland")
				&& !id.equals("composter") && !id.equals("hopper") && !id.equals("stonecutter") && !id.equals("enchanting_table");
	}

	/** Escada de mao, trepadeira ou agua: sobe e desce. */
	public static boolean climbable(String state) {
		String id = VPlan.id(state);
		return id.equals("ladder") || id.equals("vine") || id.equals("water") || id.equals("cave_vines") || id.equals("cave_vines_plant")
				|| id.equals("twisting_vines") || id.equals("weeping_vines") || id.equals("scaffolding");
	}

	/** Pisar dispara armadilha. */
	public static boolean trigger(String state) {
		String id = VPlan.id(state);
		return id.endsWith("pressure_plate") || id.equals("tripwire");
	}

	/**
	 * Para blocos que precisam de apoio: deslocamento {dx, dy, dz} ate o bloco que os segura; {@code null} para os que
	 * se seguram sozinhos.
	 */
	public static int[] supportOffset(String state) {
		String id = VPlan.id(state);
		String facing = VPlan.prop(state, "facing");
		if (id.equals("wall_torch") || id.equals("soul_wall_torch") || id.equals("redstone_wall_torch") || id.equals("ladder") || id.endsWith("_wall_banner")
				|| id.equals("tripwire_hook") || id.endsWith("_wall_sign")) {
			return back(facing);
		}
		if (id.equals("lever") || id.endsWith("_button")) {
			String face = VPlan.prop(state, "face");
			if ("floor".equals(face)) {
				return new int[] {0, -1, 0};
			}
			if ("ceiling".equals(face)) {
				return new int[] {0, 1, 0};
			}
			return back(facing);
		}
		if (hangingLantern(id)) {
			return "true".equals(VPlan.prop(state, "hanging")) ? new int[] {0, 1, 0} : new int[] {0, -1, 0};
		}
		if (id.equals("torch") || id.equals("soul_torch") || id.equals("redstone_torch") || id.equals("redstone_wire") || id.endsWith("rail")
				|| id.endsWith("pressure_plate") || id.endsWith("_carpet") || id.endsWith("_banner") || id.equals("candle") || id.endsWith("_candle")
				|| id.equals("short_grass") || id.equals("fern") || id.equals("dead_bush") || id.equals("bush") || id.endsWith("_sapling")
				|| id.equals("flower_pot") || id.startsWith("potted_") || id.equals("repeater") || id.equals("comparator") || id.equals("sea_pickle")
				|| id.equals("brown_mushroom") || id.equals("red_mushroom") || id.equals("pink_petals") || id.equals("leaf_litter")
				|| id.equals("short_dry_grass") || id.equals("tall_dry_grass") || id.equals("firefly_bush") || id.equals("sweet_berry_bush")
				|| id.equals("snow") || id.endsWith("_tulip") || id.equals("poppy") || id.equals("dandelion") || id.equals("cornflower")
				|| id.equals("azure_bluet") || id.equals("oxeye_daisy") || id.equals("allium") || id.equals("lily_of_the_valley")) {
			return new int[] {0, -1, 0};
		}
		return null;
	}

	/** Laje de cima, degrau de cabeca para baixo e (para tapete) qualquer bloco servem de chao. */
	static boolean topFace(String support, String item) {
		if (isAir(support)) {
			return false;
		}
		if (VPlan.id(item).endsWith("carpet")) {
			return true;
		}
		String id = VPlan.id(support);
		return ("top".equals(VPlan.prop(support, "type")) && id.endsWith("_slab")) || ("top".equals(VPlan.prop(support, "half")) && id.endsWith("_stairs"));
	}

	/** Lanterna pendurada aceita corrente, cerca e muro em cima. */
	static boolean acceptsHanging(String support, int[] need) {
		if (need[1] != 1) {
			return false;
		}
		String id = VPlan.id(support);
		return id.endsWith("chain") || id.endsWith("_fence") || id.endsWith("_wall") || id.endsWith("_slab") || id.endsWith("_stairs");
	}

	private static int[] back(String facing) {
		if (facing == null) {
			return new int[] {0, -1, 0};
		}
		return switch (facing) {
			case "north" -> new int[] {0, 0, 1};
			case "south" -> new int[] {0, 0, -1};
			case "east" -> new int[] {-1, 0, 0};
			case "west" -> new int[] {1, 0, 0};
			default -> new int[] {0, -1, 0};
		};
	}

	/** Lanterna de verdade (comum, de alma, de cobre): lanterna do mar e abobora iluminada sao blocos cheios. */
	static boolean hangingLantern(String id) {
		return id.endsWith("lantern") && !id.equals("sea_lantern") && !id.equals("jack_o_lantern");
	}
}
