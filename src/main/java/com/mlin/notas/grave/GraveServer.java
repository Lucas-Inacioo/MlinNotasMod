package com.mlin.notas.grave;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.accessories.AccessoryData;
import com.mlin.notas.accessories.AccessoryDef;
import com.mlin.notas.accessories.AccessoryServer;
import com.mlin.notas.accessories.AccessorySlots;
import com.mlin.notas.util.XpMath;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Prediction;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lado servidor das lapides: na morte sem keepInventory guarda inventario, equipamento, acessorios e
 * {@link GraveRules#XP_KEEP_PERCENT}% do XP numa lapide no lugar seguro mais perto ({@link GravePlacement}); abrir,
 * devolver nos encaixes (dono agachado), quebrar, sincronizar a lista com os clientes e aplicar uma vez por mundo o
 * padrao keepInventory = false.
 */
public final class GraveServer {
	/** Marca no Overworld: o padrao do mod para keepInventory ja foi aplicado neste mundo. */
	public static final AttachmentType<Boolean> KEEP_INVENTORY_DEFAULT_APPLIED = AttachmentRegistry.create(
			MlinNotas.id("keep_inventory_default_applied"), builder -> builder.persistent(Codec.BOOL));
	/** Conquista escondida (sem tela) dada ao recuperar a propria lapide; a quest "Descanse em Paz" olha para ela. */
	public static final String RECOVERED_ADVANCEMENT = "mlinnotas:recovered_grave";

	/** Ultimo lugar com chao de cada jogador (para morte no vazio). So na memoria. */
	private static final Map<UUID, GlobalPos> LAST_GROUND = new HashMap<>();

	private GraveServer() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(GravePayloads.Sync.TYPE, GravePayloads.Sync.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendList(handler.player));
		ServerLifecycleEvents.SERVER_STARTED.register(GraveServer::applyKeepInventoryDefault);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> LAST_GROUND.clear());
		ServerTickEvents.END_SERVER_TICK.register(GraveServer::trackGround);
	}

	/** Liga o padrao do mod (keepInventory = false) uma unica vez por mundo. Devolve true se mudou a gamerule. */
	public static boolean applyKeepInventoryDefault(MinecraftServer server) {
		AttachmentTarget marker = (AttachmentTarget) server.overworld();
		boolean applied = Boolean.TRUE.equals(marker.getAttached(KEEP_INVENTORY_DEFAULT_APPLIED));
		if (!GraveRules.shouldApplyKeepInventoryDefault(applied)) {
			return false;
		}
		GameRules rules = server.getGameRules();
		boolean change = rules.get(GameRules.KEEP_INVENTORY) != GraveRules.MOD_DEFAULT_KEEP_INVENTORY;
		if (change) {
			rules.set(GameRules.KEEP_INVENTORY, GraveRules.MOD_DEFAULT_KEEP_INVENTORY, server);
			MlinNotas.LOGGER.info("[{}] keepInventory desligado (padrao do mod: lapides guardam os itens)", MlinNotas.MOD_ID);
		}
		marker.setAttached(KEEP_INVENTORY_DEFAULT_APPLIED, true);
		return change;
	}

	private static void trackGround(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (player.onGround() && !player.isSpectator() && !player.isInLava() && player.isAlive()) {
				LAST_GROUND.put(player.getUUID(), GlobalPos.of(player.level().dimension(), player.blockPosition()));
			}
		}
	}

	/** Para testes: esquece/define o ultimo chao do jogador. */
	public static void setLastGround(ServerPlayer player, GlobalPos pos) {
		if (pos == null) {
			LAST_GROUND.remove(player.getUUID());
		} else {
			LAST_GROUND.put(player.getUUID(), pos);
		}
	}

	// ---------------------------------------------------------------- morte

	/**
	 * Chamado no inicio de {@code Player.dropEquipment} (mixin), so sem keepInventory e depois de sumir com os itens
	 * com Maldicao do Desaparecimento: tira tudo do jogador para uma lapide. O que nao der para guardar continua no
	 * inventario e cai como no vanilla.
	 */
	public static void onDeath(ServerPlayer player, ServerLevel level) {
		if (player.isSpectator()) {
			return;
		}
		BlockPos deathPos = BlockPos.containing(player.getX(), player.getY() + 0.2, player.getZ());
		GraveContents contents = capture(player);
		contents.setXp(GraveRules.storedXp(XpMath.totalPoints(player.experienceLevel, player.experienceProgress)));
		player.skipDropExperience();

		GraveHooks.CLAIM_DEATH_ITEMS.invoker().claim(player, level, deathPos, contents);
		if (contents.isEmpty()) {
			return;
		}
		GravePlacement.Result spot = findSpot(level, player, deathPos);
		if (spot == null) {
			// sem lugar nenhum (coluna inteira de blocos): devolve ao inventario para o vanilla soltar, XP em orbes
			giveBack(player, contents);
			return;
		}
		BlockPos pos = new BlockPos(spot.pos().x(), spot.pos().y(), spot.pos().z());
		GraveInfo info = place(level, pos, player.getDirection().getOpposite(), player.getUUID(), player.getName().getString(), contents);
		announce(level.getServer(), player, info);
		GraveHooks.GRAVE_PLACED.invoker().placed(player, level, pos, info);
	}

	/** Copia e esvazia inventario (0..42) e acessorios na disposicao da lapide. */
	static GraveContents capture(ServerPlayer player) {
		GraveContents contents = new GraveContents();
		Inventory inventory = player.getInventory();
		int size = Math.min(inventory.getContainerSize(), GraveRules.INVENTORY_SLOTS);
		for (int i = 0; i < size; i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty()) {
				contents.set(i, stack.copy());
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
		// qualquer posicao a mais (outro mod) vai para as sobras; o que nao couber fica e cai como no vanilla
		for (int i = size; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && contents.addExtra(stack.copy())) {
				inventory.setItem(i, ItemStack.EMPTY);
			}
		}
		java.util.List<ItemStack> accessories = AccessoryData.get(player);
		for (int i = 0; i < GraveRules.ACCESSORY_SLOTS && i < accessories.size(); i++) {
			contents.set(GraveRules.ACCESSORY_START + i, accessories.get(i).copy());
		}
		AccessoryData.clear(player);
		AccessoryServer.refresh(player);
		return contents;
	}

	private static void giveBack(ServerPlayer player, GraveContents contents) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < GraveRules.SIZE; i++) {
			ItemStack stack = contents.take(i);
			if (stack.isEmpty()) {
				continue;
			}
			int slot = GraveRules.inventoryIndex(i);
			int accessory = GraveRules.accessoryIndex(i);
			if (slot >= 0 && inventory.getItem(slot).isEmpty()) {
				inventory.setItem(slot, stack);
			} else if (accessory >= 0 && AccessoryData.get(player, accessory).isEmpty()) {
				// volta ao encaixe: o AccessoryServer solta no AFTER_DEATH como antes das lapides
				AccessoryData.set(player, accessory, stack);
			} else {
				player.drop(stack, true, Prediction.SERVER_ONLY);
			}
		}
		if (contents.xp() > 0) {
			net.minecraft.world.entity.ExperienceOrb.award(player.level(), player.position(), contents.xp());
		}
	}

	static GravePlacement.Result findSpot(ServerLevel level, ServerPlayer player, BlockPos deathPos) {
		int minY = level.getMinY();
		int maxY = Math.min(level.getMaxY(), minY + level.dimensionType().logicalHeight() - 1);
		GlobalPos last = LAST_GROUND.get(player.getUUID());
		GravePlacement.Pos lastGround = last != null && last.dimension() == level.dimension()
				? new GravePlacement.Pos(last.pos().getX(), last.pos().getY(), last.pos().getZ())
				: null;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		return GravePlacement.find((x, y, z) -> cell(level, cursor.set(x, y, z)), deathPos.getX(), deathPos.getY(), deathPos.getZ(), minY, maxY,
				lastGround);
	}

	/** O que a celula e para o {@link GravePlacement}. */
	public static GravePlacement.Cell cell(ServerLevel level, BlockPos pos) {
		if (!level.isInWorldBounds(pos) || !level.isLoaded(pos)) {
			return GravePlacement.Cell.BLOCKED;
		}
		BlockState state = level.getBlockState(pos);
		FluidState fluid = state.getFluidState();
		if (fluid.is(FluidTags.LAVA) || state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(BlockTags.CAMPFIRES)) {
			return GravePlacement.Cell.LAVA;
		}
		if (!fluid.isEmpty()) {
			return GravePlacement.Cell.WATER;
		}
		if (state.canBeReplaced() && !state.hasBlockEntity()) {
			return GravePlacement.Cell.OPEN;
		}
		if (state.isFaceSturdy(level, pos, Direction.UP)) {
			return GravePlacement.Cell.SOLID;
		}
		return GravePlacement.Cell.BLOCKED;
	}

	/** Coloca a lapide e registra no {@link GraveData}. */
	public static GraveInfo place(ServerLevel level, BlockPos pos, Direction facing, UUID owner, String ownerName, GraveContents contents) {
		boolean water = level.getFluidState(pos).is(Fluids.WATER);
		BlockState state = GraveRegistry.GRAVE.defaultBlockState().setValue(GraveBlock.FACING, facing.getAxis().isHorizontal() ? facing : Direction.NORTH)
				.setValue(GraveBlock.WATERLOGGED, water);
		level.setBlock(pos, state, Block.UPDATE_ALL);
		long day = level.getServer().overworld().getOverworldClockTime() / 24000L + 1;
		if (level.getBlockEntity(pos) instanceof GraveBlockEntity grave) {
			grave.fill(owner, ownerName, contents, day);
		}
		GraveInfo info = new GraveInfo(owner.toString(), ownerName, dimension(level), pos.getX(), pos.getY(), pos.getZ(), contents.stackCount(),
				contents.xp(), day);
		GraveData.get(level.getServer()).put(info);
		broadcast(level.getServer());
		return info;
	}

	private static void announce(MinecraftServer server, ServerPlayer player, GraveInfo info) {
		Component coords = Component.literal(info.x() + ", " + info.y() + ", " + info.z()).withStyle(style -> style.withColor(ChatFormatting.GREEN)
				.withClickEvent(new ClickEvent.CopyToClipboard(info.x() + " " + info.y() + " " + info.z()))
				.withHoverEvent(new HoverEvent.ShowText(Component.translatable("chat.copy.click"))));
		Component dim = dimensionName(info.dimension());
		player.sendSystemMessage(Component.translatable("message.mlinnotas.grave.placed", coords, dim, info.items(), info.xp())
				.withStyle(ChatFormatting.GRAY));
		Component others = Component.translatable("message.mlinnotas.grave.placed_other", info.ownerName(), coords, dim).withStyle(ChatFormatting.GRAY);
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other != player) {
				other.sendSystemMessage(others);
			}
		}
	}

	static Component dimensionName(String dimension) {
		return switch (dimension) {
			case "minecraft:overworld" -> Component.translatable("message.mlinnotas.grave.dim.overworld");
			case "minecraft:the_nether" -> Component.translatable("message.mlinnotas.grave.dim.nether");
			case "minecraft:the_end" -> Component.translatable("message.mlinnotas.grave.dim.end");
			default -> Component.literal(dimension);
		};
	}

	// ---------------------------------------------------------------- usar, quebrar, fechar

	/** Botao direito: dono agachado recebe tudo de volta; senao abre a tela (o dono ja leva o XP). */
	public static void use(ServerPlayer player, ServerLevel level, BlockPos pos, GraveBlockEntity grave) {
		if (grave.isOwner(player) && player.isShiftKeyDown()) {
			restore(player, level, pos, grave);
			return;
		}
		if (grave.isOwner(player)) {
			giveXp(player, grave);
		}
		Component title = Component.translatable("container.mlinnotas.gravestone", grave.ownerName().isEmpty() ? "?" : grave.ownerName());
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new GraveMenu(id, inventory, grave), title));
		level.playSound(null, pos, SoundEvents.STONE_HIT, SoundSource.BLOCKS, 0.8F, 0.7F);
	}

	/**
	 * Devolve tudo ao dono: cada item no mesmo encaixe (armadura vestida, barra de atalhos, mao secundaria,
	 * acessorios) se estiver livre; o resto no inventario e, sem espaco, no chao. Remove a lapide.
	 */
	public static void restore(ServerPlayer player, ServerLevel level, BlockPos pos, GraveBlockEntity grave) {
		restore(player, level, pos, grave, true);
	}

	static void restore(ServerPlayer player, ServerLevel level, BlockPos pos, GraveBlockEntity grave, boolean removeBlock) {
		Inventory inventory = player.getInventory();
		java.util.List<ItemStack> leftovers = new java.util.ArrayList<>();
		for (int i = 0; i < GraveRules.SIZE; i++) {
			ItemStack stack = grave.removeItemNoUpdate(i);
			if (stack.isEmpty()) {
				continue;
			}
			int slot = GraveRules.inventoryIndex(i);
			int accessory = GraveRules.accessoryIndex(i);
			if (slot >= 0 && inventory.getItem(slot).isEmpty() && fitsSlot(player, slot, stack)) {
				inventory.setItem(slot, stack);
			} else if (accessory >= 0 && fitsAccessory(player, accessory, stack)) {
				AccessoryData.set(player, accessory, stack);
			} else {
				leftovers.add(stack);
			}
		}
		for (ItemStack stack : leftovers) {
			inventory.placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
		}
		AccessoryServer.refresh(player);
		giveXp(player, grave);
		grave.setChanged();
		player.containerMenu.broadcastChanges();
		recovered(player);
		level.playSound(null, pos, SoundEvents.ARMOR_EQUIP_GENERIC.value(), SoundSource.PLAYERS, 1.0F, 1.0F);
		player.sendOverlayMessage(Component.translatable("message.mlinnotas.grave.restored"));
		if (removeBlock) {
			remove(level, pos);
		}
	}

	private static boolean fitsSlot(ServerPlayer player, int slot, ItemStack stack) {
		EquipmentSlot equipment = Inventory.EQUIPMENT_SLOT_MAPPING.get(slot);
		if (equipment == null || equipment == EquipmentSlot.OFFHAND) {
			return true;
		}
		return player.canUseSlot(equipment) && player.isEquippableInSlot(stack, equipment);
	}

	private static boolean fitsAccessory(ServerPlayer player, int index, ItemStack stack) {
		AccessoryDef def = AccessoryData.def(stack);
		return def != null && AccessoryData.get(player, index).isEmpty() && AccessorySlots.fits(index, def);
	}

	/** Quebrada por um jogador: o dono recebe como no agachar; outro jogador leva tudo para o inventario. */
	public static void brokenBy(ServerPlayer player, ServerLevel level, BlockPos pos, GraveBlockEntity grave) {
		if (grave.isOwner(player)) {
			restore(player, level, pos, grave, false);
			return;
		}
		Inventory inventory = player.getInventory();
		for (int i = 0; i < GraveRules.SIZE; i++) {
			ItemStack stack = grave.removeItemNoUpdate(i);
			if (!stack.isEmpty()) {
				inventory.placeItemBackInInventory(stack, Prediction.SERVER_ONLY);
			}
		}
		// o XP e do dono: fica em orbes no lugar (quem estiver perto pega)
		int xp = grave.takeXp();
		if (xp > 0) {
			net.minecraft.world.entity.ExperienceOrb.award(level, Vec3.atCenterOf(pos), xp);
		}
		grave.setChanged();
	}

	/** Tela fechada: se nao sobrou nada, a lapide some (o XP que restar vira orbes). */
	public static void onMenuClosed(Player player, GraveBlockEntity grave) {
		if (!(player.level() instanceof ServerLevel level) || grave.isRemoved() || !grave.isEmpty()) {
			updateSummary(grave);
			return;
		}
		if (player instanceof ServerPlayer serverPlayer && grave.isOwner(serverPlayer)) {
			giveXp(serverPlayer, grave);
			recovered(serverPlayer);
		}
		BlockPos pos = grave.getBlockPos();
		if (level.getBlockEntity(pos) == grave) {
			remove(level, pos);
		}
	}

	private static void giveXp(ServerPlayer player, GraveBlockEntity grave) {
		int xp = grave.takeXp();
		if (xp > 0) {
			player.giveExperiencePoints(xp);
			player.level().playSound(null, player.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 1.0F);
			updateSummary(grave);
		}
	}

	private static void recovered(ServerPlayer player) {
		AdvancementHolder advancement = player.level().getServer().getAdvancements().get(net.minecraft.resources.Identifier.parse(RECOVERED_ADVANCEMENT));
		if (advancement != null) {
			player.getAdvancements().award(advancement, "recovered");
		}
	}

	/** Atualiza o resumo (pilhas/XP) da lapide na lista dos clientes. */
	private static void updateSummary(GraveBlockEntity grave) {
		if (!(grave.getLevel() instanceof ServerLevel level)) {
			return;
		}
		BlockPos pos = grave.getBlockPos();
		GraveData data = GraveData.get(level.getServer());
		data.index().get(dimension(level), pos.getX(), pos.getY(), pos.getZ()).ifPresent(info -> {
			GraveInfo updated = info.withContents(grave.stackCount(), grave.xp());
			if (!updated.equals(info)) {
				data.put(updated);
				broadcast(level.getServer());
			}
		});
	}

	/** Tira o bloco (ja vazio). */
	private static void remove(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		boolean water = state.is(GraveRegistry.GRAVE) && state.getValue(GraveBlock.WATERLOGGED);
		level.setBlock(pos, water ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
		level.levelEvent(2001, pos, Block.getId(state));
	}

	/** O bloco saiu do mundo por qualquer motivo: tira da lista. */
	public static void onRemoved(ServerLevel level, BlockPos pos) {
		if (GraveData.get(level.getServer()).remove(dimension(level), pos.getX(), pos.getY(), pos.getZ())) {
			broadcast(level.getServer());
		}
	}

	// ---------------------------------------------------------------- sincronizacao

	public static String dimension(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	public static void sendList(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, GravePayloads.Sync.TYPE)) {
			ServerPlayNetworking.send(player, new GravePayloads.Sync(GraveData.get(player.level().getServer()).index().all()));
		}
	}

	public static void broadcast(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sendList(player);
		}
	}
}
