package com.mlin.notas.gametest;

import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.FieldPins;
import com.mlin.notas.village.HeightField;
import com.mlin.notas.village.HeightFieldBuilder;
import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.VillageBoardBlock;
import com.mlin.notas.village.VillageBuilder;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillagePlanner;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageTerrain;
import com.mlin.notas.village.WallRebuild;
import com.mlin.notas.village.WorldTerrain;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.List;
import java.util.function.IntBinaryOperator;

/**
 * Vilas 3.0 fases 3 e 4 (lote {@code mlinnotas-gametest:v3_terrain}): numa encosta de verdade (terreno sintetico gravado no
 * mundo de teste, plano diretor + campo de alturas calculados sobre ele) a terraplenagem leva cada coluna dos aneis para a
 * altura do campo, as muralhas feitas depois nao ficam enterradas, todo portao tem passagem andavel de 1x2 (4 de ar livre
 * sobre a rua) e {@code /village admin rebuild_walls} refaz uma muralha estragada sem tocar em bloco de jogador.
 */
public class Terrain3GameTest {
	private static final int RUN = (int) (System.currentTimeMillis() / 1000 % 4000) * 400 + 8_000_000;
	private static final int R = 84;

	/** Encosta suave nos dois eixos (relativa ao centro). */
	private static final IntBinaryOperator SLOPE = (dx, dz) -> Math.floorDiv(dx, 8) + Math.floorDiv(dz, 12);

	private record Site(VillageState village, HeightField field, MasterPlan plan, int y0) {
	}

	private static Site site(GameTestHelper helper, int x) {
		ServerLevel level = helper.getLevel();
		for (int cx = (x - R) >> 4; cx <= (x + R) >> 4; cx++) {
			for (int cz = -R >> 4; cz <= R >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		level.getChunk(x >> 4, 0);
		int y0 = Math.max(level.getSeaLevel() + 12, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, 0)).getY()) + 4;
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				int h = y0 + SLOPE.applyAsInt(dx, dz);
				for (int y = y0 - 26; y < y0 + 34; y++) {
					BlockState state = y < h - 1 ? Blocks.DIRT.defaultBlockState() : y == h - 1 ? Blocks.GRASS_BLOCK.defaultBlockState()
							: Blocks.AIR.defaultBlockState();
					level.setBlock(new BlockPos(x + dx, y, dz), state, 2);
				}
			}
		}
		HeightFieldBuilder.Natural natural = (dx, dz) -> y0 + SLOPE.applyAsInt(dx, dz);
		int minY = level.getSeaLevel() + 1;
		int plaza = HeightFieldBuilder.plazaHeight(natural, minY);
		HeightFieldBuilder.Result result = HeightFieldBuilder.forNewVillage(4242L, x, 0, plaza, natural, minY, List.of(), FieldPins.none());
		BlockPos board = new BlockPos(x, plaza + 1, 0);
		VillageBoardBlock.placeAt(level, ModBlocks.VILLAGE_BOARD.defaultBlockState(), board, 3);
		VillageState village = VillageServer.create(level, board, true);
		VillageLayout layout = new VillageLayout();
		village.setLayout(layout);
		layout.ensureRadius(VillageLevel.CAMP.radius());
		layout.setMaster(result.plan());
		VillageData.get(level.getServer()).putField(village.key(), result.field());
		return new Site(village, result.field(), result.plan(), y0);
	}

	private static void release(GameTestHelper helper, Site s) {
		ServerLevel level = helper.getLevel();
		for (int cx = (s.village().x() - R) >> 4; cx <= (s.village().x() + R) >> 4; cx++) {
			for (int cz = -R >> 4; cz <= R >> 4; cz++) {
				level.setChunkForced(cx, cz, false);
			}
		}
		VillageData.get(level.getServer()).remove(s.village().key());
	}

	private static BlockPos at(VillageState v, int x, int y, int z) {
		return new BlockPos(v.x() + x, y, v.z() + z);
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw new IllegalStateException(message);
		}
	}

	private static void buildWall(ServerLevel level, VillageState village, VillageProject project) {
		VillagePlanner.Planned planned = VillagePlanner.plan(project, village, new WorldTerrain(level, VillageServer.center(village), true), 99L);
		check(planned != null && !planned.placements().isEmpty(), "plano da muralha " + project);
		VillageData.Job job = VillageData.Job.create(village.key(), planned);
		VillageBuilder.runJob(level, village, job, true);
		VillageData.get(level.getServer()).putRecord(village.key(), WallRebuild.recordKey(project), job.buildRecord());
		village.addBuilt(project);
	}

	/** Passagem de 1x2 em toda celula de portao: chao firme no nivel e 4 de ar livre por cima. */
	private static void checkGates(ServerLevel level, Site s) {
		int cells = 0;
		for (HeightField.Gate g : s.field().gates()) {
			for (int x = g.x0(); x <= g.x1(); x++) {
				for (int z = g.z0(); z <= g.z1(); z++) {
					if (!s.field().kindRel(x, z).gate()) {
						continue;
					}
					int lv = g.level();
					check(!level.getBlockState(at(s.village(), x, lv, z)).isAir(), "sem chao no portao em " + x + "," + z);
					for (int y = lv + 1; y <= lv + 4; y++) {
						BlockState b = level.getBlockState(at(s.village(), x, y, z));
						check(b.isAir() || b.getCollisionShape(level, at(s.village(), x, y, z)).isEmpty(), "portao tapado em " + x + "," + y + "," + z + ": " + b);
					}
					cells++;
				}
			}
		}
		check(cells >= 40, "celulas de portao conferidas: " + cells);
	}

	/** Topo de cada coluna da muralha de pedra pelo menos 4 acima do maior chao do campo dos dois lados. */
	private static int checkStoneWallHeights(ServerLevel level, Site s) {
		int checked = 0;
		for (HeightField.WallPoint p : s.field().wallPoints(HeightField.Wall.STONE)) {
			if (p.gate()) {
				continue;
			}
			BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, at(s.village(), p.x(), 0, p.z()));
			int high = Math.max(p.ground(), Math.max(p.inner(), p.outer()));
			BlockState below = level.getBlockState(top.below());
			var pal = com.mlin.notas.village.VillagePalette.of(s.village().style());
			var b = below.getBlock();
			boolean wallBlock = b == pal.foundation || b == pal.stone || b == pal.stoneAlt || b == pal.brick || b == pal.stoneSlab || below.is(Blocks.LANTERN);
			if (!wallBlock) {
				continue; // coluna que ficou aberta (rua, jogador)
			}
			check(top.getY() - 1 >= high + 4, "muralha enterrada em " + p.x() + "," + p.z() + ": topo " + (top.getY() - 1) + " chao " + high);
			checked++;
		}
		return checked;
	}

	@GameTest(maxTicks = 3000, environment = "mlinnotas-gametest:v3_terrain")
	public void ringJobOnASlopeReachesTheFieldHeights(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Site s = site(helper, 90_000 + RUN);
		VillageState village = s.village();
		for (int ring = 1; ring <= 3; ring++) {
			VillageTerrain.prepareNow(level, village, ring, false);
			int columns = 0;
			for (long col : s.plan().columns(ring)) {
				int x = VillageLayout.colX(col);
				int z = VillageLayout.colZ(col);
				int target = s.field().heightRel(x, z);
				check(s.plan().plannedHeight(x, z) == target, "plano diferente do campo em " + x + "," + z);
				int[] scan = WorldTerrain.scan(level, village.x() + x, village.z() + z, false);
				boolean stake = scan[0] == target + 1 && level.getBlockState(at(village, x, target + 1, z)).is(net.minecraft.tags.BlockTags.FENCES);
				check(scan[2] == 0 && (scan[0] == target || stake), "anel " + ring + " coluna " + x + "," + z + " no y " + scan[0] + ", campo " + target);
				check(!level.getBlockState(at(village, x, target - 1, z)).isAir(), "chao solto em " + x + "," + z);
				columns++;
			}
			check(columns > 100 && village.ring(ring).done(), "anel " + ring + " pronto com " + columns + " colunas");
		}
		release(helper, s);
		helper.succeed();
	}

	@GameTest(maxTicks = 6000, environment = "mlinnotas-gametest:v3_terrain")
	public void wallsOnASlopeAreNotBuriedAndEveryGateIsPassable(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Site s = site(helper, 91_000 + RUN);
		VillageState village = s.village();
		for (int ring = 1; ring <= 6; ring++) {
			VillageTerrain.prepareNow(level, village, ring, false);
		}
		buildWall(level, village, VillageProject.WALL);
		buildWall(level, village, VillageProject.OUTER_WALL);
		int checked = checkStoneWallHeights(level, s);
		check(checked > 150, "colunas da muralha de pedra conferidas: " + checked);
		checkGates(level, s);
		release(helper, s);
		helper.succeed();
	}

	@GameTest(maxTicks = 8000, environment = "mlinnotas-gametest:v3_terrain")
	public void rebuildWallsRestoresTheWallAndKeepsPlayerBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Site s = site(helper, 92_000 + RUN);
		VillageState village = s.village();
		for (int ring = 1; ring <= 6; ring++) {
			VillageTerrain.prepareNow(level, village, ring, false);
		}
		buildWall(level, village, VillageProject.WALL);
		// estraga um portao (tira o arco e enche a passagem) e poe um bloco de jogador ao lado da muralha
		HeightField.Gate gate = s.field().gates().stream().filter(g -> g.wall() == HeightField.Wall.STONE).findFirst().orElseThrow();
		for (int x = gate.x0(); x <= gate.x1(); x++) {
			for (int z = gate.z0(); z <= gate.z1(); z++) {
				if (s.field().kindRel(x, z).gate()) {
					level.setBlock(at(village, x, gate.level() + 2, z), Blocks.COBBLESTONE.defaultBlockState(), 2);
					level.setBlock(at(village, x, gate.level() + 5, z), Blocks.AIR.defaultBlockState(), 2);
				}
			}
		}
		int px = gate.cx() + 6 * Integer.signum(gate.cx());
		int pz = gate.cz() + 6 * Integer.signum(gate.cz());
		BlockPos player = at(village, px, s.field().heightRel(px, pz) + 1, pz);
		level.setBlock(player, Blocks.OAK_PLANKS.defaultBlockState(), 2);
		WallRebuild.Report report = WallRebuild.run(level, village);
		check(report != null && report.placed() > 100, "relatorio da reconstrucao: " + report);
		check(level.getBlockState(player).is(Blocks.OAK_PLANKS), "o bloco do jogador sumiu");
		checkGates(level, s);
		check(checkStoneWallHeights(level, s) > 150, "muralha refeita com colunas demais abertas");
		release(helper, s);
		helper.succeed();
	}
}
