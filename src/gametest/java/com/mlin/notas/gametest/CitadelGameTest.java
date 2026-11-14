package com.mlin.notas.gametest;

import com.mlin.notas.boss.BossRegistry;
import com.mlin.notas.end.CitadelPiece;
import com.mlin.notas.end.CitadelPlan;
import com.mlin.notas.end.CitadelPlanner;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.util.Pos;
import com.mlin.notas.vstructures.VanillaRemakePiece;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Game tests da Cidadela do End (lote proprio): estados validos e a cidadela inteira montada longe dos outros testes. */
public class CitadelGameTest {
	private static final String ENV = "mlinnotas-gametest:citadel";

	/** Monta a peca inteira, chunk por chunk, como o gerador de mundo faz. */
	static CitadelPiece placeWhole(ServerLevel level, long seed, BlockPos origin) {
		CitadelPiece piece = new CitadelPiece(seed, origin);
		BoundingBox box = piece.getBoundingBox();
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				level.setChunkForced(cx, cz, true);
				level.getChunk(cx, cz);
				BoundingBox chunkBB = new BoundingBox(cx << 4, level.getMinY(), cz << 4, (cx << 4) + 15, level.getMaxY(), (cz << 4) + 15);
				piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), RandomSource.create(seed + cx * 31L + cz),
						chunkBB, new ChunkPos(cx, cz), origin);
			}
		}
		return piece;
	}

	static void release(ServerLevel level, CitadelPiece piece) {
		BoundingBox box = piece.getBoundingBox();
		for (Entity e : level.getEntitiesOfClass(Entity.class, AABB.of(box).inflate(4), e -> !(e instanceof Player))) {
			e.discard();
		}
		for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
			for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
				level.setChunkForced(cx, cz, false);
			}
		}
	}

	@GameTest(environment = ENV)
	public void everyPlannedBlockStateExists(GameTestHelper helper) {
		for (long seed : new long[] {0L, 1L, 42L, -7L, 20261113L}) {
			CitadelPlan plan = CitadelPlanner.plan(seed);
			List<String> bad = new ArrayList<>();
			for (String s : new HashSet<>(plan.blocks().values())) {
				try {
					VanillaRemakePiece.parse(s);
				} catch (RuntimeException e) {
					bad.add(e.getMessage());
				}
			}
			helper.assertTrue(bad.isEmpty(), "seed " + seed + ": " + bad);
		}
		helper.assertTrue(VanillaRemakePiece.parse(CitadelPlanner.ALTAR).is(BossRegistry.ASTRAL_ALTAR), "altar astral no cofre");
		helper.succeed();
	}

	@GameTest(environment = ENV, maxTicks = 200)
	public void wholeCitadelIsBuiltWithShipElytraAndLoot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		long seed = 4242L;
		BlockPos origin = new BlockPos(40000, 150, -40000);
		CitadelPiece piece = placeWhole(level, seed, origin);
		CitadelPlan plan = piece.plan();
		try {
			// todos os blocos do plano no lugar
			int total = 0;
			int present = 0;
			for (Map.Entry<Long, String> e : plan.blocks().entrySet()) {
				long k = e.getKey();
				BlockPos pos = piece.world(CitadelPlan.keyX(k), CitadelPlan.keyY(k), CitadelPlan.keyZ(k));
				total++;
				if (level.getBlockState(pos).getBlock() == VanillaRemakePiece.parse(e.getValue()).getBlock()) {
					present++;
				}
			}
			helper.assertTrue(present >= total * 0.99, "blocos no mundo: " + present + " de " + total);
			// elevadores em todos os andares da fortaleza
			for (int f = 0; f < CitadelPlanner.KEEP_FLOORS; f++) {
				helper.assertTrue(level.getBlockState(origin.above(f * CitadelPlanner.FLOOR_HEIGHT)).is(ModBlocks.PURPUR_ELEVATOR), "elevador no andar " + f);
			}
			// altar no cofre
			Pos altar = plan.positionsOf("astral_altar").getFirst();
			helper.assertTrue(level.getBlockState(piece.world(altar.x(), altar.y(), altar.z())).is(BossRegistry.ASTRAL_ALTAR), "altar astral");
			// elitro: uma moldura so, na cabine da nave
			Pos elytra = plan.marker("elytra");
			List<ItemFrame> frames = level.getEntitiesOfClass(ItemFrame.class, AABB.of(piece.getBoundingBox()).inflate(2), f -> f.getItem().is(Items.ELYTRA));
			helper.assertTrue(frames.size() == 1, "uma moldura com elitro, achou " + frames.size());
			helper.assertTrue(frames.getFirst().blockPosition().equals(piece.world(elytra.x(), elytra.y(), elytra.z())),
					"moldura na cabine: " + frames.getFirst().blockPosition());
			// baus com loot da End City
			for (long k : plan.lootTables().keySet()) {
				BlockPos pos = piece.world(CitadelPlan.keyX(k), CitadelPlan.keyY(k), CitadelPlan.keyZ(k));
				helper.assertTrue(level.getBlockEntity(pos) instanceof ChestBlockEntity c && c.getLootTable() != null
						&& c.getLootTable().identifier().getPath().equals("chests/end_city_treasure"), "bau sem loot em " + pos);
			}
			long shulkers = level.getEntitiesOfClass(Shulker.class, AABB.of(piece.getBoundingBox()).inflate(2)).size();
			helper.assertTrue(shulkers >= plan.count(CitadelPlan.Kind.SHULKER) - 2, "shulkers: " + shulkers);
			helper.assertTrue(level.getBlockState(piece.world(0, -1, 0)).is(Blocks.END_STONE), "ilha embaixo da fortaleza");
		} finally {
			release(level, piece);
		}
		helper.succeed();
	}
}
