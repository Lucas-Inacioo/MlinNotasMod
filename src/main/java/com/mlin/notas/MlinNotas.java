package com.mlin.notas;

import com.mlin.notas.command.NotaCommand;
import com.mlin.notas.chest.ChestUpgrades;
import com.mlin.notas.chest.ChestUpgrades;
import com.mlin.notas.enchanting.SelectiveEnchanting;
import com.mlin.notas.furnace.FurnaceUpgrades;
import com.mlin.notas.notes.NotebookManager;
import com.mlin.notas.registry.ModBlockEntities;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModBlockEntities;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModCreativeTab;
import com.mlin.notas.registry.ModDataComponents;
import com.mlin.notas.registry.ModMenus;
import com.mlin.notas.registry.ModDataComponents;
import com.mlin.notas.registry.ModMenus;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.recipes.RecipeSyncServer;
import com.mlin.notas.registry.ModPayloads;
import com.mlin.notas.sorting.InventorySortServer;
import com.mlin.notas.tree.TreeCapitator;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Entrypoint principal (comum a cliente e servidor).
 */
public class MlinNotas implements ModInitializer {
	public static final String MOD_ID = "mlinnotas";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Estado das anotacoes por jogador, vivo enquanto o servidor estiver de pe. */
	public static final NotebookManager NOTEBOOKS = new NotebookManager();

	@Override
	public void onInitialize() {
		ModDataComponents.init();
		ModBlocks.init();
		ModItems.init();
		ModBlockEntities.init();
		ModMenus.init();
		ModCreativeTab.init();
		ModPayloads.init();

		TreeCapitator.init();
		InventorySortServer.init();
		FurnaceUpgrades.init();
		RecipeSyncServer.init();
		SelectiveEnchanting.init();
		ChestUpgrades.init();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				NotaCommand.register(dispatcher));
		LOGGER.info("[{}] inicializado", MOD_ID);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
