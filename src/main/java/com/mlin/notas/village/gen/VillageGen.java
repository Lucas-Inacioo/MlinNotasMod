package com.mlin.notas.village.gen;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.village.HeightField;
import com.mlin.notas.village.VillageFieldServer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.AbstractOreFeature;
import net.minecraft.world.level.levelgen.feature.DiskFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.GeodeFeature;
import net.minecraft.world.level.levelgen.feature.LakeFeature;
import net.minecraft.world.level.levelgen.feature.ScatteredOreFeature;
import net.minecraft.world.level.levelgen.feature.SimpleBlockFeature;
import net.minecraft.world.level.levelgen.feature.SnowAndFreezeFeature;
import net.minecraft.world.level.levelgen.feature.SpringFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

/**
 * Vilas 3.0 fase 2: o gerador do Overworld nasce com o terreno do campo de alturas da vila inicial. Registra os tipos
 * de funcao de densidade ({@link VillageBlendFunction}, {@link VillageSurfaceFunction}; o JSON que embrulha a densidade
 * vanilla vem de {@code scripts/VillageGenContent.java}), o desvio para ler o terreno cru ({@link #raw}) e as regras de
 * decoracao/carver/estrutura chamadas pelos mixins {@code FeaturePlacerVillageMixin},
 * {@code NoiseBasedChunkGeneratorVillageMixin} e {@code ChunkGeneratorVillageMixin}.
 */
public final class VillageGen {
	/** Profundidade do desvio por thread (leituras do terreno natural, sem a mistura). */
	private static final ThreadLocal<int[]> BYPASS = ThreadLocal.withInitial(() -> new int[1]);

	private VillageGen() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, MlinNotas.id("village_blend"), VillageBlendFunction.CODEC);
		Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, MlinNotas.id("village_surface"), VillageSurfaceFunction.CODEC);
	}

	// ---------------------------------------------------------------- desvio

	/** O gerador desta thread esta lendo o terreno cru (sem o campo)? */
	public static boolean bypassed() {
		return BYPASS.get()[0] > 0;
	}

	/** Roda {@code body} lendo o terreno natural (sem a mistura da vila) nesta thread. */
	public static <T> T raw(Supplier<T> body) {
		int[] depth = BYPASS.get();
		depth[0]++;
		try {
			return body.get();
		} finally {
			depth[0]--;
		}
	}

	public static double rawDouble(DoubleSupplier body) {
		int[] depth = BYPASS.get();
		depth[0]++;
		try {
			return body.getAsDouble();
		} finally {
			depth[0]--;
		}
	}

	// ---------------------------------------------------------------- decoracao

	/** Campo ativo, so na geracao do Overworld (nao no osso de farinha nem em outra dimensao). */
	private static HeightField fieldFor(WorldGenLevel level) {
		HeightField field = VillageFieldServer.active();
		if (field == null || !(level instanceof WorldGenRegion region) || region.getLevel().dimension() != Level.OVERWORLD) {
			return null;
		}
		return field;
	}

	static VillageGenRules.FeatureClass classify(Feature feature) {
		if (feature instanceof SnowAndFreezeFeature) {
			return VillageGenRules.FeatureClass.CHUNK_WIDE;
		}
		if (feature instanceof AbstractOreFeature || feature instanceof ScatteredOreFeature) {
			return VillageGenRules.FeatureClass.ORE;
		}
		if (feature instanceof SimpleBlockFeature) {
			return VillageGenRules.FeatureClass.GROUND_COVER;
		}
		if (feature instanceof LakeFeature || feature instanceof SpringFeature || feature instanceof GeodeFeature || feature instanceof DiskFeature) {
			return VillageGenRules.FeatureClass.TERRAIN;
		}
		return VillageGenRules.FeatureClass.OTHER;
	}

	/**
	 * A decoracao pode nascer em {@code origin}? (Chamado pelo mixin no {@code FeaturePlacer}, para cada posicao final
	 * depois dos modificadores de posicao.)
	 */
	public static boolean allowsFeature(WorldGenLevel level, Feature feature, BlockPos origin) {
		HeightField field = fieldFor(level);
		if (field == null || !field.contains(origin.getX(), origin.getZ())) {
			return true;
		}
		int x = origin.getX();
		int z = origin.getZ();
		return VillageGenRules.allowsFeature(field.kind(x, z), classify(feature), origin.getY(), field.height(x, z), field.depth(x, z), field.weight(x, z), roll(x, z));
	}

	/** Sorteio fixo 0..1 pela coluna (rareamento das arvores na faixa). */
	static float roll(int x, int z) {
		long h = x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 31;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 29;
		return (h >>> 40) / (float) (1L << 24);
	}

	/**
	 * Depois de uma planta solta nascer num quarteirao: se o que ficou nao e substituivel (abobora, melancia, arbusto de
	 * frutas), tira.
	 */
	public static void afterGroundCover(WorldGenLevel level, Feature feature, BlockPos origin) {
		if (feature instanceof SnowAndFreezeFeature) {
			fixSnow(level, origin);
			return;
		}
		if (!(feature instanceof SimpleBlockFeature)) {
			return;
		}
		HeightField field = fieldFor(level);
		if (field == null || !field.contains(origin.getX(), origin.getZ()) || !field.kind(origin.getX(), origin.getZ()).inside()) {
			return;
		}
		BlockState state = level.getBlockState(origin);
		if (!state.isAir() && !state.canBeReplaced()) {
			level.setBlock(origin, Blocks.AIR.defaultBlockState(), 2);
		}
	}

	/**
	 * Depois da neve/gelo do topo de um chunk (a ultima decoracao): neve em po no chao do plano (encostas nevadas) vira
	 * bloco de neve, para ninguem afundar na rua ou no lote.
	 */
	private static void fixSnow(WorldGenLevel level, BlockPos origin) {
		HeightField field = fieldFor(level);
		if (field == null) {
			return;
		}
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int dx = 0; dx < 16; dx++) {
			for (int dz = 0; dz < 16; dz++) {
				int x = origin.getX() + dx;
				int z = origin.getZ() + dz;
				if (!field.contains(x, z) || !field.kind(x, z).inside()) {
					continue;
				}
				int h = field.height(x, z);
				for (int y = h - 3; y <= h; y++) {
					if (level.getBlockState(pos.set(x, y, z)).is(Blocks.POWDER_SNOW)) {
						level.setBlock(pos, Blocks.SNOW_BLOCK.defaultBlockState(), 2);
					}
				}
			}
		}
	}

	// ---------------------------------------------------------------- carvers

	/** Filtro do carving mask do chunk ({@code chunkX}, {@code chunkZ}): nada de caverna na camada fechada da vila. */
	public static CarvingMask.Filter carvingFilter(CarvingMask.Filter original, int chunkX, int chunkZ) {
		HeightField field = VillageFieldServer.active();
		if (field == null) {
			return original;
		}
		int bx = chunkX << 4;
		int bz = chunkZ << 4;
		if (bx + 15 < field.centerX() - field.extent() || bx > field.centerX() + field.extent() || bz + 15 < field.centerZ() - field.extent()
				|| bz > field.centerZ() + field.extent()) {
			return original;
		}
		return (x, y, z) -> {
			if (original != null && !original.test(x, y, z)) {
				return false;
			}
			int wx = bx + x;
			int wz = bz + z;
			if (!field.contains(wx, wz)) {
				return true;
			}
			return VillageGenRules.allowsCarve(y, field.height(wx, wz), field.depth(wx, wz), VillageGenRules.alpha(field.weight(wx, wz), field.kind(wx, wz)));
		};
	}

	// ---------------------------------------------------------------- estruturas

	/**
	 * Inicio de estrutura no Overworld: se a caixa cruza o campo e ela e de superficie (ou subterranea mas sobe ate a
	 * camada fechada), nao nasce.
	 */
	public static StructureStart filterStart(Structure structure, StructureStart start) {
		HeightField field = VillageFieldServer.active();
		if (field == null || !start.isValid()) {
			return start;
		}
		BoundingBox box = start.getBoundingBox();
		int x0 = Math.max(box.minX(), field.centerX() - field.extent());
		int x1 = Math.min(box.maxX(), field.centerX() + field.extent());
		int z0 = Math.max(box.minZ(), field.centerZ() - field.extent());
		int z1 = Math.min(box.maxZ(), field.centerZ() + field.extent());
		if (x0 > x1 || z0 > z1) {
			return start;
		}
		GenerationStep.Decoration step = structure.step();
		boolean underground = step == GenerationStep.Decoration.UNDERGROUND_STRUCTURES || step == GenerationStep.Decoration.STRONGHOLDS;
		int minClosed = Integer.MAX_VALUE;
		if (underground) {
			for (int x = x0; x <= x1; x += 2) {
				for (int z = z0; z <= z1; z += 2) {
					if (field.kind(x, z) != HeightField.Kind.NATURAL) {
						minClosed = Math.min(minClosed, field.height(x, z) - Math.max(field.depth(x, z), 1));
					}
				}
			}
			if (minClosed == Integer.MAX_VALUE) {
				return start;
			}
		}
		return VillageGenRules.allowsStructure(underground, box.maxY(), minClosed) ? start : StructureStart.INVALID_START;
	}
}
