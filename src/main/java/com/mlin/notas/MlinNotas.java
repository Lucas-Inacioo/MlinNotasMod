package com.mlin.notas;

import com.mlin.notas.command.NotaCommand;
import com.mlin.notas.chest.ChestUpgrades;
import com.mlin.notas.enchanting.SelectiveEnchanting;
import com.mlin.notas.furnace.FurnaceUpgrades;
import com.mlin.notas.notes.NotebookManager;
import com.mlin.notas.registry.ModBlockEntities;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModCreativeTab;
import com.mlin.notas.registry.ModDataComponents;
import com.mlin.notas.registry.ModMenus;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.recipes.RecipeSyncServer;
import com.mlin.notas.registry.ModPayloads;
import com.mlin.notas.sorting.InventorySortServer;
import com.mlin.notas.tree.TreeCapitator;
import com.mlin.notas.tier.ModWorldgen;
import com.mlin.notas.end.Elevators;
import com.mlin.notas.end.EndBiomes;
import com.mlin.notas.registry.ModEntities;
import com.mlin.notas.registry.ModStructures;
import com.mlin.notas.tier.VanillaRebalance;
import com.mlin.notas.harvest.CropHarvester;
import com.mlin.notas.sleep.SleepServer;
import com.mlin.notas.waila.WailaServer;
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
		com.mlin.notas.registry.ModSounds.init();
		VanillaRebalance.init();
		ModDataComponents.init();
		ModBlocks.init();
		ModEntities.init();
		ModItems.init();
		ModBlockEntities.init();
		ModMenus.init();
		ModStructures.init();
		ModCreativeTab.init();
		ModPayloads.init();

		TreeCapitator.init();
		InventorySortServer.init();
		FurnaceUpgrades.init();
		RecipeSyncServer.init();
		SelectiveEnchanting.init();
		ChestUpgrades.init();
		ModWorldgen.init();
		Elevators.init();
		EndBiomes.init();
		com.mlin.notas.nether.NetherRegistry.init();
		com.mlin.notas.boss.BossRegistry.init();
		com.mlin.notas.dragon.DragonRegistry.init();
		CropHarvester.init();
		SleepServer.init();
		WailaServer.init();
		com.mlin.notas.waystone.WaystoneServer.init();
		com.mlin.notas.variants.MobVariantsServer.init();
		com.mlin.notas.skills.SkillServer.init();
		com.mlin.notas.beacon.BeaconServer.init();
		com.mlin.notas.quests.QuestServer.init();

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				NotaCommand.register(dispatcher));
		LOGGER.info("[{}] inicializado", MOD_ID);
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
