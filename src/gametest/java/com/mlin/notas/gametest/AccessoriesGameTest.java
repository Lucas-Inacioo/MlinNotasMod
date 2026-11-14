package com.mlin.notas.gametest;

import com.mlin.notas.accessories.AccessoryContainer;
import com.mlin.notas.accessories.AccessoryData;
import com.mlin.notas.accessories.AccessoryMenu;
import com.mlin.notas.accessories.AccessoryRegistry;
import com.mlin.notas.accessories.AccessoryServer;
import com.mlin.notas.accessories.AccessoryStat;
import com.mlin.notas.dragon.DragonHoard;
import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillServer;
import com.mlin.notas.tier.Tier;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Game tests dos acessorios (lote proprio: mexem no nivel de Mineracao do mundo, que define o estagio). Equipar aplica o
 * modificador {@code mlinnotas:accessory_*} cortado pelo estagio; a reducao de dano passa pelo mixin; reliquia dispara
 * no maximo 1x/s; shift+clique respeita encaixe e unicos; morte sem keepInventory guarda tudo na lapide; o tesouro do dragao traz
 * a reliquia.
 */
public class AccessoriesGameTest {
	private static final String ENV = "mlinnotas-gametest:accessories";

	private static AttributeModifier modifier(ServerPlayer player, Holder<Attribute> attribute, AccessoryStat stat) {
		return player.getAttribute(attribute).getModifier(AccessoryServer.modifierId(stat));
	}

	private static void expect(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void equipAppliesModifierClampedByStage(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		int original = SkillServer.progress(server).level(SkillBranch.MINING);
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		try {
			SkillServer.setLevel(server, SkillBranch.MINING, Tier.IRON.level());
			for (String id : List.of("netherite_ring", "netherite_ring", "duelist_glove", "life_amulet")) {
				expect(AccessoryServer.equip(player, new ItemStack(AccessoryRegistry.item(id))), "nao equipou " + id);
			}
			expect(!AccessoryServer.equip(player, new ItemStack(AccessoryRegistry.item("gold_ring"))), "terceiro anel nao cabe");

			// antes do Golem: 16% de dano vira 5%; vida sem orcamento
			AttributeModifier damage = modifier(player, Attributes.ATTACK_DAMAGE, AccessoryStat.DAMAGE);
			expect(damage != null && Math.abs(damage.amount() - 0.05) < 1.0E-6, "dano no estagio 1: " + damage);
			expect(damage.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, "operacao do dano");
			expect(modifier(player, Attributes.MAX_HEALTH, AccessoryStat.MAX_HEALTH) == null, "vida antes do Golem deveria ser 0");
			expect(modifier(player, Attributes.ATTACK_SPEED, AccessoryStat.ATTACK_SPEED) != null, "velocidade de ataque (teto fixo)");

			// Netherite: teto de 15% e vida +2 (amuleto)
			SkillServer.setLevel(server, SkillBranch.MINING, Tier.NETHERITE.level());
			AccessoryServer.refresh(player);
			expect(Math.abs(modifier(player, Attributes.ATTACK_DAMAGE, AccessoryStat.DAMAGE).amount() - 0.15) < 1.0E-6, "dano no estagio 3");
			expect(Math.abs(modifier(player, Attributes.MAX_HEALTH, AccessoryStat.MAX_HEALTH).amount() - 2.0) < 1.0E-6, "vida no estagio 3");
			expect(Math.abs(player.getMaxHealth() - 22.0F) < 1.0E-4, "vida maxima " + player.getMaxHealth());

			// Endita: nao corta mais (16%)
			SkillServer.setLevel(server, SkillBranch.MINING, Tier.ENDITE.level());
			AccessoryServer.refresh(player);
			expect(Math.abs(modifier(player, Attributes.ATTACK_DAMAGE, AccessoryStat.DAMAGE).amount() - 0.16) < 1.0E-6, "dano no estagio 4");

			// desequipar tira o modificador
			new AccessoryContainer(player).clearContent();
			expect(modifier(player, Attributes.ATTACK_DAMAGE, AccessoryStat.DAMAGE) == null, "modificador ficou depois de tirar tudo");
			helper.succeed();
		} finally {
			SkillServer.setLevel(server, SkillBranch.MINING, original);
		}
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void damageReductionGoesThroughTheMixin(GameTestHelper helper) {
		ServerPlayer player = SkillsGameTest.survivalPlayer(helper, "accessory_armor");
		for (int i = 0; i < 400 && !player.connection.hasClientLoaded(); i++) {
			player.connection.tickClientLoadTimeout();
		}
		AccessoryServer.equip(player, new ItemStack(AccessoryRegistry.item("iron_necklace")));
		AccessoryServer.equip(player, new ItemStack(AccessoryRegistry.item("abyssal_shell")));
		player.setHealth(20.0F);
		player.hurtServer(helper.getLevel(), player.damageSources().generic(), 10.0F);
		// colar de ferro: 2% (abaixo de todo teto)
		expect(Math.abs(player.getHealth() - (20.0F - 9.8F)) < 1.0E-3, "dano generico com 2% de reducao: " + player.getHealth());
		// espera a recarga de dano do jogador (damageCooldownTime)
		helper.runAfterDelay(20, () -> {
			player.setHealth(20.0F);
			player.hurtServer(helper.getLevel(), player.damageSources().lava(), 10.0F);
			// fogo: 2% e depois 30% (nunca imune)
			float expected = 20.0F - 10.0F * 0.98F * 0.70F;
			expect(Math.abs(player.getHealth() - expected) < 1.0E-3, "fogo com a concha: " + player.getHealth() + " esperado " + expected);
			helper.getLevel().getServer().getPlayerList().remove(player);
			helper.succeed();
		});
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void relicProcRespectsCooldown(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		AccessoryServer.equip(player, new ItemStack(AccessoryRegistry.item("wither_ring")));
		Cow cow = helper.spawn(EntityTypes.COW, 3, 1, 3);
		cow.setNoAi(true);
		cow.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 1.0F);
		expect(cow.hasEffect(MobEffects.WITHER), "primeiro golpe deveria murchar");
		cow.removeEffect(MobEffects.WITHER);
		cow.setInvulnerableTime(0);
		cow.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 1.0F);
		expect(!cow.hasEffect(MobEffects.WITHER), "segundo golpe no mesmo segundo nao pode disparar");
		helper.runAfterDelay(21, () -> {
			cow.setInvulnerableTime(0);
			cow.hurtServer(helper.getLevel(), player.damageSources().playerAttack(player), 1.0F);
			expect(cow.hasEffect(MobEffects.WITHER), "depois de 1 s dispara de novo");
			helper.succeed();
		});
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void shiftClickEquipsIntoTheRightSlot(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		AccessoryMenu menu = new AccessoryMenu(1, player.getInventory(), new AccessoryContainer(player));
		int firstInventory = 6; // depois dos 6 encaixes vem o inventario principal (slot 9 do jogador)
		player.getInventory().setItem(9, new ItemStack(AccessoryRegistry.item("traveler_belt")));
		player.getInventory().setItem(10, new ItemStack(AccessoryRegistry.item("vampire_fang")));
		player.getInventory().setItem(11, new ItemStack(AccessoryRegistry.item("vampire_fang")));
		menu.quickMoveStack(player, firstInventory);
		menu.quickMoveStack(player, firstInventory + 1);
		menu.quickMoveStack(player, firstInventory + 2);
		List<ItemStack> equipped = AccessoryData.get(player);
		expect(equipped.get(3).is(AccessoryRegistry.item("traveler_belt")), "cinto no encaixe do cinto");
		expect(equipped.get(0).is(AccessoryRegistry.item("vampire_fang")), "presa no primeiro anel");
		expect(equipped.get(1).isEmpty(), "presa repetida nao pode ir no segundo anel");
		expect(!menu.slots.get(1).mayPlace(new ItemStack(AccessoryRegistry.item("vampire_fang"))), "slot recusa a repetida");
		expect(!menu.slots.get(2).mayPlace(new ItemStack(AccessoryRegistry.item("gold_ring"))), "colar recusa anel");
		// tirar com shift+clique volta para o inventario
		menu.quickMoveStack(player, 3);
		expect(AccessoryData.get(player).get(3).isEmpty(), "cinto saiu");
		expect(player.getInventory().countItem(AccessoryRegistry.item("traveler_belt")) == 1, "cinto voltou ao inventario");
		helper.succeed();
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void deathPutsAccessoriesInTheGrave(GameTestHelper helper) {
		ServerPlayer player = SkillsGameTest.survivalPlayer(helper, "accessory_death");
		try {
			AccessoryServer.equip(player, new ItemStack(AccessoryRegistry.item("champion_seal")));
			Vec3 at = player.position();
			player.die(player.damageSources().generic());
			expect(AccessoryData.get(player).stream().allMatch(ItemStack::isEmpty), "encaixes deveriam ficar vazios");
			// sem keepInventory os acessorios vao para a lapide (grave/GraveServer), nao para o chao
			List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(4),
					e -> e.getItem().is(AccessoryRegistry.item("champion_seal")));
			expect(drops.isEmpty(), "o selo nao deveria cair no chao");
			List<com.mlin.notas.grave.GraveInfo> graves = com.mlin.notas.grave.GraveData.get(helper.getLevel().getServer()).index()
					.ofOwner(player.getStringUUID());
			expect(!graves.isEmpty(), "lapide criada");
			net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(graves.get(0).x(), graves.get(0).y(), graves.get(0).z());
			com.mlin.notas.grave.GraveBlockEntity grave = (com.mlin.notas.grave.GraveBlockEntity) helper.getLevel().getBlockEntity(pos);
			boolean found = false;
			for (int i = com.mlin.notas.grave.GraveRules.ACCESSORY_START; i < com.mlin.notas.grave.GraveRules.EXTRA_START; i++) {
				found |= grave.getItem(i).is(AccessoryRegistry.item("champion_seal"));
			}
			expect(found, "o selo deveria estar na lapide");
			grave.clearContent();
			helper.getLevel().removeBlock(pos, false);
			helper.succeed();
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
	}


	@GameTest(environment = ENV, maxTicks = 100)
	public void dragonHoardCarriesTheRelic(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		LootTable table = server.reloadableRegistries().getLootTable(DragonHoard.LOOT);
		LootParams params = new LootParams.Builder(helper.getLevel()).withParameter(LootContextParams.ORIGIN, Vec3.ZERO)
				.create(LootContextParamSets.CHEST);
		List<ItemStack> items = table.getRandomItems(params);
		expect(items.stream().anyMatch(stack -> stack.is(AccessoryRegistry.item("dragonscale_charm"))), "tesouro sem a reliquia: " + items);
		helper.succeed();
	}
}
