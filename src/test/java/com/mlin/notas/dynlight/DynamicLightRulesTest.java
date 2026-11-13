package com.mlin.notas.dynlight;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamicLightRulesTest {
	private static DynamicLightRules.Source src(int x, int y, int z, int lum) {
		return new DynamicLightRules.Source(x, y, z, lum);
	}

	@Test
	void itemLightComesFromTheBlockOrTheTable() {
		assertEquals(14, DynamicLightRules.itemLuminance("minecraft:torch", 14, false));
		assertEquals(15, DynamicLightRules.itemLuminance("minecraft:glowstone", 15, false));
		assertEquals(0, DynamicLightRules.itemLuminance("minecraft:stone", 0, false));
		assertEquals(15, DynamicLightRules.itemLuminance("minecraft:lava_bucket", 0, false), "balde de lava nao e bloco mas brilha");
		assertEquals(14, DynamicLightRules.itemLuminance("minecraft:glow_berries", 0, false), "bagas brilhantes: bloco padrao sem bagas");
		assertEquals(12, DynamicLightRules.itemLuminance("mlinnotas:some_lamp", 12, false), "blocos do mod com luz entram sozinhos");
		assertEquals(15, DynamicLightRules.itemLuminance("x:y", 99, false), "limite 15");
	}

	@Test
	void torchesGoOutUnderwaterButLanternsDont() {
		assertEquals(0, DynamicLightRules.itemLuminance("minecraft:torch", 14, true));
		assertEquals(0, DynamicLightRules.itemLuminance("minecraft:lava_bucket", 0, true));
		assertEquals(15, DynamicLightRules.itemLuminance("minecraft:sea_lantern", 15, true));
		assertEquals(15, DynamicLightRules.itemLuminance("minecraft:lantern", 15, true));
	}

	@Test
	void glowingMobs() {
		assertEquals(10, DynamicLightRules.entityLuminance("minecraft:blaze"));
		assertEquals(8, DynamicLightRules.entityLuminance("minecraft:magma_cube"));
		assertEquals(0, DynamicLightRules.entityLuminance("minecraft:zombie"));
	}

	@Test
	void lightFallsLinearlyToZeroAtRange() {
		assertEquals(14.0, DynamicLightRules.levelAt(0, 14), 1e-9);
		assertTrue(DynamicLightRules.levelAt(1, 14) > 12 && DynamicLightRules.levelAt(1, 14) < 14);
		assertTrue(DynamicLightRules.levelAt(4, 14) > DynamicLightRules.levelAt(5, 14));
		assertEquals(0.0, DynamicLightRules.levelAt(DynamicLightRules.RANGE, 15));
		assertEquals(0.0, DynamicLightRules.levelAt(20, 15));
		assertEquals(0.0, DynamicLightRules.levelAt(0, 0));
	}

	@Test
	void sectionsCoverTheRangeAndAtMostTwoPerAxis() {
		// alcance de 7,75 blocos: no maximo 2 secoes por eixo
		List<Long> middle = DynamicLightRules.sectionsOf(src(8, 8, 8, 14));
		assertEquals(8, middle.size());
		assertTrue(middle.contains(0L));
		assertTrue(middle.contains(DynamicLightRules.packSection(1, 1, 1)));
		// fonte perto do canto: 2x2x2
		List<Long> corner = DynamicLightRules.sectionsOf(src(16, -1, 31, 14));
		assertEquals(8, corner.size());
		assertTrue(corner.contains(DynamicLightRules.packSection(0, -1, 1)));
		assertTrue(corner.contains(DynamicLightRules.packSection(1, 0, 2)));
		assertTrue(DynamicLightRules.sectionsOf(src(0, 0, 0, 0)).isEmpty(), "sem luz nao refaz nada");
	}

	@Test
	void packRoundTripsNegativeCoordinates() {
		for (int[] c : new int[][] {{0, 0, 0}, {-1, -4, -1}, {1874999, 19, -1874999}, {-30, 511, 77}}) {
			long p = DynamicLightRules.packSection(c[0], c[1], c[2]);
			assertEquals(c[0], DynamicLightRules.sectionX(p));
			assertEquals(c[1], DynamicLightRules.sectionY(p));
			assertEquals(c[2], DynamicLightRules.sectionZ(p));
		}
	}

	@Test
	void onlyChangedSourcesRebuild() {
		List<DynamicLightRules.Source> before = List.of(src(8, 8, 8, 14), src(100, 70, 100, 15));
		assertTrue(DynamicLightRules.changedSections(before, before).isEmpty(), "nada andou");
		// a tocha andou um bloco: refaz so as secoes dela, nao as da outra fonte
		Set<Long> moved = DynamicLightRules.changedSections(before, List.of(src(9, 8, 8, 14), src(100, 70, 100, 15)));
		assertEquals(Set.copyOf(DynamicLightRules.sectionsOf(src(8, 8, 8, 14))), moved);
		// apagou (saiu da lista): refaz onde ela estava
		Set<Long> gone = DynamicLightRules.changedSections(before, List.of(src(8, 8, 8, 14)));
		assertEquals(Set.copyOf(DynamicLightRules.sectionsOf(src(100, 70, 100, 15))), gone);
		// mudou o brilho no mesmo lugar tambem refaz
		assertFalse(DynamicLightRules.changedSections(before, List.of(src(8, 8, 8, 10), src(100, 70, 100, 15))).isEmpty());
	}

	@Test
	void selectKeepsTheNearestAndTheBrightestPerBlock() {
		List<DynamicLightRules.Source> all = List.of(src(50, 0, 0, 10), src(1, 0, 0, 5), src(1, 0, 0, 12), src(-3, 0, 0, 14), src(9, 0, 0, 0));
		List<DynamicLightRules.Source> picked = DynamicLightRules.select(all, 0.5, 0.5, 0.5, 2);
		assertEquals(List.of(src(1, 0, 0, 12), src(-3, 0, 0, 14)), picked);
		assertEquals(3, DynamicLightRules.select(all, 0, 0, 0, 64).size(), "sem luz fica de fora; mesmo bloco conta uma vez");
	}

	@Test
	void fieldTakesTheBrightestSourceAndMergesIntoLightCoords() {
		LightField field = new LightField(List.of(src(0, 64, 0, 14), src(3, 64, 0, 8)));
		assertEquals(14.0, field.levelAt(0, 64, 0), 1e-9);
		assertTrue(field.levelAt(2, 64, 0) > field.levelAt(5, 64, 0));
		assertEquals(0.0, field.levelAt(40, 64, 0));
		assertEquals(0.0, LightField.EMPTY.levelAt(0, 0, 0));
		int sky = 15 << 20;
		int dark = sky; // bloco 0
		int lit = LightField.applyToPacked(dark, 10.5);
		assertEquals(sky, lit & 0xFFFF0000, "luz do ceu intacta");
		assertEquals(168, lit & 0xFFFF);
		int bright = sky | (13 << 4);
		assertEquals(bright, LightField.applyToPacked(bright, 6.0), "nunca escurece");
		assertEquals(240, LightField.applyToPacked(0, 20.0) & 0xFFFF, "teto 15");
	}

	@Test
	void optionsJsonRoundTripAndClamp() {
		DynamicLightOptions o = new DynamicLightOptions();
		o.droppedItems = false;
		o.maxSources = 7;
		DynamicLightOptions back = DynamicLightOptions.fromJson(o.toJson());
		assertFalse(back.droppedItems);
		assertEquals(7, back.maxSources);
		assertTrue(back.enabled);
		assertEquals(64, DynamicLightOptions.fromJson("{\"maxSources\": 900}").maxSources);
		assertTrue(DynamicLightOptions.fromJson("lixo{").enabled);
		assertFalse(DynamicLightOptions.fromJson("{\"enabled\": false}").enabled);
	}
}
