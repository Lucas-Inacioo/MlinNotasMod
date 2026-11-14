package com.mlin.notas.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

/**
 * Coloca os blocos das obras no mundo (ponte com o jogo). Cada bloco so entra se o lugar ainda for natural (ar,
 * planta, terra, pedra): se um jogador construiu ali depois do planejamento, o bloco da obra e pulado. A camada do
 * chao desce ate o terreno (alicerce de pedra ou terra). Cercas, vidracas, muros e degraus se ligam aos vizinhos.
 */
public final class VillageBuilder {
	/** Blocos (nao-ar) por tick numa obra em andamento: uma casa leva uns 5 segundos. */
	public static final int BLOCKS_PER_TICK = 6;
	public static final int FILL_DEPTH = 10;

	private VillageBuilder() {
	}

	public static BlockPos world(VillageState village, int x, int y, int z) {
		return new BlockPos(village.x() + x, y, village.z() + z);
	}

	/**
	 * Avanca a obra: ate {@link #BLOCKS_PER_TICK} blocos solidos (o ar e barato e vai junto). Para se o proximo bloco
	 * estiver num chunk descarregado. Devolve true quando terminou.
	 */
	public static boolean step(ServerLevel level, VillageState village, VillageData.Job job, VillagePalette palette) {
		VillageState.Rect rect = lotRect(village, job);
		int budget = BLOCKS_PER_TICK;
		int airBudget = 64;
		while (!job.done() && budget > 0 && airBudget > 0) {
			Placement p = job.placement(job.index);
			BlockPos pos = world(village, p.x(), p.y(), p.z());
			if (!level.isLoaded(pos)) {
				return false;
			}
			boolean placed = place(level, palette, pos, p, job.index);
			if (placed) {
				record(level, job, rect, p, pos);
			}
			job.index++;
			if (p.part() == VPart.AIR) {
				airBudget--;
				continue;
			}
			if (placed) {
				budget--;
				effects(level, pos, job.index);
			}
		}
		return job.done();
	}

	/** Lote do plano da obra (para anotar so os blocos da construcao, nao a rua nem a terraplenagem). */
	static VillageState.Rect lotRect(VillageState village, VillageData.Job job) {
		if (job.lot < 0 || village.layout() == null) {
			return null;
		}
		VillageLayout.Lot lot = village.layout().lot(job.lot);
		return lot == null ? null : lot.rect();
	}

	/** Anota o bloco que a obra colocou dentro do lote (demolir tira so o que a vila construiu). */
	static void record(ServerLevel level, VillageData.Job job, VillageState.Rect rect, Placement p, BlockPos pos) {
		if (rect == null || p.part().earthwork() || p.part() == VPart.AIR || !rect.contains(p.x(), p.z())) {
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!state.isAir()) {
			job.record(pos.asLong(), BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
		}
	}

	/** Termina a obra na hora, anotando os blocos (vila inicial, comandos, testes). */
	public static void runJob(ServerLevel level, VillageState village, VillageData.Job job, boolean trees) {
		VillagePalette palette = VillagePalette.of(village.style(), job.variant());
		VillageState.Rect rect = lotRect(village, job);
		while (!job.done()) {
			Placement p = job.placement(job.index);
			BlockPos pos = world(village, p.x(), p.y(), p.z());
			if (place(level, palette, pos, p, job.index, trees)) {
				record(level, job, rect, p, pos);
			}
			job.index++;
		}
	}

	/**
	 * Demole o que a obra colocou: de tras para frente (detalhes primeiro), so os blocos que ainda sao os mesmos. O que
	 * ficava acima do chao vira ar; o chao e o que estava abaixo voltam a ser grama/terra. Conteudo de baus cai no chao.
	 * Devolve quantos blocos foram tirados.
	 */
	public static int demolish(ServerLevel level, VillageState village, VillageData.BuildRecord record) {
		VillagePalette palette = VillagePalette.of(village.style());
		int removed = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int i = record.size() - 1; i >= 0; i--) {
			pos.set(BlockPos.getX(record.positions[i]), BlockPos.getY(record.positions[i]), BlockPos.getZ(record.positions[i]));
			if (!level.isLoaded(pos)) {
				continue;
			}
			BlockState current = level.getBlockState(pos);
			if (!BuiltInRegistries.BLOCK.getKey(current.getBlock()).toString().equals(record.block(i))) {
				continue;
			}
			BlockState replacement;
			if (pos.getY() < record.ground) {
				replacement = palette.subsoil.defaultBlockState();
			} else if (pos.getY() == record.ground) {
				replacement = palette.soil.defaultBlockState();
			} else {
				replacement = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
			}
			level.setBlock(pos, replacement, Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
			removed++;
		}
		return removed;
	}

	/** {@code trees}: corta arvores no caminho (vila inicial, mundo recem-criado). */
	public static void buildNow(ServerLevel level, VillageState village, VillagePlanner.Planned planned, boolean trees) {
		VillagePalette palette = VillagePalette.of(village.style(),
				planned.placements().isEmpty() ? 0 : variant(planned.placements().getFirst().key()));
		int i = 0;
		for (Placement p : planned.placements()) {
			place(level, palette, world(village, p.x(), p.y(), p.z()), p, i++, trees);
		}
	}

	/** Variante (telhado) de uma obra a partir do seu primeiro bloco. */
	public static int variant(long firstKey) {
		return (Long.hashCode(firstKey * 0x9E3779B97F4A7C15L) >>> 3) % 1000;
	}

	private static void effects(ServerLevel level, BlockPos pos, int index) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return;
		}
		level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 4,
				0.3, 0.3, 0.3, 0.05);
		if (index % 3 == 0) {
			level.playSound(null, pos, state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 0.6F, 0.9F + (index % 5) * 0.05F);
		}
		if (index % 7 == 0) {
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 2, 0.4, 0.3, 0.4, 0.0);
		}
	}

	/**
	 * Derruba uma arvore nascida no mundo a partir de um tronco: todos os troncos ligados (galhos inclusive) e as
	 * folhas soltas em volta que nao encostam em outra arvore. Assim a vila abre clareiras sem deixar copas no ar.
	 */
	public static void fellTree(ServerLevel level, BlockPos start) {
		if (structural(level, start)) {
			return;
		}
		java.util.Set<BlockPos> logs = new java.util.HashSet<>();
		java.util.ArrayDeque<BlockPos> queue = new java.util.ArrayDeque<>();
		queue.add(start.immutable());
		logs.add(start.immutable());
		while (!queue.isEmpty() && logs.size() < 256) {
			BlockPos p = queue.poll();
			for (BlockPos n : BlockPos.betweenClosed(p.offset(-1, -1, -1), p.offset(1, 1, 1))) {
				if (Math.abs(n.getX() - start.getX()) > 8 || Math.abs(n.getZ() - start.getZ()) > 8 || logs.contains(n)) {
					continue;
				}
				if (level.getBlockState(n).is(BlockTags.LOGS) && !structural(level, n)) {
					BlockPos copy = n.immutable();
					logs.add(copy);
					queue.add(copy);
				}
			}
		}
		for (BlockPos p : logs) {
			level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		java.util.Set<BlockPos> leaves = new java.util.HashSet<>();
		for (BlockPos p : logs) {
			for (BlockPos n : BlockPos.betweenClosed(p.offset(-3, -2, -3), p.offset(3, 3, 3))) {
				BlockState s = level.getBlockState(n);
				if (s.getBlock() instanceof net.minecraft.world.level.block.LeavesBlock
						&& !s.getValue(net.minecraft.world.level.block.LeavesBlock.PERSISTENT)) {
					leaves.add(n.immutable());
				}
			}
		}
		for (BlockPos leaf : leaves) {
			boolean otherTree = false;
			for (BlockPos n : BlockPos.betweenClosed(leaf.offset(-3, -3, -3), leaf.offset(3, 3, 3))) {
				if (level.getBlockState(n).is(BlockTags.LOGS)) {
					otherTree = true;
					break;
				}
			}
			if (!otherTree) {
				level.setBlock(leaf, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}
	}

	/** Tronco encostado em algo de construcao (tabua, vidro, porta, folha permanente...): e de jogador, nao se mexe. */
	static boolean structural(ServerLevel level, BlockPos pos) {
		for (Direction d : Direction.values()) {
			BlockState s = level.getBlockState(pos.relative(d));
			if (!(s.is(BlockTags.LOGS) || WorldTerrain.replaceable(s) || s.is(net.minecraft.world.level.block.Blocks.VINE)
					|| s.is(net.minecraft.world.level.block.Blocks.COCOA) || s.is(net.minecraft.world.level.block.Blocks.BEE_NEST))) {
				return true;
			}
		}
		return false;
	}

	/** Derruba as arvores nascidas no mundo com tronco no retangulo (relativo ao centro), entre {@code y0} e {@code y1}. */
	public static void clearTrees(ServerLevel level, VillageState village, int x0, int z0, int x1, int z1, int y0, int y1, boolean any) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int x = x0; x <= x1; x++) {
			for (int z = z0; z <= z1; z++) {
				for (int y = y0; y <= y1; y++) {
					pos.set(village.x() + x, y, village.z() + z);
					if (level.isLoaded(pos) && level.getBlockState(pos).is(BlockTags.LOGS) && (any || WorldTerrain.naturalTree(level, pos))) {
						fellTree(level, pos.immutable());
					}
				}
			}
		}
	}

	/** Abre a clareira da obra: lote com 2 de folga (ou a praca) sem arvores. */
	public static void clearSite(ServerLevel level, VillageState village, VillagePlanner.Planned planned, boolean any) {
		if (planned.lot() != null) {
			VillagePlanner.Lot lot = planned.lot();
			clearTrees(level, village, lot.x() - 2, lot.z() - 2, lot.x() + lot.w() + 1, lot.z() + lot.d() + 1, lot.groundY() - 1, lot.groundY() + 14, any);
		}
		if (planned.project() == VillageProject.PLAZA) {
			clearTrees(level, village, -7, -7, 7, 7, village.y() - 4, village.y() + 14, any);
		}
		if (planned.project() == VillageProject.WALL) {
			// faixa de 3 blocos de cada lado da muralha
			int h = VillagePlanner.WALL_HALF + 4;
			for (int x = -h; x <= h; x++) {
				for (int z = -h; z <= h; z++) {
					if (VillagePlanner.insideWall(x, z, 3) && !VillagePlanner.insideWall(x, z, -3)) {
						clearTrees(level, village, x, z, x, z, village.y() - 12, village.y() + 24, any);
					}
				}
			}
		}
	}

	/** Coloca um bloco do plano se o lugar ainda for natural. Devolve true se colocou um bloco (nao-ar). */
	public static boolean place(ServerLevel level, VillagePalette palette, BlockPos pos, Placement p, int salt) {
		return place(level, palette, pos, p, salt, false);
	}

	public static boolean place(ServerLevel level, VillagePalette palette, BlockPos pos, Placement p, int salt, boolean trees) {
		BlockState current = level.getBlockState(pos);
		boolean treeLog = current.is(BlockTags.LOGS) && (trees || WorldTerrain.naturalTree(level, pos));
		if (treeLog) {
			fellTree(level, pos);
			current = level.getBlockState(pos);
		}
		if (p.part() == VPart.AIR) {
			if (!current.isAir() && WorldTerrain.replaceable(current, treeLog)) {
				// tronco e folhas avisam os vizinhos (folhas soltas murcham); o resto so atualiza o cliente
				boolean tree = current.is(BlockTags.LOGS) || current.is(BlockTags.LEAVES);
				level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), tree ? Block.UPDATE_ALL : Block.UPDATE_CLIENTS);
			}
			return false;
		}
		if (p.part().skip() || !WorldTerrain.replaceable(current, treeLog)) {
			return false;
		}
		BlockState state = palette.state(p.part(), p.dir(), salt * 31 + pos.getX() * 7 + pos.getZ());
		if (state.isAir()) {
			return false;
		}
		if (p.part() == VPart.DOOR) {
			BlockPos up = pos.above();
			if (!WorldTerrain.replaceable(level.getBlockState(up))) {
				return false;
			}
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			level.setBlock(up, state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
			return true;
		}
		if (p.part() == VPart.BED) {
			Direction facing = state.getValue(BedBlock.FACING);
			BlockPos head = pos.relative(facing);
			if (!WorldTerrain.replaceable(level.getBlockState(head))) {
				return false;
			}
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
			level.setBlock(head, state.setValue(BedBlock.PART, BedPart.HEAD), Block.UPDATE_ALL);
			return true;
		}
		Block block = state.getBlock();
		if (block instanceof CrossCollisionBlock || block instanceof WallBlock || block instanceof StairBlock || block instanceof FenceGateBlock) {
			state = Block.updateFromNeighbourShapes(state, level, pos);
		}
		level.setBlock(pos, state, Block.UPDATE_ALL);
		if (p.fill()) {
			BlockState fill = (p.part().fillsStone() ? palette.foundation : palette.subsoil).defaultBlockState();
			BlockPos.MutableBlockPos below = pos.mutable();
			for (int i = 0; i < FILL_DEPTH; i++) {
				below.move(Direction.DOWN);
				BlockState here = level.getBlockState(below);
				if (!here.isAir() && !WorldTerrain.clearable(here) && here.getFluidState().isEmpty()) {
					break;
				}
				level.setBlock(below, fill, Block.UPDATE_CLIENTS);
			}
		}
		return true;
	}

	/** Aldeoes e golens da obra pronta, com particulas de festa. */
	public static void spawnResidents(ServerLevel level, VillageState village, VillageData.Job job) {
		for (int i = 0; i + 2 < job.spawns.length; i += 3) {
			BlockPos pos = world(village, job.spawns[i], job.spawns[i + 1], job.spawns[i + 2]);
			EntityTypes.VILLAGER.spawn(level, pos, EntitySpawnReason.MOB_SUMMONED);
			level.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 12, 0.5, 0.6, 0.5, 0.0);
		}
		for (int i = 0; i + 2 < job.golems.length; i += 3) {
			spawnGolem(level, world(village, job.golems[i], job.golems[i + 1], job.golems[i + 2]));
		}
	}

	public static void spawnGolem(ServerLevel level, BlockPos pos) {
		IronGolem golem = EntityTypes.IRON_GOLEM.spawn(level, pos, EntitySpawnReason.MOB_SUMMONED);
		if (golem != null) {
			golem.setPlayerCreated(true);
		}
	}
}
