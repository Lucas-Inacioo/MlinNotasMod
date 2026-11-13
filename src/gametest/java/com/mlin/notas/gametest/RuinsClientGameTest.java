package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.minimap.WorldMapScreen;
import com.mlin.notas.client.waystone.WaystoneClient;
import com.mlin.notas.client.waystone.WaystoneScreen;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.ruins.RuinKind;
import com.mlin.notas.ruins.RuinPart;
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
		// espectador (a camera atravessa terreno e copas) sem a mensagem de modo de jogo no chat; sem HUD nas fotos
		real.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setGameMode(net.minecraft.world.level.GameType.SPECTATOR));
		real.getServer().runCommand("time set noon");
		real.getServer().runCommand("weather clear");
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
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
			RuinPlan plan = view.plan();
			int[] s = plan.surfaceBounds();
			int[] b = plan.bounds();
			int span = Math.max(s[2] - s[0], s[3] - s[1]);
			double cx = (s[0] + s[2]) / 2.0 + 0.5;
			double cz = (s[1] + s[3]) / 2.0 + 0.5;
			int height = Math.max(4, b[4]);
			// vista 3/4 do lado da entrada, girada para o lado do desabamento (onde esta o entulho)
			Pos entrance = plan.marker(RuinPlan.MARKER_ENTRANCE);
			double front = Math.atan2(entrance.z() + 0.5 - cz, entrance.x() + 0.5 - cx);
			double lx = 0;
			double lz = 0;
			for (long k : plan.loose()) {
				lx += RuinPlan.keyX(k) - cx;
				lz += RuinPlan.keyZ(k) - cz;
			}
			double toward = Math.atan2(lz, lx);
			double turn = Math.sin(toward - front) >= 0 ? 40 : -40;
			double ang = front + Math.toRadians(turn);
			// distancia e altura por tipo: perto e alto nas florestas (copas), baixo no poco, de frente na mina
			double dist;
			double up;
			double lookY = Math.min(height * 0.35, 5);
			switch (kind) {
				case CAMP -> {
					dist = 9;
					up = 15;
					lookY = 0;
				}
				case CHAPEL -> {
					// por cima das copas da floresta escura, olhando para a clareira da capela
					ang = front + Math.toRadians(turn * 0.5);
					dist = 11;
					up = 20;
					lookY = 2;
				}
				case WELL -> {
					dist = 11;
					up = 6;
					lookY = 2;
				}
				case MINE -> {
					ang = front + Math.toRadians(turn * 0.4);
					dist = 12;
					up = 6;
					lookY = 2;
				}
				default -> {
					dist = span * 0.75 + 7;
					up = Math.max(7, height * 0.55 + span * 0.25);
				}
			}
			double lookX = kind == RuinKind.MINE ? 0.5 : cx;
			double lookZ = kind == RuinKind.MINE ? 0.5 : cz;
			double[] eye = {lookX + Math.cos(ang) * dist, up, lookZ + Math.sin(ang) * dist};
			shot(context, real, o, eye, new double[] {lookX, lookY, lookZ}, "mlinnotas-ruin-" + kind.id());
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
			if (check.startsWith("SEM")) {
				throw new IllegalStateException(kind.id() + " em " + o + ": " + check);
			}
			// interiores (camera de espectador atravessa o terreno)
			Pos chest = plan.marker(RuinPlan.MARKER_CHEST);
			switch (kind) {
				case CHAPEL -> {
					Pos summon = plan.marker(RuinPlan.MARKER_BOSS_SUMMON);
					double[] toDoor = unit(entrance.x() - summon.x(), entrance.z() - summon.z());
					shot(context, real, o, new double[] {summon.x() + 0.5 + toDoor[0] * 11, 4.5, summon.z() + 0.5 + toDoor[1] * 11},
							new double[] {summon.x() + 0.5, 1.5, summon.z() + 0.5}, "mlinnotas-ruin-ruined_chapel-nave");
				}
				case WELL -> {
					shot(context, real, o, new double[] {cx + Math.cos(ang) * 4.5, 2.2, cz + Math.sin(ang) * 4.5},
							new double[] {cx, 2.5, cz}, "mlinnotas-ruin-old_well-close");
					// da parede do fundo (ao lado do bau) olhando para a agua embaixo do poco
					Pos ladder = plan.positionsOf(RuinPart.LADDER).stream().min((p, q) -> Integer.compare(p.y(), q.y())).orElseThrow();
					double[] fromLadder = unit(chest.x() - ladder.x(), chest.z() - ladder.z());
					shot(context, real, o, new double[] {chest.x() + 0.5 - fromLadder[0] * 1.5, ladder.y() + 1.2, chest.z() + 0.5 - fromLadder[1] * 1.5},
							new double[] {ladder.x() + 0.5, ladder.y() + 0.5, ladder.z() + 0.5}, "mlinnotas-ruin-old_well-cistern");
				}
				case MINE -> {
					Pos cart = plan.positionsOf(RuinPart.MINECART).getFirst();
					Pos ramp = plan.positionsOf(RuinPart.RAIL_UP).stream().min((p, q) -> Integer.compare(p.y(), q.y())).orElseThrow();
					shot(context, real, o, new double[] {ramp.x() + 0.5, ramp.y() + 1.8, ramp.z() + 0.5},
							new double[] {cart.x() + 0.5, cart.y() + 0.5, cart.z() + 0.5}, "mlinnotas-ruin-disused_mine-chamber");
					Pos altar = plan.marker(RuinPlan.MARKER_BOSS_ALTAR);
					double[] toChest = unit(chest.x() - altar.x(), chest.z() - altar.z());
					shot(context, real, o, new double[] {altar.x() + 0.5 + toChest[0] * 2.5, altar.y() + 2.6, altar.z() + 0.5 + toChest[1] * 2.5},
							new double[] {altar.x() - toChest[0] * 3 + 0.5, altar.y() + 0.5, altar.z() - toChest[1] * 3 + 0.5},
							"mlinnotas-ruin-disused_mine-altar");
				}
				case WATCHTOWER -> {
					// do outro lado, mais baixo: o lado desabado e o campo de entulho
					double back = front + Math.toRadians(turn * 2.6);
					shot(context, real, o, new double[] {cx + Math.cos(back) * (dist * 0.85), 6, cz + Math.sin(back) * (dist * 0.85)},
							new double[] {cx, 5, cz}, "mlinnotas-ruin-ruined_watchtower-collapse");
				}
				case CAMP -> shot(context, real, o, new double[] {cx + Math.cos(ang) * 6, 3.2, cz + Math.sin(ang) * 6},
						new double[] {cx, 1, cz}, "mlinnotas-ruin-abandoned_camp-ground");
				default -> {
				}
			}
		}
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}

	private static double[] unit(double x, double z) {
		double len = Math.max(1e-6, Math.sqrt(x * x + z * z));
		return new double[] {x / len, z / len};
	}

	/** Camera em {@code eye} (relativo a origem da ruina) olhando para {@code look}; espera os chunks e tira o screenshot. */
	private static void shot(ClientGameTestContext context, TestSingleplayerContext real, BlockPos o, double[] eye, double[] look, String name) {
		double ex = o.getX() + eye[0];
		double ey = o.getY() + eye[1];
		double ez = o.getZ() + eye[2];
		double dx = o.getX() + look[0] - ex;
		double dz = o.getZ() + look[2] - ez;
		double dy = o.getY() + look[1] - (ey + 1.62);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		real.getServer().runCommand(String.format(java.util.Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", ex, ey, ez, yaw, pitch));
		context.waitTicks(40);
		real.getConnection().waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot(name);
	}

	private record RuinView(BlockPos origin, RuinPlan plan, String style) {
	}
}
