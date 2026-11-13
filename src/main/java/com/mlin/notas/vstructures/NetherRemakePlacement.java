package com.mlin.notas.vstructures;

import com.mlin.notas.nether.GroundedStructure;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Optional;

/**
 * Colocacao das estruturas do Nether refeitas ({@link VKind#nether()}): o chao vem do terreno base amostrado como no
 * {@link GroundedStructure} (o Nether novo tem chao de 40 a 90 e teto ~40 blocos acima, entao o heightmap de superficie
 * nao serve), a altura sai de {@link NetherSiting} e a estrutura vira uma peca por fatia de 16x16 do plano.
 */
final class NetherRemakePlacement {
	private NetherRemakePlacement() {
	}

	static Optional<Structure.GenerationStub> find(VKind kind, Structure.GenerationContext context) {
		ChunkPos chunkPos = context.chunkPos();
		int x = chunkPos.getMiddleBlockX();
		int z = chunkPos.getMiddleBlockZ();
		WorldgenRandom random = context.random();
		long seed = random.nextLong();
		int turns = random.nextInt(4);
		boolean mirror = random.nextBoolean();
		int variant = kind == VKind.BASTION_REMNANT ? random.nextInt(BastionPlanner.TYPES) : 0;
		VPlan plan = VanillaRemakePiece.planFor(kind, seed, variant);
		int[] b = plan.bounds();
		int[] area = plan.surfaceBounds();
		int[][] samples = NetherSiting.samplePoints(area);
		int[] floors = new int[samples.length];
		for (int i = 0; i < samples.length; i++) {
			int[] w = VanillaRemakeStructure.turn(samples[i][0], samples[i][1], turns, mirror);
			floors[i] = GroundedStructure.floorAt(context, x + w[0], z + w[1]);
		}
		int y = NetherSiting.originY(kind, floors, b[1], b[4], context.chunkGenerator().getSeaLevel());
		BlockPos origin = new BlockPos(x, y, z);
		return Optional.of(new Structure.GenerationStub(origin, builder -> {
			for (long tile : VanillaRemakePiece.tileBounds(plan).keySet()) {
				builder.addPiece(new VanillaRemakePiece(kind, variant, seed, origin, turns, mirror, tile));
			}
		}));
	}
}
