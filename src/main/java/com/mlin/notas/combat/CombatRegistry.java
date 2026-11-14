package com.mlin.notas.combat;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.tier.ModMaterials;
import com.mlin.notas.tier.Tier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Weapon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Registro das armas: cada {@link WeaponClass} em cada um dos 12 niveis (os vanilla usam os materiais vanilla
 * rebalanceados) e as 5 armas de chefe. Os itens entram na aba criativa do mod pela {@link ModItems#register}.
 */
public final class CombatRegistry {
	private static final Map<Tier, Map<WeaponClass, Item>> WEAPONS = new EnumMap<>(Tier.class);
	private static final Map<BossWeapon, Item> BOSS_WEAPONS = new EnumMap<>(BossWeapon.class);
	private static final List<Item> ALL = new ArrayList<>();

	private CombatRegistry() {
	}

	public static void init() {
		if (!ALL.isEmpty()) {
			return;
		}
		for (WeaponClass weaponClass : WeaponClass.ALL) {
			for (Tier tier : Tier.values()) {
				Item item = ModItems.register(weaponClass.itemName(tier), p -> new WeaponItem(p, weaponClass, null),
						properties(tier, weaponClass, WeaponStats.of(weaponClass, tier)));
				WEAPONS.computeIfAbsent(tier, t -> new EnumMap<>(WeaponClass.class)).put(weaponClass, item);
				ALL.add(item);
			}
		}
		for (BossWeapon weapon : BossWeapon.values()) {
			Item.Properties properties = properties(weapon.powerTier(), weapon.weaponClass(), weapon.stats()).rarity(Rarity.EPIC);
			Item item = ModItems.register(weapon.id(), p -> new WeaponItem(p, weapon.weaponClass(), weapon), properties);
			BOSS_WEAPONS.put(weapon, item);
			ALL.add(item);
		}
		CombatServer.init();
	}

	/** Arma de uma classe num nivel. */
	public static Item weapon(Tier tier, WeaponClass weaponClass) {
		return WEAPONS.get(tier).get(weaponClass);
	}

	public static Item bossWeapon(BossWeapon weapon) {
		return BOSS_WEAPONS.get(weapon);
	}

	/** Todas as armas registradas aqui (classes x niveis + armas de chefe). */
	public static List<Item> all() {
		return Collections.unmodifiableList(ALL);
	}

	private static Item.Properties properties(Tier tier, WeaponClass weaponClass, WeaponStats.Stats stats) {
		Item.Properties properties = new Item.Properties();
		if (tier.fireResistant()) {
			properties.fireResistant();
		}
		if (tier == Tier.CELESTIUM) {
			properties.rarity(Rarity.EPIC);
		} else if (tier.ordinal() >= Tier.ENDITE.ordinal()) {
			properties.rarity(Rarity.RARE);
		} else if (tier == Tier.INFERNITE) {
			properties.rarity(Rarity.UNCOMMON);
		}
		// base de espada (durabilidade, encantabilidade, reparo, teias), com os atributos da classe por cima
		properties.sword(ModMaterials.tool(tier), 0.0F, 0.0F);
		ItemAttributeModifiers.Builder attributes = ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE, new AttributeModifier(Item.BASE_ATTACK_DAMAGE_ID, WeaponStats.damageModifier(stats),
						AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED, new AttributeModifier(Item.BASE_ATTACK_SPEED_ID, WeaponStats.speedModifier(stats),
						AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
		float reach = WeaponStats.reachModifier(stats);
		if (reach != 0.0F) {
			attributes.add(Attributes.ENTITY_INTERACTION_RANGE, new AttributeModifier(MlinNotas.id("weapon_reach"), reach,
					AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.MAINHAND);
		}
		properties.attributes(attributes.build());
		properties.component(DataComponents.WEAPON, new Weapon(1, weaponClass.disableBlockingSeconds()));
		return properties;
	}
}
