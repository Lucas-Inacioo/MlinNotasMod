package com.mlin.notas.gametest;

import com.mlin.notas.end.CitadelPiece;
import com.mlin.notas.end.CitadelPlan;
import com.mlin.notas.end.CrystalMite;
import com.mlin.notas.end.EndBiomes;
import com.mlin.notas.end.Elevators;
import com.mlin.notas.end.PurpurSentinel;
import com.mlin.notas.end.VoidJelly;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModEntities;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.registry.ModStructures;
import com.mlin.notas.tier.ModWorldgen;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;

import java.util.List;

/** Game tests do rework do End: Cidadela, elevador, biomas e criaturas. */
public class EndGameTest {
	@GameTest
	public void citadelReplacesTheVanillaEndCity(GameTestHelper helper) {
		var structures = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
		helper.assertTrue(structures.get(ModStructures.END_CITADEL).isPresent(), "estrutura mlinnotas:end_citadel registrada");
		StructureSet set = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE_SET)
				.getOrThrow(ResourceKey.create(Registries.STRUCTURE_SET, Identifier.withDefaultNamespace("end_cities"))).value();
		helper.assertTrue(set.structures().size() == 1 && set.structures().getFirst().structure().is(ModStructures.END_CITADEL),
				"o structure set das End Cities deveria gerar a cidadela");
		helper.succeed();
	}

	@GameTest
	public void citadelPieceBuildsTheHallAndSavesItsSeed(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(new BlockPos(4, 1, 4));
		CitadelPiece piece = new CitadelPiece(1234L, origin);
		// so um pedaco 7x4x7 em volta do centro (o resto da cidadela ficaria fora da area do teste)
		BoundingBox box = new BoundingBox(origin.getX() - 3, origin.getY(), origin.getZ() - 3, origin.getX() + 3, origin.getY() + 3, origin.getZ() + 3);
		piece.postProcess(helper.getLevel(), helper.getLevel().structureManager(), helper.getLevel().getChunkSource().getGenerator(),
				RandomSource.create(1), box, ChunkPos.containing(origin), origin);
		helper.assertBlockPresent(ModBlocks.PURPUR_ELEVATOR, 4, 1, 4);
		helper.assertBlockPresent(Blocks.PURPUR_BLOCK, 5, 1, 4);
		helper.assertBlockPresent(Blocks.AIR, 4, 2, 4);
		long sentinels = helper.getLevel().getEntitiesOfClass(PurpurSentinel.class, new net.minecraft.world.phys.AABB(origin).inflate(4)).size();
		helper.assertTrue(sentinels == 2, "duas sentinelas no hall, achou " + sentinels);

		CompoundTag tag = piece.createTag(net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext.fromLevel(helper.getLevel()));
		CitadelPiece loaded = new CitadelPiece(tag);
		helper.assertTrue(loaded.origin().equals(origin), "origem salva");
		helper.assertTrue(loaded.plan().blocks().equals(piece.plan().blocks()), "mesma semente, mesmo plano");
		helper.assertTrue(loaded.plan().count(CitadelPlan.Kind.ELYTRA_FRAME) == 1, "elitro no cofre");
		helper.succeed();
	}

	@SuppressWarnings("removal")
	@GameTest
	public void elevatorTeleportsBetweenFloors(GameTestHelper helper) {
		helper.setBlock(2, 1, 2, ModBlocks.PURPUR_ELEVATOR);
		helper.setBlock(2, 4, 2, ModBlocks.PURPUR_ELEVATOR);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		try {
			BlockPos low = helper.absolutePos(new BlockPos(2, 1, 2));
			player.teleportTo(low.getX() + 0.5, low.getY() + 1, low.getZ() + 0.5);
			helper.assertTrue(Elevators.use(player, true), "pular no elevador deveria subir; jogador em " + player.position() + ", elevador em " + low + ", bloco sob os pes " + helper.getLevel().getBlockState(net.minecraft.core.BlockPos.containing(player.getX(), player.getY() - 0.2, player.getZ())));
			helper.assertTrue(player.getBlockY() == low.getY() + 4, "chega em cima do elevador de cima, y=" + player.getBlockY());
			helper.assertTrue(!Elevators.use(player, true), "nao ha elevador mais acima");
			helper.assertTrue(Elevators.use(player, false), "agachar desce");
			helper.assertTrue(player.getBlockY() == low.getY() + 1, "volta para baixo");
			helper.setBlock(2, 5, 2, Blocks.STONE);
			helper.assertTrue(!Elevators.use(player, true), "destino sem espaco e ignorado");
		} finally {
			helper.getLevel().getServer().getPlayerList().remove(player);
		}
		helper.succeed();
	}

	private static boolean biomeHas(Biome biome, ResourceKey<PlacedFeature> feature) {
		for (HolderSet<PlacedFeature> step : biome.getGenerationSettings().features()) {
			for (Holder<PlacedFeature> holder : step) {
				if (holder.is(feature)) {
					return true;
				}
			}
		}
		return false;
	}

	@GameTest
	public void newEndBiomesAreRegisteredAndHaveEndOres(GameTestHelper helper) {
		var biomes = helper.getLevel().registryAccess().lookupOrThrow(Registries.BIOME);
		for (ResourceKey<Biome> key : List.of(EndBiomes.MOSS_FIELDS, EndBiomes.CRYSTAL_GROVE, EndBiomes.VOID_WASTES)) {
			Holder<Biome> biome = biomes.getOrThrow(key);
			helper.assertTrue(biome.is(BiomeTags.IS_END), key + " na tag is_end");
			helper.assertTrue(biomeHas(biome.value(), ModWorldgen.ORE_ENDITE), key + " com endita");
			helper.assertTrue(biomeHas(biome.value(), ModWorldgen.ORE_ASTRALITE), key + " com astralita");
		}
		helper.assertTrue(biomes.getOrThrow(EndBiomes.MOSS_FIELDS).is(BiomeTags.HAS_END_CITY), "cidadelas tambem nos campos de musgo");
		helper.succeed();
	}

	@GameTest
	public void endCreaturesBehave(GameTestHelper helper) {
		VoidJelly jelly = helper.spawn(ModEntities.VOID_JELLY, 2, 3, 2);
		helper.assertTrue(jelly.isNoGravity(), "agua-viva flutua");
		PurpurSentinel sentinel = helper.spawn(ModEntities.PURPUR_SENTINEL, 5, 1, 5);
		helper.assertTrue(sentinel.getAttributeValue(Attributes.MAX_HEALTH) == 40.0, "sentinela com 40 de vida");
		helper.assertTrue(!sentinel.removeWhenFarAway(10000.0), "sentinela nao some");

		CrystalMite mite = helper.spawn(ModEntities.CRYSTAL_MITE, 1, 1, 5);
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, 2, 1, 5);
		mite.doHurtTarget(helper.getLevel(), zombie);
		helper.assertTrue(zombie.hasEffect(MobEffects.GLOWING), "mordida do acaro faz brilhar");

		Zombie eater = (Zombie) EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.COMMAND);
		ItemStack jellyFood = new ItemStack(ModItems.VOID_JELLY);
		jellyFood.finishUsingItem(helper.getLevel(), eater);
		helper.assertTrue(eater.hasEffect(MobEffects.SLOW_FALLING), "geleia do vazio da queda lenta");
		helper.succeed();
	}
}
