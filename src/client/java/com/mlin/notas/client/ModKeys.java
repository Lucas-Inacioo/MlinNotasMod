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

	private ModKeys() {
	}

	private static KeyMapping register(String name, int key) {
		return KeyMappingHelper.registerKeyMapping(new KeyMapping("key.mlinnotas." + name, key, CATEGORY));
	}

	public static void init() {
	}
}
