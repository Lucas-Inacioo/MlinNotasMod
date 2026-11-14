package com.mlin.notas.grave;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Conteudo de uma lapide: itens na disposicao de {@link GraveRules} (54 posicoes), XP guardado, dono e dia da morte.
 * E um container (para a tela), mas sem faces para funis. Sincroniza tudo com o cliente (WAILA e nome gravado).
 * Removida por qualquer outro meio (comando, mod), solta o conteudo e o XP no chao: nada se perde.
 */
public class GraveBlockEntity extends BlockEntity implements WorldlyContainer {
	private static final int[] NO_SLOTS = new int[0];

	private final NonNullList<ItemStack> items = NonNullList.withSize(GraveRules.SIZE, ItemStack.EMPTY);
	private @Nullable UUID owner;
	private String ownerName = "";
	private int xp;
	private long day = 1;

	public GraveBlockEntity(BlockPos pos, BlockState state) {
		super(GraveRegistry.GRAVE_ENTITY, pos, state);
	}

	/** Preenche a lapide recem-colocada. */
	public void fill(UUID owner, String ownerName, GraveContents contents, long day) {
		this.owner = owner;
		this.ownerName = ownerName;
		for (int i = 0; i < GraveRules.SIZE; i++) {
			items.set(i, contents.get(i).copy());
		}
		this.xp = contents.xp();
		this.day = day;
		setChanged();
	}

	public @Nullable UUID owner() {
		return owner;
	}

	public boolean isOwner(Player player) {
		return owner != null && owner.equals(player.getUUID());
	}

	public String ownerName() {
		return ownerName;
	}

	public int xp() {
		return xp;
	}

	public long day() {
		return day;
	}

	/** Tira o XP guardado (fica zero) e devolve. */
	public int takeXp() {
		int taken = xp;
		if (taken > 0) {
			xp = 0;
			setChanged();
		}
		return taken;
	}

	/** Pilhas guardadas (posicoes nao vazias). */
	public int stackCount() {
		int count = 0;
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				count++;
			}
		}
		return count;
	}

	/** Lista interna (leitura, WAILA). */
	public NonNullList<ItemStack> items() {
		return items;
	}

	// ---------------------------------------------------------------- persistencia e sincronizacao

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		for (int i = 0; i < items.size(); i++) {
			items.set(i, ItemStack.EMPTY);
		}
		ContainerHelper.loadAllItems(input, items);
		owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
		ownerName = input.getStringOr("owner_name", "");
		xp = input.getIntOr("xp", 0);
		day = input.getLongOr("day", 1L);
	}

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		ContainerHelper.saveAllItems(output, items, true);
		output.storeNullable("owner", UUIDUtil.CODEC, owner);
		output.putString("owner_name", ownerName);
		output.putInt("xp", xp);
		output.putLong("day", day);
	}

	@Override
	public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
		return ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveCustomOnly(registries);
	}

	@Override
	public void setChanged() {
		super.setChanged();
		if (level != null && !level.isClientSide()) {
			level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		}
	}

	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		// solta os itens (Container) e o XP que sobrou
		super.preRemoveSideEffects(pos, state);
		if (level instanceof ServerLevel serverLevel && xp > 0) {
			ExperienceOrb.award(serverLevel, Vec3.atCenterOf(pos), xp);
			xp = 0;
		}
	}

	// ---------------------------------------------------------------- container (so a tela; funis nao enxergam)

	@Override
	public int getContainerSize() {
		return GraveRules.SIZE;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int count) {
		ItemStack removed = ContainerHelper.removeItem(items, slot, count);
		if (!removed.isEmpty()) {
			setChanged();
		}
		return removed;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		items.set(slot, stack);
		setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return !isRemoved() && player.position().closerThan(Vec3.atCenterOf(worldPosition), 8.0);
	}

	@Override
	public void clearContent() {
		for (int i = 0; i < items.size(); i++) {
			items.set(i, ItemStack.EMPTY);
		}
		setChanged();
	}

	@Override
	public int[] getSlotsForFace(Direction direction) {
		return NO_SLOTS;
	}

	@Override
	public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction direction) {
		return false;
	}

	@Override
	public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction direction) {
		return false;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return false;
	}
}
