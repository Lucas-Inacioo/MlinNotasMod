package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.boss.BossProjectile;
import com.mlin.notas.dragon.ArenaPart;
import com.mlin.notas.dragon.ArenaPlan;
import com.mlin.notas.dragon.ArenaPlanner;
import com.mlin.notas.dragon.DragonArenaFeature;
import com.mlin.notas.dragon.DragonBoss;
import com.mlin.notas.dragon.DragonHoard;
import com.mlin.notas.dragon.DragonRules;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.EndSpikeFeature;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Game test de cliente do rework do dragao: gera um mundo normal, vai a ilha central do End e confere a arena gerada
 * (blocos do plano no mundo, portal intacto), fotografa a ilha (visao geral, um pilar, o portal), o dragao na fase 2
 * (cristais renascendo, meteoros do vazio marcados e caindo, onda de choque do mergulho) e o bau do tesouro depois da
 * morte. Screenshots em build/run/clientGameTest/screenshots/mlinnotas-dragon-*.
 */
@SuppressWarnings("UnstableApiUsage")
public class DragonClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext world = context.worldBuilder().setUseConsistentSettings(false).create()) {
			world.getConnection().waitForChunksRender();
			world.getServer().runCommand("gamemode creative @p");
			// vai para o End e espera a luta comecar (portal de saida e dragao)
			camera(context, world, 0, 40, 60, 180.0F, 30.0F);
			context.waitTicks(100);
			parkDragon(world);
			context.waitTicks(20);
			world.getConnection().waitForChunksRender();
			checkArena(world);
			fight(context, world);
			hoard(context, world);
			// a ilha depois da luta (sem a neblina do chefe)
			context.runOnClient(mc -> hud(mc, false));
			shot(context, world, "mlinnotas-dragon-island", 0, 55, 50, 180.0F, 48.0F);
			shot(context, world, "mlinnotas-dragon-island-low", 40, 20, 40, 135.0F, 18.0F);
			pillar(context, world);
			shot(context, world, "mlinnotas-dragon-portal", 0, 7, 19, 180.0F, 18.0F);
			shot(context, world, "mlinnotas-dragon-plaza", 15, 6, 15, 135.0F, 15.0F);
			context.runOnClient(mc -> hud(mc, true));
		}
	}

	private static void hud(net.minecraft.client.Minecraft mc, boolean visible) {
		if (mc.gui.hud.isHidden() == visible) {
			mc.gui.hud.toggle();
		}
	}

	private static int ground(ServerLevel end, int x, int z) {
		end.getChunk(x >> 4, z >> 4);
		return end.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
	}

	/** Teleporta o jogador para (x, chao(0,0)+dy, z) no End, olhando para (yaw, pitch). */
	private static void camera(ClientGameTestContext context, TestSingleplayerContext world, double x, double dy, double z, float yaw, float pitch) {
		world.getServer().runOnServer(server -> {
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			ServerLevel end = server.getLevel(Level.END);
			int base = ground(end, 8, 0);
			player.getAbilities().flying = true;
			player.onUpdateAbilities();
			player.teleportTo(end, x + 0.5, base + dy, z + 0.5, Set.of(), yaw, pitch, true);
		});
		context.waitTicks(10);
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext world, String name, double x, double dy, double z, float yaw, float pitch) {
		camera(context, world, x, dy, z, yaw, pitch);
		context.waitTicks(20);
		world.getConnection().waitForChunksRender();
		context.takeScreenshot(name);
	}

	/** Tira o dragao de cena (parado bem alto) para as fotos da ilha. */
	private static void parkDragon(TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerLevel end = server.getLevel(Level.END);
			for (EnderDragon d : end.getDragons()) {
				d.setNoAi(true);
				d.snapTo(0, 220, 120, 0, 0);
			}
		});
	}

	/** Os blocos do plano estao no mundo e o portal de saida continua no lugar. */
	private static void checkArena(TestSingleplayerContext world) {
		String report = world.getServer().computeOnServer(server -> {
			ServerLevel end = server.getLevel(Level.END);
			ArenaPlan plan = DragonArenaFeature.plan(end, end.getChunkSource().getGenerator());
			int total = 0;
			int present = 0;
			for (Map.Entry<Long, ArenaPart> e : plan.blocks().entrySet()) {
				long k = e.getKey();
				BlockPos pos = new BlockPos(ArenaPlan.keyX(k), ArenaPlan.keyY(k), ArenaPlan.keyZ(k));
				end.getChunk(pos);
				total++;
				if (end.getBlockState(pos).is(DragonArenaFeature.state(e.getValue()).getBlock())) {
					present++;
				}
			}
			int podium = ground(end, 0, 0);
			boolean bedrock = end.getBlockState(new BlockPos(0, podium - 1, 0)).is(Blocks.BEDROCK);
			return total + " " + present + " " + bedrock + " " + podium + " " + end.getDragons().size();
		});
		MlinNotas.LOGGER.info("[DragonClientGameTest] arena: blocos/presentes/portal/y/dragoes = {}", report);
		String[] r = report.split(" ");
		int total = Integer.parseInt(r[0]);
		int present = Integer.parseInt(r[1]);
		if (total < 2000 || present < total * 0.9) {
			throw new IllegalStateException("arena incompleta no mundo: " + present + " de " + total + " blocos");
		}
		if (!Boolean.parseBoolean(r[2])) {
			throw new IllegalStateException("portal de saida (pilar de bedrock) sumiu: " + report);
		}
		if (Integer.parseInt(r[4]) != 1) {
			throw new IllegalStateException("deveria haver 1 dragao: " + report);
		}
	}

	/** Foto de um pilar alto, de baixo, com o soco, os contrafortes e os colares. */
	private static void pillar(ClientGameTestContext context, TestSingleplayerContext world) {
		int[] spike = world.getServer().computeOnServer(server -> {
			ServerLevel end = server.getLevel(Level.END);
			EndSpikeFeature.EndSpike tall = EndSpikeFeature.getSpikesForLevel(end).stream().max(Comparator.comparingInt(EndSpikeFeature.EndSpike::getHeight))
					.orElseThrow();
			return new int[] {tall.getCenterX(), tall.getCenterZ(), tall.getRadius(), tall.getHeight()};
		});
		double len = Math.sqrt(spike[0] * spike[0] + spike[1] * spike[1]);
		double ux = spike[0] / len;
		double uz = spike[1] / len;
		// de lado e um pouco para dentro da ilha, olhando para o pilar
		double cx = spike[0] - ux * 17 - uz * 6;
		double cz = spike[1] - uz * 17 + ux * 6;
		float yaw = (float) Math.toDegrees(Math.atan2(-(spike[0] - cx), spike[1] - cz));
		shot(context, world, "mlinnotas-dragon-pillar", cx, 4, cz, yaw, -18.0F);
	}

	/** Fase 2 com cristais renascendo, meteoros marcados/caindo e a onda de choque. */
	private static void fight(ClientGameTestContext context, TestSingleplayerContext world) {
		camera(context, world, 0, 8, 22, 180.0F, 12.0F);
		String info = world.getServer().computeOnServer(server -> {
			ServerLevel end = server.getLevel(Level.END);
			EnderDragon dragon = end.getDragons().getFirst();
			int base = ground(end, 8, 0);
			// tira os cristais dos 2 pilares mais baixos para ver o renascimento
			List<EndSpikeFeature.EndSpike> spikes = EndSpikeFeature.getSpikesForLevel(end).stream()
					.sorted(Comparator.comparing(EndSpikeFeature.EndSpike::isGuarded).thenComparingInt(EndSpikeFeature.EndSpike::getHeight)).toList();
			for (int i = 0; i < 2; i++) {
				end.getEntitiesOfClass(EndCrystal.class, spikes.get(i).getTopBoundingBox()).forEach(c -> c.discard());
			}
			dragon.snapTo(0.5, base + 11, -6.5, 0.0F, 0.0F);
			dragon.setYHeadRot(0.0F);
			dragon.setHealth(dragon.getMaxHealth() * 0.45F);
			return dragon.getMaxHealth() + " " + DragonBoss.phase(dragon);
		});
		context.waitTicks(15);
		String phase = world.getServer().computeOnServer(server -> {
			ServerLevel end = server.getLevel(Level.END);
			EnderDragon dragon = end.getDragons().getFirst();
			int crystals = 0;
			for (EndSpikeFeature.EndSpike s : EndSpikeFeature.getSpikesForLevel(end)) {
				crystals += end.getEntitiesOfClass(EndCrystal.class, s.getTopBoundingBox()).size();
			}
			int base = ground(end, 8, 0);
			for (double[] m : new double[][] {{-5, 4}, {4, 3}, {-1, -1}, {6, 10}, {-7, 11}}) {
				DragonBoss.markMeteor(end, dragon, m[0] + 0.5, m[1] + 0.5, 2, DragonRules.METEOR_WARNING + (long) (m[0] + 8) * 2);
			}
			return dragon.getAttachedOrElse(DragonBoss.PHASE, 1) + " " + crystals + " " + DragonBoss.pendingMeteors(dragon) + " base " + base;
		});
		MlinNotas.LOGGER.info("[DragonClientGameTest] dragao: vida/fase {} -> fase/cristais/meteoros {}", info, phase);
		String[] p = phase.split(" ");
		if (Float.parseFloat(info.split(" ")[0]) != (float) com.mlin.notas.dragon.DragonRules.BASE_HEALTH) {
			throw new IllegalStateException("vida do dragao deveria ser escalada para " + com.mlin.notas.dragon.DragonRules.BASE_HEALTH + " (1 jogador): " + info);
		}
		if (Integer.parseInt(p[0]) != 2) {
			throw new IllegalStateException("o dragao deveria ter entrado na fase 2: " + phase);
		}
		if (Integer.parseInt(p[1]) < 10) {
			throw new IllegalStateException("os cristais deveriam renascer na fase 2 (10 pilares): " + phase);
		}
		context.waitTicks(20);
		context.takeScreenshot("mlinnotas-dragon-phase2-warning");
		context.waitTicks(DragonRules.METEOR_WARNING - 8);
		context.takeScreenshot("mlinnotas-dragon-phase2-meteors");
		int falling = world.getServer().computeOnServer(server -> server.getLevel(Level.END)
				.getEntitiesOfClass(BossProjectile.class, new AABB(BlockPos.ZERO).inflate(40, 200, 40)).size());
		MlinNotas.LOGGER.info("[DragonClientGameTest] meteoros no ar: {}", falling);
		context.waitTicks(30);
		// mergulho: circulo marcado, depois a onda de choque
		world.getServer().runOnServer(server -> {
			ServerLevel end = server.getLevel(Level.END);
			EnderDragon dragon = end.getDragons().getFirst();
			int y = ground(end, 6, 0);
			DragonBoss.startDive(end, dragon, new Vec3(6.5, y, 0.5));
		});
		context.waitTicks(20);
		context.takeScreenshot("mlinnotas-dragon-dive-warning");
		context.waitTicks(DragonRules.DIVE_WINDUP - 20 + 9);
		context.takeScreenshot("mlinnotas-dragon-shockwave");
		context.waitTicks(30);
	}

	/** Mata o dragao e confere o bau do tesouro no estrado norte. */
	private static void hoard(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerLevel end = server.getLevel(Level.END);
			EnderDragon dragon = end.getDragons().getFirst();
			dragon.kill(end);
		});
		context.waitTicks(20);
		BlockPos chest = world.getServer().computeOnServer(server -> {
			ServerLevel end = server.getLevel(Level.END);
			for (int y = 40; y < 100; y++) {
				BlockPos pos = new BlockPos(ArenaPlanner.HOARD_X, y, ArenaPlanner.HOARD_Z);
				if (end.getBlockState(pos).is(Blocks.CHEST)) {
					return pos;
				}
			}
			return null;
		});
		if (chest == null) {
			throw new IllegalStateException("bau do tesouro (" + DragonHoard.LOOT + ") nao apareceu no estrado norte");
		}
		context.runOnClient(mc -> hud(mc, false));
		shot(context, world, "mlinnotas-dragon-hoard", 0, 5, -3, 180.0F, 25.0F);
		context.runOnClient(mc -> hud(mc, true));
	}
}
