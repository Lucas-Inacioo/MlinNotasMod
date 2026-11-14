package com.mlin.notas.gametest;

import com.mlin.notas.gating.ToolLocks;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillServer;
import com.mlin.notas.tier.Tier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Trava de ferramentas pelo nivel de Mineracao do mundo: com o mundo em Ouro uma picareta de diamante nao quebra nada,
 * a espada de diamante nao ataca e a enxada de diamante nao ara; depois de Diamante funciona. Armadura de diamante
 * continua vestindo, ferro continua valendo e o criativo ignora a trava. Um teste so (o nivel e global).
 */
public class GatingGameTest {
	private static final String ENV = "mlinnotas-gametest:gating";

	@GameTest(environment = ENV, maxTicks = 60)
	public void toolsFollowWorldMiningLevel(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		SkillServer.reset(server);
		SkillServer.setLevel(server, SkillBranch.MINING, Tier.GOLD.level());
		ServerPlayer player = SkillsGameTest.survivalPlayer(helper, "gating-a");
		try {
			// tags: o item sabe o proprio nivel
			helper.assertTrue(ToolLocks.tier(new ItemStack(Items.DIAMOND_PICKAXE)) == Tier.DIAMOND, "picareta de diamante = Diamante");
			helper.assertTrue(ToolLocks.tier(new ItemStack(Items.GOLDEN_SWORD)) == Tier.GOLD, "espada de ouro = Ouro");
			helper.assertTrue(ToolLocks.tier(new ItemStack(ModItems.wand(Tier.CELESTIUM))) == Tier.CELESTIUM, "varinha de celestio");
			helper.assertTrue(ToolLocks.tier(new ItemStack(Items.DIAMOND_CHESTPLATE)) == null, "armadura nao tem trava");

			// 1) picareta de diamante no Ouro: nao quebra pedra
			BlockPos stone = new BlockPos(1, 1, 1);
			helper.setBlock(stone, Blocks.STONE);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
			helper.assertTrue(!player.gameMode.destroyBlock(helper.absolutePos(stone)), "diamante travado no Ouro");
			helper.assertBlockPresent(Blocks.STONE, stone);
			// ataque recusado (o mesmo teste que o jogo faz antes do golpe e da estocada)
			helper.assertTrue(player.cannotAttackWithItem(new ItemStack(Items.DIAMOND_SWORD), 5), "espada de diamante nao ataca");
			helper.assertTrue(player.cannotAttackWithItem(new ItemStack(Items.DIAMOND_SPEAR), 5), "lanca de diamante nao ataca");
			helper.assertTrue(!player.cannotAttackWithItem(new ItemStack(Items.IRON_SWORD), 5), "espada de ferro ataca");
			// enxada de diamante nao ara
			BlockPos dirt = new BlockPos(2, 1, 1);
			helper.setBlock(dirt, Blocks.DIRT);
			ItemStack hoe = new ItemStack(Items.DIAMOND_HOE);
			player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
			BlockPos dirtAbs = helper.absolutePos(dirt);
			hoe.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(dirtAbs).add(0, 0.5, 0), Direction.UP,
					dirtAbs, false)));
			helper.assertBlockPresent(Blocks.DIRT, dirt);
			// ferro (abaixo do Ouro) continua valendo
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
			helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(stone)), "ferro quebra no Ouro");
			helper.assertBlockPresent(Blocks.AIR, stone);

			// 2) armadura de diamante veste normalmente
			ItemStack chestplate = new ItemStack(Items.DIAMOND_CHESTPLATE);
			player.setItemInHand(InteractionHand.MAIN_HAND, chestplate);
			player.gameMode.useItem(player, helper.getLevel(), chestplate, InteractionHand.MAIN_HAND);
			helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE), "peitoral de diamante vestido");

			// 3) criativo ignora a trava
			player.setGameMode(GameType.CREATIVE);
			helper.setBlock(stone, Blocks.STONE);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
			helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(stone)), "criativo quebra com diamante");
			helper.assertTrue(!player.cannotAttackWithItem(new ItemStack(Items.NETHERITE_SWORD), 5), "criativo ataca com netherite");
			player.setGameMode(GameType.SURVIVAL);

			// 4) Mineracao em Diamante: a picareta, a espada e a enxada funcionam
			SkillServer.ensureLevel(server, SkillBranch.MINING, Tier.DIAMOND.level());
			helper.setBlock(stone, Blocks.STONE);
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
			helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(stone)), "diamante quebra no Diamante");
			helper.assertBlockPresent(Blocks.AIR, stone);
			helper.assertTrue(!player.cannotAttackWithItem(new ItemStack(Items.DIAMOND_SWORD), 5), "espada de diamante ataca");
			helper.assertTrue(player.cannotAttackWithItem(new ItemStack(Items.NETHERITE_SWORD), 5), "netherite ainda travada");
			player.setItemInHand(InteractionHand.MAIN_HAND, hoe);
			hoe.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(dirtAbs).add(0, 0.5, 0), Direction.UP,
					dirtAbs, false)));
			helper.assertBlockPresent(Blocks.FARMLAND, dirt);
		} finally {
			server.getPlayerList().remove(player);
			SkillServer.reset(server);
		}
		helper.succeed();
	}
}
