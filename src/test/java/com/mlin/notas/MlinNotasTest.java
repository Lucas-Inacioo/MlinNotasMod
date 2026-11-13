package com.mlin.notas;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Teste que toca uma classe do Minecraft (Identifier) sem precisar de Bootstrap.
 * Para classes que dependem de registries (ItemStack, Blocks...) chame
 * SharedConstants.tryDetectVersion() e Bootstrap.bootStrap() em um @BeforeAll.
 */
class MlinNotasTest {
	@Test
	void idUsesModNamespace() {
		Identifier id = MlinNotas.id("caderno");
		assertEquals("mlinnotas", id.getNamespace());
		assertEquals("caderno", id.getPath());
	}
}
