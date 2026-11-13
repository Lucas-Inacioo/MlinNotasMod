package com.mlin.notas.tree;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;

/**
 * Queda rapida de folhas (ponte com o jogo, chamada pelo {@code LeavesBlockMixin}). Quando um tronco some (machado,
 * {@link TreeCapitator}, fogo, explosao...) o vanilla ja propaga a distancia das folhas por ticks agendados; aqui, cada
 * folha que fica solta agenda mais um tick com atraso sorteado ({@link LeafDecayRules#delay}) e cai nele. As posicoes
 * agendadas pelo mod ficam num conjunto por dimensao para separar o "nosso" tick do tick vanilla de vizinho.
 */
public final class FastLeafDecay {
	private static final int MAX_PENDING = 200_000;
	private static final Map<ResourceKey<Level>, LongSet> PENDING = new HashMap<>();

	private FastLeafDecay() {
	}

	public static void init() {
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PENDING.clear());
	}

	/**
	 * Tick agendado de uma folha que ja estava solta. Devolve true se o mod cuidou do tick (folha caiu ou foi
	 * reagendada); false deixa o vanilla recalcular a distancia (a folha voltou a encostar numa arvore).
	 */
	public static boolean onTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!LeafDecayRules.orphaned(state.getValue(LeavesBlock.DISTANCE), state.getValue(LeavesBlock.PERSISTENT))) {
			return false;
		}
		LongSet pending = PENDING.get(level.dimension());
		if (!LeafDecayRules.orphaned(currentDistance(level, pos), false)) {
			if (pending != null) {
				pending.remove(pos.asLong());
			}
			return false;
		}
		if (pending != null && pending.remove(pos.asLong())) {
			Block.dropResources(state, level, pos);
			level.removeBlock(pos, false);
		} else {
			schedule(level, pos, state.getBlock(), random);
		}
		return true;
	}

	/** Fim do tick vanilla: se a folha acabou de ficar solta, agenda a queda. */
	public static void afterTick(ServerLevel level, BlockPos pos, RandomSource random) {
		BlockState now = level.getBlockState(pos);
		if (now.getBlock() instanceof LeavesBlock && now.hasProperty(LeavesBlock.DISTANCE)
				&& LeafDecayRules.orphaned(now.getValue(LeavesBlock.DISTANCE), now.getValue(LeavesBlock.PERSISTENT))) {
			schedule(level, pos, now.getBlock(), random);
		}
	}

	private static void schedule(ServerLevel level, BlockPos pos, Block block, RandomSource random) {
		LongSet pending = PENDING.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet());
		if (pending.size() > MAX_PENDING) {
			pending.clear();
		}
		pending.add(pos.asLong());
		level.scheduleTick(pos, block, LeafDecayRules.delay(random.nextInt()));
	}

	private static int currentDistance(ServerLevel level, BlockPos pos) {
		int[] distances = new int[6];
		BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
		Direction[] directions = Direction.values();
		for (int i = 0; i < directions.length; i++) {
			neighbor.setWithOffset(pos, directions[i]);
			distances[i] = LeavesBlock.getOptionalDistanceAt(level.getBlockState(neighbor)).orElse(LeafDecayRules.DECAY_DISTANCE);
		}
		return LeafDecayRules.distanceFromNeighbors(distances);
	}
}
