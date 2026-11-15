package com.mlin.notas.village;

import com.mlin.notas.MlinNotas;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * {@code /village admin rebuild_walls} (Vilas 3.0 fase 4, para o mundo ja jogado): refaz as muralhas terminadas nas
 * alturas do campo de alturas. Tira os blocos que a vila colocou (anotados quando a muralha foi feita pelo mod novo e,
 * nas muralhas antigas sem anotacao, so blocos da paleta de pedra da vila nas colunas da linha, das torres e dos
 * portoes; o que nao e da paleta, como tabuas, vidro, bau ou tocha, nunca e tocado), deixa as ruas dos portoes planas no
 * nivel do campo com a passagem livre, reabre as obras publicas de muralha e monta a muralha de novo pelo mesmo
 * planejador de uma obra normal. Coluna com bloco de jogador fica aberta, como em qualquer obra.
 */
public final class WallRebuild {
	/** Chave (lote) das anotacoes da muralha de pedra e da externa em {@code villages.dat}. */
	public static final int STONE_RECORD = -2;
	public static final int OUTER_RECORD = -3;

	public record Report(int removed, int regraded, int placed, int reopenedWorks) {
	}

	private WallRebuild() {
	}

	public static int recordKey(VillageProject project) {
		return project == VillageProject.OUTER_WALL ? OUTER_RECORD : STONE_RECORD;
	}

	/** Colunas (relativas) onde a muralha de pedra pode ter blocos: linha com 1 de folga e as 8 torres 5x5. */
	static Set<Long> stoneColumns() {
		Set<Long> out = new LinkedHashSet<>();
		int h = VillagePlanner.WALL_HALF + 2;
		for (int x = -h; x <= h; x++) {
			for (int z = -h; z <= h; z++) {
				if (nearLine(x, z)) {
					out.add(VillageLayout.col(x, z));
				}
			}
		}
		int d = VillagePlanner.WALL_DIAGONAL - VillagePlanner.WALL_HALF;
		int[][] v = {{VillagePlanner.WALL_HALF, d}, {d, VillagePlanner.WALL_HALF}};
		for (int sx : new int[] {-1, 1}) {
			for (int sz : new int[] {-1, 1}) {
				for (int[] c : v) {
					box(out, sx * c[0] - sx, sz * c[1] - sz, 2);
				}
			}
		}
		return out;
	}

	private static boolean nearLine(int x, int z) {
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (VillagePlanner.wallCell(x + dx, z + dz)) {
					return true;
				}
			}
		}
		return false;
	}

	/** Colunas da muralha externa: as duas camadas com 1 de folga e as torres 5x5. */
	static Set<Long> outerColumns() {
		Set<Long> out = new LinkedHashSet<>();
		int e = RuralRing.WALL + 4;
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				if (OuterWallPlanner.layer(x, z) != 0) {
					box(out, x, z, 1);
				}
			}
		}
		for (int[] t : OuterWallPlanner.towers()) {
			box(out, t[0], t[1], 2);
		}
		return out;
	}

	private static void box(Set<Long> out, int cx, int cz, int r) {
		for (int x = cx - r; x <= cx + r; x++) {
			for (int z = cz - r; z <= cz + r; z++) {
				out.add(VillageLayout.col(x, z));
			}
		}
	}

	/** Refaz as muralhas terminadas da vila. Null se a vila nao tem plano diretor nem campo. */
	public static Report run(ServerLevel level, VillageState village) {
		HeightField field = VillageFieldServer.ensureField(level, village);
		if (field == null || village.layout() == null || village.layout().master() == null) {
			return null;
		}
		MasterPlan plan = village.layout().master();
		VillageData data = VillageData.get(level.getServer());
		boolean stone = village.built(VillageProject.WALL) > 0;
		boolean outer = village.built(VillageProject.OUTER_WALL) > 0 && plan.rural();
		if (!stone && !outer) {
			return new Report(0, 0, 0, 0);
		}
		VillagePalette palette = VillagePalette.of(village.style());
		Set<Block> wallBlocks = new HashSet<>(java.util.List.of(palette.foundation, palette.stone, palette.stoneAlt, palette.brick, palette.stoneSlab,
				palette.stoneStairs));
		int reopened = com.mlin.notas.village.works.VillageWorks.releaseWallWorks(level, village);
		int removed = 0;
		Set<Long> columns = new LinkedHashSet<>();
		if (stone) {
			columns.addAll(stoneColumns());
		}
		if (outer) {
			columns.addAll(outerColumns());
		}
		removed += removeRecorded(level, village, data, stone, outer);
		removed += removeGeometric(level, village, field, wallBlocks, columns);
		int regraded = regradeGates(level, village, field, palette, stone, outer);
		int placed = 0;
		BlockPos center = VillageServer.center(village);
		for (VillageProject project : new VillageProject[] {VillageProject.WALL, VillageProject.OUTER_WALL}) {
			if (project == VillageProject.WALL ? !stone : !outer) {
				continue;
			}
			VillagePlanner.Planned planned = VillagePlanner.plan(project, village, new WorldTerrain(level, center, true), village.seed() ^ 0x5A17L);
			if (planned == null) {
				continue;
			}
			for (Placement p : planned.placements()) {
				level.getChunk(village.x() + p.x() >> 4, village.z() + p.z() >> 4);
			}
			VillageData.Job job = VillageData.Job.create(village.key(), planned);
			VillageBuilder.runJob(level, village, job, true);
			data.putRecord(village.key(), recordKey(project), job.buildRecord());
			placed += job.buildRecord().size();
		}
		data.changed();
		MlinNotas.LOGGER.info("[{}] muralhas de {} refeitas: {} blocos tirados, {} colunas de portao acertadas, {} colocados, {} obras reabertas",
				MlinNotas.MOD_ID, village.name(), removed, regraded, placed, reopened);
		return new Report(removed, regraded, placed, reopened);
	}

	/** Tira os blocos anotados nas muralhas (os que ainda sao os mesmos). */
	private static int removeRecorded(ServerLevel level, VillageState village, VillageData data, boolean stone, boolean outer) {
		int removed = 0;
		for (int key : new int[] {STONE_RECORD, OUTER_RECORD}) {
			if (key == STONE_RECORD ? !stone : !outer) {
				continue;
			}
			VillageData.BuildRecord record = data.record(village.key(), key);
			if (record == null) {
				continue;
			}
			BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
			for (int i = record.size() - 1; i >= 0; i--) {
				pos.set(BlockPos.getX(record.positions[i]), BlockPos.getY(record.positions[i]), BlockPos.getZ(record.positions[i]));
				level.getChunk(pos);
				BlockState cur = level.getBlockState(pos);
				if (BuiltInRegistries.BLOCK.getKey(cur.getBlock()).toString().equals(record.block(i))) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					removed++;
				}
			}
			data.removeRecord(village.key(), key);
		}
		return removed;
	}

	/**
	 * Muralha sem anotacao (feita antes): de cima do chao ate 11 abaixo do campo, so blocos da paleta de pedra nas colunas
	 * da muralha. Acima do campo vira ar; abaixo vira terra (o novo alicerce enche de novo).
	 */
	private static int removeGeometric(ServerLevel level, VillageState village, HeightField field, Set<Block> wallBlocks, Set<Long> columns) {
		int removed = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (long col : columns) {
			int x = VillageLayout.colX(col);
			int z = VillageLayout.colZ(col);
			int fh = field.heightRel(x, z);
			if (fh == Integer.MIN_VALUE) {
				continue;
			}
			int wx = village.x() + x;
			int wz = village.z() + z;
			level.getChunk(wx >> 4, wz >> 4);
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) - 1;
			for (int y = top; y >= fh - 11; y--) {
				pos.set(wx, y, wz);
				BlockState cur = level.getBlockState(pos);
				boolean wallBlock = wallBlocks.contains(cur.getBlock()) || y > fh + 1 && (cur.is(Blocks.LANTERN) || cur.is(Blocks.IRON_BARS));
				if (!wallBlock) {
					continue;
				}
				BlockState replacement = y > fh ? Blocks.AIR.defaultBlockState() : Blocks.DIRT.defaultBlockState();
				level.setBlock(pos, replacement, Block.UPDATE_CLIENTS);
				removed++;
			}
		}
		return removed;
	}

	/**
	 * Rua do portao no nivel do campo: acima do nivel tudo some (passagem livre ate 6), a coluna chega no nivel (terra
	 * por baixo) e o topo e caminho se nao for chao firme. Colunas de portao so (3 de cada lado da linha da muralha).
	 */
	private static int regradeGates(ServerLevel level, VillageState village, HeightField field, VillagePalette palette, boolean stone, boolean outer) {
		int done = 0;
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (HeightField.Gate gate : field.gates()) {
			if (gate.wall() == HeightField.Wall.STONE ? !stone : !outer) {
				continue;
			}
			for (int x = gate.x0(); x <= gate.x1(); x++) {
				for (int z = gate.z0(); z <= gate.z1(); z++) {
					if (!field.kindRel(x, z).gate()) {
						continue;
					}
					int wx = village.x() + x;
					int wz = village.z() + z;
					level.getChunk(wx >> 4, wz >> 4);
					int lv = gate.level();
					int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz) - 1;
					for (int y = Math.max(top, lv + 6); y > lv; y--) {
						pos.set(wx, y, wz);
						BlockState cur = level.getBlockState(pos);
						// ate 4 acima da rua a passagem fica livre (calcamento mais alto, meio-fio, folhas); acima so sai o que e de muralha
						if (!cur.isAir() && (y <= lv + 4 || wallBlockLike(cur, palette) || WorldTerrain.replaceable(cur, true))) {
							level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
						}
					}
					pos.set(wx, lv, wz);
					if (level.getBlockState(pos).isAir() || level.getBlockState(pos).getFluidState().isSource()) {
						level.setBlock(pos, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
					for (int y = lv - 1; y > lv - 12; y--) {
						pos.set(wx, y, wz);
						BlockState cur = level.getBlockState(pos);
						if (!cur.isAir() && cur.getFluidState().isEmpty()) {
							break;
						}
						level.setBlock(pos, palette.subsoil.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
					done++;
				}
			}
		}
		return done;
	}

	private static boolean wallBlockLike(BlockState cur, VillagePalette palette) {
		Block b = cur.getBlock();
		return b == palette.brick || b == palette.stone || b == palette.stoneAlt || b == palette.stoneSlab || b == palette.stoneStairs
				|| b == palette.foundation;
	}

	/** Todas as colunas (relativas) das duas muralhas, para os testes. */
	static Set<Long> allColumns() {
		Set<Long> out = new HashSet<>(stoneColumns());
		out.addAll(outerColumns());
		return out;
	}
}
