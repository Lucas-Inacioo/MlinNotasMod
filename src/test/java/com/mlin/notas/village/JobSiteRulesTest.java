package com.mlin.notas.village;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class JobSiteRulesTest {
	@Test
	void smithyAcceptsOnlySmithTypes() {
		for (String p : List.of("armorer", "weaponsmith", "toolsmith")) {
			assertTrue(JobSiteRules.accepts("smithy", p), p);
		}
		assertFalse(JobSiteRules.accepts("smithy", "fisherman"));
		assertFalse(JobSiteRules.accepts("smithy", "farmer"));
	}

	@Test
	void barrelDecorationInFarmOrStreetNeverWorks() {
		List<String> barrel = List.of("fisherman");
		assertFalse(JobSiteRules.allowed(barrel, "granary", true));
		assertFalse(JobSiteRules.allowed(barrel, "winery", true));
		assertFalse(JobSiteRules.allowed(barrel, null, true));
		assertFalse(JobSiteRules.allowed(barrel, "smithy", true));
	}

	@Test
	void playerPlacedSitesAlwaysWork() {
		assertTrue(JobSiteRules.allowed(List.of("fisherman"), null, false));
		assertTrue(JobSiteRules.allowed(List.of("fisherman"), "granary", false));
	}

	@Test
	void sitesOfTheRightBuildingWork() {
		assertTrue(JobSiteRules.allowed(List.of("armorer"), "smithy", true));
		assertTrue(JobSiteRules.allowed(List.of("farmer"), "farm", true));
		assertTrue(JobSiteRules.allowed(List.of("farmer"), "granary", true));
		assertTrue(JobSiteRules.allowed(List.of("librarian"), "library", true));
		assertTrue(JobSiteRules.allowed(List.of("mason"), "sawmill", true));
		assertTrue(JobSiteRules.allowed(List.of("cleric"), "church", true));
		assertTrue(JobSiteRules.allowed(List.of("cartographer"), "explorers_guild", true));
		assertTrue(JobSiteRules.allowed(List.of("leatherworker"), "tannery", true));
		assertFalse(JobSiteRules.allowed(List.of("leatherworker"), "house", true));
	}

	@Test
	void marketAcceptsAnyProfession() {
		for (String p : List.of("fisherman", "butcher", "cleric", "mason")) {
			assertTrue(JobSiteRules.allowed(List.of(p), "market", true), p);
		}
	}

	@Test
	void housesAndGardensAcceptNothing() {
		assertFalse(JobSiteRules.accepts("house", "farmer"));
		assertFalse(JobSiteRules.accepts("garden", "farmer"));
		assertFalse(JobSiteRules.accepts("empty_lot", "fisherman"));
		assertFalse(JobSiteRules.accepts(null, "farmer"));
	}

	@Test
	void notAJobSiteIsAlwaysAllowed() {
		assertTrue(JobSiteRules.allowed(List.of(), null, true));
	}

	@Test
	void everyKindInTheTableIsARealProject() {
		for (String kind : JobSiteRules.kinds()) {
			assertTrue(VillageProject.byId(kind) != null, kind);
		}
	}
}
