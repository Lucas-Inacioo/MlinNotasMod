package com.mlin.notas.food;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Tabua de corte: botao direito com um ingrediente que tenha receita {@link CuttingRecipe} corta um na hora (agachado
 * corta a pilha toda). Sem faca nem ferramenta: a tabua e a ferramenta.
 */
public class CuttingBoardBlock extends Block {
	public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
	private static final VoxelShape SHAPE = Block.column(14.0, 0.0, 1.5);

	public CuttingBoardBlock(Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	/** Receita de corte do item, ou vazio. */
	public static Optional<RecipeHolder<CuttingRecipe>> recipeFor(ServerLevel level, ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		return level.recipeAccess().getRecipeFor(FoodRegistry.CUTTING_TYPE, new SingleRecipeInput(stack), level);
	}

	/**
	 * Corta {@code times} itens da pilha (limitado ao que ha) e solta o resultado em cima da tabua. Devolve quantos
	 * foram cortados.
	 */
	public static int cut(ServerLevel level, BlockPos pos, ItemStack stack, int times, @Nullable Player player) {
		Optional<RecipeHolder<CuttingRecipe>> recipe = recipeFor(level, stack);
		if (recipe.isEmpty()) {
			return 0;
		}
		int n = Math.min(times, stack.getCount());
		ItemStack particle = stack.copyWithCount(1);
		boolean consume = player == null || !player.hasInfiniteMaterials();
		for (int i = 0; i < n; i++) {
			Block.popResource(level, pos.above(), recipe.get().value().output().create());
		}
		if (consume) {
			stack.shrink(n);
		}
		level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, particle.getItem()), pos.getX() + 0.5, pos.getY() + 0.2, pos.getZ() + 0.5,
				6 + n, 0.15, 0.05, 0.15, 0.05);
		level.playSound(null, pos, SoundEvents.AXE_STRIP.value(), SoundSource.BLOCKS, 0.7F, 1.2F + level.getRandom().nextFloat() * 0.2F);
		return n;
	}

	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hitResult) {
		if (stack.isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (!(level instanceof ServerLevel serverLevel)) {
			// o cliente nao conhece as receitas: a tag mlinnotas:cuttable lista o que a tabua corta
			return stack.is(FoodRegistry.CUTTABLE) ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		if (recipeFor(serverLevel, stack).isEmpty()) {
			return InteractionResult.TRY_WITH_EMPTY_HAND;
		}
		cut(serverLevel, pos, stack, player.isShiftKeyDown() ? stack.getCount() : 1, player);
		return InteractionResult.SUCCESS_SERVER;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}
}
