package com.mlin.notas.registry;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.end.CitadelPiece;
import com.mlin.notas.end.CitadelStructure;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

/** Estruturas do mod. O JSON da estrutura e do structure set fica em data/. */
public final class ModStructures {
	public static final StructureType<CitadelStructure> CITADEL = Registry.register(BuiltInRegistries.STRUCTURE_TYPE,
			MlinNotas.id("end_citadel"), () -> CitadelStructure.CODEC);
	public static final StructurePieceType CITADEL_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE,
			MlinNotas.id("end_citadel"), (StructurePieceType.ContextlessType) CitadelPiece::new);
	/** A estrutura em si (data/mlinnotas/worldgen/structure/end_citadel.json). */
	public static final ResourceKey<Structure> END_CITADEL = ResourceKey.create(Registries.STRUCTURE, MlinNotas.id("end_citadel"));

	private ModStructures() {
	}

	public static void init() {
	}
}
