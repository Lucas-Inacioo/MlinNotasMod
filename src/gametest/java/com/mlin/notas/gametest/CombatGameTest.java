package com.mlin.notas.gametest;

import com.mlin.notas.combat.BossWeapon;
import com.mlin.notas.combat.CombatRegistry;
import com.mlin.notas.combat.CombatServer;
import com.mlin.notas.combat.DodgeRules;
import com.mlin.notas.combat.WeaponClass;
import com.mlin.notas.tier.Tier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

/**
 * Game tests do "Combate melhor" (lote proprio): o montante acerta 3 zumbis num arco e nao o de tras, a adaga faz
 * mais dano pelas costas, o martelo fura a armadura (e devolve a armadura depois) com onda de choque, o terceiro
 * golpe e o golpe final, a arma de chefe dispara o efeito e a esquiva da invulnerabilidade com recarga.
 */
public class CombatGameTest {
	private static final String ENV = "mlinnotas-gametest:combat";

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	/** Jogador falso com a arma na mao (atributos aplicados na hora, ele nao e tickado) e golpe carregado. */
	private static ServerPlayer fighter(GameTestHelper helper, Item weapon, double x, double z, float yaw) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(new Vec3(x, 1.0, z));
		player.snapTo(pos.x, pos.y, pos.z, yaw, 0.0F);
		ItemStack stack = new ItemStack(weapon);
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		stack.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
			var instance = player.getAttribute(attribute);
			instance.removeModifier(modifier.id());
			instance.addTransientModifier(modifier);
		});
		charge(player);
		return player;
	}

	/** Carga cheia do golpe (o jogador falso nao tica, entao o contador nao sobe sozinho). */
	private static void charge(ServerPlayer player) {
		try {
			Field field = LivingEntity.class.getDeclaredField("attackStrengthTicker");
			field.setAccessible(true);
			field.setInt(player, 200);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Husk husk(GameTestHelper helper, double x, double z, float yaw) {
		Husk husk = helper.spawnWithNoFreeWill(EntityTypes.HUSK, new Vec3(x, 1.0, z));
		husk.setBaby(false);
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			husk.setItemSlot(slot, ItemStack.EMPTY);
		}
		husk.setYRot(yaw);
		husk.yBodyRot = yaw;
		husk.yHeadRot = yaw;
		return husk;
	}

	private static float taken(LivingEntity entity) {
		return entity.getMaxHealth() - entity.getHealth();
	}

	@GameTest(environment = ENV)
	public void greatswordHitsThreeInArc(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = fighter(helper, CombatRegistry.weapon(Tier.IRON, WeaponClass.GREATSWORD), 4.5, 1.5, 0.0F);
		Husk front = husk(helper, 4.5, 3.6, 180.0F);
		Husk left = husk(helper, 3.2, 3.0, 180.0F);
		Husk right = husk(helper, 5.8, 3.0, 180.0F);
		Husk behind = husk(helper, 4.5, 0.2, 0.0F);
		player.attack(front);
		for (Husk husk : new Husk[] {front, left, right}) {
			helper.assertTrue(taken(husk) > 0.0F, "o montante devia acertar o zumbi em " + husk.position());
		}
		helper.assertTrue(taken(behind) == 0.0F, "o zumbi de tras ficou fora do arco");
		helper.assertTrue(taken(left) < taken(front), "os do arco levam so parte do dano");
		helper.succeed();
	}

	@GameTest(environment = ENV)
	public void daggerBackstabDealsMore(GameTestHelper helper) {
		floor(helper);
		Item dagger = CombatRegistry.weapon(Tier.IRON, WeaponClass.DAGGER);
		// cada golpe com um jogador novo: nenhum e golpe final
		ServerPlayer a = fighter(helper, dagger, 2.5, 1.5, 0.0F);
		Husk facing = husk(helper, 2.5, 3.0, 180.0F);
		a.attack(facing);
		ServerPlayer b = fighter(helper, dagger, 5.5, 1.5, 0.0F);
		Husk turned = husk(helper, 5.5, 3.0, 0.0F);
		b.attack(turned);
		float front = taken(facing);
		float back = taken(turned);
		helper.assertTrue(front > 0.0F, "a adaga acertou de frente");
		helper.assertTrue(Math.abs(back / front - 1.4F) < 0.05F, "pelas costas = +40% (frente " + front + ", costas " + back + ")");
		helper.succeed();
	}

	@GameTest(environment = ENV, maxTicks = 40)
	public void warhammerPiercesArmorAndShakesTheGround(GameTestHelper helper) {
		floor(helper);
		Husk armored = husk(helper, 4.5, 3.5, 180.0F);
		armored.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.DIAMOND_HELMET));
		armored.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
		armored.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.DIAMOND_LEGGINGS));
		armored.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.DIAMOND_BOOTS));
		Husk beside = husk(helper, 6.2, 3.5, 180.0F);
		helper.runAfterDelay(3, () -> {
			double armor = armored.getAttributeValue(Attributes.ARMOR);
			double toughness = armored.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
			helper.assertTrue(armor >= 10.0, "armadura de diamante equipada: " + armor);
			ServerPlayer player = fighter(helper, CombatRegistry.weapon(Tier.IRON, WeaponClass.WARHAMMER), 4.5, 1.5, 0.0F);
			float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
			player.attack(armored);
			float unpierced = CombatRules.getDamageAfterAbsorb(armored, damage, player.damageSources().playerAttack(player), (float) armor,
					(float) toughness);
			float got = taken(armored);
			helper.assertTrue(got > unpierced + 0.5F, "o martelo devia furar a armadura: levou " + got + ", sem furar seria " + unpierced);
			helper.assertTrue(got < damage, "mas a armadura ainda conta: " + got + " < " + damage);
			helper.assertTrue(armored.getAttributeValue(Attributes.ARMOR) == armor, "a armadura volta ao normal depois do golpe");
			helper.assertTrue(taken(beside) > 0.0F, "a onda de choque acerta quem esta do lado");
			helper.succeed();
		});
	}

	@GameTest(environment = ENV)
	public void thirdHitIsTheFinisher(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = fighter(helper, CombatRegistry.weapon(Tier.IRON, WeaponClass.RAPIER), 4.5, 1.5, 0.0F);
		float[] dealt = new float[3];
		for (int i = 0; i < 3; i++) {
			Husk husk = husk(helper, 1.5 + i * 2.0, 3.0, 180.0F);
			player.snapTo(helper.absoluteVec(new Vec3(1.5 + i * 2.0, 1.0, 1.5)).x, player.getY(), player.getZ(), 0.0F, 0.0F);
			charge(player);
			player.attack(husk);
			dealt[i] = taken(husk);
			helper.assertTrue(CombatServer.comboStep(player) == i, "passo do combo " + i + " = " + CombatServer.comboStep(player));
		}
		helper.assertTrue(Math.abs(dealt[1] - dealt[0]) < 0.01F, "1o e 2o golpes iguais");
		helper.assertTrue(Math.abs(dealt[2] / dealt[0] - WeaponClass.RAPIER.finisherMultiplier()) < 0.05F,
				"golpe final +25%: " + dealt[0] + " -> " + dealt[2]);
		CombatServer.reset(player);
		helper.succeed();
	}

	@GameTest(environment = ENV)
	public void bossWeaponProcsAtMostOncePerSecond(GameTestHelper helper) {
		floor(helper);
		ServerPlayer player = fighter(helper, CombatRegistry.bossWeapon(BossWeapon.ASTRAL_LANCE), 4.5, 1.5, 0.0F);
		Husk target = husk(helper, 4.5, 4.5, 180.0F);
		Husk other = husk(helper, 0.5, 7.0, 180.0F);
		player.attack(target);
		float first = taken(other);
		helper.assertTrue(first > 0.0F, "o raio estelar acerta o outro inimigo");
		// logo em seguida (mesmo tick): sem raio de novo
		charge(player);
		target.damageCooldownTime = 0;
		player.attack(target);
		helper.assertTrue(taken(other) == first, "recarga do efeito");
		CombatServer.reset(player);
		helper.succeed();
	}

	@GameTest(environment = ENV)
	public void dodgeGivesInvulnerabilityWithCooldown(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
		player.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		helper.assertTrue(CombatServer.dodge(player, 0.0F, -1.0F), "primeira esquiva aceita");
		helper.assertTrue(!CombatServer.dodge(player, 0.0F, -1.0F), "segunda logo em seguida recusada (recarga de " + DodgeRules.COOLDOWN_TICKS + ")");
		helper.assertTrue(CombatServer.dodging(player, player.damageSources().generic()), "invulneravel logo depois de esquivar");
		helper.assertTrue(!CombatServer.dodging(player, player.damageSources().fellOutOfWorld()), "o vazio ignora a esquiva");
		helper.runAfterDelay(DodgeRules.INVULNERABLE_TICKS, () -> {
			helper.assertTrue(!CombatServer.dodging(player, player.damageSources().generic()), "a invulnerabilidade acaba");
			CombatServer.reset(player);
			helper.succeed();
		});
	}
}
