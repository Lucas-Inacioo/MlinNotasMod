package com.mlin.notas.village;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link Terrain} lido do mundo de verdade em volta do centro da vila. Coluna livre = topo de chao natural, sem agua
 * em cima e so ar/plantas/neve/folhas soltas ate 12 acima. Qualquer outro bloco (tabuas, caminho de terra, tocha, bau,
 * tronco, folhas colocadas por jogador...) torna a coluna ocupada: as obras da vila nunca destroem construcoes.
 */
public final class WorldTerrain implements Terrain {
	public static final int CLEARANCE = 12;

	private final ServerLevel level;
	private final int cx;
	private final int cz;
	private final Map<Long, int[]> cache = new HashMap<>();
	private final boolean trees;

	public WorldTerrain(ServerLevel level, BlockPos center) {
		this(level, center, false);
	}

	/** {@code trees}: arvores tambem podem ser cortadas (so na vila inicial, num mundo recem-criado). */
	public WorldTerrain(ServerLevel level, BlockPos center, boolean trees) {
		this.trees = trees;
		this.level = level;
		this.cx = center.getX();
		this.cz = center.getZ();
	}

	/** Chao natural (o topo do terreno que a vila pode aplainar). */
	public static boolean naturalGround(BlockState state) {
		return state.is(BlockTags.SUBSTRATE_OVERWORLD) || state.is(BlockTags.SAND) || state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(Blocks.GRAVEL)
				|| state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.CLAY) || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE)
				|| state.is(Blocks.TERRACOTTA) || state.is(Blocks.CALCITE);
	}

	/** Pode sumir para a obra passar: ar, plantas, neve fina, folhas que nasceram sozinhas. */
	public static boolean clearable(BlockState state) {
		if (state.isAir()) {
			return true;
		}
		if (!state.getFluidState().isEmpty()) {
			return false;
		}
		if (state.getBlock() instanceof LeavesBlock) {
			return !state.getValue(LeavesBlock.PERSISTENT);
		}
		return state.canBeReplaced() || state.is(BlockTags.FLOWERS) || state.is(BlockTags.SAPLINGS) || state.is(Blocks.SNOW)
				|| state.is(Blocks.CACTUS) || state.is(Blocks.SUGAR_CANE) || state.is(Blocks.BROWN_MUSHROOM) || state.is(Blocks.RED_MUSHROOM)
				|| state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.PUMPKIN) || state.is(Blocks.MELON) || state.is(Blocks.MOSS_CARPET)
				|| state.is(Blocks.BAMBOO) || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.RED_MUSHROOM_BLOCK)
				|| state.is(Blocks.BROWN_MUSHROOM_BLOCK);
	}

	/** A obra pode trocar este bloco: so natural (chao ou o que some). */
	public static boolean replaceable(BlockState state) {
		return clearable(state) || naturalGround(state);
	}

	/** Como {@link #clearable(BlockState)}, mas com {@code trees} os troncos tambem somem. */
	public static boolean clearable(BlockState state, boolean trees) {
		return clearable(state) || (trees && state.is(BlockTags.LOGS));
	}

	public static boolean replaceable(BlockState state, boolean trees) {
		return clearable(state, trees) || naturalGround(state);
	}

	/**
	 * Tronco de arvore nascida no mundo: sobe ate o topo da coluna de troncos e procura folhas nao permanentes (as do
	 * jogador sao permanentes) em volta. Cabana de troncos de jogador nao tem folhas soltas encostadas, entao fica.
	 */
	public static boolean naturalTree(LevelReader level, BlockPos pos) {
		BlockPos.MutableBlockPos top = pos.mutable();
		for (int i = 0; i < 32 && level.getBlockState(top.above()).is(BlockTags.LOGS); i++) {
			top.move(0, 1, 0);
		}
		BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
		for (int dy = -2; dy <= 2; dy++) {
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					BlockState s = level.getBlockState(p.set(top.getX() + dx, top.getY() + dy, top.getZ() + dz));
					if (s.getBlock() instanceof LeavesBlock && !s.getValue(LeavesBlock.PERSISTENT)) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private int[] column(int x, int z) {
		return cache.computeIfAbsent(((long) x << 32) ^ (z & 0xFFFFFFFFL), k -> scan(cx + x, cz + z));
	}

	private int[] scan(int wx, int wz) {
		// carrega (ou gera) o chunk: o mapa de alturas de chunk ausente devolve o fundo do mundo
		level.getChunk(wx >> 4, wz >> 4);
		int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, wx, wz) - 1;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(wx, top, wz);
		// desce por plantas/neve que nao bloqueiam (e por arvores nascidas no mundo) e acha o chao
		boolean treeColumn = trees;
		while (pos.getY() > level.getMinY()) {
			BlockState here = level.getBlockState(pos);
			if (!treeColumn && here.is(BlockTags.LOGS) && naturalTree(level, pos)) {
				treeColumn = true;
			}
			if (!clearable(here, treeColumn)) {
				break;
			}
			pos.move(0, -1, 0);
		}
		int ground = pos.getY();
		BlockState groundState = level.getBlockState(pos);
		boolean free = naturalGround(groundState) && groundState.getFluidState().isEmpty();
		if (free) {
			for (int dy = 1; dy <= CLEARANCE; dy++) {
				pos.setY(ground + dy);
				BlockState above = level.getBlockState(pos);
				if (!clearable(above, treeColumn) && !(above.is(BlockTags.LOGS) && naturalTree(level, pos))) {
					free = false;
					break;
				}
			}
		}
		return new int[] {ground, free ? 1 : 0};
	}

	@Override
	public int ground(int x, int z) {
		return column(x, z)[0];
	}

	@Override
	public boolean free(int x, int z) {
		return column(x, z)[1] == 1;
	}

	/** O que impede (ou atrapalha) construir na coluna, para o contorno no mundo. */
	public enum Obstacle {
		NONE, TREE, PLAYER, WATER
	}

	/** Coluna vista de perto: o pior obstaculo e o bloco a destacar (null = nada). */
	public record Inspection(Obstacle obstacle, BlockPos block) {
	}

	/**
	 * Olha a coluna (x, z relativos ao centro): agua no chao, bloco que nao e natural (de jogador ou de outra
	 * estrutura) no chao ou ate {@link #CLEARANCE} acima, ou tronco de arvore nascida no mundo (o construtor derruba).
	 */
	public Inspection inspect(int x, int z) {
		int ground = ground(x, z);
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(cx + x, ground, cz + z);
		BlockState groundState = level.getBlockState(pos);
		if (!groundState.getFluidState().isEmpty()) {
			return new Inspection(Obstacle.WATER, pos.immutable());
		}
		if (!naturalGround(groundState)) {
			return new Inspection(Obstacle.PLAYER, pos.immutable());
		}
		BlockPos tree = null;
		for (int dy = 1; dy <= CLEARANCE; dy++) {
			pos.setY(ground + dy);
			BlockState above = level.getBlockState(pos);
			if (above.is(BlockTags.LOGS)) {
				if (trees || naturalTree(level, pos)) {
					if (tree == null) {
						tree = pos.immutable();
					}
					continue;
				}
				return new Inspection(Obstacle.PLAYER, pos.immutable());
			}
			if (!clearable(above, trees)) {
				return new Inspection(Obstacle.PLAYER, pos.immutable());
			}
		}
		return tree == null ? new Inspection(Obstacle.NONE, null) : new Inspection(Obstacle.TREE, tree);
	}
}
