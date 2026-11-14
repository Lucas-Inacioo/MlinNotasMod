package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.end.CitadelPiece;
import com.mlin.notas.end.CitadelPlan;
import com.mlin.notas.end.CitadelPlanner;
import com.mlin.notas.util.Pos;
import com.mlin.notas.vstructures.VanillaRemakePiece;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.Map;
import java.util.Set;

/**
 * Game test de cliente da Cidadela do End: monta a cidadela inteira no vazio do End (entre a ilha central e as ilhas
 * externas), confere os blocos no mundo e fotografa por fora (varios angulos, de baixo, a nave, as ilhotas) e por dentro
 * (salao, biblioteca, banquete, alquimia, cofre, observatorio, cabine do elitro, uma torre). Screenshots em
 * build/run/clientGameTest/screenshots/mlinnotas-citadel-*.
 */
@SuppressWarnings("UnstableApiUsage")
public class CitadelClientGameTest implements FabricClientGameTest {
	private static final long SEED = 20261113L;
	private static final BlockPos ORIGIN = new BlockPos(480, 70, 480);

	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @p");
			context.runOnClient(mc -> mc.options.gamma().set(1.0));
			camera(context, world, 0, 60, 90, 0, 20, 0);
			String report = world.getServer().computeOnServer(server -> {
				ServerLevel end = server.getLevel(Level.END);
				CitadelPiece piece = CitadelGameTest.placeWhole(end, SEED, ORIGIN);
				for (Mob mob : end.getEntitiesOfClass(Mob.class, AABB.of(piece.getBoundingBox()).inflate(4))) {
					mob.setNoAi(true);
				}
				int total = 0;
				int present = 0;
				for (Map.Entry<Long, String> e : piece.plan().blocks().entrySet()) {
					long k = e.getKey();
					total++;
					if (end.getBlockState(piece.world(CitadelPlan.keyX(k), CitadelPlan.keyY(k), CitadelPlan.keyZ(k))).getBlock()
							== VanillaRemakePiece.parse(e.getValue()).getBlock()) {
						present++;
					}
				}
				return total + " " + present;
			});
			MlinNotas.LOGGER.info("[CitadelClientGameTest] blocos/presentes = {}", report);
			String[] r = report.split(" ");
			if (Integer.parseInt(r[1]) < Integer.parseInt(r[0]) * 0.98) {
				throw new IllegalStateException("cidadela incompleta no mundo: " + report);
			}
			CitadelPlan plan = CitadelPiece.planFor(SEED);
			context.runOnClient(mc -> hud(mc, false));

			// por fora
			shot(context, world, "mlinnotas-citadel-overview", 62, 48, 62, 0, 18, 0);
			shot(context, world, "mlinnotas-citadel-south", 0, 14, 82, 0, 22, 0);
			shot(context, world, "mlinnotas-citadel-west", -80, 28, -12, 0, 18, 0);
			shot(context, world, "mlinnotas-citadel-below", 46, -40, -46, 0, -6, 0);
			shot(context, world, "mlinnotas-citadel-courtyard", -19, 2, 20, 0, 14, 0);
			CitadelPlan.Tower dock = plan.tower("dock");
			int dx = Integer.signum(dock.x());
			int dz = Integer.signum(dock.z());
			int sx = dx * CitadelPlanner.SHIP_DIST;
			int sz = dz * CitadelPlanner.SHIP_DIST;
			// nave de lado (de fora para dentro) e de cima do cais
			shot(context, world, "mlinnotas-citadel-ship", sx + dx * 30 + Math.abs(dz) * 8, CitadelPlanner.DECK_Y + 8, sz + dz * 30 + Math.abs(dx) * 8,
					sx, CitadelPlanner.DECK_Y + 4, sz);
			shot(context, world, "mlinnotas-citadel-ship-deck", dock.x() + dx * 3, CitadelPlanner.DECK_Y + 4, dock.z() + dz * 3, sx, CitadelPlanner.DECK_Y + 6, sz);
			Pos elytra = plan.marker("elytra");
			CitadelPlan.Spawn frame = plan.spawns().stream().filter(s -> s.kind() == CitadelPlan.Kind.ELYTRA_FRAME).findFirst().orElseThrow();
			shot(context, world, "mlinnotas-citadel-ship-cabin", elytra.x() + frame.dirX() * 6, elytra.y() - 0.6, elytra.z() + frame.dirZ() * 6,
					elytra.x(), elytra.y(), elytra.z());
			// uma ilhota com ponte
			Pos islet = null;
			for (long k : plan.lootTables().keySet()) {
				int x = CitadelPlan.keyX(k);
				int z = CitadelPlan.keyZ(k);
				if (Math.sqrt(x * x + z * z) > CitadelPlanner.RADIUS + 4 && Math.abs(CitadelPlan.keyY(k)) < 10) {
					islet = new Pos(x, CitadelPlan.keyY(k), z);
				}
			}
			if (islet != null) {
				double len = Math.sqrt(islet.x() * islet.x() + islet.z() * islet.z());
				shot(context, world, "mlinnotas-citadel-islet", islet.x() * (len + 14) / len, islet.y() + 9, islet.z() * (len + 14) / len,
						islet.x() * 0.7, islet.y() + 2, islet.z() * 0.7);
			}

			// por dentro
			shot(context, world, "mlinnotas-citadel-hall", -5, 1, 5, 4, 2, -4);
			shot(context, world, "mlinnotas-citadel-nest", -4, 7, 4, 5, 7, -5);
			shot(context, world, "mlinnotas-citadel-library", -2, 13, 1, 4, 13.5, -4);
			shot(context, world, "mlinnotas-citadel-dining", 4, 19, 4, -3, 19.5, -3);
			shot(context, world, "mlinnotas-citadel-alchemy", 3, 25, 3, -4, 25.5, -4);
			shot(context, world, "mlinnotas-citadel-vault", -3, 31, 3, 2, 31.5, -2);
			shot(context, world, "mlinnotas-citadel-observatory", 1, 37, 1, -2, 38, -2);
			shot(context, world, "mlinnotas-citadel-roof", 5, 37, 5, 0, 44, 0);
			CitadelPlan.Tower tall = plan.towers().stream().filter(t -> t.name().startsWith("tower_")).max((a, b) -> Integer.compare(a.floors(), b.floors()))
					.orElseThrow();
			int ty = tall.floorY(tall.floors() - 1) + 1;
			shot(context, world, "mlinnotas-citadel-tower-top", tall.x() - Integer.signum(tall.x()) * 2 + 1, ty, tall.z() - Integer.signum(tall.z()) * 2 + 1,
					tall.x() + Integer.signum(tall.x()) * 4, ty + 1, tall.z() + Integer.signum(tall.z()) * 4);
			shot(context, world, "mlinnotas-citadel-bridge", tall.x() / 2.0 + Math.abs(Integer.signum(tall.z())) * 10, 24,
					tall.z() / 2.0 + Math.abs(Integer.signum(tall.x())) * 10, tall.x() / 2.0, 14, tall.z() / 2.0);
			context.runOnClient(mc -> hud(mc, true));
		}
	}

	private static void hud(net.minecraft.client.Minecraft mc, boolean visible) {
		if (mc.gui.hud.isHidden() == visible) {
			mc.gui.hud.toggle();
		}
	}

	/** Teleporta o jogador (pes em (x, y, z) relativo a origem da cidadela) olhando para (tx, ty, tz). */
	private static void camera(ClientGameTestContext context, TestSingleplayerContext world, double x, double y, double z, double tx, double ty, double tz) {
		double ex = x + 0.5;
		double ey = y + 1.62;
		double ez = z + 0.5;
		double ddx = tx + 0.5 - ex;
		double ddy = ty + 0.5 - ey;
		double ddz = tz + 0.5 - ez;
		float yaw = (float) Math.toDegrees(Math.atan2(-ddx, ddz));
		float pitch = (float) -Math.toDegrees(Math.atan2(ddy, Math.sqrt(ddx * ddx + ddz * ddz)));
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			ServerLevel end = server.getLevel(Level.END);
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			player.teleportTo(end, ORIGIN.getX() + x + 0.5, ORIGIN.getY() + y, ORIGIN.getZ() + z + 0.5, Set.of(), yaw, pitch, true);
		});
		context.waitTicks(10);
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext world, String name, double x, double y, double z, double tx, double ty,
			double tz) {
		camera(context, world, x, y, z, tx, ty, tz);
		context.waitTicks(20);
		world.getConnection().waitForChunksRender();
		context.takeScreenshot(name);
	}
}
