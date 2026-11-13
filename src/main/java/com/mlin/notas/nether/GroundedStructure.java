package com.mlin.notas.nether;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePiecesBuilder;

import java.util.Optional;

/**
 * Embrulho de estrutura que gera outra ({@code delegate}, ex.: a fortaleza ou o bastiao vanilla) e depois move todas as
 * pecas na vertical para que o fundo da caixa fique em {@code chao + floor_offset}. O chao vem do terreno base (sem
 * decoracao) amostrado no centro e em 4 pontos em volta ({@link NetherFloor}).
 *
 * <p>Necessario porque o Nether vanilla e uma caverna: o bastiao nasce em y=33 fixo e a fortaleza entre y 48 e 70. Com
 * o terreno novo (chao de 40 a 90) eles ficariam enterrados ou boiando.</p>
 */
public class GroundedStructure extends Structure {
	public static final MapCodec<GroundedStructure> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			settingsCodec(i),
			Structure.CODEC.fieldOf("delegate").forGetter(s -> s.delegate),
			Codec.INT.optionalFieldOf("floor_offset", 0).forGetter(s -> s.floorOffset),
			Codec.INT.optionalFieldOf("max_top", Integer.MAX_VALUE).forGetter(s -> s.maxTop)).apply(i, GroundedStructure::new));

	private final Holder<Structure> delegate;
	private final int floorOffset;
	private final int maxTop;

	public GroundedStructure(Structure.StructureSettings settings, Holder<Structure> delegate, int floorOffset, int maxTop) {
		super(settings);
		this.delegate = delegate;
		this.floorOffset = floorOffset;
		this.maxTop = maxTop;
	}

	@Override
	protected Optional<Structure.GenerationStub> findGenerationPoint(Structure.GenerationContext context) {
		Optional<Structure.GenerationStub> inner = delegate.value().findValidGenerationPoint(context);
		if (inner.isEmpty()) {
			return Optional.empty();
		}
		StructurePiecesBuilder builder = inner.get().getPiecesBuilder();
		if (builder.isEmpty()) {
			return Optional.empty();
		}
		BoundingBox box = builder.getBoundingBox();
		int cx = (box.minX() + box.maxX()) / 2;
		int cz = (box.minZ() + box.maxZ()) / 2;
		int rx = Math.min(16, box.getXSpan() / 3);
		int rz = Math.min(16, box.getZSpan() / 3);
		int floor = NetherFloor.average(floorAt(context, cx, cz), floorAt(context, cx - rx, cz), floorAt(context, cx + rx, cz),
				floorAt(context, cx, cz - rz), floorAt(context, cx, cz + rz));
		int dy = NetherFloor.shift(box.minY(), box.maxY(), floor, floorOffset, maxTop);
		builder.offsetPiecesVertically(dy);
		return Optional.of(new Structure.GenerationStub(inner.get().position().above(dy), Either.right(builder)));
	}

	/** Chao da coluna (primeiro ar sobre rocha acima do mar de lava) no terreno base. */
	static int floorAt(Structure.GenerationContext context, int x, int z) {
		LevelHeightAccessor height = context.heightAccessor();
		NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z, height, context.randomState());
		int minY = height.getMinY();
		int size = height.getHeight();
		boolean[] solid = new boolean[size];
		boolean[] fluid = new boolean[size];
		for (int i = 0; i < size; i++) {
			BlockState state = column.getBlock(minY + i);
			fluid[i] = !state.getFluidState().isEmpty();
			solid[i] = !state.isAir() && !fluid[i];
		}
		return NetherFloor.find(solid, fluid, minY, context.chunkGenerator().getSeaLevel());
	}

	@Override
	public StructureType<?> type() {
		return NetherRegistry.GROUNDED;
	}
}
