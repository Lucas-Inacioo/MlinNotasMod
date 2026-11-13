package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.minimap.WorldMapScreen;
import com.mlin.notas.client.waystone.WaystoneClient;
import com.mlin.notas.client.waystone.WaystoneScreen;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.ruins.RuinKind;
import com.mlin.notas.ruins.RuinPiece;
import com.mlin.notas.ruins.RuinPlan;
import com.mlin.notas.util.Pos;
import com.mlin.notas.waystone.WaystoneBlock;
import com.mlin.notas.waystone.WaystoneData;
import com.mlin.notas.waystone.WaystoneInfo;
import com.mlin.notas.waystone.WaystoneServer;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;


/**
 * Game test de cliente das ruinas e pedras de teleporte: tela da pedra (lista com custos), icone no minimapa e no
 * mapa grande, viagem pelo pacote de verdade e cada tipo de ruina achado num mundo gerado de verdade (screenshots).
 */
@SuppressWarnings("UnstableApiUsage")
public class RuinsClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().create()) {
			world.getConnection().waitForChunksRender();
			waystones(context, world);
		}
		try (TestSingleplayerContext real = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("mlinnotas-ruinas")).create()) {
			real.getConnection().waitForChunksRender();
			ruins(context, real);
		}
	}

	private static void waystones(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runCommand("time set noon");
		BlockPos near = world.getServer().computeOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			ServerLevel level = player.level();
			BlockPos base = player.blockPosition();
			BlockPos here = new BlockPos(base.getX() + 2, level.getHeight(Heightmap.Types.MOTION_BLOCKING, base.getX() + 2, base.getZ()), base.getZ());
			BlockPos close = new BlockPos(base.getX() + 18, level.getHeight(Heightmap.Types.MOTION_BLOCKING, base.getX() + 18, base.getZ() - 14),
					base.getZ() - 14);
			WaystoneBlock.placeAt(level, ModBlocks.WAYSTONE.defaultBlockState(), here, 3);
			WaystoneBlock.placeAt(level, ModBlocks.WAYSTONE.defaultBlockState(), close, 3);
			WaystoneServer.activate(level, close, "Torre Caida");
			WaystoneServer.activate(level, base.offset(600, 0, 0), "Mina Velha");
			WaystoneServer.activate(level, base.offset(-1600, 0, 900), "Casa da Praia");
			WaystoneData.get(server).activate(new WaystoneInfo("minecraft:the_nether", 10, 70, 10, "Portal do Nether"));
			WaystoneServer.broadcast(server);
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			player.setExperienceLevels(5);
			player.teleportTo(here.getX() + 0.5, here.getY(), here.getZ() + 2.5);
			// botao direito "de verdade" no servidor: ativa e manda o pacote que abre a tela no cliente
			WaystoneServer.use(player, level, here);
			return here;
		});
		context.waitForScreen(WaystoneScreen.class);
		context.waitTicks(10);
		int known = context.computeOnClient(mc -> WaystoneClient.all().size());
		if (known < 5) {
			throw new IllegalStateException("o cliente deveria conhecer as 5 pedras ativadas, conhece " + known);
		}
		context.takeScreenshot("mlinnotas-waystone-screen");

		// viaja pelo pacote: da pedra atual para a "Mina Velha" (600 blocos = 2 niveis)
		context.runOnClient(mc -> {
			WaystoneScreen screen = (WaystoneScreen) mc.gui.screen();
			WaystoneInfo target = WaystoneClient.all().stream().filter(w -> w.name().equals("Mina Velha")).findFirst().orElseThrow();
			WaystoneClient.travel(screen.source(), target);
			mc.gui.setScreen(null);
		});
		context.waitFor(mc -> mc.player != null && mc.player.getX() > near.getX() + 500, 100);
		context.waitTicks(5);
		int levels = world.getServer().computeOnServer(server -> server.getPlayerList().getPlayers().getFirst().experienceLevel);
		if (levels != 3) {
			throw new IllegalStateException("600 blocos deveriam custar 2 niveis (5 -> 3), ficou " + levels);
		}
		MlinNotas.LOGGER.info("[RuinsClientGameTest] viagem pelo pacote ok, niveis {}", levels);

		// volta para perto das pedras e mostra o minimapa com os icones
		world.getServer().runCommand("tp @p " + (near.getX() + 0.5) + " " + (near.getY() + 1) + " " + (near.getZ() + 4.5) + " 180 30");
		context.waitTicks(40);
		world.getConnection().waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot("mlinnotas-waystone-minimap");

		context.runOnClient(mc -> mc.gui.setScreen(new WorldMapScreen(null)));
		context.waitForScreen(WorldMapScreen.class);
		context.waitTicks(10);
		context.takeScreenshot("mlinnotas-waystone-worldmap");
		context.runOnClient(mc -> mc.gui.setScreen(null));
	}

	private static void ruins(ClientGameTestContext context, TestSingleplayerContext real) {
		real.getServer().runCommand("gamemode creative @p");
		real.getServer().runCommand("time set noon");
		real.getServer().runCommand("weather clear");
		for (RuinKind kind : RuinKind.values()) {
			BlockPos found = real.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
						.getOrThrow(ResourceKey.create(Registries.STRUCTURE, MlinNotas.id(kind.id())));
				Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator()
						.findNearestMapStructure(level, HolderSet.direct(structure), BlockPos.ZERO, 120, false);
				return result == null ? null : result.getFirst();
			});
			if (found == null) {
				throw new IllegalStateException("nenhuma ruina " + kind.id() + " num raio grande do mundo real");
			}
			// carrega o chunk de inicio e le a peca (origem, plano)
			RuinView view = real.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE)
						.getValueOrThrow(ResourceKey.create(Registries.STRUCTURE, MlinNotas.id(kind.id())));
				ChunkAccess chunk = level.getChunk(found.getX() >> 4, found.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS);
				StructureStart start = chunk.getStartForStructure(structure);
				if (start == null) {
					return null;
				}
				for (StructurePiece piece : start.getPieces()) {
					if (piece instanceof RuinPiece ruin) {
						return new RuinView(ruin.origin(), ruin.plan(), ruin.style().name());
					}
				}
				return null;
			});
			if (view == null) {
				throw new IllegalStateException("estrutura " + kind.id() + " em " + found + " sem RuinPiece");
			}
			BlockPos o = view.origin();
			int[] b = view.plan().bounds();
			int span = Math.max(b[3] - b[0], b[5] - b[2]);
			// camera ao sul e acima, olhando para o centro da ruina
			double eyeX = o.getX() + 0.5 + span * 0.35;
			double eyeZ = o.getZ() + 0.5 + span * 0.6 + 4;
			double eyeY = o.getY() + Math.max(12, span * 0.8);
			double dx = o.getX() + 0.5 - eyeX;
			double dz = o.getZ() + 0.5 - eyeZ;
			double dy = o.getY() + 2 - (eyeY + 1.62);
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
			real.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", eyeX, eyeY, eyeZ, yaw, pitch));
			real.getServer().runOnServer(server -> {
				ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
			});
			context.waitTicks(60);
			real.getConnection().waitForChunksRender();
			context.waitTicks(20);
			String check = real.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				Pos chest = view.plan().marker(RuinPlan.MARKER_CHEST);
				BlockPos at = o.offset(chest.x(), chest.y(), chest.z());
				boolean hasChest = level.getBlockState(at).is(Blocks.CHEST);
				Pos waystone = view.plan().marker(RuinPlan.MARKER_WAYSTONE);
				boolean hasWaystone = waystone == null || level.getBlockState(o.offset(waystone.x(), waystone.y(), waystone.z())).is(ModBlocks.WAYSTONE);
				String biome = level.getBiome(o).unwrapKey().map(k -> k.identifier().toString()).orElse("?");
				return (hasChest ? "" : "SEM BAU ") + (hasWaystone ? "" : "SEM PEDRA ") + "bioma " + biome;
			});
			MlinNotas.LOGGER.info("[RuinsClientGameTest] {} em {} (estilo {}): {}", kind.id(), o, view.style(), check);
			context.takeScreenshot("mlinnotas-ruin-" + kind.id());
			if (check.startsWith("SEM")) {
				throw new IllegalStateException(kind.id() + " em " + o + ": " + check);
			}
		}
	}

	private record RuinView(BlockPos origin, RuinPlan plan, String style) {
	}
}
