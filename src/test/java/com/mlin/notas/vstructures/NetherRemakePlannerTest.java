package com.mlin.notas.vstructures;

import com.mlin.notas.util.Pos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fortaleza e bastiao refeitos: identidade vanilla de cada um e a altura no terreno do Nether. */
class NetherRemakePlannerTest {
	@BeforeAll
	static void bootstrap() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	private static final long[] SEEDS = {0L, 1L, 2L, 42L, -7L, 1234L, 99999L, 987654321987L};

	private static long loot(VPlan plan, String table) {
		return plan.lootTables().values().stream().filter(table::equals).count();
	}

	private static long mobs(VPlan plan, String type) {
		return plan.entities().stream().filter(s -> s.type().equals(type)).count();
	}

	@Test
	void fortressIsMonumentalWithBlazePlatformsWartAndChests() {
		for (long seed : SEEDS) {
			VPlan plan = VKind.NETHER_FORTRESS.plan(seed, 0);
			assertTrue(plan.spawnerMap().size() >= 2, "poucos geradores");
			assertTrue(plan.spawnerMap().values().stream().allMatch(FortressPlanner.BLAZE::equals), "so geradores de blaze");
			assertTrue(loot(plan, FortressPlanner.LOOT) >= 8, "baus da fortaleza: " + loot(plan, FortressPlanner.LOOT));
			assertEquals(plan.lootTables().size(), loot(plan, FortressPlanner.LOOT), "todos os baus com loot vanilla");
			assertTrue(mobs(plan, "minecraft:wither_skeleton") >= 6, "esqueletos wither de patrulha");
			assertTrue(plan.count("nether_wart") >= 60, "estufa de fungo pequena");
			assertTrue(plan.count("soul_sand") >= 60, "sem areia das almas");
			assertNotNull(plan.marker(FortressPlanner.MARKER_THRONE));
			int[] b = plan.bounds();
			assertTrue(b[3] - b[0] >= 120 && b[5] - b[2] >= 120, "fortaleza pequena: " + java.util.Arrays.toString(b));
			long deep = plan.flagMap().values().stream().filter(f -> (f & VPlan.FILL_DEEP) != 0).count();
			assertTrue(deep > 200, "pilares que descem ate o chao: " + deep);
			// quase tudo e tijolo do Nether
			long bricks = plan.blocks().values().stream().filter(s -> VPlan.id(s).contains("nether_brick")).count();
			long solid = plan.blocks().values().stream().filter(s -> !BlockTraits.isAir(s)).count();
			assertTrue(bricks > solid * 0.8, "paleta: " + bricks + "/" + solid);
		}
	}

	@Test
	void fortressSpawnersAreOnPlatformsReachableFromTheGate() {
		for (long seed : SEEDS) {
			VPlan plan = VKind.NETHER_FORTRESS.plan(seed, 0);
			Pos start = plan.marker(VPlan.MARKER_ENTRANCE);
			Set<Long> reach = plan.reachableFrom(start.x(), start.y(), start.z(), false);
			for (String m : new String[] {FortressPlanner.MARKER_KEEP_SPAWNER, FortressPlanner.MARKER_TOWER_SPAWNER, FortressPlanner.MARKER_THRONE,
					FortressPlanner.MARKER_GARDEN}) {
				Pos pos = plan.marker(m);
				assertTrue(VPlan.usableFrom(reach, pos.x(), pos.y(), pos.z()) || reach.contains(VPlan.key(pos.x(), pos.y(), pos.z())), m + " inalcancavel");
			}
			// o gerador do terraco fica num estrado dentro do anel de cercas
			Pos keep = plan.marker(FortressPlanner.MARKER_KEEP_SPAWNER);
			assertEquals("spawner", VPlan.id(plan.get(keep.x(), keep.y(), keep.z())));
			assertTrue(BlockTraits.sturdy(plan.get(keep.x(), keep.y() - 1, keep.z())));
		}
	}

	@Test
	void bastionTypesKeepTheirVanillaIdentity() {
		for (long seed : SEEDS) {
			for (int type = 0; type < BastionPlanner.TYPES; type++) {
				VPlan plan = VKind.BASTION_REMNANT.plan(seed, type);
				String where = "tipo " + type + " seed " + seed;
				assertTrue(mobs(plan, "minecraft:piglin") >= 4, where + ": poucos piglins");
				assertTrue(mobs(plan, "minecraft:piglin_brute") >= 1, where + ": sem piglin bruto");
				assertTrue(plan.count("gilded_blackstone") >= 10, where + ": pouco blackstone dourado");
				assertTrue(plan.count("gold_block") >= 1, where + ": sem bloco de ouro");
				assertNotNull(plan.marker(BastionPlanner.MARKER_GOLD), where);
				assertEquals("gold_block", VPlan.id(plan.get(plan.marker(BastionPlanner.MARKER_GOLD).x(), plan.marker(BastionPlanner.MARKER_GOLD).y(),
						plan.marker(BastionPlanner.MARKER_GOLD).z())), where + ": ouro escondido");
				long falls = plan.blocks().values().stream().filter(s -> s.equals("minecraft:lava[level=8]")).count();
				assertTrue(falls >= 8, where + ": sem quedas de lava");
				assertTrue(plan.lootTables().values().stream().allMatch(t -> t.startsWith("minecraft:chests/bastion_")), where + ": loot de bastiao");
				switch (type) {
					case BastionPlanner.TREASURE -> {
						assertEquals(1, loot(plan, BastionPlanner.LOOT_TREASURE), where);
						assertTrue(plan.count("magma_block") >= 100, where + ": nucleo de magma");
						assertTrue(plan.count("gold_block") >= 4, where);
						assertEquals(2, plan.spawnerMap().values().stream().filter("minecraft:magma_cube"::equals).count(), where);
						assertTrue(mobs(plan, "minecraft:piglin_brute") >= 2, where);
					}
					case BastionPlanner.STABLES -> {
						assertTrue(loot(plan, BastionPlanner.LOOT_STABLE) >= 2, where);
						assertTrue(mobs(plan, "minecraft:hoglin") >= 8, where + ": poucos hoglins");
					}
					case BastionPlanner.BRIDGE -> assertTrue(loot(plan, BastionPlanner.LOOT_BRIDGE) >= 2, where);
					default -> assertTrue(loot(plan, BastionPlanner.LOOT_OTHER) >= 8, where);
				}
			}
		}
	}

	@Test
	void bastionFitsBetweenNetherFloorAndCeiling() {
		for (int type = 0; type < BastionPlanner.TYPES; type++) {
			int[] b = VKind.BASTION_REMNANT.plan(5, type).bounds();
			assertTrue(b[4] <= 32, "tipo " + type + " alto demais: " + b[4]);
		}
		int[] f = VKind.NETHER_FORTRESS.plan(5, 0).bounds();
		assertTrue(f[4] <= 34, "fortaleza alta demais: " + f[4]);
	}

	@Test
	void sitingUsesTheFloorAndStaysBetweenLavaAndBedrock() {
		int[] flat = new int[25];
		java.util.Arrays.fill(flat, 50);
		assertEquals(50, NetherSiting.originY(VKind.BASTION_REMNANT, flat, -1, 30, 31));
		int[] lava = new int[25];
		java.util.Arrays.fill(lava, 31);
		assertEquals(32, NetherSiting.originY(VKind.NETHER_FORTRESS, lava, 0, 34, 31), "acima do mar de lava");
		int[] high = new int[25];
		java.util.Arrays.fill(high, 100);
		assertEquals(NetherSiting.CEILING - 34, NetherSiting.originY(VKind.NETHER_FORTRESS, high, 0, 34, 31), "topo abaixo da bedrock");
		int[] mixed = new int[25];
		for (int i = 0; i < 25; i++) {
			mixed[i] = 40 + i;
		}
		assertEquals(52, NetherSiting.originY(VKind.BASTION_REMNANT, mixed, -1, 30, 31), "mediana");
		assertTrue(NetherSiting.originY(VKind.NETHER_FORTRESS, mixed, 0, 34, 31) < 52, "fortaleza um pouco abaixo (pontes ja ficam altas)");
		int[][] pts = NetherSiting.samplePoints(new int[] {-10, -20, 10, 20});
		assertEquals(25, pts.length);
		assertEquals(-10, pts[0][0]);
		assertEquals(20, pts[24][1]);
	}

	@Test
	void tilesSplitTheFortressIntoTightBoxes() {
		VPlan plan = VKind.NETHER_FORTRESS.plan(3, 0);
		Map<Long, int[]> tiles = VanillaRemakePiece.tileBounds(plan);
		assertTrue(tiles.size() > 20, "poucas fatias: " + tiles.size());
		long covered = 0;
		for (Map.Entry<Long, int[]> e : tiles.entrySet()) {
			int[] t = e.getValue();
			covered += (long) (t[3] - t[0] + 1) * (t[5] - t[2] + 1);
			assertTrue(t[3] - t[0] < VanillaRemakePiece.TILE && t[5] - t[2] < VanillaRemakePiece.TILE);
		}
		for (long k : plan.blocks().keySet()) {
			int[] t = tiles.get(VanillaRemakePiece.tileOf(VPlan.keyX(k), VPlan.keyZ(k)));
			assertTrue(t[0] <= VPlan.keyX(k) && VPlan.keyX(k) <= t[3] && t[1] <= VPlan.keyY(k) && VPlan.keyY(k) <= t[4]);
		}
		int[] b = plan.bounds();
		long whole = (long) (b[3] - b[0] + 1) * (b[5] - b[2] + 1);
		// as caixas cobrem bem menos que o retangulo inteiro: monstros da fortaleza so nascem na construcao
		assertTrue(covered < whole * 0.75, "fatias cobrem " + covered + " de " + whole);
	}
}
