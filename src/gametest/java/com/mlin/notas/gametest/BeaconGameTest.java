package com.mlin.notas.gametest;

import com.mlin.notas.beacon.BeaconFlight;
import com.mlin.notas.beacon.BeaconMenuTier;
import com.mlin.notas.beacon.BeaconPowers;
import com.mlin.notas.beacon.BeaconTiers;
import com.mlin.notas.beacon.Prospecting;
import com.mlin.notas.furnace.FurnaceUpgrades;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModEffects;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.tier.GearKind;
import com.mlin.notas.tier.ModWorldgen;
import com.mlin.notas.nether.NetherRegistry;
import net.minecraft.world.level.biome.Biomes;
import com.mlin.notas.tier.Tier;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Game tests do sinalizador por nivel, dos blocos de metal, do Garimpo, do Voo e da fundicao dupla. */
public class BeaconGameTest {
	/** Jogador de sobrevivencia dentro do mundo, com conexao falsa (o mock in-level vanilla e sempre criativo). */
	static ServerPlayer survivalPlayer(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		CommonListenerCookie cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "beacon-test"), false);
		ServerPlayer player = new ServerPlayer(server, helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
		Connection connection = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(connection);
		server.getPlayerList().placeNewPlayer(connection, player, cookie);
		player.setGameMode(GameType.SURVIVAL);
		return player;
	}

	static void remove(GameTestHelper helper, ServerPlayer player) {
		helper.getLevel().getServer().getPlayerList().remove(player);
	}

	/** Piramide de 1 camada (3x3) com o sinalizador em cima; devolve a posicao absoluta do sinalizador. */
	static BlockPos smallPyramid(GameTestHelper helper, int x, int z, Block base) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(x + dx, 1, z + dz, base);
			}
		}
		helper.setBlock(x, 2, z, Blocks.BEACON);
		return helper.absolutePos(new BlockPos(x, 2, z));
	}

	@GameTest
	public void storageBlocksAreBeaconBasesAndNeedTheRightPickaxe(GameTestHelper helper) {
		for (Tier tier : Tier.values()) {
			Block block = ModBlocks.storage(tier);
			if (!tier.modGear()) {
				helper.assertTrue(block == null, "nivel vanilla sem bloco novo: " + tier);
				continue;
			}
			helper.assertTrue(block != null, "bloco de " + tier.id());
			helper.assertTrue(block.defaultBlockState().is(BlockTags.BEACON_BASE_BLOCKS), tier.id() + "_block e base de sinalizador");
			helper.assertTrue(BeaconTiers.metalOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString()) == tier,
					"metal do bloco " + tier.id());
			for (String recipe : List.of(tier.id() + "_block", tier.id() + "_ingot_from_" + tier.id() + "_block")) {
				helper.assertTrue(helper.getLevel().getServer().getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE,
						com.mlin.notas.MlinNotas.id(recipe))).isPresent(), "receita " + recipe);
			}
			if (tier.hasOre()) {
				helper.assertTrue(ModBlocks.rawStorage(tier) != null, "bloco bruto de " + tier.id());
				helper.assertTrue(!ModBlocks.rawStorage(tier).defaultBlockState().is(BlockTags.BEACON_BASE_BLOCKS), "bloco bruto nao e base");
			}
		}
		for (Block vanilla : List.of(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK, Blocks.NETHERITE_BLOCK)) {
			helper.assertTrue(vanilla.defaultBlockState().is(BlockTags.BEACON_BASE_BLOCKS), "vanilla continua base: " + vanilla);
		}
		// celestio exige picareta de astralita; endita exige infernita
		ItemStack endite = new ItemStack(ModItems.gear(Tier.ENDITE, GearKind.PICKAXE));
		ItemStack astralite = new ItemStack(ModItems.gear(Tier.ASTRALITE, GearKind.PICKAXE));
		ItemStack netherite = new ItemStack(Items.NETHERITE_PICKAXE);
		helper.assertTrue(!endite.isCorrectToolForDrops(ModBlocks.storage(Tier.CELESTIUM).defaultBlockState()), "endita nao quebra celestio");
		helper.assertTrue(astralite.isCorrectToolForDrops(ModBlocks.storage(Tier.CELESTIUM).defaultBlockState()), "astralita quebra celestio");
		helper.assertTrue(!netherite.isCorrectToolForDrops(ModBlocks.storage(Tier.ENDITE).defaultBlockState()), "netherite nao quebra endita");
		helper.assertTrue(new ItemStack(Items.IRON_PICKAXE).isCorrectToolForDrops(ModBlocks.storage(Tier.COBALT).defaultBlockState()), "ferro quebra cobalto");
		helper.succeed();
	}

	@SuppressWarnings("removal")
	@GameTest
	public void pyramidsOfDifferentMetalsGiveDifferentTierRangeAndEffects(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos iron = smallPyramid(helper, 1, 1, Blocks.IRON_BLOCK);
		BlockPos celestium = smallPyramid(helper, 5, 1, ModBlocks.storage(Tier.CELESTIUM));
		BlockPos mixed = smallPyramid(helper, 3, 5, ModBlocks.storage(Tier.CELESTIUM));
		helper.setBlock(2, 1, 4, Blocks.GOLD_BLOCK);

		helper.assertTrue(BeaconPowers.pyramidTier(level, iron, 1) == Tier.IRON, "piramide de ferro");
		helper.assertTrue(BeaconPowers.pyramidTier(level, celestium, 1) == Tier.CELESTIUM, "piramide de celestio");
		helper.assertTrue(BeaconPowers.pyramidTier(level, mixed, 1) == Tier.GOLD, "piramide mista vale o metal mais fraco");
		helper.assertTrue(BeaconTiers.range(Tier.IRON, 1) == 20 && BeaconTiers.range(Tier.CELESTIUM, 1) == 80, "alcance de 1 camada");

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			// a 30 blocos: fora do alcance do ferro (20), dentro do celestio (80)
			player.teleportTo(iron.getX() + 30.5, iron.getY(), iron.getZ() + 0.5);
			List<Player> fromIron = BeaconPowers.apply(level, iron, 1, BeaconPowers.pyramidTier(level, iron, 1), MobEffects.SPEED, null);
			helper.assertTrue(!fromIron.contains(player) && !player.hasEffect(MobEffects.SPEED), "ferro nao alcanca 30 blocos");
			player.teleportTo(celestium.getX() + 30.5, celestium.getY(), celestium.getZ() + 0.5);
			List<Player> fromCelestium = BeaconPowers.apply(level, celestium, 1, BeaconPowers.pyramidTier(level, celestium, 1), MobEffects.SPEED, null);
			helper.assertTrue(fromCelestium.contains(player) && player.hasEffect(MobEffects.SPEED), "celestio alcanca 30 blocos");

			// reforco do primario com 4 camadas: netherite fica no II, celestio chega ao III
			player.removeAllEffects();
			BeaconPowers.apply(level, celestium, 4, Tier.NETHERITE, MobEffects.HASTE, MobEffects.HASTE);
			MobEffectInstance haste = player.getEffect(MobEffects.HASTE);
			helper.assertTrue(haste != null && haste.getAmplifier() == 1, "netherite: Pressa II");
			player.removeAllEffects();
			BeaconPowers.apply(level, celestium, 4, Tier.CELESTIUM, MobEffects.HASTE, MobEffects.HASTE);
			haste = player.getEffect(MobEffects.HASTE);
			helper.assertTrue(haste != null && haste.getAmplifier() == 2, "celestio: Pressa III");

			// poderes extras: Garimpo so com infernita+, Voo so com celestio
			player.removeAllEffects();
			BeaconPowers.apply(level, celestium, 4, Tier.NETHERITE, MobEffects.SPEED, ModEffects.PROSPECTING);
			helper.assertTrue(!player.hasEffect(ModEffects.PROSPECTING), "netherite nao da Garimpo");
			BeaconPowers.apply(level, celestium, 4, Tier.INFERNITE, MobEffects.SPEED, ModEffects.PROSPECTING);
			helper.assertTrue(player.hasEffect(ModEffects.PROSPECTING), "infernita da Garimpo");
			BeaconPowers.apply(level, celestium, 4, Tier.ASTRALITE, MobEffects.SPEED, ModEffects.BEACON_FLIGHT);
			helper.assertTrue(!player.hasEffect(ModEffects.BEACON_FLIGHT), "astralita nao da Voo");
			BeaconPowers.apply(level, celestium, 4, Tier.CELESTIUM, MobEffects.SPEED, ModEffects.BEACON_FLIGHT);
			helper.assertTrue(player.hasEffect(ModEffects.BEACON_FLIGHT), "celestio da Voo");
			BeaconFlight.unregister(level, celestium);

			// o sinalizador aceita os extras como secundario (mixin em validateEffects)
			helper.assertTrue(BeaconBlockEntity.validateEffects(MobEffects.HASTE, ModEffects.PROSPECTING, 4), "Garimpo valido com 4 camadas");
			helper.assertTrue(!BeaconBlockEntity.validateEffects(MobEffects.HASTE, ModEffects.BEACON_FLIGHT, 3), "Voo exige 4 camadas");
			helper.assertTrue(!BeaconBlockEntity.validateEffects(null, ModEffects.BEACON_FLIGHT, 4), "Voo exige primario");
		} finally {
			remove(helper, player);
		}
		helper.succeed();
	}

	/**
	 * Caminho real: piramide de 4 camadas de celestio, o sinalizador vanilla calcula as camadas, o menu (com o data slot
	 * do metal) aceita Voo, o mixin aplica o efeito e o jogador ganha voo; fora do alcance perde.
	 */
	@GameTest(maxTicks = 500, skyAccess = true, padding = 3)
	public void celestiumBeaconGrantsFlightInRangeAndRemovesItOutside(GameTestHelper helper) {
		Block celestium = ModBlocks.storage(Tier.CELESTIUM);
		for (int layer = 1; layer <= 4; layer++) {
			int half = 5 - layer;
			for (int x = 4 - half; x <= 4 + half; x++) {
				for (int z = 4 - half; z <= 4 + half; z++) {
					helper.setBlock(x, layer, z, celestium);
				}
			}
		}
		helper.setBlock(4, 5, 4, Blocks.BEACON);
		BlockPos beaconPos = helper.absolutePos(new BlockPos(4, 5, 4));
		BeaconBlockEntity beacon = helper.getBlockEntity(new BlockPos(4, 5, 4), BeaconBlockEntity.class);
		ServerPlayer player = survivalPlayer(helper);
		player.teleportTo(beaconPos.getX() + 3.5, beaconPos.getY(), beaconPos.getZ() + 0.5);
		helper.assertTrue(!player.getAbilities().mayfly, "sobrevivencia comeca sem voo");
		BeaconMenu menu = (BeaconMenu) beacon.createMenu(0, player.getInventory(), player);
		boolean[] configured = {false};
		boolean[] flew = {false};
		helper.onEachTick(() -> {
			if (!configured[0] && menu.getLevels() == 4) {
				helper.assertTrue(((BeaconMenuTier) menu).mlinnotas$tierCode() == BeaconTiers.encode(Tier.CELESTIUM), "menu ve piramide de celestio");
				menu.getSlot(0).set(new ItemStack(Items.IRON_INGOT));
				helper.assertTrue(menu.updateEffects(Optional.of(MobEffects.SPEED), Optional.of(ModEffects.BEACON_FLIGHT)), "Voo aceito no menu");
				configured[0] = true;
			}
			if (configured[0] && !flew[0] && player.getAbilities().mayfly) {
				flew[0] = true;
				helper.assertTrue(player.hasEffect(ModEffects.BEACON_FLIGHT), "efeito Voo aplicado pelo sinalizador");
				// sai do alcance (200 blocos): perde o voo e o efeito
				player.teleportTo(beaconPos.getX() + 260.5, beaconPos.getY(), beaconPos.getZ() + 0.5);
			}
		});
		helper.succeedWhen(() -> {
			helper.assertTrue(flew[0], "ainda nao voou");
			helper.assertTrue(!player.getAbilities().mayfly, "voo deveria sumir fora do alcance");
			helper.assertTrue(!player.hasEffect(ModEffects.BEACON_FLIGHT), "efeito deveria sumir fora do alcance");
			helper.assertTrue(!BeaconFlight.granted(player), "marca de voo limpa");
			remove(helper, player);
		});
	}

	@GameTest
	public void flightEndsWhenTheBeaconStops(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos beaconPos = smallPyramid(helper, 3, 3, ModBlocks.storage(Tier.CELESTIUM));
		ServerPlayer player = survivalPlayer(helper);
		try {
			player.teleportTo(beaconPos.getX() + 2.5, beaconPos.getY(), beaconPos.getZ() + 0.5);
			BeaconPowers.apply(level, beaconPos, 4, Tier.CELESTIUM, MobEffects.SPEED, ModEffects.BEACON_FLIGHT);
			BeaconFlight.tick(player);
			helper.assertTrue(player.getAbilities().mayfly, "voo dentro do alcance");
			player.getAbilities().flying = true;
			helper.setBlock(3, 2, 3, Blocks.AIR);
			BeaconFlight.tick(player);
			helper.assertTrue(!player.getAbilities().mayfly && !player.getAbilities().flying, "sinalizador removido: sem voo");
			helper.assertTrue(player.hasEffect(MobEffects.SLOW_FALLING), "queda lenta ao perder o voo no ar");
		} finally {
			remove(helper, player);
		}
		helper.succeed();
	}

	private static int countDrops(GameTestHelper helper, Item item) {
		AABB box = new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12);
		int total = 0;
		for (ItemEntity entity : helper.getLevel().getEntitiesOfClass(ItemEntity.class, box)) {
			if (entity.getItem().is(item)) {
				total += entity.getItem().getCount();
			}
		}
		return total;
	}

	@GameTest
	public void prospectingGivesExtraOre(GameTestHelper helper) {
		ServerPlayer player = survivalPlayer(helper);
		try {
			int ores = 24;
			// sem Garimpo: exatamente 1 ferro bruto por minerio
			for (int i = 0; i < ores; i++) {
				helper.setBlock(i % 6, 1 + i / 6, 0, Blocks.IRON_ORE);
			}
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
			for (int i = 0; i < ores; i++) {
				player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(i % 6, 1 + i / 6, 0)));
			}
			int plain = countDrops(helper, Items.RAW_IRON);
			helper.assertTrue(plain == ores, "sem Garimpo: " + ores + " brutos, veio " + plain);
			helper.killAllEntitiesOfClass(ItemEntity.class);

			// com Garimpo: 50% de +1 por minerio
			player.addEffect(new MobEffectInstance(ModEffects.PROSPECTING, 2000, 0, true, true));
			for (int i = 0; i < ores; i++) {
				helper.setBlock(i % 6, 1 + i / 6, 2, Blocks.IRON_ORE);
			}
			for (int i = 0; i < ores; i++) {
				player.gameMode.destroyBlock(helper.absolutePos(new BlockPos(i % 6, 1 + i / 6, 2)));
			}
			int boosted = countDrops(helper, Items.RAW_IRON);
			helper.assertTrue(boosted > ores + 2 && boosted <= ores * 2, "com Garimpo deveria passar de " + ores + ", veio " + boosted);

			// nada extra de bloco que nao e minerio, nem com ferramenta errada
			helper.setBlock(0, 1, 4, Blocks.STONE);
			helper.assertTrue(Prospecting.extraDrop(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 4)), Blocks.STONE.defaultBlockState(), null,
					player, player.getMainHandItem(), () -> 0.0).isEmpty(), "pedra nao e minerio");
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WOODEN_PICKAXE));
			helper.assertTrue(Prospecting.extraDrop(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 4)), Blocks.DIAMOND_ORE.defaultBlockState(), null,
					player, player.getMainHandItem(), () -> 0.0).isEmpty(), "ferramenta errada nao da extra");
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
			ItemStack diamond = Prospecting.extraDrop(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 4)), Blocks.DIAMOND_ORE.defaultBlockState(),
					null, player, new ItemStack(Items.DIAMOND_PICKAXE), () -> 0.0);
			helper.assertTrue(diamond.is(Items.DIAMOND) && diamond.getCount() == 1, "gema extra: " + diamond);
			ItemStack cobalt = Prospecting.extraDrop(helper.getLevel(), helper.absolutePos(new BlockPos(0, 1, 4)), ModBlocks.COBALT_ORE.defaultBlockState(),
					null, player, new ItemStack(Items.DIAMOND_PICKAXE), () -> 0.0);
			helper.assertTrue(cobalt.is(ModItems.raw(Tier.COBALT)), "minerio do mod tambem: " + cobalt);
		} finally {
			remove(helper, player);
		}
		helper.succeed();
	}

	private static boolean biomeHas(GameTestHelper helper, ResourceKey<net.minecraft.world.level.biome.Biome> biome,
			ResourceKey<net.minecraft.world.level.levelgen.placement.PlacedFeature> feature) {
		var value = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(biome).value();
		for (var step : value.getGenerationSettings().features()) {
			for (var holder : step) {
				if (holder.is(feature)) {
					return true;
				}
			}
		}
		return false;
	}

	@GameTest
	public void richVeinsGenerateInTheirDimension(GameTestHelper helper) {
		helper.assertTrue(biomeHas(helper, Biomes.PLAINS, ModWorldgen.ORE_COBALT_RICH), "veio rico de cobalto no Overworld");
		helper.assertTrue(!biomeHas(helper, Biomes.NETHER_WASTES, ModWorldgen.ORE_COBALT_RICH), "cobalto fora do Nether");
		helper.assertTrue(biomeHas(helper, Biomes.NETHER_WASTES, ModWorldgen.ORE_INFERNITE_RICH), "veio rico de infernita no Nether");
		helper.assertTrue(biomeHas(helper, NetherRegistry.INFERNITE_PEAKS, ModWorldgen.ORE_INFERNITE_RICH_PEAKS), "veio extra nos Picos de Infernita");
		helper.assertTrue(!biomeHas(helper, Biomes.NETHER_WASTES, ModWorldgen.ORE_INFERNITE_RICH_PEAKS), "veio dos picos so nos picos");
		helper.assertTrue(biomeHas(helper, Biomes.THE_END, ModWorldgen.ORE_ENDITE_RICH), "veio rico de endita no End");
		helper.assertTrue(!biomeHas(helper, Biomes.THE_END, ModWorldgen.ORE_ASTRALITE_RICH), "astralita rica nao na ilha central");
		helper.assertTrue(biomeHas(helper, Biomes.END_HIGHLANDS, ModWorldgen.ORE_ASTRALITE_RICH), "astralita rica nas ilhas externas");
		helper.assertTrue(!biomeHas(helper, Biomes.PLAINS, ModWorldgen.ORE_ENDITE_RICH), "endita fora do Overworld");
		helper.succeed();
	}

	@GameTest(maxTicks = 400)
	public void enditeFurnaceDoublesOres(GameTestHelper helper) {
		helper.setBlock(1, 1, 1, Blocks.FURNACE);
		helper.setBlock(3, 1, 1, Blocks.FURNACE);
		helper.setBlock(5, 1, 1, Blocks.FURNACE);
		AbstractFurnaceBlockEntity endite = helper.getBlockEntity(new BlockPos(1, 1, 1), AbstractFurnaceBlockEntity.class);
		AbstractFurnaceBlockEntity diamond = helper.getBlockEntity(new BlockPos(3, 1, 1), AbstractFurnaceBlockEntity.class);
		AbstractFurnaceBlockEntity enditeSand = helper.getBlockEntity(new BlockPos(5, 1, 1), AbstractFurnaceBlockEntity.class);
		FurnaceUpgrades.setTier(endite, Tier.ENDITE);
		FurnaceUpgrades.setTier(diamond, Tier.DIAMOND);
		FurnaceUpgrades.setTier(enditeSand, Tier.ENDITE);
		endite.setItem(0, new ItemStack(Items.RAW_IRON, 4));
		diamond.setItem(0, new ItemStack(Items.RAW_IRON, 4));
		enditeSand.setItem(0, new ItemStack(Items.SAND, 4));
		for (AbstractFurnaceBlockEntity furnace : List.of(endite, diamond, enditeSand)) {
			furnace.setItem(1, new ItemStack(Items.COAL, 4));
		}
		helper.succeedWhen(() -> {
			helper.assertTrue(endite.getItem(0).isEmpty() && diamond.getItem(0).isEmpty() && enditeSand.getItem(0).isEmpty(), "ainda fundindo");
			int doubled = endite.getItem(2).getCount();
			int normal = diamond.getItem(2).getCount();
			int glass = enditeSand.getItem(2).getCount();
			helper.assertTrue(endite.getItem(2).is(Items.IRON_INGOT) && doubled == 8, "endita: 4 brutos -> 8 lingotes, veio " + doubled);
			helper.assertTrue(normal == 4, "diamante: 4 brutos -> 4 lingotes, veio " + normal);
			helper.assertTrue(glass == 4, "areia nao dobra, veio " + glass);
		});
	}
}
