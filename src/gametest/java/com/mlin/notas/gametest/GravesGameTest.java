package com.mlin.notas.gametest;

import com.mlin.notas.accessories.AccessoryData;
import com.mlin.notas.accessories.AccessoryRegistry;
import com.mlin.notas.accessories.AccessoryServer;
import com.mlin.notas.grave.GraveBlockEntity;
import com.mlin.notas.grave.GraveContents;
import com.mlin.notas.grave.GraveData;
import com.mlin.notas.grave.GraveHooks;
import com.mlin.notas.grave.GraveInfo;
import com.mlin.notas.grave.GraveRegistry;
import com.mlin.notas.grave.GraveRules;
import com.mlin.notas.grave.GraveServer;
import com.mlin.notas.util.XpMath;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Lapides (lote proprio; a morte com keepInventory ligado fica num lote separado porque a gamerule e do mundo): morrer
 * guarda inventario, armadura, mao secundaria, acessorios e metade do XP; morrer na lava poe a lapide em chao seguro;
 * o dono agachado recebe tudo nos mesmos encaixes; quebrar entrega o conteudo; explosao e funil nao mexem; o gancho
 * {@link GraveHooks} pode levar os itens.
 */
public class GravesGameTest {
	private static final String ENV = "mlinnotas-gametest:graves";
	private static final String ENV_KEEP = "mlinnotas-gametest:graves_keep";

	private static final List<ItemStack> HOOKED = new java.util.concurrent.CopyOnWriteArrayList<>();

	static {
		// interceptador de teste: so age em jogadores com nome "hooked"
		GraveHooks.CLAIM_DEATH_ITEMS.register((player, level, deathPos, contents) -> {
			if (!player.getName().getString().startsWith("hooked")) {
				return false;
			}
			HOOKED.addAll(contents.takeMatching(stack -> true));
			contents.setXp(0);
			return true;
		});
	}

	private static void expect(boolean condition, String message) {
		if (!condition) {
			throw new IllegalStateException(message);
		}
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static void equipFull(ServerPlayer player) {
		player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
		player.getInventory().setItem(1, new ItemStack(Items.TORCH, 40));
		player.getInventory().setItem(20, new ItemStack(Items.COBBLESTONE, 64));
		player.getInventory().setItem(35, new ItemStack(Items.BREAD, 7));
		player.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
		player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
		player.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
		player.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
		player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
		expect(AccessoryServer.equip(player, new ItemStack(AccessoryRegistry.item("champion_seal"))), "selo nao equipou");
		player.giveExperienceLevels(10);
	}

	private static GraveInfo graveOf(MinecraftServer server, ServerPlayer player) {
		List<GraveInfo> graves = GraveData.get(server).index().ofOwner(player.getStringUUID());
		expect(!graves.isEmpty(), "nenhuma lapide de " + player.getName().getString());
		return graves.get(0);
	}

	private static BlockPos pos(GraveInfo info) {
		return new BlockPos(info.x(), info.y(), info.z());
	}

	/** Remove a lapide sem soltar nada (limpeza do teste). */
	private static void discard(ServerLevel level, BlockPos pos) {
		if (level.getBlockEntity(pos) instanceof GraveBlockEntity grave) {
			grave.clearContent();
			grave.takeXp();
		}
		level.removeBlock(pos, false);
	}

	private static void removeItems(GameTestHelper helper) {
		helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(16)).forEach(ItemEntity::discard);
	}

	private static boolean isGrave(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).is(GraveRegistry.GRAVE);
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void deathStoresEverythingInAGrave(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = SkillsGameTest.survivalPlayer(helper, "grave_full");
		try {
			equipFull(player);
			int points = XpMath.totalPoints(player.experienceLevel, player.experienceProgress);
			player.die(player.damageSources().generic());
			GraveInfo info = graveOf(level.getServer(), player);
			BlockPos pos = pos(info);
			expect(pos.equals(helper.absolutePos(new BlockPos(3, 1, 3))), "lapide fora do lugar da morte: " + pos);
			expect(isGrave(level, pos) && level.getBlockEntity(pos) instanceof GraveBlockEntity, "bloco da lapide");
			GraveBlockEntity grave = (GraveBlockEntity) level.getBlockEntity(pos);
			expect(grave.getItem(0).is(Items.IRON_SWORD), "espada na barra 0");
			expect(grave.getItem(1).getCount() == 40, "tochas na barra 1");
			expect(grave.getItem(20).is(Items.COBBLESTONE) && grave.getItem(35).is(Items.BREAD), "inventario principal");
			expect(grave.getItem(39).is(Items.IRON_HELMET) && grave.getItem(38).is(Items.IRON_CHESTPLATE)
					&& grave.getItem(37).is(Items.IRON_LEGGINGS) && grave.getItem(36).is(Items.IRON_BOOTS), "armadura");
			expect(grave.getItem(40).is(Items.SHIELD), "mao secundaria");
			boolean seal = false;
			for (int i = GraveRules.ACCESSORY_START; i < GraveRules.EXTRA_START; i++) {
				seal |= grave.getItem(i).is(AccessoryRegistry.item("champion_seal"));
			}
			expect(seal, "acessorio na lapide");
			expect(grave.xp() == GraveRules.storedXp(points) && grave.xp() > 0, "XP guardado " + grave.xp() + " de " + points);
			expect(info.items() == 10 && info.xp() == grave.xp(), "resumo na lista: " + info);
			expect(grave.ownerName().equals("grave_full") && grave.isOwner(player), "dono");
			expect(player.getInventory().isEmpty() && AccessoryData.get(player).stream().allMatch(ItemStack::isEmpty), "jogador esvaziado");
			List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(6));
			expect(drops.isEmpty(), "nada no chao: " + drops);
			discard(level, pos);
			expect(GraveData.get(level.getServer()).index().ofOwner(player.getStringUUID()).isEmpty(), "lista atualizada ao remover");
			helper.succeed();
		} finally {
			level.getServer().getPlayerList().remove(player);
		}
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void lavaDeathPutsTheGraveOnSafeGround(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		// poca de lava 3x3 (camada sobre o chao, com borda de pedra embaixo)
		for (int x = 2; x <= 4; x++) {
			for (int z = 2; z <= 4; z++) {
				helper.setBlock(x, 1, z, Blocks.LAVA);
			}
		}
		ServerPlayer player = SkillsGameTest.survivalPlayer(helper, "grave_lava");
		try {
			player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
			BlockPos lava = helper.absolutePos(new BlockPos(3, 1, 3));
			player.teleportTo(lava.getX() + 0.5, lava.getY() + 0.1, lava.getZ() + 0.5);
			player.die(level.damageSources().lava());
			BlockPos pos = pos(graveOf(level.getServer(), player));
			expect(isGrave(level, pos), "lapide colocada");
			expect(level.getFluidState(pos).isEmpty(), "lapide fora da lava: " + pos);
			expect(level.getBlockState(pos.below()).is(Blocks.STONE), "chao firme embaixo");
			BlockPos rel = helper.relativePos(pos);
			expect(rel.getY() == 1 && (rel.getX() < 2 || rel.getX() > 4 || rel.getZ() < 2 || rel.getZ() > 4), "na margem: " + rel);
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				expect(!level.getFluidState(pos.relative(dir)).is(net.minecraft.tags.FluidTags.LAVA), "sem lava encostada");
			}
			expect(((GraveBlockEntity) level.getBlockEntity(pos)).getItem(0).getCount() == 3, "diamantes salvos");
			discard(level, pos);
			helper.succeed();
		} finally {
			level.getServer().getPlayerList().remove(player);
		}
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void ownerSneakClickRestoresSlots(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer owner = SkillsGameTest.survivalPlayer(helper, "grave_owner");
		try {
			GraveContents contents = new GraveContents();
			contents.set(0, new ItemStack(Items.IRON_PICKAXE));
			contents.set(5, new ItemStack(Items.TORCH, 12));
			contents.set(39, new ItemStack(Items.IRON_HELMET));
			contents.set(36, new ItemStack(Items.IRON_BOOTS));
			contents.set(40, new ItemStack(Items.SHIELD));
			contents.set(GraveRules.ACCESSORY_START + 1, new ItemStack(AccessoryRegistry.item("champion_seal")));
			contents.setXp(100);
			BlockPos pos = helper.absolutePos(new BlockPos(5, 1, 5));
			GraveServer.place(level, pos, Direction.NORTH, owner.getUUID(), "grave_owner", contents);
			// a barra 0 ja esta ocupada: a picareta vai para outro lugar do inventario
			owner.getInventory().setItem(0, new ItemStack(Items.STICK));
			owner.setShiftKeyDown(true);
			GraveServer.use(owner, level, pos, (GraveBlockEntity) level.getBlockEntity(pos));
			expect(!isGrave(level, pos), "lapide some depois de devolver");
			expect(owner.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && owner.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS),
					"armadura vestida");
			expect(owner.getOffhandItem().is(Items.SHIELD), "escudo na mao secundaria");
			expect(owner.getInventory().getItem(5).getCount() == 12, "tochas de volta na barra 5");
			expect(owner.getInventory().getItem(0).is(Items.STICK) && owner.getInventory().countItem(Items.IRON_PICKAXE) == 1,
					"picareta guardada em outro lugar");
			expect(AccessoryData.get(owner, 1).is(AccessoryRegistry.item("champion_seal")), "anel no mesmo encaixe (2o anel)");
			expect(XpMath.totalPoints(owner.experienceLevel, owner.experienceProgress) >= 99, "XP devolvido");
			expect(GraveData.get(level.getServer()).index().ofOwner(owner.getStringUUID()).isEmpty(), "fora da lista");
			removeItems(helper);
			helper.succeed();
		} finally {
			level.getServer().getPlayerList().remove(owner);
		}
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void breakingHandsTheContents(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer friend = SkillsGameTest.survivalPlayer(helper, "grave_friend");
		try {
			GraveContents contents = new GraveContents();
			contents.set(3, new ItemStack(Items.GOLD_INGOT, 9));
			contents.set(38, new ItemStack(Items.IRON_CHESTPLATE));
			contents.setXp(30);
			BlockPos pos = helper.absolutePos(new BlockPos(2, 1, 2));
			GraveServer.place(level, pos, Direction.SOUTH, java.util.UUID.randomUUID(), "outra_pessoa", contents);
			expect(friend.gameMode.destroyBlock(pos), "quebrou");
			expect(!isGrave(level, pos), "bloco saiu");
			expect(friend.getInventory().countItem(Items.GOLD_INGOT) == 9 && friend.getInventory().countItem(Items.IRON_CHESTPLATE) == 1,
					"conteudo foi para quem quebrou");
			List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4));
			expect(drops.isEmpty(), "nada no chao");

			// removida por fora (comando): solta tudo no chao, nada se perde
			BlockPos other = helper.absolutePos(new BlockPos(5, 1, 2));
			GraveContents more = new GraveContents();
			more.set(0, new ItemStack(Items.EMERALD, 5));
			GraveServer.place(level, other, Direction.NORTH, java.util.UUID.randomUUID(), "x", more);
			level.setBlock(other, Blocks.AIR.defaultBlockState(), 3);
			expect(level.getEntitiesOfClass(ItemEntity.class, new AABB(other).inflate(3), e -> e.getItem().is(Items.EMERALD)).size() == 1,
					"esmeraldas no chao");
			removeItems(helper);
			helper.succeed();
		} finally {
			level.getServer().getPlayerList().remove(friend);
		}
	}

	@GameTest(environment = ENV, maxTicks = 60)
	public void explosionsHoppersAndBossesCannotTakeIt(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		GraveContents contents = new GraveContents();
		contents.set(0, new ItemStack(Items.DIAMOND, 4));
		BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
		helper.setBlock(3, 1, 3, Blocks.HOPPER);
		GraveServer.place(level, pos, Direction.NORTH, java.util.UUID.randomUUID(), "x", contents);
		expect(level.getBlockState(pos).is(BlockTags.WITHER_IMMUNE) && level.getBlockState(pos).is(BlockTags.DRAGON_IMMUNE), "imune a chefes");
		level.explode(null, pos.getX() + 1.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4.0F, Level.ExplosionInteraction.TNT);
		helper.runAfterDelay(30, () -> {
			expect(isGrave(level, pos), "explosao nao quebra a lapide");
			GraveBlockEntity grave = (GraveBlockEntity) level.getBlockEntity(pos);
			expect(grave.getItem(0).getCount() == 4, "funil nao puxou nada");
			discard(level, pos);
			removeItems(helper);
			helper.succeed();
		});
	}

	@GameTest(environment = ENV, maxTicks = 100)
	public void hookCanClaimTheDeathItems(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer player = SkillsGameTest.survivalPlayer(helper, "hooked_player");
		try {
			player.getInventory().setItem(4, new ItemStack(Items.APPLE, 5));
			HOOKED.clear();
			player.die(player.damageSources().generic());
			expect(GraveData.get(level.getServer()).index().ofOwner(player.getStringUUID()).isEmpty(), "sem lapide");
			expect(HOOKED.stream().anyMatch(stack -> stack.is(Items.APPLE) && stack.getCount() == 5), "o gancho levou as macas");
			expect(level.getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(4)).isEmpty(), "nada no chao");
			helper.succeed();
		} finally {
			level.getServer().getPlayerList().remove(player);
		}
	}

	@GameTest(environment = ENV_KEEP, maxTicks = 100)
	public void keepInventoryMakesNoGrave(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		// o padrao do mod ja foi aplicado ao subir o servidor, e so uma vez
		expect(Boolean.TRUE.equals(((AttachmentTarget) server.overworld()).getAttached(GraveServer.KEEP_INVENTORY_DEFAULT_APPLIED)), "marca aplicada");
		expect(!GraveServer.applyKeepInventoryDefault(server), "nao aplica de novo");
		GameRules rules = server.getGameRules();
		boolean before = rules.get(GameRules.KEEP_INVENTORY);
		rules.set(GameRules.KEEP_INVENTORY, true, server);
		ServerPlayer player = SkillsGameTest.survivalPlayer(helper, "grave_keep");
		try {
			player.getInventory().setItem(0, new ItemStack(Items.IRON_SWORD));
			player.die(player.damageSources().generic());
			expect(GraveData.get(server).index().ofOwner(player.getStringUUID()).isEmpty(), "sem lapide com keepInventory");
			expect(player.getInventory().getItem(0).is(Items.IRON_SWORD), "inventario mantido");
			expect(!GraveServer.applyKeepInventoryDefault(server) && rules.get(GameRules.KEEP_INVENTORY), "escolha do jogador mantida");
			helper.succeed();
		} finally {
			rules.set(GameRules.KEEP_INVENTORY, before, server);
			server.getPlayerList().remove(player);
		}
	}
}
