package com.mlin.notas.season;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CropSeasonsTest {
	private static SeasonDate mid(Season season) {
		return new SeasonDate(season, Season.Phase.MID, 3, 8);
	}

	@Test
	void profilesCoverVanillaCropsAndSaplings() {
		for (String id : new String[] {"minecraft:wheat", "minecraft:carrots", "minecraft:potatoes", "minecraft:beetroots",
				"minecraft:pumpkin_stem", "minecraft:melon_stem", "minecraft:sweet_berry_bush", "minecraft:sugar_cane",
				"minecraft:oak_sapling", "minecraft:cherry_sapling", "minecraft:torchflower_crop"}) {
			assertNotNull(CropSeasons.profile(id), id);
		}
		assertNull(CropSeasons.profile("minecraft:nether_wart"), "verruga do Nether nao tem estacao");
		assertNull(CropSeasons.profile("minecraft:cocoa"), "cacau e tropical");
		assertNull(CropSeasons.profile("minecraft:grass_block"));
	}

	@Test
	void wheatAndPotatoesLikeSpringAndSummerBeetAndPumpkinLikeAutumn() {
		CropSeasons.Profile wheat = CropSeasons.profile("minecraft:wheat");
		assertTrue(CropSeasons.seasonal(wheat, mid(Season.SPRING), SeasonClimate.TEMPERATE) > 1.0F);
		assertEquals(1.0F, CropSeasons.seasonal(wheat, mid(Season.SUMMER), SeasonClimate.TEMPERATE));
		assertTrue(CropSeasons.seasonal(wheat, mid(Season.AUTUMN), SeasonClimate.TEMPERATE) < 1.0F);
		for (String id : new String[] {"minecraft:beetroots", "minecraft:pumpkin_stem"}) {
			CropSeasons.Profile p = CropSeasons.profile(id);
			float autumn = CropSeasons.seasonal(p, mid(Season.AUTUMN), SeasonClimate.TEMPERATE);
			for (Season other : new Season[] {Season.SPRING, Season.SUMMER, Season.WINTER}) {
				assertTrue(autumn > CropSeasons.seasonal(p, mid(other), SeasonClimate.TEMPERATE), id + " melhor no outono que em " + other);
			}
		}
	}

	@Test
	void winterOutdoorsAlmostStopsButGreenhouseIsNormal() {
		CropSeasons.Profile wheat = CropSeasons.profile("minecraft:wheat");
		float outdoors = CropSeasons.multiplier(wheat, mid(Season.WINTER), SeasonClimate.TEMPERATE, false);
		assertTrue(outdoors <= 0.1F, "inverno ao ar livre: " + outdoors);
		assertEquals(CropSeasons.Status.DORMANT, CropSeasons.Status.of(outdoors));
		assertEquals(1.0F, CropSeasons.multiplier(wheat, mid(Season.WINTER), SeasonClimate.TEMPERATE, true));
		assertTrue(CropSeasons.multiplier(wheat, mid(Season.SPRING), SeasonClimate.TEMPERATE, true) > 1.0F,
				"estufa nao corta o bonus da estacao boa");
	}

	@Test
	void hotBiomesHaveMildDrySeasonInsteadOfWinter() {
		CropSeasons.Profile wheat = CropSeasons.profile("minecraft:wheat");
		assertEquals(CropSeasons.seasonal(wheat, mid(Season.AUTUMN), SeasonClimate.TEMPERATE),
				CropSeasons.seasonal(wheat, mid(Season.WINTER), SeasonClimate.HOT));
	}

	@Test
	void earlyAndLatePhasesBlendWithNeighbourSeason() {
		CropSeasons.Profile wheat = CropSeasons.profile("minecraft:wheat");
		float earlySpring = CropSeasons.seasonal(wheat, new SeasonDate(Season.SPRING, Season.Phase.EARLY, 0, 8), SeasonClimate.TEMPERATE);
		float midSpring = CropSeasons.seasonal(wheat, mid(Season.SPRING), SeasonClimate.TEMPERATE);
		float lateAutumn = CropSeasons.seasonal(wheat, new SeasonDate(Season.AUTUMN, Season.Phase.LATE, 7, 8), SeasonClimate.TEMPERATE);
		assertTrue(earlySpring < midSpring, "comeco da primavera ainda tem frio do inverno");
		assertTrue(lateAutumn < CropSeasons.seasonal(wheat, mid(Season.AUTUMN), SeasonClimate.TEMPERATE));
	}

	@Test
	void greenhouseNeedsRoofAndGlassOrStrongLight() {
		assertTrue(CropSeasons.greenhouse(true, true, 0));
		assertTrue(CropSeasons.greenhouse(true, false, 12));
		assertFalse(CropSeasons.greenhouse(true, false, 7));
		assertFalse(CropSeasons.greenhouse(false, false, 15), "luz forte ao ar livre nao e estufa");
	}

	@Test
	void tickDecisions() {
		assertTrue(CropSeasons.allowTick(0.3F, 0.1));
		assertFalse(CropSeasons.allowTick(0.3F, 0.5));
		assertTrue(CropSeasons.allowTick(1.2F, 0.99));
		assertTrue(CropSeasons.bonusTick(1.25F, 0.2));
		assertFalse(CropSeasons.bonusTick(1.25F, 0.3));
		assertFalse(CropSeasons.bonusTick(1.0F, 0.0));
	}
}
