package com.mlin.notas.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Game test de cliente: abre o jogo de verdade (precisa de janela/GPU) e tira screenshot.
 * Rodar com: gradlew runClientGameTest. Screenshots em build/run/clientGameTest/screenshots.
 */
@SuppressWarnings("UnstableApiUsage")
public class MlinNotasClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			context.takeScreenshot("mlinnotas-singleplayer");
		}
	}
}
