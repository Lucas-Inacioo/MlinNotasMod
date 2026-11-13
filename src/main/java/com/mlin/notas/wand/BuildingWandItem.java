package com.mlin.notas.wand;

import com.mlin.notas.util.Pos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.function.Consumer;

/**
 * Varinha de construcao: clique com o botao direito numa face de um bloco e ela estende aquele plano
 * (todos os blocos iguais conectados) uma camada para fora, consumindo blocos do inventario.
 */
public class BuildingWandItem extends Item {
	private final int maxBlocks;

	public BuildingWandItem(Properties properties, int maxBlocks) {
		super(properties);
		this.maxBlocks = maxBlocks;
	}

	public int maxBlocks() {
		return maxBlocks;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		int placed = extend(serverLevel, player, context.getClickedPos(), context.getClickedFace(), context.getItemInHand(), context.getHand() == net.minecraft.world.InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
		return placed > 0 ? InteractionResult.SUCCESS : InteractionResult.FAIL;
	}

	/** Executa a extensao no servidor. Devolve quantos blocos foram colocados. */
	public int extend(ServerLevel level, Player player, BlockPos clicked, Direction face, ItemStack wand, EquipmentSlot slot) {
		BlockState state = level.getBlockState(clicked);
		Block block = state.getBlock();
		ItemStack blockItem = new ItemStack(block.asItem());
		if (blockItem.isEmpty()) {
			return 0;
		}
		boolean creative = player.hasInfiniteMaterials();
		int available = creative ? Integer.MAX_VALUE : count(player.getInventory(), block);
		if (available <= 0) {
			return 0;
		}
		Pos normal = new Pos(face.getStepX(), face.getStepY(), face.getStepZ());
		List<Pos> targets = WandPlanner.plan(
				toPos(clicked), normal,
				p -> level.getBlockState(toBlockPos(p)).is(block),
				p -> level.getBlockState(toBlockPos(p)).canBeReplaced() && !level.isOutsideBuildHeight(toBlockPos(p)),
				Math.min(maxBlocks, available));
		int placed = 0;
		for (Pos p : targets) {
			BlockPos target = toBlockPos(p);
			if (!creative && !consume(player.getInventory(), block)) {
				break;
			}
			if (level.setBlock(target, state, Block.UPDATE_ALL)) {
				placed++;
			}
		}
		if (placed > 0) {
			level.playSound(null, clicked.getX(), clicked.getY(), clicked.getZ(), state.getSoundType().getPlaceSound(), SoundSource.BLOCKS);
			if (!creative) {
				wand.hurtAndBreak(1, player, slot);
			}
		}
		return placed;
	}

	private static int count(Inventory inventory, Block block) {
		int total = 0;
		for (ItemStack stack : inventory.getNonEquipmentItems()) {
			if (stack.is(block.asItem())) {
				total += stack.getCount();
			}
		}
		return total;
	}

	private static boolean consume(Inventory inventory, Block block) {
		for (ItemStack stack : inventory.getNonEquipmentItems()) {
			if (stack.is(block.asItem())) {
				stack.shrink(1);
				return true;
			}
		}
		return false;
	}

	private static Pos toPos(BlockPos pos) {
		return new Pos(pos.getX(), pos.getY(), pos.getZ());
	}

	private static BlockPos toBlockPos(Pos pos) {
		return new BlockPos(pos.x(), pos.y(), pos.z());
	}

	@Override
	public void appendHoverText(ItemStack itemStack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
		tooltip.accept(Component.translatable("item.mlinnotas.wand.tooltip", maxBlocks));
	}
}
