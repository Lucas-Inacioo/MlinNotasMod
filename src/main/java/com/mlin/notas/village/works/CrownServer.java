package com.mlin.notas.village.works;

import com.mlin.notas.village.Facing;
import com.mlin.notas.village.MasterPlan;
import com.mlin.notas.village.VPart;
import com.mlin.notas.village.VillageBuilder;
import com.mlin.notas.village.VillagePalette;
import com.mlin.notas.village.VillagePlanner;
import com.mlin.notas.village.VillageState;
import com.mlin.notas.village.WorldTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Centro historico (Vilas 2.0 fase 8, ponte com o jogo): quando a obra "wall:historic" termina na Metropole, as grades de
 * ferro dos portoes da muralha de pedra saem de vez (com as alavancas) e os portoes dos eixos viram arcos abertos da
 * largura da avenida: os pilares de tijolo dos lados da passagem perdem a parte de baixo e ganham um degrau invertido
 * de arco. So mexe em bloco que e exatamente o da muralha da vila (tijolo da paleta) ou o que as obras puseram.
 */
public final class CrownServer {
	private CrownServer() {
	}

	/** Abre os portoes da muralha de pedra. Devolve quantos blocos mudaram. */
	public static int openHistoricGates(ServerLevel level, VillageState village, VillageWorksData.State st) {
		int changed = 0;
		for (VillageWorksData.GateState g : st.gates) {
			for (int i = 0; i + 2 < g.bars.length; i += 3) {
				BlockPos pos = new BlockPos(village.x() + g.bars[i], g.bars[i + 1], village.z() + g.bars[i + 2]);
				level.getChunk(pos);
				if (level.getBlockState(pos).is(Blocks.IRON_BARS)) {
					level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
					changed++;
				}
			}
			BlockPos lever = new BlockPos(village.x() + g.lever[0], g.lever[1], village.z() + g.lever[2]);
			if (level.getBlockState(lever).getBlock() instanceof LeverBlock) {
				level.setBlock(lever, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
				changed++;
			}
		}
		st.gates.clear();
		MasterPlan plan = village.layout() == null ? null : village.layout().master();
		VillagePalette palette = VillagePalette.of(village.style());
		Block brick = palette.state(VPart.BRICK, null, 0).getBlock();
		Block foundation = palette.state(VPart.FOUNDATION, null, 0).getBlock();
		int h = VillagePlanner.WALL_HALF;
		for (Facing side : Facing.values()) {
			for (int t : new int[] {-2, 2}) {
				int x = side.alongX() ? side.dx * h : t;
				int z = side.alongX() ? t : side.dz * h;
				int street = plan != null && plan.ground(x, z) ? plan.streetHeight(x, z) : WorldTerrain.scan(level, village.x() + x, village.z() + z, false)[0];
				Facing toCenter = side.alongX() ? (t < 0 ? Facing.SOUTH : Facing.NORTH) : (t < 0 ? Facing.EAST : Facing.WEST);
				for (int y = street + 1; y <= street + 4; y++) {
					BlockPos pos = VillageBuilder.world(village, x, y, z);
					level.getChunk(pos);
					BlockState cur = level.getBlockState(pos);
					if (cur.getBlock() != brick && cur.getBlock() != foundation) {
						continue;
					}
					BlockState next = y == street + 4 ? palette.state(VPart.STONE_STAIRS_TOP, toCenter.opposite(), 0) : Blocks.AIR.defaultBlockState();
					level.setBlock(pos, next, Block.UPDATE_ALL);
					changed++;
				}
			}
		}
		return changed;
	}
}
