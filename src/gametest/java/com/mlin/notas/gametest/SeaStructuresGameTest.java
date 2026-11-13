package com.mlin.notas.gametest;

import com.mlin.notas.registry.ModStructures;
import com.mlin.notas.util.Pos;
import com.mlin.notas.vstructures.BuriedTreasurePlanner;
import com.mlin.notas.vstructures.IglooPlanner;
import com.mlin.notas.vstructures.MonumentPlanner;
import com.mlin.notas.vstructures.SwampHutPlanner;
import com.mlin.notas.vstructures.VKind;
import com.mlin.notas.vstructures.VPlan;
import com.mlin.notas.vstructures.VanillaRemakePiece;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.feline.CatVariants;
import net.minecraft.world.entity.monster.ElderGuardian;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Game tests das estruturas do mar e pequenas refeitas (lote proprio): cada uma e montada inteira longe da area dos
 * testes, com um nivel do mar de mentira acima dela para o ar virar agua (o mundo de teste e plano, sem mar).
 */
public class SeaStructuresGameTest {
	private static final String ENV = "mlinnotas-gametest:socean";
	static final String[] IDS = {"monument", "shipwreck", "shipwreck_beached", "ocean_ruin_warm", "ocean_ruin_cold", "buried_treasure", "igloo",
			"swamp_hut", "ruined_portal", "ruined_portal_desert", "ruined_portal_jungle", "ruined_portal_swamp", "ruined_portal_mountain",
			"ruined_portal_ocean", "ruined_portal_nether", "trail_ruins"};

	static VanillaRemakePiece placeWhole(ServerLevel level, VKind kind, int variant, long seed, BlockPos origin, int seaLevel) {
		VanillaRemakePiece piece = new VanillaRemakePiece(kind, variant, seed, origin, 0, false);
		BoundingBox box = piece.getBoundingBox();
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				level.setChunkForced(cx, cz, true);
				level.getChunk(cx, cz);
				BoundingBox chunkBB = new BoundingBox(cx << 4, level.getMinY(), cz << 4, (cx << 4) + 15, level.getMaxY(), (cz << 4) + 15);
				piece.placeIn(level, RandomSource.create(seed + cx * 31L + cz), chunkBB, new ChunkPos(cx, cz), seaLevel);
			}
		}
		return piece;
	}

	static void release(ServerLevel level, VanillaRemakePiece piece) {
		BoundingBox box = piece.getBoundingBox();
		for (Entity e : level.getEntitiesOfClass(Entity.class, AABB.of(box).inflate(4), e -> !(e instanceof Player))) {
			e.discard();
		}
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				level.setChunkForced(cx, cz, false);
			}
		}
	}

	private static boolean lootIs(ServerLevel level, BlockPos pos, String table) {
		return level.getBlockEntity(pos) instanceof ChestBlockEntity c && c.getLootTable() != null
				&& c.getLootTable().identifier().toString().equals(table);
	}

	private static long chestsWith(ServerLevel level, VanillaRemakePiece piece, String table) {
		return piece.plan().lootTables().keySet().stream()
				.filter(k -> lootIs(level, piece.world(VPlan.keyX(k), VPlan.keyY(k), VPlan.keyZ(k)), table)).count();
	}

	@GameTest(environment = ENV)
	public void seaStructuresUseTheRemakeType(GameTestHelper helper) {
		var structures = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		for (String id : IDS) {
			Structure s = structures.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace(id))).value();
			helper.assertTrue(s.type() == ModStructures.VANILLA_REMAKE, "minecraft:" + id + " usa o tipo refeito, achou " + s.type());
		}
		for (String table : new String[] {"monument_treasury", "witch_hut"}) {
			var key = ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("mlinnotas", "chests/" + table));
			helper.assertTrue(helper.getLevel().getServer().reloadableRegistries().getLootTable(key) != LootTable.EMPTY, "loot mlinnotas:chests/" + table);
		}
		helper.succeed();
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void monumentIsFloodedWithGoldAndElderGuardians(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = new BlockPos(30000, -55, 34000);
		VanillaRemakePiece piece = placeWhole(level, VKind.MONUMENT, 0, 11L, origin, origin.getY() + 40);
		helper.runAtTickTime(10, () -> {
			VPlan plan = piece.plan();
			long gold = plan.positionsOf("gold_block").stream().filter(p -> level.getBlockState(piece.world(p)).is(Blocks.GOLD_BLOCK)).count();
			Pos core = plan.marker(MonumentPlanner.MARKER_CORE);
			BlockState aboveCore = level.getBlockState(piece.world(core.x(), core.y() + 2, core.z()));
			Pos pent = plan.marker(MonumentPlanner.MARKER_PENTHOUSE);
			BlockState penthouse = level.getBlockState(piece.world(pent));
			long sponges = plan.positionsOf("wet_sponge").stream().filter(p -> level.getBlockState(piece.world(p)).is(Blocks.WET_SPONGE)).count();
			List<ElderGuardian> elders = level.getEntitiesOfClass(ElderGuardian.class, AABB.of(piece.getBoundingBox()).inflate(2));
			long treasury = chestsWith(level, piece, MonumentPlanner.TREASURY);
			release(level, piece);
			helper.assertTrue(gold == 8, "8 blocos de ouro: " + gold);
			helper.assertTrue(aboveCore.is(Blocks.WATER), "nucleo alagado: " + aboveCore);
			helper.assertTrue(penthouse.is(Blocks.WATER), "cobertura alagada: " + penthouse);
			helper.assertTrue(sponges >= 10, "esponjas molhadas: " + sponges);
			helper.assertTrue(elders.size() == 3, "3 guardioes ancestrais: " + elders.size());
			helper.assertTrue(elders.stream().allMatch(e -> e.isPersistenceRequired() && e.isInWater()), "persistentes e na agua");
			helper.assertTrue(treasury >= 2, "baus da tesouraria: " + treasury);
			helper.succeed();
		});
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void iglooHasTheHiddenLabWithClericAndZombieVillager(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = new BlockPos(30200, -40, 34000);
		VanillaRemakePiece piece = placeWhole(level, VKind.IGLOO, 0, 4L, origin, -100);
		helper.runAtTickTime(10, () -> {
			VPlan plan = piece.plan();
			Pos t = plan.marker(IglooPlanner.MARKER_TRAPDOOR);
			BlockState trapdoor = level.getBlockState(piece.world(t));
			BlockState carpet = level.getBlockState(piece.world(t.x(), t.y() + 1, t.z()));
			Pos lab = plan.marker(IglooPlanner.MARKER_LAB);
			BlockState stand = level.getBlockState(piece.world(lab));
			long chests = chestsWith(level, piece, IglooPlanner.CHEST);
			AABB area = AABB.of(piece.getBoundingBox()).inflate(2);
			List<Villager> villagers = level.getEntitiesOfClass(Villager.class, area);
			boolean cleric = villagers.stream().anyMatch(v -> v.getVillagerData().profession().is(VillagerProfession.CLERIC));
			int zombies = level.getEntitiesOfClass(ZombieVillager.class, area).size();
			release(level, piece);
			helper.assertTrue(trapdoor.is(Blocks.SPRUCE_TRAPDOOR), "alcapao: " + trapdoor);
			helper.assertTrue(carpet.is(Blocks.CARPET.white()), "tapete por cima: " + carpet);
			helper.assertTrue(stand.is(Blocks.BREWING_STAND), "suporte de pocoes no laboratorio: " + stand);
			helper.assertTrue(chests == 1, "bau do iglu");
			helper.assertTrue(cleric, "clerigo preso: " + villagers.size());
			helper.assertTrue(zombies == 1, "aldeao zumbi: " + zombies);
			helper.succeed();
		});
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void swampHutSpawnsTheWitchAndABlackCat(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = new BlockPos(30400, -60, 34000);
		VanillaRemakePiece piece = placeWhole(level, VKind.SWAMP_HUT, 0, 8L, origin, -100);
		helper.runAtTickTime(10, () -> {
			AABB area = AABB.of(piece.getBoundingBox()).inflate(2);
			int witches = level.getEntitiesOfClass(Witch.class, area).size();
			List<Cat> cats = level.getEntitiesOfClass(Cat.class, area);
			boolean black = cats.stream().anyMatch(c -> c.get(DataComponents.CAT_VARIANT).is(CatVariants.ALL_BLACK));
			Pos c = piece.plan().marker(SwampHutPlanner.MARKER_CAULDRON);
			BlockState cauldron = level.getBlockState(piece.world(c));
			long chests = chestsWith(level, piece, SwampHutPlanner.CHEST);
			release(level, piece);
			helper.assertTrue(witches == 1, "bruxa: " + witches);
			helper.assertTrue(black, "gato preto: " + cats.size());
			helper.assertTrue(cauldron.is(Blocks.WATER_CAULDRON), "caldeirao: " + cauldron);
			helper.assertTrue(chests == 1, "bau da bruxa");
			helper.succeed();
		});
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void shipwreckAndBuriedTreasureKeepTheMapChests(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos shipOrigin = new BlockPos(30600, -55, 34000);
		VanillaRemakePiece ship = placeWhole(level, VKind.SHIPWRECK, 0, 21L, shipOrigin, shipOrigin.getY() + 30);
		BlockPos treasureOrigin = new BlockPos(30800, -55, 34000);
		VanillaRemakePiece treasure = placeWhole(level, VKind.BURIED_TREASURE, 0, 3L, treasureOrigin, -100);
		helper.runAtTickTime(10, () -> {
			long map = chestsWith(level, ship, "minecraft:chests/shipwreck_map");
			long supply = chestsWith(level, ship, "minecraft:chests/shipwreck_supply");
			long gold = chestsWith(level, ship, "minecraft:chests/shipwreck_treasure");
			BlockPos chest = treasureOrigin.offset(0, BuriedTreasurePlanner.CHEST_Y, 0);
			boolean buried = lootIs(level, chest, BuriedTreasurePlanner.TREASURE);
			boolean covered = !level.getBlockState(chest.above()).isAir();
			BlockState hull = level.getBlockState(ship.world(0, 0, 0));
			com.mlin.notas.util.Pos cabin = ship.plan().marker(com.mlin.notas.vstructures.ShipwreckPlanner.MARKER_CABIN);
			BlockState cabinWater = level.getBlockState(ship.world(cabin.x(), cabin.y() + 1, cabin.z() + 2));
			release(level, ship);
			release(level, treasure);
			helper.assertTrue(map == 1 && supply >= 1 && gold == 1, "baus do naufragio: mapa " + map + ", suprimentos " + supply + ", tesouro " + gold);
			helper.assertTrue(buried && covered, "bau enterrado na origem");
			helper.assertTrue(!hull.isAir(), "porao alagado ou casco: " + hull);
			helper.assertTrue(cabinWater.is(Blocks.WATER), "cabine alagada: " + cabinWater);
			helper.succeed();
		});
	}
}
