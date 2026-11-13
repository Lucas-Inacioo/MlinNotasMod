package com.mlin.notas.gametest;

import com.mlin.notas.beacon.Prospecting;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillData;
import com.mlin.notas.skills.SkillEffects;
import com.mlin.notas.skills.SkillNode;
import com.mlin.notas.skills.SkillProgress;
import com.mlin.notas.skills.SkillServer;
import com.mlin.notas.skills.SkillTree;
import com.mlin.notas.tier.Tier;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Game tests da arvore de habilidades. O progresso e do mundo inteiro (compartilhado entre os testes), entao tudo roda
 * num unico teste sequencial, num ambiente proprio ({@code mlinnotas-gametest:skills} = lote separado, que nao roda em
 * paralelo com os outros) e o mundo volta ao inicio no fim.
 */
public class SkillsGameTest {
	private static final String ENV = "mlinnotas-gametest:skills";

	static ServerPlayer survivalPlayer(GameTestHelper helper, String name) {
		MinecraftServer server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		BlockPos at = helper.absolutePos(new BlockPos(3, 1, 3));
		player.teleportTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5);
		return player;
	}

	private static int count(ServerPlayer player, Item item) {
		int total = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.is(item)) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static double modifier(ServerPlayer player, Holder<Attribute> attribute, Identifier id) {
		AttributeModifier modifier = player.getAttribute(attribute).getModifier(id);
		return modifier == null ? 0 : modifier.amount();
	}

	private static int drops(GameTestHelper helper, Item item) {
		int total = 0;
		for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(10))) {
			if (entity.getItem().is(item)) {
				total += entity.getItem().getCount();
			}
		}
		return total;
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void sharedSkillTreeFlow(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		SkillServer.reset(server);
		ServerPlayer alice = survivalPlayer(helper, "skills-a");
		ServerPlayer bob = survivalPlayer(helper, "skills-b");
		try {
			SkillProgress progress = SkillData.get(server).progress();
			SkillNode stone = SkillTree.node(SkillBranch.MINING, Tier.STONE.level());
			SkillNode iron = SkillTree.node(SkillBranch.MINING, Tier.IRON.level());

			// 1) pre-requisito faltando: recusado e nada sai do inventario
			alice.getInventory().add(new ItemStack(Items.RAW_COPPER, 16));
			alice.giveExperienceLevels(10);
			helper.assertTrue(SkillServer.deposit(alice, iron) == SkillProgress.Outcome.LOCKED, "Ferro sem Cobre deveria ser recusado");
			helper.assertTrue(count(alice, Items.RAW_COPPER) == 16 && alice.experienceLevel == 10, "recusa nao cobra nada");

			// 2) minerio de carvao nao quebra so com Madeira
			BlockPos coal = new BlockPos(1, 1, 1);
			helper.setBlock(coal, Blocks.COAL_ORE);
			alice.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
			alice.gameMode.destroyBlock(helper.absolutePos(coal));
			helper.assertBlockPresent(Blocks.COAL_ORE, coal);

			// 3) deposito parcial (alice) + deposito que completa (bob) libera para os dois
			alice.getInventory().add(new ItemStack(Items.COBBLESTONE, 20));
			bob.getInventory().add(new ItemStack(Items.COBBLESTONE, 40));
			helper.assertTrue(SkillServer.deposit(alice, stone) == SkillProgress.Outcome.PARTIAL, "deposito parcial");
			helper.assertTrue(count(alice, Items.COBBLESTONE) == 0, "alice entregou os 20");
			helper.assertTrue(progress.deposited(stone, "minecraft:cobblestone") == 20, "20 depositados");
			helper.assertTrue(progress.level(SkillBranch.MINING) == 1, "ainda nao liberou");
			helper.assertTrue(SkillServer.deposit(bob, stone) == SkillProgress.Outcome.UNLOCKED, "bob completa");
			helper.assertTrue(count(bob, Items.COBBLESTONE) == 28, "bob so pagou os 12 que faltavam, ficou com " + count(bob, Items.COBBLESTONE));
			helper.assertTrue(progress.level(SkillBranch.MINING) == Tier.STONE.level(), "Pedra liberada para o mundo");

			// 4) agora o carvao quebra (para os dois), mas o ferro continua travado
			alice.gameMode.destroyBlock(helper.absolutePos(coal));
			helper.assertBlockPresent(Blocks.AIR, coal);
			BlockPos ironOre = new BlockPos(2, 1, 1);
			helper.setBlock(ironOre, Blocks.IRON_ORE);
			bob.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
			bob.gameMode.destroyBlock(helper.absolutePos(ironOre));
			helper.assertBlockPresent(Blocks.IRON_ORE, ironOre);

			// 5) atributos: alice compra Combate I (com XP) e os dois ganham o bonus
			SkillServer.setLevel(server, SkillBranch.MINING, Tier.NETHERITE.level());
			alice.getInventory().add(new ItemStack(Items.COPPER_INGOT, 16));
			helper.assertTrue(SkillServer.deposit(alice, SkillTree.node(SkillBranch.COMBAT, 1)) == SkillProgress.Outcome.UNLOCKED, "Combate I");
			helper.assertTrue(alice.experienceLevel == 5, "pagou 5 niveis, ficou com " + alice.experienceLevel);
			for (ServerPlayer player : List.of(alice, bob)) {
				helper.assertTrue(modifier(player, Attributes.ATTACK_DAMAGE, SkillServer.COMBAT_MODIFIER) == SkillEffects.meleeBonus(1),
						"Combate em " + player.getPlainTextName());
			}
			SkillServer.setLevel(server, SkillBranch.VIGOR, 3);
			SkillServer.setLevel(server, SkillBranch.AGILITY, 2);
			for (ServerPlayer player : List.of(alice, bob)) {
				helper.assertTrue(player.getMaxHealth() == 26.0F, "Vigor III = 26 de vida, veio " + player.getMaxHealth());
				helper.assertTrue(Math.abs(modifier(player, Attributes.MOVEMENT_SPEED, SkillServer.SPEED_MODIFIER) - 0.08) < 1e-9, "velocidade");
				helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER) - 0.8) < 1e-9, "queda -20%");
			}
			// o atributo volta depois de renascer
			ServerPlayer respawned = server.getPlayerList().respawn(bob, false, net.minecraft.world.entity.Entity.RemovalReason.KILLED);
			bob = respawned;
			helper.assertTrue(bob.getMaxHealth() == 26.0F, "Vigor depois de renascer: " + bob.getMaxHealth());

			// 6) liga de celestio so com Mineracao: Celestio
			List<ItemStack> grid = new ArrayList<>();
			for (Item item : List.of(ModItems.ingot(Tier.ASTRALITE), ModItems.ingot(Tier.ASTRALITE), ModItems.ingot(Tier.ENDITE),
					ModItems.ingot(Tier.ENDITE), ModItems.ingot(Tier.INFERNITE), ModItems.ingot(Tier.INFERNITE), Items.NETHERITE_INGOT)) {
				grid.add(new ItemStack(item));
			}
			grid.add(ItemStack.EMPTY);
			grid.add(ItemStack.EMPTY);
			CraftingInput input = CraftingInput.of(3, 3, grid);
			helper.assertTrue(server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel()).isEmpty(),
					"liga de celestio travada sem o nivel");
			SkillServer.setLevel(server, SkillBranch.MINING, Tier.CELESTIUM.level());
			var recipe = server.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel());
			helper.assertTrue(recipe.isPresent() && recipe.get().value().assemble(input).is(ModItems.CELESTIUM_INGOT), "liga liberada com Celestio");

			// 7) Prospeccao V = 100%: um minerio bruto extra garantido
			alice.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
			helper.assertTrue(Prospecting.extraDrop(helper.getLevel(), helper.absolutePos(ironOre), Blocks.IRON_ORE.defaultBlockState(), null, alice,
					alice.getMainHandItem(), () -> 0.0).isEmpty(), "sem Prospeccao nada extra");
			SkillServer.setLevel(server, SkillBranch.PROSPECTING, 5);
			helper.killAllEntitiesOfClass(ItemEntity.class);
			alice.gameMode.destroyBlock(helper.absolutePos(ironOre));
			helper.assertBlockPresent(Blocks.AIR, ironOre);
			int raw = drops(helper, Items.RAW_IRON);
			helper.assertTrue(raw == 2, "Prospeccao V: 1 + 1 extra, veio " + raw);

			// 8) mineracao em veio: sem a tecla so o bloco; com a tecla o veio (com diagonal e ardosia), outro minerio fica
			BlockPos[] vein = {new BlockPos(1, 2, 1), new BlockPos(2, 2, 1), new BlockPos(3, 3, 1)};
			BlockPos other = new BlockPos(1, 2, 2);
			helper.setBlock(vein[0], Blocks.COAL_ORE);
			helper.setBlock(vein[1], Blocks.DEEPSLATE_COAL_ORE);
			helper.setBlock(vein[2], Blocks.COAL_ORE);
			helper.setBlock(other, Blocks.DIAMOND_ORE);
			alice.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
			com.mlin.notas.vein.VeinMineServer.setActive(alice, false);
			alice.gameMode.destroyBlock(helper.absolutePos(vein[0]));
			helper.assertBlockPresent(Blocks.AIR, vein[0]);
			helper.assertBlockPresent(Blocks.DEEPSLATE_COAL_ORE, vein[1]);
			helper.setBlock(vein[0], Blocks.COAL_ORE);
			com.mlin.notas.vein.VeinMineServer.setActive(alice, true);
			int damageBefore = alice.getMainHandItem().getDamageValue();
			alice.gameMode.destroyBlock(helper.absolutePos(vein[0]));
			for (BlockPos p : vein) {
				helper.assertBlockPresent(Blocks.AIR, p);
			}
			helper.assertBlockPresent(Blocks.DIAMOND_ORE, other);
			helper.assertTrue(alice.getMainHandItem().getDamageValue() - damageBefore == 3, "1 de durabilidade por bloco do veio");
			com.mlin.notas.vein.VeinMineServer.setActive(alice, false);
		} finally {
			server.getPlayerList().remove(alice);
			server.getPlayerList().remove(bob);
			SkillServer.reset(server);
		}
		helper.succeed();
	}
}
