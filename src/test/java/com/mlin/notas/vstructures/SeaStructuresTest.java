package com.mlin.notas.vstructures;

import com.mlin.notas.util.Pos;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Estruturas do mar e pequenas refeitas: o que cada uma precisa ter alem das regras gerais de VanillaRemakePlannerTest. */
class SeaStructuresTest {
	/** Achados para cavar: nao precisam de bau alcancavel a pe. */
	static final Set<VKind> DIG_SITES = EnumSet.of(VKind.BURIED_TREASURE, VKind.TRAIL_RUINS);
	private static final long[] SEEDS = {0L, 1L, 2L, 3L, 42L, -7L, 1234L, 99999L, 123456789L, 987654321987L, -55555555L, 31337L};

	/** O que o jogador faz para entrar: no iglu tira o tapete e abre o alcapao. */
	static void prepareForWalking(VKind kind, VPlan plan) {
		if (kind == VKind.IGLOO) {
			Pos t = plan.marker(IglooPlanner.MARKER_TRAPDOOR);
			plan.set(t.x(), t.y(), t.z(), VPlan.s("spruce_trapdoor", "facing=south", "half=top", "open=true"));
			plan.set(t.x(), t.y() + 1, t.z(), VPlan.AIR);
		}
	}

	private static Set<Long> reach(VPlan plan) {
		Pos start = plan.marker(VPlan.MARKER_ENTRANCE);
		return plan.reachableFrom(start.x(), start.y(), start.z(), false);
	}

	private static long loot(VPlan plan, String table) {
		return plan.lootTables().values().stream().filter(table::equals).count();
	}

	@Test
	void monumentHasGoldCoreThreeEldersAndIsFlooded() {
		for (long seed : SEEDS) {
			VPlan plan = VKind.MONUMENT.plan(seed, 0);
			assertEquals(8, plan.count("gold_block"), "8 blocos de ouro no nucleo");
			Pos core = plan.marker(MonumentPlanner.MARKER_CORE);
			assertEquals("gold_block", VPlan.id(plan.get(core.x(), core.y(), core.z())));
			assertEquals(3, plan.entities().stream().filter(s -> s.type().equals("minecraft:elder_guardian")).count(), "3 guardioes ancestrais");
			assertTrue(plan.count("wet_sponge") >= 10, "salas de esponja");
			assertTrue(plan.count("sea_lantern") >= 40, "lanternas do mar");
			assertTrue(plan.count("kelp_plant") + plan.count("seagrass") > 50, "recife em volta");
			assertTrue(plan.waterLevel() > plan.bounds()[4], "tudo debaixo d'agua");
			// cada guardiao ancestral fica num espaco aberto a nado a partir da entrada
			Set<Long> reach = reach(plan);
			for (VPlan.Spawn s : plan.entities()) {
				assertTrue(reach.contains(VPlan.key((int) Math.floor(s.x()), (int) Math.floor(s.y()), (int) Math.floor(s.z()))),
						"seed " + seed + ": guardiao ancestral fechado em " + s);
			}
			assertTrue(VPlan.usableFrom(reach, core.x(), core.y(), core.z()), "nucleo alcancavel a nado");
			// pilares ate o fundo
			assertTrue(plan.flagMap().values().stream().filter(f -> (f & VPlan.FILL_DOWN) != 0).count() >= 40, "pilares");
		}
	}

	@Test
	void monumentVariantsDiffer() {
		VPlan warm = VKind.MONUMENT.plan(5, 0);
		VPlan frozen = VKind.MONUMENT.plan(5, 1);
		assertTrue(warm.blocks().values().stream().anyMatch(s -> s.contains("coral")));
		assertFalse(frozen.blocks().values().stream().anyMatch(s -> s.contains("coral")), "sem coral no mar gelado");
	}

	@Test
	void shipwrecksKeepTheVanillaChestsAndVary() {
		Set<Integer> lengths = new java.util.HashSet<>();
		for (VKind kind : new VKind[] {VKind.SHIPWRECK, VKind.SHIPWRECK_BEACHED}) {
			for (long seed : SEEDS) {
				VPlan plan = kind.plan(seed, 0);
				assertEquals(1, loot(plan, ShipwreckPlanner.MAP), "bau do mapa");
				assertEquals(1, loot(plan, ShipwreckPlanner.TREASURE), "bau do tesouro");
				assertTrue(loot(plan, ShipwreckPlanner.SUPPLY) >= 1, "bau de suprimentos");
				int[] b = plan.bounds();
				lengths.add(b[5] - b[2]);
				assertTrue(plan.count("white_wool") > 0 || plan.count("white_carpet") > 0, "velas");
				if (kind == VKind.SHIPWRECK_BEACHED) {
					assertEquals(Integer.MIN_VALUE, plan.waterLevel(), "encalhado e a pe");
				}
			}
		}
		assertTrue(lengths.size() >= 3, "tipos de navio diferentes: " + lengths);
	}

	@Test
	void oceanRuinsHaveBigChestArchaeologyAndClusters() {
		for (VKind kind : new VKind[] {VKind.OCEAN_RUIN_WARM, VKind.OCEAN_RUIN_COLD}) {
			boolean warm = kind == VKind.OCEAN_RUIN_WARM;
			for (long seed : SEEDS) {
				VPlan plan = kind.plan(seed, 0);
				assertEquals(1, loot(plan, OceanRuinPlanner.BIG), "bau grande no altar");
				assertTrue(loot(plan, OceanRuinPlanner.SMALL) >= 1, "bau pequeno numa casinha");
				String dig = warm ? "minecraft:archaeology/ocean_ruin_warm" : "minecraft:archaeology/ocean_ruin_cold";
				assertTrue(plan.archaeologyMap().values().stream().filter(dig::equals).count() >= 4, "arqueologia");
				String digBlock = warm ? "suspicious_sand" : "suspicious_gravel";
				for (long k : plan.archaeologyMap().keySet()) {
					assertEquals(digBlock, VPlan.id(plan.blocks().get(k)));
				}
				assertTrue(plan.count(warm ? "sandstone" : "stone_bricks") > 50);
				assertEquals(2, plan.entities().stream().filter(s -> s.type().equals("minecraft:drowned")).count());
				int[] b = plan.bounds();
				assertTrue(b[3] - b[0] >= 30 || b[5] - b[2] >= 30, "grupo de ruinas espalhado");
			}
		}
	}

	@Test
	void buriedTreasureChestIsWhereTheMapPoints() {
		for (long seed : SEEDS) {
			VPlan plan = VKind.BURIED_TREASURE.plan(seed, 0);
			assertEquals(1, plan.lootTables().size());
			long k = plan.lootTables().keySet().iterator().next();
			assertEquals(BuriedTreasurePlanner.TREASURE, plan.lootTables().get(k));
			// o mapa aponta para a origem (bloco 9,9 do chunk): o bau fica embaixo dela, enterrado
			assertEquals(0, VPlan.keyX(k));
			assertEquals(0, VPlan.keyZ(k));
			assertTrue(VPlan.keyY(k) <= -2, "enterrado");
			for (int[] d : new int[][] {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}}) {
				assertTrue(plan.solid(d[0], VPlan.keyY(k) + d[1], d[2]), "bau coberto");
			}
			// o X na superficie
			assertTrue(plan.solid(0, -1, 0) && plan.solid(2, -1, 2) && plan.solid(-2, -1, 2));
			Set<Long> reach = reach(plan);
			assertTrue(reach.contains(VPlan.key(0, 0, 0)), "da para chegar ao X");
		}
	}

	@Test
	void iglooLabIsHiddenUnderTheCarpet() {
		for (long seed : SEEDS) {
			VPlan plan = VKind.IGLOO.plan(seed, 0);
			Pos t = plan.marker(IglooPlanner.MARKER_TRAPDOOR);
			assertEquals("spruce_trapdoor", VPlan.id(plan.get(t.x(), t.y(), t.z())));
			assertTrue(VPlan.id(plan.get(t.x(), t.y() + 1, t.z())).endsWith("carpet"), "tapete em cima do alcapao");
			Pos lab = plan.marker(IglooPlanner.MARKER_LAB);
			assertEquals("brewing_stand", VPlan.id(plan.get(lab.x(), lab.y(), lab.z())));
			assertEquals(1, loot(plan, IglooPlanner.CHEST));
			assertTrue(plan.count("ladder") >= 6, "poco com escada de mao");
			assertTrue(plan.entities().stream().anyMatch(s -> s.type().equals("minecraft:zombie_villager")));
			assertTrue(plan.entities().stream().anyMatch(s -> s.type().equals("minecraft:villager") && s.tag().equals("cleric")));
			Set<Long> closed = reach(plan);
			assertFalse(VPlan.usableFrom(closed, lab.x(), lab.y(), lab.z()), "o laboratorio so abre pelo alcapao");
			prepareForWalking(VKind.IGLOO, plan);
			assertTrue(VPlan.usableFrom(reach(plan), lab.x(), lab.y(), lab.z()), "pelo alcapao chega no laboratorio");
		}
	}

	@Test
	void swampHutHasWitchCatCauldronAndStilts() {
		for (long seed : SEEDS) {
			VPlan plan = VKind.SWAMP_HUT.plan(seed, 0);
			assertEquals(1, plan.entities().stream().filter(s -> s.type().equals("minecraft:witch")).count());
			assertEquals(1, plan.entities().stream().filter(s -> s.type().equals("minecraft:cat") && s.tag().equals("black_cat")).count());
			Pos c = plan.marker(SwampHutPlanner.MARKER_CAULDRON);
			assertEquals("water_cauldron", VPlan.id(plan.get(c.x(), c.y(), c.z())));
			assertTrue(plan.flagMap().values().stream().filter(f -> (f & VPlan.FILL_DOWN) != 0).count() >= 8, "palafitas");
			Set<Long> reach = reach(plan);
			VPlan.Spawn witch = plan.entities().stream().filter(s -> s.type().equals("minecraft:witch")).findFirst().orElseThrow();
			assertTrue(reach.contains(VPlan.key((int) Math.floor(witch.x()), (int) witch.y(), (int) Math.floor(witch.z()))), "porta aberta");
		}
	}

	@Test
	void ruinedPortalsKeepTheirIdentity() {
		for (VKind kind : VKind.values()) {
			if (!kind.name().startsWith("RUINED_PORTAL")) {
				continue;
			}
			for (int variant = 0; variant <= 1; variant++) {
				for (long seed : SEEDS) {
					VPlan plan = kind.plan(seed, variant);
					assertNotNull(plan.marker(RuinedPortalPlanner.MARKER_FRAME));
					assertTrue(plan.count("obsidian") + plan.count("crying_obsidian") >= 6, kind + ": moldura");
					assertTrue(plan.count("gold_block") >= 1, kind + ": ouro");
					assertEquals(1, loot(plan, RuinedPortalPlanner.CHEST));
					assertTrue(plan.count("netherrack") >= 10, kind + ": netherrack espalhado");
					boolean cold = variant == VariantRules.PORTAL_COLD && kind != VKind.RUINED_PORTAL_NETHER;
					if (cold) {
						assertEquals(0, plan.count("magma_block") + plan.count("lava"), kind + " frio sem magma nem lava");
					}
					if (kind == VKind.RUINED_PORTAL_NETHER) {
						assertEquals(0, plan.count("stone_bricks"), "Nether usa pedra-negra");
						assertTrue(plan.count("polished_blackstone_bricks") > 0);
					}
					if (kind == VKind.RUINED_PORTAL_OCEAN) {
						assertEquals(0, plan.count("lava"), "sem lava no mar");
					}
				}
			}
		}
	}

	@Test
	void trailRuinsAreABuriedDigSite() {
		for (long seed : SEEDS) {
			VPlan plan = VKind.TRAIL_RUINS.plan(seed, 0);
			Map<Long, String> digs = plan.archaeologyMap();
			assertTrue(digs.size() >= 20, "cascalho suspeito: " + digs.size());
			assertTrue(digs.values().stream().anyMatch(TrailRuinsPlanner.RARE::equals), "achado raro");
			long buried = digs.keySet().stream().filter(k -> VPlan.keyY(k) < 0).count();
			assertEquals(digs.size(), buried, "tudo enterrado");
			// pelo poco aberto o jogador desce e acha os primeiros
			Set<Long> reach = reach(plan);
			long reachable = digs.keySet().stream().filter(k -> VPlan.usableFrom(reach, VPlan.keyX(k), VPlan.keyY(k), VPlan.keyZ(k))).count();
			assertTrue(reachable >= 3, "seed " + seed + ": poco de escavacao chega em " + reachable);
			assertTrue(plan.count("decorated_pot") >= 3);
		}
	}
}
