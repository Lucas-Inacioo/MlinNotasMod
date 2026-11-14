package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.combat.CombatClient;
import com.mlin.notas.combat.BossWeapon;
import com.mlin.notas.combat.CombatRegistry;
import com.mlin.notas.combat.WeaponClass;
import com.mlin.notas.tier.Tier;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Game test de cliente do "Combate melhor": icones de todas as armas numa grade de inventario, a arma na mao em
 * primeira e terceira pessoa (montante, martelo, glaive, adaga, rapieira e armas de chefe), o arco do montante com
 * particulas e a esquiva (Alt) deslocando o jogador.
 */
@SuppressWarnings("UnstableApiUsage")
public class CombatClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("gamerule advance_time false");
			world.getServer().runCommand("weather clear");

			List<Item> weapons = new ArrayList<>(CombatRegistry.all());
			// icones: 36 por vez no inventario do jogador
			for (int page = 0; page * 36 < weapons.size(); page++) {
				int start = page * 36;
				world.getServer().runOnServer(server -> {
					ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
					for (int slot = 0; slot < 36; slot++) {
						int index = start + slot;
						player.getInventory().setItem(slot, index < weapons.size() ? new ItemStack(weapons.get(index)) : ItemStack.EMPTY);
					}
				});
				context.waitTicks(3);
				context.getInput().pressKey(options -> options.keyInventory);
				context.waitForScreen(InventoryScreen.class);
				context.waitTicks(3);
				context.takeScreenshot("mlinnotas-combat-icons-" + (page + 1));
				context.getInput().pressKey(options -> options.keyInventory);
				context.waitForScreen(null);
			}

			// arma na mao em primeira pessoa (a espada de ferro vanilla de referencia)
			Item[] showcase = {Items.IRON_SWORD,
					CombatRegistry.weapon(Tier.IRON, WeaponClass.GREATSWORD), CombatRegistry.weapon(Tier.DIAMOND, WeaponClass.WARHAMMER),
					CombatRegistry.weapon(Tier.COBALT, WeaponClass.GLAIVE), CombatRegistry.weapon(Tier.GOLD, WeaponClass.DAGGER),
					CombatRegistry.weapon(Tier.ENDITE, WeaponClass.RAPIER), CombatRegistry.bossWeapon(BossWeapon.IGNEOUS_GREATSWORD)};
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			for (Item item : showcase) {
				hold(context, world, item);
				context.waitTicks(35);
				context.takeScreenshot("mlinnotas-combat-hand-" + BuiltInRegistries.ITEM.getKey(item).getPath());
			}

			// terceira pessoa: suportes de armadura de perfil, cada um com uma arma (espada vanilla, 5 classes, 5 de chefe)
			List<Item> lineup = new ArrayList<>(List.of(Items.IRON_SWORD));
			for (WeaponClass weaponClass : WeaponClass.ALL) {
				lineup.add(CombatRegistry.weapon(Tier.IRON, weaponClass));
			}
			for (BossWeapon weapon : BossWeapon.values()) {
				lineup.add(CombatRegistry.bossWeapon(weapon));
			}
			hold(context, world, Items.AIR);
			world.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				player.snapTo(player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
				for (int i = 0; i < lineup.size(); i++) {
					ArmorStand stand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
					stand.snapTo(player.getX() + (i - (lineup.size() - 1) / 2.0) * 1.1, player.getY(), player.getZ() + 5.0, 90.0F, 0.0F);
					stand.setYBodyRot(90.0F);
					stand.setYHeadRot(90.0F);
					stand.setShowArms(true);
					stand.setNoBasePlate(true);
					stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(lineup.get(i)));
					level.addFreshEntity(stand);
				}
			});
			context.runOnClient(mc -> {
				mc.player.setYRot(0.0F);
				mc.player.setXRot(8.0F);
			});
			context.waitTicks(20);
			context.takeScreenshot("mlinnotas-combat-lineup");
			world.getServer().runCommand("kill @e[type=minecraft:armor_stand]");

			// arco do montante: tres zumbis a frente, golpe pelo cliente (passa pelo servidor de verdade)
			// a trava de ferramentas (gating/) cancela golpes de armas acima da Mineracao do mundo: libera tudo
			world.getServer().runOnServer(server -> com.mlin.notas.skills.SkillServer.ensureLevel(server,
					com.mlin.notas.skills.SkillBranch.MINING, Tier.CELESTIUM.level()));
			hold(context, world, CombatRegistry.weapon(Tier.IRON, WeaponClass.GREATSWORD));
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
			int[] ids = world.getServer().computeOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				ServerLevel level = player.level();
				player.snapTo(player.getX(), player.getY(), player.getZ(), 0.0F, 10.0F);
				int[] out = new int[3];
				double[][] offsets = {{0.0, 2.2}, {-1.4, 1.8}, {1.4, 1.8}};
				for (int i = 0; i < 3; i++) {
					Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
					husk.snapTo(player.getX() + offsets[i][0], player.getY(), player.getZ() + offsets[i][1], 180.0F, 0.0F);
					husk.setNoAi(true);
					level.addFreshEntity(husk);
					out[i] = husk.getId();
				}
				return out;
			});
			context.runOnClient(mc -> {
				mc.player.setYRot(0.0F);
				mc.player.setXRot(10.0F);
			});
			context.waitTicks(30);
			context.runOnClient(mc -> mc.gameMode.attack(mc.player, mc.level.getEntity(ids[0])));
			context.waitTicks(2);
			context.takeScreenshot("mlinnotas-combat-greatsword-arc");
			int hurt = world.getServer().computeOnServer(server -> {
				ServerLevel level = server.getPlayerList().getPlayers().getFirst().level();
				int count = 0;
				for (int id : ids) {
					if (level.getEntity(id) instanceof Husk husk && husk.getHealth() < husk.getMaxHealth()) {
						count++;
					}
				}
				return count;
			});
			if (hurt < 3) {
				throw new AssertionError("o montante devia acertar os 3 zumbis no arco, acertou " + hurt);
			}

			// esquiva: Alt de verdade, o jogador sai do lugar para tras
			world.getServer().runCommand("kill @e[type=minecraft:husk]");
			context.runOnClient(mc -> mc.options.setCameraType(CameraType.FIRST_PERSON));
			context.waitTicks(40);
			Vec3 before = context.computeOnClient(mc -> mc.player.position());
			context.getInput().pressKey(CombatClient.DODGE);
			context.waitTicks(15);
			Vec3 after = context.computeOnClient(mc -> mc.player.position());
			double moved = before.distanceTo(after);
			MlinNotas.LOGGER.info("[CombatClientGameTest] esquiva deslocou {} blocos", String.format("%.2f", moved));
			if (moved < 1.5) {
				throw new AssertionError("a esquiva devia deslocar o jogador, andou " + moved);
			}
		}
	}

	private static void hold(ClientGameTestContext context, TestSingleplayerContext world, Item item) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.getInventory().clearContent();
			player.getInventory().setItem(0, new ItemStack(item));
			player.getInventory().setSelectedSlot(0);
		});
		context.runOnClient(mc -> mc.player.getInventory().setSelectedSlot(0));
		context.waitTicks(3);
	}
}
