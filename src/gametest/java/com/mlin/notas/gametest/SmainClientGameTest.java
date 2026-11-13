package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.util.Pos;
import com.mlin.notas.vstructures.MansionPlanner;
import com.mlin.notas.vstructures.StrongholdPlanner;
import com.mlin.notas.vstructures.VPlan;
import com.mlin.notas.vstructures.VanillaRemakePiece;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Locale;
import java.util.Map;

/**
 * Game test de cliente da fortaleza e da mansao refeitas: acha a fortaleza como o olho do fim acha (tag
 * {@code eye_of_ender_located}) num mundo gerado de verdade, confere que as 12 molduras estao no mundo, e acha a mansao
 * pelo id vanilla; fotografa por fora e as salas principais (camera de espectador com visao noturna).
 */
@SuppressWarnings("UnstableApiUsage")
public class SmainClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext real = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("mlinnotas-smain")).create()) {
			real.getConnection().waitForChunksRender();
			real.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR));
			real.getServer().runCommand("time set noon");
			real.getServer().runCommand("weather clear");
			real.getServer().runCommand("effect give @p minecraft:night_vision infinite 0 true");
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			stronghold(context, real);
			mansion(context, real);
			context.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
	}

	private static VanillaRemakePiece pieceAt(TestSingleplayerContext real, String id, BlockPos found) {
		return real.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
					.getValueOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace(id)));
			ChunkAccess chunk = level.getChunk(found.getX() >> 4, found.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS);
			StructureStart start = chunk.getStartForStructure(structure);
			if (start == null) {
				return null;
			}
			for (StructurePiece candidate : start.getPieces()) {
				if (candidate instanceof VanillaRemakePiece remake) {
					return remake;
				}
			}
			return null;
		});
	}

	private static void stronghold(ClientGameTestContext context, TestSingleplayerContext real) {
		// o mesmo calculo do olho do fim (EnderEyeItem.use)
		BlockPos found = real.getServer().computeOnServer(server -> server.overworld()
				.findNearestMapStructure(StructureTags.EYE_OF_ENDER_LOCATED, BlockPos.ZERO, 100, false));
		if (found == null) {
			throw new IllegalStateException("o olho do fim nao acha fortaleza");
		}
		VanillaRemakePiece p = pieceAt(real, "stronghold", found);
		if (p == null) {
			throw new IllegalStateException("fortaleza em " + found + " sem a peca refeita");
		}
		MlinNotas.LOGGER.info("[SmainClientGameTest] olho do fim -> {}, fortaleza em {}", found, p.origin());
		int dist = Math.max(Math.abs(found.getX() - p.origin().getX()), Math.abs(found.getZ() - p.origin().getZ()));
		if (dist > 16) {
			throw new IllegalStateException("o olho aponta longe da fortaleza: " + dist);
		}
		VPlan plan = p.plan();
		Pos portal = plan.marker(StrongholdPlanner.MARKER_PORTAL);
		shot(context, real, p, new double[] {portal.x() - 7.5, portal.y() + 3.5, portal.z() - 7.5}, new double[] {portal.x() + 0.5, portal.y(),
				portal.z() + 0.5}, "mlinnotas-smain-stronghold-portal");
		int frames = real.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			int n = 0;
			for (Map.Entry<Long, String> e : plan.blocks().entrySet()) {
				if (VPlan.id(e.getValue()).equals("end_portal_frame")
						&& level.getBlockState(p.world(VPlan.keyX(e.getKey()), VPlan.keyY(e.getKey()), VPlan.keyZ(e.getKey()))).is(Blocks.END_PORTAL_FRAME)) {
					n++;
				}
			}
			return n;
		});
		if (frames != 12) {
			throw new IllegalStateException("molduras do portal no mundo: " + frames);
		}
		shot(context, real, p, new double[] {portal.x() + 9.5, portal.y() + 1, portal.z() + 3.5}, new double[] {portal.x() + 0.5, portal.y(),
				portal.z() + 0.5}, "mlinnotas-smain-stronghold-portal-side");
		shot(context, real, p, new double[] {3.5, StrongholdPlanner.UPPER + 0.2, 3.5}, new double[] {-3, StrongholdPlanner.UPPER + 9, 3},
				"mlinnotas-smain-stronghold-start");
		Pos lib = plan.marker(StrongholdPlanner.MARKER_LIBRARY);
		shot(context, real, p, new double[] {lib.x() - 8.5, lib.y() + 7, lib.z() - 8.5}, new double[] {lib.x() + 3, lib.y() + 1, lib.z() + 3},
				"mlinnotas-smain-stronghold-library");
		Pos bottom = plan.marker(StrongholdPlanner.MARKER_STAIRS);
		Pos top = plan.marker(StrongholdPlanner.MARKER_STAIRS_TOP);
		shot(context, real, p, new double[] {bottom.x() + 0.5, bottom.y() + 1, bottom.z() + 0.5}, new double[] {top.x() + 0.5, top.y() + 1,
				top.z() + 0.5}, "mlinnotas-smain-stronghold-stairs");
		for (String[] room : new String[][] {{StrongholdPlanner.MARKER_PRISON, "prison"}, {StrongholdPlanner.MARKER_CRYPT, "crypt"},
				{StrongholdPlanner.MARKER_FOUNTAIN, "fountain"}}) {
			Pos at = plan.marker(room[0]);
			shot(context, real, p, new double[] {at.x() - 3.5, at.y() + 1.6, at.z() - 3.5}, new double[] {at.x() + 2, at.y(), at.z() + 2},
					"mlinnotas-smain-stronghold-" + room[1]);
		}
		Pos trap = plan.marker(StrongholdPlanner.MARKER_TRAP);
		if (trap != null) {
			shot(context, real, p, new double[] {trap.x() + 0.5, trap.y() + 1.6, trap.z() + 0.5}, new double[] {trap.x() + 0.5, trap.y() + 0.5,
					trap.z() + 0.5}, "mlinnotas-smain-stronghold-trap");
		}
	}

	private static void mansion(ClientGameTestContext context, TestSingleplayerContext real) {
		BlockPos found = real.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
					.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace("mansion")));
			Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator()
					.findNearestMapStructure(level, HolderSet.direct(structure), BlockPos.ZERO, 200, false);
			return result == null ? null : result.getFirst();
		});
		if (found == null) {
			throw new IllegalStateException("nenhuma mansao perto");
		}
		VanillaRemakePiece p = pieceAt(real, "mansion", found);
		if (p == null) {
			throw new IllegalStateException("mansao em " + found + " sem a peca refeita");
		}
		MlinNotas.LOGGER.info("[SmainClientGameTest] mansao em {} (variante {})", p.origin(), p.variant());
		VPlan plan = p.plan();
		shot(context, real, p, new double[] {-44, 30, -50}, new double[] {0, 12, 0}, "mlinnotas-smain-mansion");
		shot(context, real, p, new double[] {10, 4, -30}, new double[] {0, 10, 0}, "mlinnotas-smain-mansion-front");
		shot(context, real, p, new double[] {46, 28, 50}, new double[] {0, 12, 0}, "mlinnotas-smain-mansion-back");
		Pos hall = plan.marker(MansionPlanner.MARKER_HALL + 0);
		shot(context, real, p, new double[] {hall.x() - 24.5, hall.y() + 1.5, hall.z() + 0.5}, new double[] {hall.x() + 10, hall.y() + 1, hall.z() + 0.5},
				"mlinnotas-smain-mansion-hall");
		Pos arena = plan.marker(MansionPlanner.MARKER_ARENA);
		shot(context, real, p, new double[] {arena.x() - 5.5, arena.y() + 3.5, arena.z() - 5.5}, new double[] {arena.x() + 0.5, arena.y(),
				arena.z() + 0.5}, "mlinnotas-smain-mansion-arena");
		Pos vault = plan.marker(MansionPlanner.MARKER_VAULT);
		shot(context, real, p, new double[] {vault.x() + 0.5, vault.y() + 2.5, vault.z() + (vault.z() < 0 ? 4.5 : -3.5)}, new double[] {vault.x() + 0.5,
				vault.y(), vault.z() + 0.5}, "mlinnotas-smain-mansion-vault");
		Pos attic = plan.marker(MansionPlanner.MARKER_ATTIC);
		shot(context, real, p, new double[] {attic.x() + 0.5, attic.y() + 1, attic.z() + 0.5}, new double[] {attic.x() + 8, attic.y() + 2, attic.z()},
				"mlinnotas-smain-mansion-attic");
		Pos hall2 = plan.marker(MansionPlanner.MARKER_HALL + 2);
		shot(context, real, p, new double[] {hall2.x() + 24.5, hall2.y() + 1.5, hall2.z() + 0.5}, new double[] {hall2.x() - 10, hall2.y() + 1,
				hall2.z() + 0.5}, "mlinnotas-smain-mansion-hall2");
	}

	/** Camera em {@code eye} (coordenadas do plano) olhando para {@code look}; espera os chunks e tira o screenshot. */
	private static void shot(ClientGameTestContext context, TestSingleplayerContext real, VanillaRemakePiece piece, double[] eye, double[] look, String name) {
		double[] e = piece.worldPoint(eye[0], eye[1], eye[2]);
		double[] l = piece.worldPoint(look[0], look[1], look[2]);
		double dx = l[0] - e[0];
		double dz = l[2] - e[2];
		double dy = l[1] - (e[1] + 1.62);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		real.getServer().runCommand(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", e[0], e[1], e[2], yaw, pitch));
		context.waitTicks(40);
		real.getConnection().waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot(name);
	}
}
