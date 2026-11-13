package com.mlin.notas.skills;

import com.mlin.notas.tier.Tier;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillTreeTest {
	@Test
	void miningHasOneNodePerTierAfterWood() {
		List<SkillNode> mining = SkillTree.nodes(SkillBranch.MINING);
		assertEquals(11, mining.size());
		for (int i = 0; i < mining.size(); i++) {
			assertEquals(i + 2, mining.get(i).level());
			assertEquals(Tier.values()[i + 1], mining.get(i).tier());
			assertNull(mining.get(i).miningRequired());
		}
		assertNull(SkillTree.node(SkillBranch.MINING, 1), "Madeira e o inicio, nao se compra");
	}

	@Test
	void sideBranchesHaveFiveLevelsWithRisingMiningRequirement() {
		for (SkillBranch branch : SkillBranch.values()) {
			if (branch == SkillBranch.MINING) {
				continue;
			}
			List<SkillNode> nodes = SkillTree.nodes(branch);
			assertEquals(5, nodes.size(), branch.id());
			Tier previous = Tier.WOOD;
			for (SkillNode node : nodes) {
				assertNotNull(node.miningRequired());
				assertTrue(node.miningRequired().ordinal() > previous.ordinal(), "requisito sobe a cada nivel");
				previous = node.miningRequired();
			}
		}
	}

	@Test
	void miningCostsMatchTheDesign() {
		assertEquals(32, SkillTree.node(SkillBranch.MINING, Tier.STONE.level()).itemCount("minecraft:cobblestone"));
		assertEquals(10, SkillTree.node(SkillBranch.MINING, Tier.COPPER.level()).itemCount(SkillTree.COAL));
		SkillNode iron = SkillTree.node(SkillBranch.MINING, Tier.IRON.level());
		assertEquals(10, iron.itemCount(SkillTree.COPPER));
		assertEquals(3, iron.xpLevels());
		SkillNode cobalt = SkillTree.node(SkillBranch.MINING, Tier.COBALT.level());
		assertEquals(1, cobalt.itemCount("mlinnotas:cobalt_heart"));
		assertEquals(10, cobalt.itemCount(SkillTree.GOLD));
		assertEquals(10, cobalt.xpLevels());
		SkillNode celestium = SkillTree.node(SkillBranch.MINING, Tier.CELESTIUM.level());
		assertEquals(1, celestium.itemCount("minecraft:nether_star"));
		assertEquals(10, celestium.itemCount("mlinnotas:raw_astralite"));
		assertEquals(40, celestium.xpLevels());
	}

	/** Todo item de custo e obtenivel com o nivel anterior (ou vem de um chefe). */
	@Test
	void progressionIsConsistent() {
		for (SkillNode node : SkillTree.nodes()) {
			int miningAvailable = node.branch() == SkillBranch.MINING ? node.level() - 1 : node.miningRequired().level();
			for (SkillNode.Cost cost : node.items()) {
				SkillTree.Source source = SkillTree.source(cost.id());
				assertNotNull(source, "sem origem: " + cost.id());
				if (!source.isBoss()) {
					assertTrue(source.miningTier().level() <= miningAvailable,
							node.key() + ": " + cost.id() + " exige Mineracao " + source.miningTier() + ", disponivel " + miningAvailable);
				}
			}
		}
	}

	/** Cada minerio travado bate com a origem do item que ele solta. */
	@Test
	void oreTiersAgreeWithCostSources() {
		assertEquals(Tier.STONE, OreTiers.required("minecraft:deepslate_coal_ore"));
		assertEquals(SkillTree.source(SkillTree.COAL).miningTier(), OreTiers.required("minecraft:coal_ore"));
		assertEquals(SkillTree.source(SkillTree.COPPER).miningTier(), OreTiers.required("minecraft:copper_ore"));
		assertEquals(SkillTree.source(SkillTree.IRON).miningTier(), OreTiers.required("minecraft:iron_ore"));
		assertEquals(SkillTree.source(SkillTree.GOLD).miningTier(), OreTiers.required("minecraft:gold_ore"));
		assertEquals(SkillTree.source("mlinnotas:raw_cobalt").miningTier(), OreTiers.required("mlinnotas:deepslate_cobalt_ore"));
		assertEquals(SkillTree.source("minecraft:diamond").miningTier(), OreTiers.required("minecraft:diamond_ore"));
		assertEquals(SkillTree.source("minecraft:netherite_scrap").miningTier(), OreTiers.required("minecraft:ancient_debris"));
		assertEquals(SkillTree.source("mlinnotas:raw_infernite").miningTier(), OreTiers.required("mlinnotas:infernite_ore"));
		assertEquals(SkillTree.source("mlinnotas:raw_endite").miningTier(), OreTiers.required("mlinnotas:endite_ore"));
		assertEquals(SkillTree.source("mlinnotas:raw_astralite").miningTier(), OreTiers.required("mlinnotas:astralite_ore"));
		assertEquals(Tier.IRON, OreTiers.required("minecraft:lapis_ore"));
		assertEquals(Tier.GOLD, OreTiers.required("minecraft:deepslate_redstone_ore"));
		assertEquals(Tier.DIAMOND, OreTiers.required("minecraft:emerald_ore"));
		assertNull(OreTiers.required("minecraft:stone"));
		assertTrue(OreTiers.canBreak("minecraft:stone", 1));
		assertFalse(OreTiers.canBreak("minecraft:coal_ore", 1));
		assertTrue(OreTiers.canBreak("minecraft:coal_ore", 2));
	}

	@Test
	void everyTierWithOreHasAGatedOre() {
		Set<Tier> gated = new HashSet<>(OreTiers.all().values());
		for (Tier tier : Tier.values()) {
			if (tier != Tier.WOOD && tier != Tier.CELESTIUM) {
				assertTrue(gated.contains(tier), "nenhum minerio travado em " + tier);
			}
		}
	}

	@Test
	void costTagsExist() {
		for (String id : SkillTree.sources().keySet()) {
			if (id.startsWith("#mlinnotas:")) {
				Path file = Path.of("src/main/resources/data/mlinnotas/tags/item/" + id.substring("#mlinnotas:".length()) + ".json");
				assertTrue(Files.exists(file), "tag sem arquivo: " + file);
			}
		}
	}

	@Test
	void everyCostHasAWhereToFindHintInBothLanguages() throws Exception {
		for (String lang : List.of("pt_br", "en_us")) {
			String json = Files.readString(Path.of("src/main/resources/assets/mlinnotas/lang/" + lang + ".json"));
			for (SkillNode node : SkillTree.nodes()) {
				for (SkillNode.Cost cost : node.items()) {
					String key = SkillTree.hintKey(cost.id());
					assertTrue(json.contains("\"" + key + "\""), lang + " sem dica para " + cost.id() + " (" + key + ")");
				}
			}
		}
	}

	@Test
	void nextObjectiveFollowsTheMiningTrunk() {
		SkillProgress progress = new SkillProgress();
		assertEquals("mining/2", SkillTree.nextObjective(progress::level).key());
		progress.setLevel(SkillBranch.MINING, Tier.COBALT.level());
		assertEquals("mining/" + Tier.DIAMOND.level(), SkillTree.nextObjective(progress::level).key());
		progress.setLevel(SkillBranch.MINING, Tier.CELESTIUM.level());
		assertNull(SkillTree.nextObjective(progress::level));
		assertEquals("skill.mlinnotas.hint.mlinnotas.skill_cost.coal", SkillTree.hintKey(SkillTree.COAL));
		assertEquals("skill.mlinnotas.hint.minecraft.cobblestone", SkillTree.hintKey("minecraft:cobblestone"));
	}

	@Test
	void prerequisitesListMissingLevels() {
		SkillProgress progress = new SkillProgress();
		SkillNode combat1 = SkillTree.node(SkillBranch.COMBAT, 1);
		assertEquals(List.of("mining/3"), SkillTree.missingPrerequisites(combat1, progress::level));
		SkillNode combat2 = SkillTree.node(SkillBranch.COMBAT, 2);
		assertEquals(List.of("combat/1", "mining/4"), SkillTree.missingPrerequisites(combat2, progress::level));
		progress.setLevel(SkillBranch.MINING, Tier.IRON.level());
		progress.setLevel(SkillBranch.COMBAT, 1);
		assertTrue(SkillTree.missingPrerequisites(combat2, progress::level).isEmpty());
	}

	@Test
	void effectsScaleWithLevel() {
		assertEquals(5.0, SkillEffects.meleeBonus(5));
		assertEquals(10.0, SkillEffects.healthBonus(5));
		assertEquals(0.2, SkillEffects.speedBonus(5), 1e-9);
		assertEquals(0.5, SkillEffects.fallReduction(5), 1e-9);
		assertEquals(1.0, SkillEffects.prospectingChance(5), 1e-9);
	}
}
