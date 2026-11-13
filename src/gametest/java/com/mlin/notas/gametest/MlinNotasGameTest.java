package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.notes.Notebook;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;

import java.util.UUID;

/**
 * Game tests de servidor: rodam em um servidor dedicado headless durante `gradlew build`.
 * Cada metodo @GameTest recebe uma estrutura vazia e deve terminar com helper.succeed()
 * (ou lancar uma excecao para falhar).
 */
public class MlinNotasGameTest {
	@GameTest
	public void modIsLoaded(GameTestHelper helper) {
		if (!FabricLoader.getInstance().isModLoaded(MlinNotas.MOD_ID)) {
			throw new IllegalStateException("mod " + MlinNotas.MOD_ID + " nao carregou");
		}
		helper.succeed();
	}

	@GameTest
	public void commandIsRegistered(GameTestHelper helper) {
		var dispatcher = helper.getLevel().getServer().getCommands().getDispatcher();
		if (dispatcher.getRoot().getChild("nota") == null) {
			throw new IllegalStateException("comando /nota nao registrado");
		}
		helper.succeed();
	}

	@GameTest
	public void notebookManagerKeepsNotesPerPlayer(GameTestHelper helper) {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		Notebook notebookA = MlinNotas.NOTEBOOKS.of(a);
		notebookA.add("so do A");
		if (MlinNotas.NOTEBOOKS.of(b).size() != 0) {
			throw new IllegalStateException("caderno de B deveria estar vazio");
		}
		if (MlinNotas.NOTEBOOKS.of(a) != notebookA) {
			throw new IllegalStateException("caderno de A deveria ser a mesma instancia");
		}
		helper.succeed();
	}

	@GameTest
	public void worldInteractionWorks(GameTestHelper helper) {
		helper.setBlock(0, 1, 0, Blocks.STONE);
		helper.assertBlockPresent(Blocks.STONE, 0, 1, 0);
		helper.succeed();
	}
}
