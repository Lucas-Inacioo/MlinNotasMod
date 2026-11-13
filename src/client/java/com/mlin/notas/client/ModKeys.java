package com.mlin.notas.client;

import com.mlin.notas.MlinNotas;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import com.mojang.blaze3d.platform.InputConstants;

/** Teclas do mod (categoria propria na tela de controles). */
public final class ModKeys {
	public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(MlinNotas.id("main"));

	public static final KeyMapping GAMMABRIGHT = register("gammabright", InputConstants.KEY_G);
	public static final KeyMapping SORT = register("sort", InputConstants.KEY_O);
	public static final KeyMapping SHOW_RECIPES = register("show_recipes", InputConstants.KEY_R);
	public static final KeyMapping SHOW_USAGES = register("show_usages", InputConstants.KEY_U);
	public static final KeyMapping CRAFTING_TABLET = register("crafting_tablet", InputConstants.KEY_C);
	public static final KeyMapping MAP = register("map", InputConstants.KEY_M);
	public static final KeyMapping WAYPOINT = register("waypoint", InputConstants.KEY_B);
	public static final KeyMapping CAVE_MODE = register("cave_mode", InputConstants.KEY_J);
	public static final KeyMapping TOGGLE_MINIMAP = register("toggle_minimap", InputConstants.KEY_H);
	public static final KeyMapping MINIMAP_SMALLER = register("minimap_smaller", InputConstants.KEY_MINUS);
	public static final KeyMapping MINIMAP_LARGER = register("minimap_larger", InputConstants.KEY_EQUALS);
	public static final KeyMapping MINIMAP_ZOOM_OUT = register("minimap_zoom_out", InputConstants.KEY_LBRACKET);
	public static final KeyMapping MINIMAP_ZOOM_IN = register("minimap_zoom_in", InputConstants.KEY_RBRACKET);
	public static final KeyMapping BACKPACK = register("backpack", InputConstants.KEY_V);
	public static final KeyMapping WAILA = register("waila", InputConstants.KEY_N);
	public static final KeyMapping SKILLS = register("skills", InputConstants.KEY_K);
	public static final KeyMapping QUESTS = register("quests", InputConstants.KEY_L);

	private ModKeys() {
	}

	private static KeyMapping register(String name, int key) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mlinnotas." + name, key, CATEGORY));
	}

	public static void init() {
	}
}
