package com.mlin.notas.gametest;

import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.waystone.WaystoneBlock;
import com.mlin.notas.waystone.WaystoneData;
import com.mlin.notas.waystone.WaystoneInfo;
import com.mlin.notas.waystone.WaystoneServer;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Game tests das pedras de teleporte: ativar para o mundo, viajar cobrando XP, recusar sem XP, criativo e pergaminho. */
public class WaystoneGameTest {
	private static BlockPos placeWaystone(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		BlockPos lower = helper.absolutePos(new BlockPos(3, 1, 3));
		WaystoneBlock.placeAt(helper.getLevel(), ModBlocks.WAYSTONE.defaultBlockState(), lower, 3);
		return lower;
	}

	@SuppressWarnings("removal")
	private static ServerPlayer survivalPlayerNear(GameTestHelper helper, BlockPos lower, int levels) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getAbilities().instabuild = false;
		player.getAbilities().flying = false;
		player.setExperienceLevels(levels);
		player.teleportTo(lower.getX() + 0.5, lower.getY(), lower.getZ() + 1.5);
		return player;
	}

	private static void cleanup(GameTestHelper helper, ServerPlayer player, BlockPos... waystones) {
		ServerLevel level = helper.getLevel();
		WaystoneData data = WaystoneData.get(level.getServer());
		for (BlockPos pos : waystones) {
			data.remove(WaystoneServer.dimension(level), pos.getX(), pos.getY(), pos.getZ());
		}
		level.getServer().getPlayerList().remove(player);
	}

	@GameTest
	public void firstUseActivatesForTheWholeWorld(GameTestHelper helper) {
		BlockPos lower = placeWaystone(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayerNear(helper, lower, 0);
		helper.assertTrue(WaystoneServer.at(level, lower).isEmpty(), "pedra nova ainda nao ativada");
		WaystoneServer.use(player, level, lower);
		WaystoneInfo info = WaystoneServer.at(level, lower).orElseThrow(() -> helper.assertionException("botao direito deveria ativar"));
		helper.assertTrue(!info.name().isBlank(), "nome sorteado");
		// e do mundo: qualquer um enxerga, inclusive quem nunca tocou na pedra
		helper.assertTrue(WaystoneData.get(level.getServer()).registry().isActive(WaystoneServer.dimension(level), lower.getX(), lower.getY(), lower.getZ()),
				"ativa nos dados do mundo");
		helper.assertTrue(WaystoneData.get(level.getServer()).registry().lastUsed(player.getStringUUID()).orElseThrow().key().equals(info.key()),
				"ultima pedra usada pelo jogador");
		// etiqueta renomeia
		WaystoneServer.renameWithTag(player, level, lower, "Vila do Rio");
		helper.assertTrue(WaystoneServer.at(level, lower).orElseThrow().name().equals("Vila do Rio"), "renomeada");
		// clicar na metade de cima e o mesmo que na de baixo
		helper.assertTrue(WaystoneBlock.lowerPos(level.getBlockState(lower.above()), lower.above()).equals(lower), "metade de cima aponta para a de baixo");
		// quebrar remove a pedra para todos
		level.setBlockAndUpdate(lower, Blocks.AIR.defaultBlockState());
		helper.assertTrue(WaystoneServer.at(level, lower).isEmpty(), "quebrada deixa de existir");
		helper.assertBlockPresent(Blocks.AIR, 3, 2, 3);
		cleanup(helper, player, lower);
		helper.succeed();
	}

	@GameTest
	public void travelChargesOneLevelPer500Blocks(GameTestHelper helper) {
		BlockPos lower = placeWaystone(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayerNear(helper, lower, 5);
		WaystoneServer.use(player, level, lower);
		BlockPos far = lower.offset(1000, 0, 0);
		WaystoneInfo target = WaystoneServer.activate(level, far, "Longe");
		WaystoneServer.TravelResult result = WaystoneServer.travel(player, lower, target.key());
		helper.assertTrue(result == WaystoneServer.TravelResult.OK, "viagem deveria dar certo: " + result);
		helper.assertTrue(player.experienceLevel == 3, "1000 blocos = 2 niveis; sobrou " + player.experienceLevel);
		helper.assertTrue(player.blockPosition().closerThan(far, 3.0), "chegou perto da pedra de destino: " + player.blockPosition());
		helper.assertTrue(WaystoneData.get(level.getServer()).registry().lastUsed(player.getStringUUID()).orElseThrow().key().equals(target.key()),
				"destino vira a ultima pedra usada");
		cleanup(helper, player, lower, far);
		helper.succeed();
	}

	@GameTest
	public void travelIsRefusedWithoutXp(GameTestHelper helper) {
		BlockPos lower = placeWaystone(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayerNear(helper, lower, 1);
		WaystoneServer.use(player, level, lower);
		BlockPos far = lower.offset(0, 0, 1200);
		WaystoneInfo target = WaystoneServer.activate(level, far, "Caro");
		BlockPos before = player.blockPosition();
		WaystoneServer.TravelResult result = WaystoneServer.travel(player, lower, target.key());
		helper.assertTrue(result == WaystoneServer.TravelResult.NO_XP, "sem XP deveria recusar: " + result);
		helper.assertTrue(player.blockPosition().equals(before), "nao saiu do lugar");
		helper.assertTrue(player.experienceLevel == 1, "nao cobrou nada");
		cleanup(helper, player, lower, far);
		helper.succeed();
	}

	@GameTest
	public void creativeTravelsForFree(GameTestHelper helper) {
		BlockPos lower = placeWaystone(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayerNear(helper, lower, 0);
		player.getAbilities().instabuild = true;
		WaystoneServer.use(player, level, lower);
		BlockPos far = lower.offset(-700, 0, 0);
		WaystoneInfo target = WaystoneServer.activate(level, far, "Gratis");
		WaystoneServer.TravelResult result = WaystoneServer.travel(player, lower, target.key());
		helper.assertTrue(result == WaystoneServer.TravelResult.OK, "criativo viaja sem XP: " + result);
		helper.assertTrue(player.blockPosition().closerThan(far, 3.0), "chegou");
		cleanup(helper, player, lower, far);
		helper.succeed();
	}

	@GameTest
	public void returnScrollGoesBackToTheLastWaystone(GameTestHelper helper) {
		BlockPos lower = placeWaystone(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = survivalPlayerNear(helper, lower, 0);
		ItemStack scroll = new ItemStack(ModItems.RETURN_SCROLL, 2);
		player.setItemInHand(InteractionHand.MAIN_HAND, scroll);
		// sem pedra usada: nao gasta
		ModItems.RETURN_SCROLL.use(level, player, InteractionHand.MAIN_HAND);
		helper.assertTrue(player.getMainHandItem().getCount() == 2, "sem destino o pergaminho nao e gasto");
		WaystoneServer.use(player, level, lower);
		player.teleportTo(lower.getX() + 300.5, lower.getY() + 5, lower.getZ() + 0.5);
		// a primeira tentativa falhou, entao nao ha recarga
		ModItems.RETURN_SCROLL.use(level, player, InteractionHand.MAIN_HAND);
		helper.assertTrue(player.blockPosition().closerThan(lower, 3.0), "voltou para a pedra: " + player.blockPosition());
		helper.assertTrue(player.getMainHandItem().getCount() == 1, "pergaminho gasto");
		cleanup(helper, player, lower);
		helper.succeed();
	}
}
