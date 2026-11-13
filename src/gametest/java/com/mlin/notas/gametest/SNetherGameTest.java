package com.mlin.notas.gametest;

import com.mlin.notas.registry.ModStructures;
import com.mlin.notas.vstructures.BastionPlanner;
import com.mlin.notas.vstructures.FortressPlanner;
import com.mlin.notas.vstructures.VKind;
import com.mlin.notas.vstructures.VPlan;
import com.mlin.notas.vstructures.VanillaRemakePiece;
import com.mlin.notas.vstructures.VanillaRemakeStructure;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.Weighted;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * Game tests da fortaleza do Nether e do bastiao refeitos (lote proprio): tipo e spawns do JSON, geradores de blaze,
 * loot vanilla, piglins brutos e hoglins nascendo com o bastiao e o {@code spawn_overrides} da fortaleza valendo so
 * dentro das caixas das pecas (fatias) registradas como uma estrutura de verdade.
 */
public class SNetherGameTest {
	private static final String ENV = "mlinnotas-gametest:snether";

	/** Todas as fatias da estrutura, como a colocacao faz. */
	static List<StructurePiece> pieces(VKind kind, int variant, long seed, BlockPos origin) {
		List<StructurePiece> out = new ArrayList<>();
		VPlan plan = VanillaRemakePiece.planFor(kind, seed, variant);
		for (long tile : VanillaRemakePiece.tileBounds(plan).keySet()) {
			out.add(new VanillaRemakePiece(kind, variant, seed, origin, 0, false, tile));
		}
		return out;
	}

	static BoundingBox box(List<StructurePiece> pieces) {
		BoundingBox box = pieces.getFirst().getBoundingBox();
		for (StructurePiece piece : pieces) {
			box = BoundingBox.encapsulating(box, piece.getBoundingBox());
		}
		return box;
	}

	/** Monta todas as fatias chunk por chunk, como o gerador de mundo faz. */
	static List<StructurePiece> placeWhole(ServerLevel level, VKind kind, int variant, long seed, BlockPos origin) {
		List<StructurePiece> pieces = pieces(kind, variant, seed, origin);
		BoundingBox box = box(pieces);
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				level.setChunkForced(cx, cz, true);
				level.getChunk(cx, cz);
				BoundingBox chunkBB = new BoundingBox(cx << 4, level.getMinY(), cz << 4, (cx << 4) + 15, level.getMaxY(), (cz << 4) + 15);
				for (StructurePiece piece : pieces) {
					if (piece.getBoundingBox().intersects(chunkBB)) {
						piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(),
								RandomSource.create(seed + cx * 31L + cz), chunkBB, new ChunkPos(cx, cz), origin);
					}
				}
			}
		}
		return pieces;
	}

	static void release(ServerLevel level, BoundingBox box) {
		for (Entity e : level.getEntitiesOfClass(Entity.class, AABB.of(box).inflate(4), e -> !(e instanceof Player))) {
			e.discard();
		}
		for (int cx = (box.minX() >> 4) - 1; cx <= (box.maxX() >> 4) + 1; cx++) {
			for (int cz = (box.minZ() >> 4) - 1; cz <= (box.maxZ() >> 4) + 1; cz++) {
				level.setChunkForced(cx, cz, false);
			}
		}
	}

	private static Structure structure(ServerLevel level, String id) {
		return level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValueOrThrow(ResourceKey.create(Registries.STRUCTURE,
				Identifier.withDefaultNamespace(id)));
	}

	private static long count(ServerLevel level, BoundingBox box, EntityType<?> type) {
		return level.getEntities(type, AABB.of(box).inflate(2), e -> true).size();
	}

	private static boolean hasMob(List<Weighted<MobSpawnSettings.SpawnerData>> list, EntityType<?> type) {
		return list.stream().anyMatch(w -> w.value().type() == type);
	}

	@GameTest(environment = ENV)
	public void netherStructuresUseTheRemakeType(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		for (String id : new String[] {"fortress", "bastion_remnant"}) {
			Structure s = structure(level, id);
			helper.assertTrue(s.type() == ModStructures.VANILLA_REMAKE, "minecraft:" + id + " usa o tipo refeito, achou " + s.type());
		}
		helper.assertTrue(((VanillaRemakeStructure) structure(level, "fortress")).kind() == VKind.NETHER_FORTRESS, "kind da fortaleza");
		helper.assertTrue(((VanillaRemakeStructure) structure(level, "bastion_remnant")).kind() == VKind.BASTION_REMNANT, "kind do bastiao");
		StructureSpawnOverride monsters = structure(level, "fortress").spawnOverrides().get(MobCategory.MONSTER);
		helper.assertTrue(monsters != null && monsters.boundingBox() == StructureSpawnOverride.BoundingBoxType.PIECE, "spawns da fortaleza por peca");
		helper.assertTrue(hasMob(monsters.spawns().unwrap(), EntityTypes.BLAZE) && hasMob(monsters.spawns().unwrap(), EntityTypes.WITHER_SKELETON),
				"blazes e esqueletos wither na fortaleza");
		helper.succeed();
	}

	@GameTest(environment = ENV, maxTicks = 200)
	public void fortressHasBlazeSpawnersAndBridgeLoot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = new BlockPos(40000, 0, 40000);
		List<StructurePiece> pieces = placeWhole(level, VKind.NETHER_FORTRESS, 0, 11L, origin);
		VanillaRemakePiece piece = (VanillaRemakePiece) pieces.getFirst();
		VPlan plan = piece.plan();
		helper.runAtTickTime(5, () -> {
			int blazes = 0;
			for (long k : plan.spawnerMap().keySet()) {
				BlockPos at = piece.world(VPlan.keyX(k), VPlan.keyY(k), VPlan.keyZ(k));
				if (level.getBlockEntity(at) instanceof SpawnerBlockEntity spawner
						&& spawner.getSpawner().getOrCreateDisplayEntity(level, at) instanceof net.minecraft.world.entity.monster.Blaze) {
					blazes++;
				}
			}
			long chests = plan.lootTables().keySet().stream().filter(k -> level.getBlockEntity(piece.world(VPlan.keyX(k), VPlan.keyY(k), VPlan.keyZ(k)))
					instanceof ChestBlockEntity c && c.getLootTable() != null && c.getLootTable().identifier().getPath().equals("chests/nether_bridge")).count();
			BlockPos throne = piece.world(plan.marker(FortressPlanner.MARKER_THRONE));
			boolean bricks = level.getBlockState(piece.world(0, FortressPlanner.DECK - 1, 30)).is(Blocks.NETHER_BRICKS)
					|| level.getBlockState(piece.world(0, FortressPlanner.DECK - 1, 30)).is(Blocks.CRACKED_NETHER_BRICKS)
					|| level.getBlockState(piece.world(0, FortressPlanner.DECK - 1, 30)).is(Blocks.RED_NETHER_BRICKS);
			long withers = count(level, box(pieces), EntityTypes.WITHER_SKELETON);
			boolean throneOk = level.getBlockState(throne).is(Blocks.RED_NETHER_BRICK_STAIRS);
			release(level, box(pieces));
			helper.assertTrue(blazes == plan.spawnerMap().size() && blazes >= 2, "geradores de blaze: " + blazes + " de " + plan.spawnerMap().size());
			helper.assertTrue(chests == plan.lootTables().size(), "baus com loot nether_bridge: " + chests + " de " + plan.lootTables().size());
			helper.assertTrue(bricks, "tabuleiro da ponte de tijolo do Nether");
			helper.assertTrue(withers >= 6, "esqueletos wither de patrulha: " + withers);
			helper.assertTrue(throneOk, "trono");
			helper.succeed();
		});
	}

	@GameTest(environment = ENV, maxTicks = 200)
	public void fortressSpawnOverridesApplyOnlyInsidePieces(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = new BlockPos(40400, 0, 40000);
		Structure fortress = structure(level, "fortress");
		List<StructurePiece> pieces = placeWhole(level, VKind.NETHER_FORTRESS, 0, 12L, origin);
		BoundingBox whole = box(pieces);
		ChunkPos startChunk = new ChunkPos(origin.getX() >> 4, origin.getZ() >> 4);
		StructureStart start = new StructureStart(fortress, startChunk, 0, new PiecesContainer(pieces));
		level.getChunk(startChunk.x(), startChunk.z()).setStartForStructure(fortress, start);
		for (int cx = whole.minX() >> 4; cx <= whole.maxX() >> 4; cx++) {
			for (int cz = whole.minZ() >> 4; cz <= whole.maxZ() >> 4; cz++) {
				level.getChunk(cx, cz).addReferenceForStructure(fortress, ChunkPos.pack(startChunk.x(), startChunk.z()));
			}
		}
		VanillaRemakePiece piece = (VanillaRemakePiece) pieces.getFirst();
		// em cima da ponte sul (pes no tabuleiro) e num canto entre dois bracos (dentro da caixa geral, fora das fatias)
		BlockPos found = piece.world(0, FortressPlanner.DECK, 32);
		for (int s = 16; s <= 48; s++) {
			BlockPos at = piece.world(0, FortressPlanner.DECK, s);
			if (level.getBlockState(at.below()).is(Blocks.NETHER_BRICKS)) {
				found = at;
				break;
			}
		}
		BlockPos deck = found;
		BlockPos between = piece.world(40, FortressPlanner.DECK, 40);
		var generator = level.getChunkSource().getGenerator();
		helper.runAtTickTime(5, () -> {
			var onDeck = generator.getMobsAt(level, level.structureManager(), MobCategory.MONSTER, deck).unwrap();
			var outside = generator.getMobsAt(level, level.structureManager(), MobCategory.MONSTER, between).unwrap();
			boolean fortressBounds = NaturalSpawner.isInNetherFortressBounds(deck, level, MobCategory.MONSTER, level.structureManager());
			boolean inWhole = whole.isInside(between);
			boolean inPiece = level.structureManager().structureHasPieceAt(between, start);
			BlockState below = level.getBlockState(deck.below());
			release(level, whole);
			helper.assertTrue(inWhole && !inPiece, "o ponto entre os bracos fica na caixa geral mas fora das fatias");
			helper.assertTrue(hasMob(onDeck, EntityTypes.BLAZE) && hasMob(onDeck, EntityTypes.WITHER_SKELETON), "spawns da fortaleza na ponte: " + onDeck);
			helper.assertTrue(!hasMob(outside, EntityTypes.BLAZE), "sem blazes fora das pecas: " + outside);
			helper.assertTrue(fortressBounds, "inimigos de fortaleza sobre tijolo do Nether (" + below + ")");
			helper.succeed();
		});
	}

	@GameTest(environment = ENV, maxTicks = 200)
	public void bastionTypesSpawnPiglinsBrutesAndHoglins(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<List<StructurePiece>> all = new ArrayList<>();
		for (int type = 0; type < BastionPlanner.TYPES; type++) {
			all.add(placeWhole(level, VKind.BASTION_REMNANT, type, 21L + type, new BlockPos(41000 + type * 200, 0, 40000)));
		}
		helper.runAtTickTime(10, () -> {
			StringBuilder problems = new StringBuilder();
			for (int type = 0; type < BastionPlanner.TYPES; type++) {
				List<StructurePiece> pieces = all.get(type);
				BoundingBox box = box(pieces);
				VanillaRemakePiece piece = (VanillaRemakePiece) pieces.getFirst();
				VPlan plan = piece.plan();
				long brutes = count(level, box, EntityTypes.PIGLIN_BRUTE);
				long piglins = count(level, box, EntityTypes.PIGLIN);
				long hoglins = count(level, box, EntityTypes.HOGLIN);
				long expectedHoglins = plan.entities().stream().filter(s -> s.type().equals("minecraft:hoglin")).count();
				if (brutes < 1 || piglins < 4 || hoglins < expectedHoglins) {
					problems.append("tipo ").append(type).append(": brutos ").append(brutes).append(" piglins ").append(piglins).append(" hoglins ")
							.append(hoglins).append("/").append(expectedHoglins).append("; ");
				}
				long loot = plan.lootTables().keySet().stream().filter(k -> level.getBlockEntity(piece.world(VPlan.keyX(k), VPlan.keyY(k), VPlan.keyZ(k)))
						instanceof ChestBlockEntity c && c.getLootTable() != null && c.getLootTable().identifier().getPath().startsWith("chests/bastion_")).count();
				if (loot != plan.lootTables().size()) {
					problems.append("tipo ").append(type).append(": baus ").append(loot).append("/").append(plan.lootTables().size()).append("; ");
				}
				if (!level.getBlockState(piece.world(plan.marker(BastionPlanner.MARKER_GOLD))).is(Blocks.GOLD_BLOCK)) {
					problems.append("tipo ").append(type).append(": sem ouro escondido; ");
				}
				if (type == BastionPlanner.TREASURE) {
					BlockPos chest = piece.world(plan.marker(BastionPlanner.MARKER_TREASURE));
					if (!(level.getBlockEntity(chest) instanceof ChestBlockEntity c && c.getLootTable() != null
							&& c.getLootTable().identifier().getPath().equals("chests/bastion_treasure"))) {
						problems.append("bau do tesouro; ");
					}
					if (!level.getBlockState(piece.world(plan.marker(BastionPlanner.MARKER_CORE))).is(Blocks.MAGMA_BLOCK)) {
						problems.append("nucleo de magma; ");
					}
				}
				release(level, box);
			}
			helper.assertTrue(problems.isEmpty(), problems.toString());
			helper.succeed();
		});
	}
}
