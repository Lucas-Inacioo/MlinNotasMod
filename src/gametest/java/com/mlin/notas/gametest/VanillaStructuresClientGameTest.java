package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.util.Pos;
import com.mlin.notas.vstructures.DesertPyramidPlanner;
import com.mlin.notas.vstructures.JungleTemplePlanner;
import com.mlin.notas.vstructures.OutpostPlanner;
import com.mlin.notas.vstructures.VKind;
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
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Locale;

/**
 * Game test de cliente das estruturas vanilla refeitas: acha cada uma num mundo gerado de verdade pelo id vanilla
 * (como o /locate), confere os baus e fotografa por fora (vista 3/4) e por dentro (camera de espectador com visao
 * noturna).
 */
@SuppressWarnings("UnstableApiUsage")
public class VanillaStructuresClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext real = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("mlinnotas-estruturas")).create()) {
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
			desert(context, real);
			jungle(context, real);
			outpost(context, real);
			mineshaft(context, real, VKind.MINESHAFT, true);
			mineshaft(context, real, VKind.MINESHAFT_MESA, false);
			context.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
	}

	private record View(VanillaRemakePiece piece, BlockPos found) {
	}

	/** Acha a estrutura pelo id vanilla e devolve a peca refeita (ou null). */
	private static View find(TestSingleplayerContext real, String id) {
		BlockPos found = real.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
					.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace(id)));
			Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator()
					.findNearestMapStructure(level, HolderSet.direct(structure), BlockPos.ZERO, 100, false);
			return result == null ? null : result.getFirst();
		});
		if (found == null) {
			return null;
		}
		VanillaRemakePiece piece = real.getServer().computeOnServer(server -> {
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
		if (piece == null) {
			throw new IllegalStateException("minecraft:" + id + " em " + found + " sem a peca refeita");
		}
		MlinNotas.LOGGER.info("[VanillaStructuresClientGameTest] minecraft:{} em {} (variante {})", id, piece.origin(), piece.variant());
		return new View(piece, found);
	}

	/** Confere que os baus com loot do plano viraram baus/barris/despensers no mundo. */
	private static void checkContainers(TestSingleplayerContext real, VanillaRemakePiece piece, String id) {
		String missing = real.getServer().computeOnServer(server -> {
			ServerLevel level = server.overworld();
			StringBuilder out = new StringBuilder();
			int ok = 0;
			for (long k : piece.plan().lootTables().keySet()) {
				BlockPos at = piece.world(VPlan.keyX(k), VPlan.keyY(k), VPlan.keyZ(k));
				var be = level.getBlockEntity(at);
				if (be instanceof net.minecraft.world.RandomizableContainer) {
					ok++;
				} else {
					out.append(at).append("=").append(level.getBlockState(at)).append(" ");
				}
			}
			return ok + " ok; faltando: " + out;
		});
		MlinNotas.LOGGER.info("[VanillaStructuresClientGameTest] {} conteineres: {}", id, missing);
		if (!missing.endsWith("faltando: ")) {
			// em mina, trecho que encosta em agua e pulado: so loga
			if (!id.startsWith("mineshaft")) {
				throw new IllegalStateException(id + ": conteineres do plano nao foram colocados: " + missing);
			}
		}
	}

	private static void desert(ClientGameTestContext context, TestSingleplayerContext real) {
		View v = find(real, "desert_pyramid");
		if (v == null) {
			throw new IllegalStateException("nenhum templo do deserto perto");
		}
		VanillaRemakePiece p = v.piece();
		checkContainers(real, p, "desert_pyramid");
		shot(context, real, p, new double[] {-38, 24, -52}, new double[] {0, 6, -4}, "mlinnotas-vstructure-desert-pyramid");
		shot(context, real, p, new double[] {30, 6, -42}, new double[] {0, 6, -20}, "mlinnotas-vstructure-desert-pyramid-gate");
		shot(context, real, p, new double[] {0.5, 5, -6.5}, new double[] {0.5, 0, 4}, "mlinnotas-vstructure-desert-hall");
		Pos plate = p.plan().marker(DesertPyramidPlanner.MARKER_PLATE);
		shot(context, real, p, new double[] {plate.x() - 3.5, plate.y() + 1.6, plate.z() - 3.5}, new double[] {plate.x() + 3, plate.y() - 1, plate.z() + 3},
				"mlinnotas-vstructure-desert-treasure");
		Pos tomb = p.plan().marker(DesertPyramidPlanner.MARKER_TOMB);
		shot(context, real, p, new double[] {tomb.x() - 8, tomb.y() + 2.5, tomb.z() + 0.5}, new double[] {tomb.x(), tomb.y(), tomb.z()},
				"mlinnotas-vstructure-desert-tomb");
	}

	private static void jungle(ClientGameTestContext context, TestSingleplayerContext real) {
		View v = find(real, "jungle_pyramid");
		if (v == null) {
			throw new IllegalStateException("nenhum templo da selva perto");
		}
		VanillaRemakePiece p = v.piece();
		checkContainers(real, p, "jungle_pyramid");
		shot(context, real, p, new double[] {-27, 21, -44}, new double[] {0, 7, -6}, "mlinnotas-vstructure-jungle-temple");
		shot(context, real, p, new double[] {-4.5, 8.5, -5.5}, new double[] {2, 1, 4}, "mlinnotas-vstructure-jungle-atrium");
		Pos lever = p.plan().marker(JungleTemplePlanner.MARKER_LEVER + 1);
		shot(context, real, p, new double[] {lever.x() + 0.5, 1.2, 8.6}, new double[] {lever.x() + 0.5, 1, lever.z() + 0.5},
				"mlinnotas-vstructure-jungle-puzzle");
		Pos trap = p.plan().marker(JungleTemplePlanner.MARKER_TRAP_1);
		shot(context, real, p, new double[] {trap.x() + 0.5, 0.8, trap.z() - 4.5}, new double[] {trap.x() + 0.5, 0.5, trap.z() + 2},
				"mlinnotas-vstructure-jungle-arrow-trap");
	}

	private static void outpost(ClientGameTestContext context, TestSingleplayerContext real) {
		View v = find(real, "pillager_outpost");
		if (v == null) {
			throw new IllegalStateException("nenhum posto de saqueadores perto");
		}
		VanillaRemakePiece p = v.piece();
		checkContainers(real, p, "pillager_outpost");
		shot(context, real, p, new double[] {-42, 30, -56}, new double[] {0, 6, 0}, "mlinnotas-vstructure-outpost");
		shot(context, real, p, new double[] {10, 4, -34}, new double[] {0, 6, -16}, "mlinnotas-vstructure-outpost-gate");
		Pos chest = p.plan().marker(OutpostPlanner.MARKER_TOP_CHEST);
		shot(context, real, p, new double[] {-4.5, chest.y() + 1.2, -4.5}, new double[] {chest.x(), chest.y(), chest.z()}, "mlinnotas-vstructure-outpost-deck");
		Pos cage = p.plan().marker(OutpostPlanner.MARKER_GOLEM_CAGE);
		shot(context, real, p, new double[] {cage.x() * 0.6, 4, cage.z() * 0.6 - 3}, new double[] {cage.x() + 0.5, 1, cage.z() + 0.5},
				"mlinnotas-vstructure-outpost-camp");
	}

	private static void mineshaft(ClientGameTestContext context, TestSingleplayerContext real, VKind kind, boolean required) {
		View v = find(real, kind.id());
		if (v == null) {
			if (required) {
				throw new IllegalStateException("nenhuma mina perto");
			}
			MlinNotas.LOGGER.info("[VanillaStructuresClientGameTest] sem {} perto (bioma raro), sem foto", kind.id());
			return;
		}
		VanillaRemakePiece p = v.piece();
		checkContainers(real, p, kind.id());
		String name = "mlinnotas-vstructure-" + kind.id().replace('_', '-');
		shot(context, real, p, new double[] {-2.5, 3.6, -6.5}, new double[] {2, 0, 4}, name + "-hub");
		// um tunel saindo do salao: olha ao longo dele
		for (String d : new String[] {"n", "e", "s", "w"}) {
			int dx = d.equals("e") ? 1 : d.equals("w") ? -1 : 0;
			int dz = d.equals("s") ? 1 : d.equals("n") ? -1 : 0;
			if (isAir(p.plan().get(10 * dx, 1, 10 * dz))) {
				shot(context, real, p, new double[] {6.5 * dx + 0.5, 1.0, 6.5 * dz + 0.5}, new double[] {20 * dx + 0.5, 0.8, 20 * dz + 0.5},
						name + "-tunnel");
				break;
			}
		}
	}

	private static boolean isAir(String s) {
		return s != null && (s.endsWith(":air") || s.endsWith(":cave_air"));
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
