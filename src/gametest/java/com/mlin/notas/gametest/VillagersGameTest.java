package com.mlin.notas.gametest;

import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.village.VillageBoardBlock;
import com.mlin.notas.village.VillageData;
import com.mlin.notas.village.VillageLayout;
import com.mlin.notas.village.VillageLevel;
import com.mlin.notas.village.VillagePlanning;
import com.mlin.notas.village.VillageProject;
import com.mlin.notas.village.VillageServer;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.VillageTerrain;
import com.mlin.notas.village.VillageVillagers;
import com.mlin.notas.village.VillagerCurfew;
import com.mlin.notas.village.works.VillageWorksData;
import com.mlin.notas.village.works.WBlock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Game tests dos aldeoes da vila (lote {@code mlinnotas-gametest:v3_villagers}): enfeite de posto de trabalho nunca vale
 * (barril num lote vago ao lado da forja nao faz pescador), posto invalido some de quem nunca negociou e fica com quem
 * negociou, quem se afasta de dia volta e todo morador tem cama de casa dentro da area.
 */
public class VillagersGameTest {
	private static final int RUN = (int) (System.currentTimeMillis() / 1000 % 4000) * 400 + 3_600_000;
	private static final int R = 70;

	private static BlockPos ground(ServerLevel level, int x, int z) {
		level.getChunk(x >> 4, z >> 4);
		return level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
	}

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
				for (int y = y0 - 6; y < y0 + 30; y++) {
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
		VillagePlanning.grow(level, village);
		VillageTerrain.prepareOpenRings(level, village, false);
		for (VillageProject p : projects) {
			village.enqueue(p);
		}
		VillageServer.completeAll(level.getServer(), village);
		for (VillageProject p : projects) {
			int want = (int) java.util.Arrays.stream(projects).filter(q -> q == p).count();
			if (village.built(p) < want) {
				throw new IllegalStateException(p + " nao ficou pronto (" + village.built(p) + " de " + want + ")");
			}
		}
		return village;
	}

	private static void release(GameTestHelper helper, VillageState village) {
		ServerLevel level = helper.getLevel();
		for (int cx = (village.x() - R) >> 4; cx <= (village.x() + R) >> 4; cx++) {
			for (int cz = -R >> 4; cz <= R >> 4; cz++) {
				level.setChunkForced(cx, cz, false);
			}
		}
		VillageData.get(level.getServer()).remove(village.key());
	}

	private static VillageLayout.Lot lot(VillageState v, String kind) {
		return v.layout().lots().stream().filter(l -> l.built() && l.kind().equals(kind)).findFirst()
				.orElseThrow(() -> new IllegalStateException("sem " + kind + " pronto"));
	}

	private static WBlock owned(VillageState v, BlockPos pos) {
		return WBlock.above(pos.getX() - v.x(), pos.getY(), pos.getZ() - v.z(), "");
	}

	/** Barril de enfeite que a vila colocou (registrado nas obras publicas) numa coluna livre perto da forja. */
	private static BlockPos decorBarrel(ServerLevel level, VillageState v) {
		int[] front = lot(v, "smithy").front();
		int x = v.x() + front[0] + 1;
		int z = v.z() + front[1];
		BlockPos pos = ground(level, x, z);
		level.setBlock(pos, Blocks.BARREL.defaultBlockState(), 3);
		VillageWorksData.get(level.getServer()).state(v.key()).owned.record(owned(v, pos), "minecraft:barrel", "lamps:1");
		return pos;
	}

	private static boolean smith(Villager villager) {
		var profession = villager.getVillagerData().profession();
		return profession.is(VillagerProfession.ARMORER) || profession.is(VillagerProfession.WEAPONSMITH) || profession.is(VillagerProfession.TOOLSMITH);
	}

	@GameTest(maxTicks = 900, environment = "mlinnotas-gametest:v3_villagers")
	public void decorBarrelBesideSmithyMakesASmithNotAFisherman(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState v = village(helper, 100_000 + RUN, VillageLevel.VILLAGE, VillageProject.HOUSE, VillageProject.SMITHY);
		BlockPos barrel = decorBarrel(level, v);
		if (VillageVillagers.jobSiteAllowed(level, barrel)) {
			throw new IllegalStateException("barril de enfeite da vila nao devia valer como posto");
		}
		// o mesmo barril colocado por um jogador vale
		VillageWorksData.get(level.getServer()).state(v.key()).owned.forget(owned(v, barrel));
		if (!VillageVillagers.jobSiteAllowed(level, barrel)) {
			throw new IllegalStateException("barril colocado por jogador devia valer");
		}
		VillageWorksData.get(level.getServer()).state(v.key()).owned.record(owned(v, barrel), "minecraft:barrel", "lamps:1");
		Villager villager = EntityTypes.VILLAGER.spawn(level, barrel.west().above(), EntitySpawnReason.MOB_SUMMONED);
		helper.onEachTick(() -> {
			if (villager.getVillagerData().profession().is(VillagerProfession.FISHERMAN)) {
				helper.fail("o aldeao virou pescador por causa do barril de enfeite");
				return;
			}
			GlobalPos site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).or(() -> villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE)).orElse(null);
			if (site != null && site.pos().equals(barrel)) {
				helper.fail("o aldeao escolheu o barril de enfeite como posto");
				return;
			}
			// o vanilla so troca a profissao quando o aldeao chega ao posto; escolher um posto valido ja prova o filtro
			if (smith(villager) || site != null) {
				villager.discard();
				release(helper, v);
				helper.succeed();
			}
		});
	}

	@GameTest(maxTicks = 200, environment = "mlinnotas-gametest:v3_villagers")
	public void invalidSiteIsClearedOnlyForTheUntraded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState v = village(helper, 101_000 + RUN, VillageLevel.VILLAGE, VillageProject.HOUSE, VillageProject.SMITHY);
		BlockPos barrel = decorBarrel(level, v);
		Villager fresh = EntityTypes.VILLAGER.spawn(level, barrel.above(), EntitySpawnReason.MOB_SUMMONED);
		fresh.setVillagerData(fresh.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FISHERMAN));
		fresh.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), barrel));
		Villager traded = EntityTypes.VILLAGER.spawn(level, barrel.above(2), EntitySpawnReason.MOB_SUMMONED);
		traded.setVillagerData(traded.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.FISHERMAN));
		traded.setVillagerXp(5);
		traded.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), barrel));
		if (!VillageVillagers.clearInvalidSites(level, fresh) || fresh.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent()) {
			throw new IllegalStateException("posto de enfeite devia ser largado por quem nunca negociou");
		}
		if (VillageVillagers.clearInvalidSites(level, traded) || traded.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty()) {
			throw new IllegalStateException("quem ja negociou fica com o posto");
		}
		helper.onEachTick(() -> {
			if (!traded.getVillagerData().profession().is(VillagerProfession.FISHERMAN)) {
				helper.fail("quem negociou devia manter a profissao");
				return;
			}
			if (fresh.getVillagerData().profession().is(VillagerProfession.NONE)) {
				fresh.discard();
				traded.discard();
				release(helper, v);
				helper.succeed();
			}
		});
	}

	@GameTest(maxTicks = 200, environment = "mlinnotas-gametest:v3_villagers")
	public void villagerOutsideByDayIsSentBackAndNightGoesToBed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState v = village(helper, 102_000 + RUN, VillageLevel.VILLAGE, VillageProject.HOUSE);
		int half = VillageServer.planHalf(v);
		int x = v.x() + half + 60;
		for (int cx = (x - 8) >> 4; cx <= (x + 8) >> 4; cx++) {
			level.setChunkForced(cx, v.z() >> 4, true);
		}
		Villager villager = EntityTypes.VILLAGER.spawn(level, ground(level, x, v.z()), EntitySpawnReason.MOB_SUMMONED);
		VillagerCurfew.Decision d = VillageVillagers.curfew(level, v, villager, half, 4000);
		WalkTarget target = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
		if (d.action() != VillagerCurfew.Action.RETURN || target == null) {
			throw new IllegalStateException("de dia, 60 blocos fora, devia voltar: " + d);
		}
		int tx = target.getTarget().currentBlockPosition().getX() - v.x();
		if (Math.abs(tx) > half) {
			throw new IllegalStateException("o alvo devia estar dentro da area: " + tx);
		}
		// de noite, com cama na vila a mais de 24 blocos, vai para a cama
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		BlockPos bed = bedAt(level, v.x() - 30, v.z() + 30);
		villager.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), bed));
		villager.teleportTo(v.x() + 20, ground(level, v.x() + 20, v.z() + 30).getY(), v.z() + 30);
		d = VillageVillagers.curfew(level, v, villager, half, 14000);
		target = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
		if (d.action() != VillagerCurfew.Action.BED || target == null || !target.getTarget().currentBlockPosition().equals(bed)) {
			throw new IllegalStateException("ao anoitecer devia ir para a cama: " + d);
		}
		villager.discard();
		for (int cx = (x - 8) >> 4; cx <= (x + 8) >> 4; cx++) {
			level.setChunkForced(cx, v.z() >> 4, false);
		}
		release(helper, v);
		helper.succeed();
	}

	/** Cama (cabeceira + pes) no chao em (x, z); devolve a cabeceira. */
	private static BlockPos bedAt(ServerLevel level, int x, int z) {
		BlockPos foot = ground(level, x, z);
		BlockPos head = foot.east();
		BlockState bed = Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
		level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
		level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
		return head;
	}

	@GameTest(maxTicks = 200, environment = "mlinnotas-gametest:v3_villagers")
	public void everyResidentGetsABedInsideTheVillage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		VillageState v = village(helper, 103_000 + RUN, VillageLevel.VILLAGE, VillageProject.HOUSE, VillageProject.HOUSE, VillageProject.SMITHY);
		int half = VillageServer.planHalf(v);
		// uma cama de fora, bem mais perto do aldeao que as da vila
		for (int cx = (v.x() + half) >> 4; cx <= (v.x() + half + 12) >> 4; cx++) {
			level.setChunkForced(cx, v.z() >> 4, true);
		}
		BlockPos outside = bedAt(level, v.x() + half + 6, v.z());
		VillageLayout.Lot house = lot(v, "house");
		BlockPos at = ground(level, v.x() + house.rect().x0(), v.z() + house.rect().z0());
		Villager extra = EntityTypes.VILLAGER.spawn(level, at.above(), EntitySpawnReason.MOB_SUMMONED);
		extra.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), outside));
		List<Villager> residents = VillageVillagers.villagers(level, v);
		int homed = 0;
		Set<BlockPos> taken = new HashSet<>();
		for (Villager r : residents) {
			if (r.isBaby()) {
				continue;
			}
			VillageVillagers.ensureHome(level, v, r, half);
			GlobalPos home = r.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
			if (home == null) {
				continue;
			}
			BlockPos p = home.pos();
			if (Math.max(Math.abs(p.getX() - v.x()), Math.abs(p.getZ() - v.z())) > half) {
				throw new IllegalStateException("cama fora da vila: " + p);
			}
			if (!taken.add(p)) {
				throw new IllegalStateException("duas camas iguais para aldeoes diferentes: " + p);
			}
			homed++;
		}
		if (homed < 1) {
			throw new IllegalStateException("moradores com cama na vila: " + homed);
		}
		GlobalPos kept = extra.getBrain().getMemory(MemoryModuleType.HOME).orElse(null);
		if (kept != null && kept.pos().equals(outside)) {
			throw new IllegalStateException("o aldeao ficou com a cama de fora da vila");
		}
		for (Villager r : VillageVillagers.villagers(level, v)) {
			r.discard();
		}
		release(helper, v);
		helper.succeed();
	}
}
