package com.mlin.notas.gametest;

import com.mlin.notas.furnace.FurnaceUpgrades;
import com.mlin.notas.sleep.SleepRules;
import com.mlin.notas.sleep.SleepServer;
import com.mlin.notas.tier.Tier;
import com.mlin.notas.waila.ContainerPeekPayload;
import com.mlin.notas.waila.WailaServer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Optional;

/**
 * Game tests das melhorias de qualidade de vida: colheita com replantio, sono por porcentagem e
 * consulta de conteudo de containers do HUD WAILA.
 */
public class QolGameTest {
	// ------------------------------------------------------------------ colheita

	private static void plantWheat(GameTestHelper helper, int x, int z, int age) {
		helper.setBlock(x, 1, z, Blocks.FARMLAND.defaultBlockState().setValue(FarmlandBlock.MOISTURE, FarmlandBlock.MAX_MOISTURE));
		helper.setBlock(x, 2, z, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, age));
	}

	private static InteractionResult use(GameTestHelper helper, ServerPlayer player, BlockPos relative) {
		BlockPos pos = helper.absolutePos(relative);
		BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
		return UseBlockCallback.EVENT.invoker().interact(player, helper.getLevel(), InteractionHand.MAIN_HAND, hit);
	}

	private static int droppedCount(GameTestHelper helper, Item item) {
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(10);
		int total = 0;
		for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (entity.getItem().is(item)) {
				total += entity.getItem().getCount();
			}
		}
		return total;
	}

	private static int wheatAge(GameTestHelper helper, int x, int z) {
		BlockState state = helper.getBlockState(new BlockPos(x, 2, z));
		helper.assertTrue(state.is(Blocks.WHEAT), "deveria haver trigo em " + x + "," + z + ", ha " + state);
		return state.getValue(CropBlock.AGE);
	}

	@GameTest
	public void harvestMatureWheatReplantsAndDrops(GameTestHelper helper) {
		plantWheat(helper, 2, 2, CropBlock.MAX_AGE);
		plantWheat(helper, 3, 2, CropBlock.MAX_AGE);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);

		InteractionResult result = use(helper, player, new BlockPos(2, 2, 2));
		helper.assertTrue(result == InteractionResult.SUCCESS, "colheita deveria consumir a acao, deu " + result);
		helper.assertTrue(wheatAge(helper, 2, 2) == 0, "trigo deveria ser replantado com idade 0");
		helper.assertTrue(wheatAge(helper, 3, 2) == CropBlock.MAX_AGE, "sem enxada so a planta clicada e colhida");
		helper.assertTrue(droppedCount(helper, Items.WHEAT) >= 1, "deveria dropar trigo");
		helper.succeed();
	}

	@GameTest
	public void immatureCropKeepsVanillaBehavior(GameTestHelper helper) {
		plantWheat(helper, 2, 2, 3);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		InteractionResult result = use(helper, player, new BlockPos(2, 2, 2));
		helper.assertTrue(result == InteractionResult.PASS, "planta imatura deveria seguir o vanilla, deu " + result);
		helper.assertTrue(wheatAge(helper, 2, 2) == 3, "planta imatura nao muda");
		helper.succeed();
	}

	@GameTest
	public void diamondHoeHarvestsFiveByFive(GameTestHelper helper) {
		for (int x = 1; x <= 6; x++) {
			for (int z = 1; z <= 5; z++) {
				plantWheat(helper, x, z, CropBlock.MAX_AGE);
			}
		}
		plantWheat(helper, 1, 1, 2);
		// enxada de diamante so funciona com a Mineracao do mundo em Diamante (trava de ferramentas)
		com.mlin.notas.skills.SkillServer.ensureLevel(helper.getLevel().getServer(), com.mlin.notas.skills.SkillBranch.MINING,
				com.mlin.notas.tier.Tier.DIAMOND.level());
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ItemStack hoe = new ItemStack(Items.DIAMOND_HOE);
		player.setItemSlot(EquipmentSlot.MAINHAND, hoe);

		use(helper, player, new BlockPos(3, 2, 3));
		for (int x = 1; x <= 5; x++) {
			for (int z = 1; z <= 5; z++) {
				int expected = x == 1 && z == 1 ? 2 : 0;
				helper.assertTrue(wheatAge(helper, x, z) == expected, "trigo em " + x + "," + z + " deveria ter idade " + expected);
			}
		}
		for (int z = 1; z <= 5; z++) {
			helper.assertTrue(wheatAge(helper, 6, z) == CropBlock.MAX_AGE, "coluna x=6 esta fora do 5x5");
		}
		helper.assertTrue(hoe.getDamageValue() == 24, "enxada deveria perder 1 por planta (24), perdeu " + hoe.getDamageValue());
		helper.succeed();
	}

	@GameTest
	public void cocoaReplantKeepsFacing(GameTestHelper helper) {
		helper.setBlock(2, 2, 2, Blocks.JUNGLE_LOG);
		helper.setBlock(3, 2, 2, Blocks.COCOA.defaultBlockState().setValue(CocoaBlock.FACING, Direction.WEST).setValue(CocoaBlock.AGE, CocoaBlock.MAX_AGE));
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		use(helper, player, new BlockPos(3, 2, 2));
		BlockState state = helper.getBlockState(new BlockPos(3, 2, 2));
		helper.assertTrue(state.is(Blocks.COCOA) && state.getValue(CocoaBlock.AGE) == 0 && state.getValue(CocoaBlock.FACING) == Direction.WEST,
				"cacau deveria ser replantado virado para o tronco, ha " + state);
		helper.assertTrue(droppedCount(helper, Items.COCOA_BEANS) >= 1, "deveria dropar cacau");
		helper.succeed();
	}

	// ------------------------------------------------------------------ sono

	@GameTest
	public void sleepPercentageDefaultsToHalf(GameTestHelper helper) {
		// O servidor de game test recria as gamerules a cada partida mas mantem o attachment do mundo salvo,
		// entao o teste refaz o cenario de "mundo novo" (sem marca, regra no 100 vanilla).
		MinecraftServer server = helper.getLevel().getServer();
		((AttachmentTarget) server.overworld()).removeAttached(SleepServer.DEFAULT_APPLIED);
		server.getGameRules().set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, SleepRules.VANILLA_DEFAULT_PERCENT, server);
		helper.assertTrue(SleepServer.applyModDefault(server), "mundo novo no 100 vanilla deveria mudar");
		int percent = server.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE);
		helper.assertTrue(percent == SleepRules.MOD_DEFAULT_PERCENT, "padrao do mod deveria ser 50%, esta " + percent);

		// Quem voltar para 100 depois fica com o seu valor.
		server.getGameRules().set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, SleepRules.VANILLA_DEFAULT_PERCENT, server);
		helper.assertFalse(SleepServer.applyModDefault(server), "o padrao so e aplicado uma vez por mundo");
		helper.assertTrue(server.getGameRules().get(GameRules.PLAYERS_SLEEPING_PERCENTAGE) == SleepRules.VANILLA_DEFAULT_PERCENT,
				"valor escolhido pelo jogador deveria ser mantido");
		server.getGameRules().set(GameRules.PLAYERS_SLEEPING_PERCENTAGE, SleepRules.MOD_DEFAULT_PERCENT, server);
		helper.succeed();
	}

	@GameTest
	public void sleepStatusCountsMockPlayers(GameTestHelper helper) {
		ServerPlayer sleeper = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ServerPlayer awake1 = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ServerPlayer awake2 = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		ServerPlayer spectator = (ServerPlayer) helper.makeMockServerPlayer(GameType.SPECTATOR);
		sleeper.setSleepingPos(helper.absolutePos(new BlockPos(1, 1, 1)));

		SleepRules.Status three = SleepServer.status(List.of(sleeper, awake1, awake2, spectator), 50);
		helper.assertTrue(three.active() == 3 && three.sleeping() == 1 && three.needed() == 2 && three.missing() == 1,
				"3 ativos e 1 dormindo: faltaria 1, deu " + three);
		helper.assertTrue(key(SleepServer.message(three)).equals("message.mlinnotas.sleep.status"), "mensagem de contagem");

		SleepRules.Status two = SleepServer.status(List.of(sleeper, awake1), 50);
		helper.assertTrue(two.enough(), "1 de 2 dormindo basta com 50%");
		helper.assertTrue(key(SleepServer.message(two)).equals("message.mlinnotas.sleep.skipping"), "mensagem de noite passando");
		helper.succeed();
	}

	private static String key(Component component) {
		return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "";
	}

	// ------------------------------------------------------------------ WAILA

	@GameTest
	public void containerPeekNearIsAnswered(GameTestHelper helper) {
		helper.setBlock(2, 1, 2, Blocks.CHEST);
		ChestBlockEntity chest = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 2), ChestBlockEntity.class);
		chest.setItem(0, new ItemStack(Items.DIRT, 10));
		chest.setItem(5, new ItemStack(Items.DIRT, 20));
		chest.setItem(7, new ItemStack(Items.DIAMOND));
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 5))));

		Optional<ContainerPeekPayload> answer = WailaServer.peek(player, helper.absolutePos(new BlockPos(2, 1, 2)));
		helper.assertTrue(answer.isPresent(), "jogador a 3 blocos deveria ver o conteudo");
		ContainerPeekPayload payload = answer.get();
		helper.assertTrue(payload.items().size() == 2, "terra agrupada + diamante = 2 grupos, deu " + payload.items().size());
		helper.assertTrue(payload.items().get(0).is(Items.DIRT) && payload.counts().get(0) == 30, "terra somada deveria dar 30");
		helper.assertTrue(payload.counts().get(1) == 1 && payload.furnaceTier() == -1, "diamante 1, sem fornalha");
		helper.succeed();
	}

	@GameTest
	public void containerPeekFarOrSpectatorIsRefused(GameTestHelper helper) {
		helper.setBlock(2, 1, 2, Blocks.BARREL);
		BlockPos barrel = helper.absolutePos(new BlockPos(2, 1, 2));
		ServerPlayer far = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		far.setPos(Vec3.atBottomCenterOf(barrel.offset(0, 0, 12)));
		helper.assertTrue(WailaServer.peek(far, barrel).isEmpty(), "a 12 blocos deveria recusar");

		ServerPlayer spectator = (ServerPlayer) helper.makeMockServerPlayer(GameType.SPECTATOR);
		spectator.setPos(Vec3.atBottomCenterOf(barrel.offset(0, 0, 2)));
		helper.assertTrue(WailaServer.peek(spectator, barrel).isEmpty(), "espectador nao ve conteudo");

		helper.setBlock(4, 1, 2, Blocks.STONE);
		ServerPlayer near = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		near.setPos(Vec3.atBottomCenterOf(barrel.offset(0, 0, 2)));
		helper.assertTrue(WailaServer.peek(near, helper.absolutePos(new BlockPos(4, 1, 2))).isEmpty(), "pedra nao e container");
		helper.assertTrue(WailaServer.peek(near, barrel).isPresent(), "barril perto deveria responder");
		helper.succeed();
	}

	@GameTest
	public void containerPeekReportsFurnaceUpgrade(GameTestHelper helper) {
		helper.setBlock(2, 1, 2, Blocks.FURNACE);
		AbstractFurnaceBlockEntity furnace = (AbstractFurnaceBlockEntity) helper.getBlockEntity(new BlockPos(2, 1, 2), AbstractFurnaceBlockEntity.class);
		FurnaceUpgrades.setTier(furnace, Tier.IRON);
		furnace.setItem(0, new ItemStack(Items.RAW_IRON, 5));
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.setPos(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 1, 4))));
		ContainerPeekPayload payload = WailaServer.peek(player, helper.absolutePos(new BlockPos(2, 1, 2))).orElseThrow();
		helper.assertTrue(payload.furnaceTier() == Tier.IRON.ordinal(), "upgrade de ferro deveria ir na resposta");
		helper.assertTrue(payload.items().size() == 1 && payload.counts().getFirst() == 5, "ferro bruto x5");
		helper.succeed();
	}
}
