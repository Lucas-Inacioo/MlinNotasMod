package com.mlin.notas.gametest;

import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.OuterWallPlanner;
import com.mlin.notas.village.RuralRing;
import com.mlin.notas.village.StreetPlanner;
import com.mlin.notas.village.VillageBoardBlock;
import com.mlin.notas.village.VillageBuilder;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillagePlanning;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageTerrain;
import com.mlin.notas.village.WorldTerrain;
import com.mlin.notas.village.Zone;
import com.mlin.notas.village.civic.DefenseServer;
import com.mlin.notas.village.civic.VillageGuard;
import com.mlin.notas.village.works.CrownWorks;
import com.mlin.notas.village.works.VillageWorks;
import com.mlin.notas.village.works.VillageWorksData;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Game tests da fase 8 das Vilas 2.0 (lote {@code mlinnotas-gametest:crown}): o plano ganha o anel rural na Cidade e os
 * tipos rurais vao para os campos sem camas (quem trabalha la dorme dentro da muralha); com o anel aplainado monstro
 * natural nao nasce nos campos, aldeao nos campos volta para dentro de noite, os guardas patrulham ate a cerca viva e as
 * obras poem cerca viva e postes; a muralha externa fecha a linha com portoes nas avenidas, o centro historico abre os
 * portoes da muralha de pedra e a Cidade da Coroa ganha barbacas.
 */
public class CrownGameTest {
	private static final int RUN = (int) (System.currentTimeMillis() / 1000 % 4000) * 600 + 11_000_000;
	private static final int R = 118;

	private static BlockPos ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
	}

	private static void fail(String message) {
		throw new IllegalStateException(message);
	}

	/** Chao plano ate a cerca viva em volta de (x, 0), quadro no meio; vila no nivel com as obras prontas e o anel rural aplainado. */
	private static VillageState village(GameTestHelper helper, int x, VillageLevel lvl, VillageProject... projects) {
		ServerLevel level = helper.getLevel();
		for (int cx = (x - R) >> 4; cx <= (x + R) >> 4; cx++) {
			for (int cz = -R >> 4; cz <= R >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
			}
		}
		int y0 = Math.max(level.getSeaLevel() + 6, ground(level, x, 0).getY()) + 4;
		for (int dx = -R; dx <= R; dx++) {
			for (int dz = -R; dz <= R; dz++) {
				for (int y = y0 - 5; y < y0 + 26; y++) {
					BlockState state = y < y0 - 1 ? Blocks.DIRT.defaultBlockState() : y == y0 - 1 ? Blocks.GRASS_BLOCK.defaultBlockState()
							: Blocks.AIR.defaultBlockState();
					level.setBlock(new BlockPos(x + dx, y, dz), state, 2);
				}
			}
		}
		BlockPos board = ground(level, x, 0);
		VillageBoardBlock.placeAt(level, ModBlocks.VILLAGE_BOARD.defaultBlockState(), board, 3);
		VillageState village = VillageServer.create(level, board, false);
		VillagePlanning.ensureLayout(level, village);
		village.setLevel(lvl);
		for (VillageProject p : projects) {
			village.enqueue(p);
		}
		VillagePlanning.grow(level, village);
		VillageTerrain.prepareOpenRings(level, village, false);
		VillageTerrain.prepareNow(level, village, RuralRing.RING, false);
		VillageServer.completeAll(level.getServer(), village);
		for (VillageProject p : projects) {
			int want = (int) java.util.Arrays.stream(projects).filter(q -> q == p).count();
			if (village.built(p) < want) {
				fail(p + " nao ficou pronto (" + village.built(p) + " de " + want + ")");
			}
		}
		return village;
	}

	private static void release(GameTestHelper helper, VillageState village) {
		ServerLevel level = helper.getLevel();
		AABB box = new AABB(VillageServer.center(village)).inflate(R + 4, 80, R + 4);
		for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, box)) {
			e.discard();
		}
		for (int cx = (village.x() - R) >> 4; cx <= (village.x() + R) >> 4; cx++) {
			for (int cz = -R >> 4; cz <= R >> 4; cz++) {
				level.setChunkForced(cx, cz, false);
			}
		}
		VillageData.get(level.getServer()).remove(village.key());
	}

	private static VillageProject[] with(int houses, VillageProject... more) {
		List<VillageProject> out = new ArrayList<>();
		for (int i = 0; i < houses; i++) {
			out.add(VillageProject.HOUSE);
		}
		out.addAll(List.of(more));
		return out.toArray(new VillageProject[0]);
	}

	/** Uma celula de rua de chao do anel rural (rua do anel ao norte). */
	private static int[] ruralStreet(MasterPlan plan) {
		for (int z = -RuralRing.ROAD + 1; z >= -RuralRing.ROAD - 1; z--) {
			for (int x = -10; x <= 10; x++) {
				if (plan.ground(x, z) && plan.streetKind(x, z) == StreetPlanner.RING) {
					return new int[] {x, z};
				}
			}
		}
		fail("rua do anel rural nao achada");
		return null;
	}

	@GameTest(maxTicks = 600, environment = "mlinnotas-gametest:crown")
	public void ruralFieldsHaveNoBedsAndAreProtected(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState v = village(helper, RUN, VillageLevel.TOWN,
				with(3, VillageProject.FARM, VillageProject.FARM, VillageProject.MILL, VillageProject.ORCHARD, VillageProject.PEN, VillageProject.WALL));
		MasterPlan plan = v.layout().master();
		if (!plan.rural()) {
			fail("a Cidade deveria ter o anel rural no plano");
		}
		// tipos rurais nos campos, sem camas; toda cama da vila fica dentro da muralha externa
		int rural = 0;
		List<BlockPos> beds = new ArrayList<>();
		for (VillageLayout.Lot lot : v.layout().lots()) {
			if (!lot.built()) {
				continue;
			}
			MasterPlan.Parcel p = plan.parcel(lot.parcel());
			boolean fields = p != null && p.zone() == Zone.RURAL;
			if (List.of("mill", "orchard", "pen").contains(lot.kind()) && !fields) {
				fail(lot.kind() + " deveria ficar no anel rural, ficou em " + (p == null ? "?" : p.zone()));
			}
			rural += fields ? 1 : 0;
			int target = p != null ? plan.block(p.block()).target() : v.y() - 1;
			var r = lot.rect();
			for (int x = r.x0(); x <= r.x1(); x++) {
				for (int z = r.z0(); z <= r.z1(); z++) {
					for (int y = target - 2; y <= target + 16; y++) {
						BlockPos pos = VillageBuilder.world(v, x, y, z);
						if (level.getBlockState(pos).getBlock() instanceof BedBlock) {
							beds.add(pos);
							if (fields) {
								fail(lot.kind() + " no anel rural tem cama em " + pos);
							}
						}
					}
				}
			}
		}
		if (rural < 4) {
			fail("obras no anel rural: " + rural);
		}
		if (beds.isEmpty()) {
			fail("nenhuma cama nas casas");
		}
		for (BlockPos bed : beds) {
			if (RuralRing.outsideOld(bed.getX() - v.x(), bed.getZ() - v.z())) {
				fail("cama fora da muralha externa em " + bed);
			}
		}
		// monstro natural nao nasce nos campos aplainados; ovo continua valendo; dentro da muralha (rua) nasce normal
		int[] s = ruralStreet(plan);
		BlockPos field = VillageBuilder.world(v, s[0], plan.streetHeight(s[0], s[1]) + 1, s[1]);
		Zombie natural = EntityTypes.ZOMBIE.spawn(level, field, EntitySpawnReason.NATURAL);
		if (natural != null && !natural.isRemoved()) {
			fail("monstro natural nasceu no anel rural em " + field);
		}
		Zombie egg = EntityTypes.ZOMBIE.spawn(level, field, EntitySpawnReason.SPAWN_ITEM_USE);
		if (egg == null || egg.isRemoved()) {
			fail("ovo de spawn deveria continuar valendo no anel rural");
		}
		egg.discard();
		BlockPos street = VillageServer.center(v).offset(0, 1, -40);
		Zombie inside = EntityTypes.ZOMBIE.spawn(level, street, EntitySpawnReason.NATURAL);
		if (inside == null || inside.isRemoved()) {
			fail("na rua dentro da muralha o monstro nasce normal");
		}
		inside.discard();
		// aldeao nos campos volta para dentro de noite, pelo portao mais perto
		Villager farmer = EntityTypes.VILLAGER.spawn(level, field, EntitySpawnReason.MOB_SUMMONED);
		if (DefenseServer.ruralShelter(level, v, false, false) != 0) {
			fail("de dia ninguem e mandado para dentro");
		}
		if (DefenseServer.ruralShelter(level, v, true, false) < 1) {
			fail("de noite o aldeao dos campos deveria voltar");
		}
		WalkTarget target = farmer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
		if (target == null) {
			fail("aldeao sem destino");
		}
		BlockPos to = target.getTarget().currentBlockPosition();
		if (RuralRing.outsideOld(to.getX() - v.x(), to.getZ() - v.z()) || to.distSqr(field) > 50 * 50) {
			fail("abrigo errado: " + to + " para quem esta em " + field);
		}
		farmer.discard();
		// obras: cerca viva com porteiras e postes de lanterna nos campos
		VillageWorks.runAll(level, v);
		int leaves = 0;
		for (int x = -20; x <= 20; x++) {
			int z = -(RuralRing.HALF + 1);
			int g = WorldTerrain.scan(level, v.x() + x, v.z() + z, false)[0];
			if (level.getBlockState(VillageBuilder.world(v, x, g + 1, z)).is(net.minecraft.tags.BlockTags.LEAVES)) {
				leaves++;
			}
		}
		if (leaves < 20) {
			fail("cerca viva ao norte: " + leaves + " folhas");
		}
		int lit = 0;
		for (int[] spot : CrownWorks.lampSpots(plan)) {
			if (level.getBlockState(VillageBuilder.world(v, spot[0], plan.plannedHeight(spot[0], spot[1]) + 3, spot[1])).is(Blocks.LANTERN)) {
				lit++;
			}
		}
		if (lit < 30) {
			fail("postes de lanterna nos campos: " + lit);
		}
		release(helper, v);
		helper.succeed();
	}

	@GameTest(maxTicks = 600, environment = "mlinnotas-gametest:crown")
	public void outerWallHistoricCentreBarbicansAndPatrols(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		VillageState v = village(helper, RUN + 300, VillageLevel.METROPOLIS,
				with(4, VillageProject.WALL, VillageProject.BARRACKS, VillageProject.OUTER_WALL));
		MasterPlan plan = v.layout().master();
		WorldTerrain terrain = new WorldTerrain(level, VillageServer.center(v));
		// muralha externa: a linha fechada fora dos portoes, passagem aberta nas avenidas
		int y0 = v.y() - 1;
		for (int z : new int[] {12, -12, 38}) {
			for (int y = y0 + 2; y <= y0 + 6; y++) {
				if (level.getBlockState(VillageBuilder.world(v, RuralRing.WALL, y, z)).isAir()) {
					fail("muralha externa aberta em (" + RuralRing.WALL + "," + y + "," + z + ")");
				}
			}
		}
		List<OuterWallPlanner.Gate> gates = OuterWallPlanner.gates(plan, terrain);
		if (gates.size() < 4) {
			fail("portoes da muralha externa: " + gates.size());
		}
		for (OuterWallPlanner.Gate g : gates) {
			for (int[] c : g.cells()) {
				int sh = plan.ground(c[0], c[1]) ? plan.streetHeight(c[0], c[1]) : y0;
				for (int y = sh + 1; y <= sh + 3; y++) {
					BlockState st = level.getBlockState(VillageBuilder.world(v, c[0], y, c[1]));
					if (!st.isAir() && !st.is(Blocks.LANTERN)) {
						fail("portao fechado em " + c[0] + "," + y + "," + c[1] + ": " + st);
					}
				}
			}
		}
		AABB box = new AABB(VillageServer.center(v)).inflate(R, 60, R);
		if (level.getEntitiesOfClass(IronGolem.class, box).size() < 2) {
			fail("a muralha externa traz dois golens");
		}
		// centro historico: as grades dos portoes saem e os pilares do portao viram arco aberto
		VillageWorks.runAll(level, v);
		VillageWorksData.State st = VillageWorksData.get(server).state(v.key());
		if (!st.gates.isEmpty()) {
			fail("as grades dos portoes da muralha de pedra deveriam sair no centro historico");
		}
		int sh = plan.streetHeight(50, 2);
		for (int y = sh + 1; y <= sh + 3; y++) {
			if (!level.getBlockState(VillageBuilder.world(v, 50, y, 2)).isAir()) {
				fail("portao leste da muralha de pedra nao abriu em y=" + y + ": " + level.getBlockState(VillageBuilder.world(v, 50, y, 2)));
			}
		}
		// guardas do quartel patrulham ate a cerca viva
		DefenseServer.guards(server, level, v);
		List<VillageGuard> guards = level.getEntitiesOfClass(VillageGuard.class, box);
		if (guards.isEmpty()) {
			fail("quartel sem guardas");
		}
		for (VillageGuard g : guards) {
			if (g.patrolRadius() < RuralRing.HALF - 6) {
				fail("patrulha nao cobre o anel rural: raio " + g.patrolRadius());
			}
		}
		// Cidade da Coroa: barbacas na frente dos portoes dos eixos
		v.setLevel(VillageLevel.CROWN_CITY);
		VillageWorks.runAll(level, v);
		int barbican = 0;
		for (OuterWallPlanner.Gate g : gates) {
			if (!g.axis()) {
				continue;
			}
			int lx = -g.dz();
			int lz = g.dx();
			for (int side : new int[] {-1, 1}) {
				int x = g.center()[0] + g.dx() * 2 + lx * 4 * side;
				int z = g.center()[1] + g.dz() * 2 + lz * 4 * side;
				if (!level.getBlockState(VillageBuilder.world(v, x, y0 + 5, z)).isAir()) {
					barbican++;
				}
			}
		}
		if (barbican < 6) {
			fail("torres das barbacas: " + barbican);
		}
		release(helper, v);
		helper.succeed();
	}
}
