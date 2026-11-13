package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.util.Pos;
import com.mlin.notas.vstructures.IglooPlanner;
import com.mlin.notas.vstructures.MonumentPlanner;
import com.mlin.notas.vstructures.OceanRuinPlanner;
import com.mlin.notas.vstructures.ShipwreckPlanner;
import com.mlin.notas.vstructures.SwampHutPlanner;
import com.mlin.notas.vstructures.TrailRuinsPlanner;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.Locale;

/**
 * Game test de cliente das estruturas do mar e pequenas refeitas: acha cada uma num mundo gerado de verdade pelo id
 * vanilla (como o /locate e os mapas), confere os baus e fotografa. Debaixo d'agua a camera usa visao noturna e o
 * poder do conduite; o monumento tambem e fotografado com a agua em volta drenada para ver a forma.
 */
@SuppressWarnings("UnstableApiUsage")
public class SeaStructuresClientGameTest implements FabricClientGameTest {
	private static final String TAG = "[SeaStructuresClientGameTest]";

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext real = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("mlinnotas-mar")).create()) {
			real.getConnection().waitForChunksRender();
			real.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setGameMode(GameType.SPECTATOR));
			real.getServer().runCommand("time set noon");
			real.getServer().runCommand("gamerule advance_time false");
			real.getServer().runCommand("weather clear");
			real.getServer().runCommand("effect give @p minecraft:night_vision infinite 0 true");
			real.getServer().runCommand("effect give @p minecraft:conduit_power infinite 0 true");
			context.runOnClient(mc -> {
				if (!mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
			monument(context, real);
			shipwreck(context, real, VKind.SHIPWRECK, true);
			shipwreck(context, real, VKind.SHIPWRECK_BEACHED, false);
			oceanRuin(context, real, VKind.OCEAN_RUIN_WARM);
			oceanRuin(context, real, VKind.OCEAN_RUIN_COLD);
			simple(context, real, VKind.BURIED_TREASURE, new double[] {-4, 7, -6}, new double[] {0, -1, 0}, true);
			igloo(context, real);
			swampHut(context, real);
			for (VKind kind : new VKind[] {VKind.RUINED_PORTAL, VKind.RUINED_PORTAL_DESERT, VKind.RUINED_PORTAL_JUNGLE, VKind.RUINED_PORTAL_SWAMP,
					VKind.RUINED_PORTAL_MOUNTAIN, VKind.RUINED_PORTAL_OCEAN}) {
				simple(context, real, kind, new double[] {-7, 10, -11}, new double[] {0, 2, 0}, kind == VKind.RUINED_PORTAL);
			}
			trailRuins(context, real);
			netherPortal(context, real);
			context.runOnClient(mc -> {
				if (mc.gui.hud.isHidden()) {
					mc.gui.hud.toggle();
				}
			});
		}
	}

	private record View(VanillaRemakePiece piece, BlockPos found, ResourceKey<Level> dimension) {
	}

	private static ServerLevel level(MinecraftServer server, ResourceKey<Level> dimension) {
		return server.getLevel(dimension);
	}

	/** Acha a estrutura pelo id vanilla (como o /locate) e devolve a peca refeita (ou null se nao ha perto). */
	private static View find(TestSingleplayerContext real, VKind kind, ResourceKey<Level> dimension, int radius) {
		return find(real, kind, dimension, radius, BlockPos.ZERO);
	}

	private static View find(TestSingleplayerContext real, VKind kind, ResourceKey<Level> dimension, int radius, BlockPos from) {
		String id = kind.id();
		BlockPos found = real.getServer().computeOnServer(server -> {
			ServerLevel level = level(server, dimension);
			Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
					.getOrThrow(ResourceKey.create(Registries.STRUCTURE, Identifier.withDefaultNamespace(id)));
			Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator()
					.findNearestMapStructure(level, HolderSet.direct(structure), from, radius, false);
			return result == null ? null : result.getFirst();
		});
		if (found == null) {
			MlinNotas.LOGGER.info("{} sem minecraft:{} perto", TAG, id);
			return null;
		}
		VanillaRemakePiece piece = real.getServer().computeOnServer(server -> {
			ServerLevel level = level(server, dimension);
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
		MlinNotas.LOGGER.info("{} minecraft:{} em {} (variante {}, locate {})", TAG, id, piece.origin(), piece.variant(), found);
		return new View(piece, found, dimension);
	}

	/** Prefere um exemplar fora do mar gelado (icebergs escondem tudo na foto); se nao achar, fica com o primeiro. */
	private static View warm(TestSingleplayerContext real, VKind kind) {
		View first = null;
		for (BlockPos from : new BlockPos[] {BlockPos.ZERO, new BlockPos(3000, 0, 3000), new BlockPos(-3000, 0, 3000), new BlockPos(3000, 0, -3000),
				new BlockPos(-3000, 0, -3000), new BlockPos(6000, 0, 0)}) {
			View v = find(real, kind, Level.OVERWORLD, 100, from);
			if (v == null) {
				continue;
			}
			if (first == null) {
				first = v;
			}
			String biome = real.getServer().computeOnServer(server -> server.overworld().getBiome(v.piece().origin()).unwrapKey()
					.map(k -> k.identifier().getPath()).orElse(""));
			MlinNotas.LOGGER.info("{} {} em {}: bioma {}", TAG, kind.id(), v.piece().origin(), biome);
			if (!biome.contains("frozen") && !biome.contains("cold")) {
				return v;
			}
		}
		if (first == null) {
			throw new IllegalStateException("nenhum minecraft:" + kind.id() + " perto");
		}
		return first;
	}

	private static View required(TestSingleplayerContext real, VKind kind) {
		View v = find(real, kind, Level.OVERWORLD, 100);
		if (v == null) {
			throw new IllegalStateException("nenhum minecraft:" + kind.id() + " perto");
		}
		return v;
	}

	/** Os baus do plano viraram baus com loot no mundo (carrega os chunks antes). */
	private static void checkContainers(ClientGameTestContext context, TestSingleplayerContext real, View v) {
		VanillaRemakePiece piece = v.piece();
		// leva a camera para la para os chunks gerarem por inteiro
		tp(real, v, piece.worldPoint(0, 10, 0), 0, 90);
		context.waitTicks(40);
		real.getConnection().waitForChunksRender();
		String result = real.getServer().computeOnServer(server -> {
			ServerLevel level = level(server, v.dimension());
			StringBuilder missing = new StringBuilder();
			int ok = 0;
			for (long k : piece.plan().lootTables().keySet()) {
				BlockPos at = piece.world(VPlan.keyX(k), VPlan.keyY(k), VPlan.keyZ(k));
				if (level.getBlockEntity(at) instanceof net.minecraft.world.RandomizableContainer) {
					ok++;
				} else {
					missing.append(at).append('=').append(level.getBlockState(at)).append(' ');
				}
			}
			return ok + " ok; faltando: " + missing;
		});
		MlinNotas.LOGGER.info("{} {} conteineres: {}", TAG, piece.kind().id(), result);
		if (!result.endsWith("faltando: ")) {
			throw new IllegalStateException(piece.kind().id() + ": conteineres do plano nao foram colocados: " + result);
		}
	}

	private static String name(VKind kind, String suffix) {
		return "mlinnotas-socean-" + kind.id().replace('_', '-') + (suffix.isEmpty() ? "" : "-" + suffix);
	}

	private static void monument(ClientGameTestContext context, TestSingleplayerContext real) {
		View v = warm(real, VKind.MONUMENT);
		checkContainers(context, real, v);
		VanillaRemakePiece p = v.piece();
		VPlan plan = p.plan();
		shot(context, real, v, new double[] {-30, 14, -46}, new double[] {0, 8, 0}, name(VKind.MONUMENT, "underwater"));
		Pos core = plan.marker(MonumentPlanner.MARKER_CORE);
		shot(context, real, v, new double[] {core.x() - 4.5, core.y() + 2.5, core.z() - 6.5}, new double[] {core.x() + 1, core.y(), core.z() + 1},
				name(VKind.MONUMENT, "core"));
		shot(context, real, v, new double[] {-25.5, 13, -10}, new double[] {-22, 9, 2}, name(VKind.MONUMENT, "wing"));
		Pos pent = plan.marker(MonumentPlanner.MARKER_PENTHOUSE);
		shot(context, real, v, new double[] {pent.x() - 4.5, pent.y() + 2, pent.z() - 4.5}, new double[] {pent.x(), pent.y(), pent.z()},
				name(VKind.MONUMENT, "penthouse"));
		shot(context, real, v, new double[] {0.5, 3, -30}, new double[] {0.5, 3, -18}, name(VKind.MONUMENT, "colonnade"));
		// drena a agua em volta para ver a forma inteira
		drain(real, v, -48, -12, -60, 48, 26, 42);
		shot(context, real, v, new double[] {-42, 24, -58}, new double[] {0, 6, 0}, name(VKind.MONUMENT, "drained"));
		shot(context, real, v, new double[] {48, 10, 40}, new double[] {0, 6, 0}, name(VKind.MONUMENT, "drained-back"));
	}

	private static void shipwreck(ClientGameTestContext context, TestSingleplayerContext real, VKind kind, boolean requiredHere) {
		View v = requiredHere ? warm(real, kind) : find(real, kind, Level.OVERWORLD, 100);
		if (v == null) {
			return;
		}
		checkContainers(context, real, v);
		shot(context, real, v, new double[] {-16, 9, -24}, new double[] {0, 3, 0}, name(kind, ""));
		shot(context, real, v, new double[] {16, 7, 22}, new double[] {0, 3, 0}, name(kind, "bow"));
		Pos cabin = v.piece().plan().marker(ShipwreckPlanner.MARKER_CABIN);
		shot(context, real, v, new double[] {cabin.x() + 0.5, cabin.y() - 0.2, cabin.z() + 2.5}, new double[] {cabin.x() + 0.5, cabin.y(), cabin.z()},
				name(kind, "cabin"));
	}

	private static void oceanRuin(ClientGameTestContext context, TestSingleplayerContext real, VKind kind) {
		View v = find(real, kind, Level.OVERWORLD, 100);
		if (v == null) {
			return;
		}
		checkContainers(context, real, v);
		shot(context, real, v, new double[] {-18, 12, -24}, new double[] {0, 3, 0}, name(kind, ""));
		Pos altar = v.piece().plan().marker(OceanRuinPlanner.MARKER_ALTAR);
		shot(context, real, v, new double[] {0.5, 4, -6}, new double[] {altar.x() + 0.5, altar.y(), altar.z() + 0.5}, name(kind, "altar"));
	}

	private static void igloo(ClientGameTestContext context, TestSingleplayerContext real) {
		View v = required(real, VKind.IGLOO);
		checkContainers(context, real, v);
		shot(context, real, v, new double[] {-10, 6, -15}, new double[] {0, 2, 0}, name(VKind.IGLOO, ""));
		shot(context, real, v, new double[] {0.5, 1.2, -3.5}, new double[] {0.5, 0.5, 3}, name(VKind.IGLOO, "inside"));
		Pos lab = v.piece().plan().marker(IglooPlanner.MARKER_LAB);
		shot(context, real, v, new double[] {0.5, lab.y() + 0.4, 1.2}, new double[] {0.5, lab.y() + 0.5, 6}, name(VKind.IGLOO, "lab"));
	}

	private static void swampHut(ClientGameTestContext context, TestSingleplayerContext real) {
		View v = required(real, VKind.SWAMP_HUT);
		checkContainers(context, real, v);
		shot(context, real, v, new double[] {-11, 8, -15}, new double[] {0, 4, 0}, name(VKind.SWAMP_HUT, ""));
		Pos c = v.piece().plan().marker(SwampHutPlanner.MARKER_CAULDRON);
		shot(context, real, v, new double[] {-1.5, c.y() + 0.6, -2.5}, new double[] {c.x(), c.y(), c.z()}, name(VKind.SWAMP_HUT, "inside"));
	}

	private static void trailRuins(ClientGameTestContext context, TestSingleplayerContext real) {
		View v = find(real, VKind.TRAIL_RUINS, Level.OVERWORLD, 100);
		if (v == null) {
			return;
		}
		shot(context, real, v, new double[] {-9, 14, -12}, new double[] {0, 0, 0}, name(VKind.TRAIL_RUINS, ""));
		Pos pit = v.piece().plan().marker(TrailRuinsPlanner.MARKER_PIT);
		shot(context, real, v, new double[] {-1.5, 1.5, -1.5}, new double[] {pit.x() + 1, pit.y() - 1, pit.z() + 1}, name(VKind.TRAIL_RUINS, "pit"));
	}

	private static void simple(ClientGameTestContext context, TestSingleplayerContext real, VKind kind, double[] eye, double[] look, boolean mustFind) {
		View v = mustFind ? required(real, kind) : find(real, kind, Level.OVERWORLD, 64);
		if (v == null) {
			return;
		}
		checkContainers(context, real, v);
		shot(context, real, v, eye, look, name(kind, ""));
	}

	private static void netherPortal(ClientGameTestContext context, TestSingleplayerContext real) {
		View v = find(real, VKind.RUINED_PORTAL_NETHER, Level.NETHER, 64);
		if (v == null) {
			return;
		}
		checkContainers(context, real, v);
		shot(context, real, v, new double[] {-8, 5, -13}, new double[] {0, 3, 0}, name(VKind.RUINED_PORTAL_NETHER, ""));
	}

	/** Troca a agua por barreira (invisivel, e a agua de fora nao volta) numa caixa (coordenadas do plano), uma camada por comando por causa do limite do /fill. */
	private static void drain(TestSingleplayerContext real, View v, int x1, int y1, int z1, int x2, int y2, int z2) {
		real.getServer().runCommand("gamerule max_block_modifications 1000000");
		for (int y = y1; y <= y2; y++) {
			double[] a = v.piece().worldPoint(x1, y, z1);
			double[] b = v.piece().worldPoint(x2, y, z2);
			real.getServer().runCommand(String.format(Locale.ROOT, "fill %d %d %d %d %d %d barrier replace water", (int) a[0], (int) a[1], (int) a[2],
					(int) b[0], (int) b[1], (int) b[2]));
		}
	}

	private static void tp(TestSingleplayerContext real, View v, double[] e, float yaw, float pitch) {
		String dim = v.dimension().identifier().toString();
		real.getServer().runCommand(String.format(Locale.ROOT, "execute in %s run tp @p %.2f %.2f %.2f %.1f %.1f", dim, e[0], e[1], e[2], yaw, pitch));
	}

	/** Camera em {@code eye} (coordenadas do plano) olhando para {@code look}; espera os chunks e tira o screenshot. */
	private static void shot(ClientGameTestContext context, TestSingleplayerContext real, View v, double[] eye, double[] look, String name) {
		double[] e = v.piece().worldPoint(eye[0], eye[1], eye[2]);
		double[] l = v.piece().worldPoint(look[0], look[1], look[2]);
		double dx = l[0] - e[0];
		double dz = l[2] - e[2];
		double dy = l[1] - (e[1] + 1.62);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		tp(real, v, e, yaw, pitch);
		context.waitTicks(40);
		real.getConnection().waitForChunksRender();
		context.waitTicks(30);
		context.takeScreenshot(name);
	}
}
