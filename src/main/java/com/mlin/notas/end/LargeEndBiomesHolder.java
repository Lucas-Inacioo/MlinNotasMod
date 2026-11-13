package com.mlin.notas.end;

import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Biome;

/** Interface "pato" que o mixin do TheEndBiomeSource implementa para guardar o registro de biomas. */
public interface LargeEndBiomesHolder {
	void mlinnotas$setBiomeLookup(HolderGetter<Biome> biomes);
}
