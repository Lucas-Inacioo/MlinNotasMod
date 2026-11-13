package com.mlin.notas.accessories;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccessoryCatalogTest {
	private static final Path ASSETS = Path.of("src/main/resources/assets/mlinnotas");

	@Test
	void artifactAndRelicCountIsInRange() {
		int count = AccessoryCatalog.ofKind(AccessoryKind.ARTIFACT).size() + AccessoryCatalog.ofKind(AccessoryKind.RELIC).size();
		assertTrue(count >= 15 && count <= 25, "artefatos + reliquias: " + count);
		assertEquals(12, AccessoryCatalog.ofKind(AccessoryKind.BASIC).size());
	}

	@Test
	void everyBossHasOneRelic() {
		for (String boss : List.of("cobalt_golem", "abyssal_guardian", "igneous_colossus", "ender_dragon", "astral_herald", "wither", "dungeon")) {
			AccessoryDef relic = AccessoryCatalog.relicOf(boss);
			assertNotNull(relic, boss);
			assertEquals(AccessoryKind.RELIC, relic.kind());
		}
	}

	@Test
	void everyArtifactAndRelicHasALootSource() {
		Set<String> dropped = new HashSet<>();
		ArtifactLoot.pools().forEach((table, pool) -> {
			assertTrue(pool.chance() > 0 && pool.chance() <= 1.0, table);
			assertFalse(pool.items().isEmpty(), table);
			for (String id : pool.items()) {
				AccessoryDef def = AccessoryCatalog.byId(id);
				assertNotNull(def, table + " -> " + id);
				assertTrue(def.kind() != AccessoryKind.BASIC, "basicos so por receita: " + id);
			}
			dropped.addAll(pool.items());
		});
		dropped.add("champion_seal"); // mini-chefes, por codigo
		for (AccessoryDef def : AccessoryCatalog.all()) {
			if (def.kind() != AccessoryKind.BASIC) {
				assertTrue(dropped.contains(def.id()), "sem fonte: " + def.id());
			}
		}
	}

	@Test
	void noSingleAccessoryBreaksTheEndgameBudget() {
		for (AccessoryDef def : AccessoryCatalog.all()) {
			def.stats().forEach((stat, value) -> assertTrue(value <= stat.cap(AccessoryStage.ENDGAME) + 1.0E-9, def.id() + " " + stat));
		}
	}

	@Test
	void relicsRespectTheNoImmunityRule() {
		AccessoryDef shell = AccessoryCatalog.byId("abyssal_shell");
		assertEquals(0.30, shell.stat(AccessoryStat.FIRE_REDUCTION), 1.0E-9);
		for (AccessoryDef def : AccessoryCatalog.ofKind(AccessoryKind.RELIC)) {
			def.stats().forEach((stat, value) -> assertTrue(!stat.percent() || value < 0.5, def.id()));
		}
	}

	@Test
	void everyAccessoryHasModelTextureAndNames() throws IOException {
		String pt = Files.readString(ASSETS.resolve("lang/pt_br.json"), StandardCharsets.UTF_8);
		String en = Files.readString(ASSETS.resolve("lang/en_us.json"), StandardCharsets.UTF_8);
		for (AccessoryDef def : AccessoryCatalog.all()) {
			assertTrue(Files.exists(ASSETS.resolve("items/" + def.id() + ".json")), def.id());
			assertTrue(Files.exists(ASSETS.resolve("models/item/" + def.id() + ".json")), def.id());
			assertTrue(Files.exists(ASSETS.resolve("textures/item/" + def.id() + ".png")), def.id());
			assertTrue(pt.contains("\"item.mlinnotas." + def.id() + "\""), def.id());
			assertTrue(en.contains("\"item.mlinnotas." + def.id() + "\""), def.id());
			if (def.kind() == AccessoryKind.RELIC) {
				assertTrue(pt.contains("\"accessory.mlinnotas.relic_of." + def.bossFor() + "\""), def.id());
			}
		}
		for (AccessoryStat stat : AccessoryStat.values()) {
			assertTrue(pt.contains("\"accessory.mlinnotas.stat." + stat.id() + "\""), stat.id());
			assertTrue(en.contains("\"accessory.mlinnotas.stat_name." + stat.id() + "\""), stat.id());
		}
		for (AccessoryAbility ability : AccessoryAbility.values()) {
			assertTrue(pt.contains("\"accessory.mlinnotas.ability." + ability.id() + "\""), ability.id());
			assertTrue(en.contains("\"accessory.mlinnotas.ability." + ability.id() + "\""), ability.id());
		}
		for (AccessoryStage stage : AccessoryStage.values()) {
			assertTrue(pt.contains("\"accessory.mlinnotas.stage." + stage.id() + "\""), stage.id());
		}
		for (AccessorySlotType type : AccessorySlotType.values()) {
			assertTrue(Files.exists(ASSETS.resolve("textures/gui/sprites/container/slot/" + type.id() + ".png")), type.id());
			assertTrue(pt.contains("\"accessory.mlinnotas.slot." + type.id() + "\""), type.id());
		}
	}
}
