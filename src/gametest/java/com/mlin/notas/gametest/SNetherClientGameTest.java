package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.util.Pos;
import com.mlin.notas.vstructures.BastionPlanner;
import com.mlin.notas.vstructures.FortressPlanner;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Game test de cliente da fortaleza e do bastiao refeitos num Nether gerado de verdade: acha cada um pelo id vanilla
 * (como o /locate), confere que os baus do plano viraram baus no mundo, loga a altura em relacao ao chao e fotografa
 * por fora (vista 3/4) e por dentro (camera de espectador com visao noturna). Procura bastioes em varios pontos ate
 * fotografar os 4 tipos (ou desistir).
 */
@SuppressWarnings("UnstableApiUsage")
public class SNetherClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext real = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("mlinnotas-snether")).create()) {
			real.getConnection().waitForChunksRender();
			real.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR));
			real.getServer().runCommand("effect give @p minecraft:night_vision infinite 0 true");
			real.getServer().runCommand("execute in minecraft:the_nether run tp @p 0 80 0");
			context.waitTicks(40);
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			fortress(context, real);
			bastions(context, real);
			context.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
	}

	private static ServerLevel nether(MinecraftServer server) {
		return server.getLevel(Level.NETHER);
	}

	/** Acha a estrutura pelo id vanilla perto de {@code from} e devolve uma peca refeita dela (ou null). */
	private static VanillaRemakePiece find(TestSingleplayerContext real, String id, BlockPos from) {
		BlockPos found = real.getServer().computeOnServer(server -> {
			ServerLevel level = nether(server);
			Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
					.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace(id)));
			Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator()
					.findNearestMapStructure(level, HolderSet.direct(structure), from, 60, false);
			return result == null ? null : result.getFirst();
		});
		if (found == null) {
			return null;
		}
		VanillaRemakePiece piece = real.getServer().computeOnServer(server -> {
			ServerLevel level = nether(server);
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
		MlinNotas.LOGGER.info("[SNetherClientGameTest] minecraft:{} em {} (variante {}, {} fatias)", id, piece.origin(), piece.variant(),
				real.getServer().computeOnServer(server -> {
					ServerLevel level = nether(server);
					Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
							.getValueOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace(id)));
					return level.getChunk(found.getX() >> 4, found.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS).getStartForStructure(structure).getPieces().size();
				}));
		return piece;
	}

	/** Confere (depois das fotos, com os chunks gerados) que os baus do plano viraram conteineres com loot. */
	private static void checkContainers(TestSingleplayerContext real, VanillaRemakePiece piece, String id) {
		String result = real.getServer().computeOnServer(server -> {
			ServerLevel level = nether(server);
			StringBuilder out = new StringBuilder();
			int ok = 0;
			for (long k : piece.plan().lootTables().keySet()) {
				BlockPos at = piece.world(VPlan.keyX(k), VPlan.keyY(k), VPlan.keyZ(k));
				if (level.getBlockEntity(at) instanceof net.minecraft.world.RandomizableContainer) {
					ok++;
				} else {
					out.append(at).append("=").append(level.getBlockState(at)).append(" ");
				}
			}
			return ok + " ok; faltando: " + out;
		});
		MlinNotas.LOGGER.info("[SNetherClientGameTest] {} conteineres: {}", id, result);
		if (!result.endsWith("faltando: ")) {
			throw new IllegalStateException(id + ": conteineres do plano nao foram colocados: " + result);
		}
	}

	private static void fortress(ClientGameTestContext context, TestSingleplayerContext real) {
		VanillaRemakePiece p = find(real, "fortress", BlockPos.ZERO);
		if (p == null) {
			throw new IllegalStateException("nenhuma fortaleza perto");
		}
		int d = FortressPlanner.DECK;
		shot(context, real, p, new double[] {-42, 30, 46}, new double[] {0, 8, 0}, "mlinnotas-snether-fortress");
		shot(context, real, p, new double[] {9, 4, 71}, new double[] {0, 8, 50}, "mlinnotas-snether-fortress-gate");
		shot(context, real, p, new double[] {9, d + 2, 30}, new double[] {-6, 2, 14}, "mlinnotas-snether-fortress-bridge-arches");
		shot(context, real, p, new double[] {0.5, d + 0.2, -38.5}, new double[] {0.5, d + 2, -60}, "mlinnotas-snether-fortress-north-bridge");
		shot(context, real, p, new double[] {-40.5, d + 0.2, 0.5}, new double[] {-20, d + 1, 0.5}, "mlinnotas-snether-fortress-patrol-hall");
		Pos throne = p.plan().marker(FortressPlanner.MARKER_THRONE);
		shot(context, real, p, new double[] {0.5, throne.y() + 1.5, 8.5}, new double[] {0.5, throne.y(), throne.z() + 0.5}, "mlinnotas-snether-fortress-throne");
		shot(context, real, p, new double[] {-6.5, d + 1, 9.5}, new double[] {6, d, -4}, "mlinnotas-snether-fortress-war-hall");
		Pos tower = p.plan().marker(FortressPlanner.MARKER_TOWER_SPAWNER);
		shot(context, real, p, new double[] {tower.x() + 4.5, tower.y() + 3, tower.z() + 7.5}, new double[] {tower.x() + 0.5, tower.y(), tower.z() + 0.5},
				"mlinnotas-snether-fortress-blaze-tower");
		shot(context, real, p, new double[] {6.5, 24, 6.5}, new double[] {0.5, 22, 0.5}, "mlinnotas-snether-fortress-keep-roof");
		shot(context, real, p, new double[] {52.5, d + 1, 0.5}, new double[] {64, d + 3, 0.5}, "mlinnotas-snether-fortress-wart-garden");
		checkContainers(real, p, "fortress");
	}

	private static void bastions(ClientGameTestContext context, TestSingleplayerContext real) {
		Set<Integer> seen = new HashSet<>();
		BlockPos[] from = {BlockPos.ZERO, new BlockPos(1500, 64, 0), new BlockPos(-1500, 64, 0), new BlockPos(0, 64, 1500), new BlockPos(0, 64, -1500),
				new BlockPos(1500, 64, 1500), new BlockPos(-1500, 64, -1500), new BlockPos(1500, 64, -1500), new BlockPos(-1500, 64, 1500),
				new BlockPos(3000, 64, 0), new BlockPos(-3000, 64, 0), new BlockPos(0, 64, 3000)};
		for (BlockPos start : from) {
			if (seen.size() == BastionPlanner.TYPES) {
				break;
			}
			VanillaRemakePiece p = find(real, "bastion_remnant", start);
			if (p == null || !seen.add(p.variant())) {
				continue;
			}
			String name = "mlinnotas-snether-bastion-" + switch (p.variant()) {
				case BastionPlanner.STABLES -> "stables";
				case BastionPlanner.TREASURE -> "treasure";
				case BastionPlanner.BRIDGE -> "bridge";
				default -> "housing";
			};
			shot(context, real, p, new double[] {-32, 26, 38}, new double[] {0, 6, 0}, name);
			switch (p.variant()) {
				case BastionPlanner.TREASURE -> {
					shot(context, real, p, new double[] {0.5, 14, 15.5}, new double[] {0.5, 12, 0.5}, name + "-core");
					shot(context, real, p, new double[] {-14.5, 1.5, 14.5}, new double[] {0.5, 8, 0.5}, name + "-lava");
				}
				case BastionPlanner.STABLES -> {
					shot(context, real, p, new double[] {0.5, 1.2, 20.5}, new double[] {0.5, 0, -10}, name + "-aisle");
					shot(context, real, p, new double[] {14.5, 10.5, -19.5}, new double[] {-4, 2, 4}, name + "-gallery");
				}
				case BastionPlanner.BRIDGE -> {
					shot(context, real, p, new double[] {0.5, 9.2, 16.5}, new double[] {0.5, 9, -8}, name + "-deck");
					shot(context, real, p, new double[] {12, 4, 6}, new double[] {0, 3, 6}, name + "-falls");
				}
				default -> {
					shot(context, real, p, new double[] {4.5, 1.6, 16.5}, new double[] {0.5, 4, 0.5}, name + "-courtyard");
					shot(context, real, p, new double[] {12.5, 1.2, 8.5}, new double[] {14, 1, 16}, name + "-house");
				}
			}
			checkContainers(real, p, name);
		}
		MlinNotas.LOGGER.info("[SNetherClientGameTest] tipos de bastiao fotografados: {}", seen);
		if (seen.isEmpty()) {
			throw new IllegalStateException("nenhum bastiao perto");
		}
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
		real.getServer().runCommand(String.format(Locale.ROOT, "execute in minecraft:the_nether run tp @p %.2f %.2f %.2f %.1f %.1f", e[0], e[1], e[2], yaw,
				pitch));
		context.waitTicks(40);
		real.getConnection().waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot(name);
	}
}
