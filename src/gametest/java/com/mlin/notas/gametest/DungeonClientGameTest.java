package com.mlin.notas.gametest;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.dungeon.DungeonBosses;
import com.mlin.notas.dungeon.DungeonPiece;
import com.mlin.notas.dungeon.DungeonPlan;
import com.mlin.notas.dungeon.DungeonPlan.Room;
import com.mlin.notas.dungeon.DungeonPlan.RoomType;
import com.mlin.notas.dungeon.DungeonRegistry;
import com.mlin.notas.dungeon.DungeonTheme;
import com.mlin.notas.util.Pos;
import com.mojang.datafixers.util.Pair;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;

import java.util.Locale;

/**
 * Game test de cliente das masmorras: acha cada tema num mundo gerado de verdade, confere no servidor o que a geracao
 * colocou (portao, cofre, mini-chefe, baus com loot, enigma que abre com um item jogado na placa) e fotografa o
 * predio da entrada, a sala de entrada, a sala do tema, a sala do mini-chefe e a camara final.
 */
@SuppressWarnings("UnstableApiUsage")
public class DungeonClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext real = context.worldBuilder().setUseConsistentSettings(false)
				.adjustSettings(settings -> settings.setSeed("mlinnotas-masmorras")).create()) {
			real.getConnection().waitForChunksRender();
			dungeons(context, real);
		}
	}

	private record View(BlockPos origin, DungeonPlan plan) {
	}

	private static void dungeons(ClientGameTestContext context, TestSingleplayerContext real) {
		real.getServer().runOnServer(server -> server.getPlayerList().getPlayers().getFirst().setGameMode(net.minecraft.world.level.GameType.SPECTATOR));
		real.getServer().runCommand("time set noon");
		real.getServer().runCommand("weather clear");
		context.runOnClient(mc -> {
			if (!mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
		for (DungeonTheme theme : DungeonTheme.values()) {
			BlockPos found = real.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				Holder<Structure> structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(DungeonRegistry.structureKey(theme));
				Pair<BlockPos, Holder<Structure>> result = level.getChunkSource().getGenerator()
						.findNearestMapStructure(level, HolderSet.direct(structure), BlockPos.ZERO, 100, false);
				return result == null ? null : result.getFirst();
			});
			if (found == null) {
				throw new IllegalStateException("nenhuma masmorra " + theme.id() + " num raio grande do mundo real");
			}
			View view = real.getServer().computeOnServer(server -> {
				ServerLevel level = server.overworld();
				Structure structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValueOrThrow(DungeonRegistry.structureKey(theme));
				ChunkAccess chunk = level.getChunk(found.getX() >> 4, found.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS);
				StructureStart start = chunk.getStartForStructure(structure);
				if (start == null) {
					return null;
				}
				for (StructurePiece piece : start.getPieces()) {
					if (piece instanceof DungeonPiece dungeon) {
						return new View(dungeon.origin(), dungeon.plan());
					}
				}
				return null;
			});
			if (view == null) {
				throw new IllegalStateException("estrutura " + theme.id() + " em " + found + " sem DungeonPiece");
			}
			BlockPos o = view.origin();
			DungeonPlan plan = view.plan();
			MlinNotas.LOGGER.info("[DungeonClientGameTest] {} em {}: {} andares, {} salas", theme.id(), o, plan.floors(), plan.rooms().size());

			// fachada: 3/4 de frente, um pouco de cima
			real.getServer().runCommand("effect clear @p");
			shot(context, real, o, new double[] {-9, 8, -14}, new double[] {0, 3, 1}, "mlinnotas-dungeon-" + theme.id() + "-entrance");
			// daqui em diante, visao noturna para ver os detalhes no subsolo
			real.getServer().runCommand("effect give @p minecraft:night_vision infinite 0 true");
			// escadaria vista de dentro do predio
			shot(context, real, o, new double[] {0, -0.4, 2.5}, new double[] {0, -12, 16}, "mlinnotas-dungeon-" + theme.id() + "-stairs");
			roomShot(context, real, o, plan.room(RoomType.ENTRY), "mlinnotas-dungeon-" + theme.id() + "-entry");
			Room themed = plan.rooms().stream().filter(r -> r.type() == RoomType.SHRINE).findFirst()
					.orElse(plan.room(RoomType.LIBRARY) != null ? plan.room(RoomType.LIBRARY) : plan.room(RoomType.SPAWNER));
			roomShot(context, real, o, themed, "mlinnotas-dungeon-" + theme.id() + "-" + themed.type().name().toLowerCase(Locale.ROOT));
			Room library = plan.room(RoomType.LIBRARY);
			if (library != null && library != themed) {
				roomShot(context, real, o, library, "mlinnotas-dungeon-" + theme.id() + "-library");
			}
			roomShot(context, real, o, plan.room(RoomType.TRAP), "mlinnotas-dungeon-" + theme.id() + "-trap");
			centerShot(context, real, o, plan.room(RoomType.BOSS), "mlinnotas-dungeon-" + theme.id() + "-boss");
			Room vault = plan.room(RoomType.VAULT);
			Pos gate = plan.marker(DungeonPlan.MARKER_GATE);
			// camara final vista da porta (lado do portao), olhando o cofre e o bau do fundo
			double gx = Math.signum(gate.x() - vault.cx());
			double gz = Math.signum(gate.z() - vault.cz());
			double half = (vault.x1() - vault.x0()) / 2.0;
			shot(context, real, o, new double[] {vault.cx() + 0.5 + gx * (half - 0.3), vault.fy() + 2.4, vault.cz() + 0.5 + gz * (half - 0.3)},
					new double[] {vault.cx() + 0.5 - gx * half, vault.fy() + 1.5, vault.cz() + 0.5 - gz * half}, "mlinnotas-dungeon-" + theme.id() + "-vault");

			String check = real.getServer().computeOnServer(server -> check(server.overworld(), o, plan));
			MlinNotas.LOGGER.info("[DungeonClientGameTest] {}: {}", theme.id(), check);
			if (!check.isEmpty()) {
				throw new IllegalStateException(theme.id() + " em " + o + ": " + check);
			}
			// enigma: um item jogado na placa de madeira abre a porta do nicho (com o jogador perto, para os blocos tickarem)
			roomShot(context, real, o, plan.room(RoomType.PUZZLE), "mlinnotas-dungeon-" + theme.id() + "-puzzle");
			Pos plate = plan.puzzle().plate();
			Pos door = plan.puzzle().door();
			// espectador nao mantem os chunks tickando: vira criativo (voando) durante o teste do enigma
			mode(real, net.minecraft.world.level.GameType.CREATIVE);
			context.waitTicks(20);
			real.getServer().runOnServer(server -> {
				ServerLevel level = server.overworld();
				BlockPos at = o.offset(plate.x(), plate.y(), plate.z());
				ItemEntity item = new ItemEntity(level, at.getX() + 0.5, at.getY() + 0.1, at.getZ() + 0.5, new ItemStack(Items.BONE));
				item.setPickUpDelay(32767);
				item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
				level.addFreshEntity(item);
			});
			context.waitTicks(30);
			boolean open = real.getServer().computeOnServer(server -> {
				BlockState state = server.overworld().getBlockState(o.offset(door.x(), door.y(), door.z()));
				return state.getBlock() instanceof DoorBlock && state.getValue(DoorBlock.OPEN);
			});
			if (!open) {
				String debug = real.getServer().computeOnServer(server -> {
					ServerLevel level = server.overworld();
					StringBuilder sb = new StringBuilder();
					sb.append("placa=").append(level.getBlockState(o.offset(plate.x(), plate.y(), plate.z())));
					for (Pos p : plan.positionsOf(com.mlin.notas.dungeon.DungeonPart.REDSTONE)) {
						sb.append(" | ").append(p).append('=').append(level.getBlockState(o.offset(p.x(), p.y(), p.z())));
					}
					sb.append(" | porta=").append(level.getBlockState(o.offset(door.x(), door.y(), door.z())));
					sb.append(" itens=").append(level.getEntitiesOfClass(ItemEntity.class, new AABB(o.offset(plate.x(), plate.y(), plate.z())).inflate(3)).size());
					return sb.toString();
				});
				MlinNotas.LOGGER.error("[DungeonClientGameTest] enigma: {}", debug);
				throw new IllegalStateException(theme.id() + ": a porta do enigma nao abriu com um item na placa");
			}
			Room puzzle = plan.room(RoomType.PUZZLE);
			roomShot(context, real, o, puzzle, "mlinnotas-dungeon-" + theme.id() + "-puzzle-open");
			real.getServer().runOnServer(server -> server.overworld().getEntitiesOfClass(ItemEntity.class,
					new AABB(o.offset(plate.x(), plate.y(), plate.z())).inflate(2)).forEach(ItemEntity::discard));
			mode(real, net.minecraft.world.level.GameType.SPECTATOR);
		}
		context.runOnClient(mc -> {
			if (mc.gui.hud.isHidden()) {
				mc.gui.hud.toggle();
			}
		});
	}

	private static void mode(TestSingleplayerContext real, net.minecraft.world.level.GameType type) {
		real.getServer().runOnServer(server -> {
			var player = server.getPlayerList().getPlayers().getFirst();
			player.setGameMode(type);
			if (type == net.minecraft.world.level.GameType.CREATIVE) {
				player.getAbilities().flying = true;
				player.onUpdateAbilities();
			}
		});
	}

	/** Confere o que a geracao colocou no mundo. Vazio = tudo certo. */
	private static String check(ServerLevel level, BlockPos o, DungeonPlan plan) {
		StringBuilder out = new StringBuilder();
		Pos gate = plan.marker(DungeonPlan.MARKER_GATE);
		if (!level.getBlockState(o.offset(gate.x(), gate.y(), gate.z())).is(DungeonRegistry.GATE)) {
			out.append("sem portao; ");
		}
		Pos vault = plan.marker(DungeonPlan.MARKER_VAULT);
		if (!level.getBlockState(o.offset(vault.x(), vault.y(), vault.z())).is(Blocks.VAULT)) {
			out.append("sem cofre; ");
		}
		Pos chest = plan.marker(DungeonPlan.MARKER_FINAL_CHEST);
		if (!(level.getBlockEntity(o.offset(chest.x(), chest.y(), chest.z())) instanceof ChestBlockEntity c) || c.getLootTable() == null) {
			out.append("bau final sem loot; ");
		}
		Pos boss = plan.marker(DungeonPlan.MARKER_BOSS);
		BlockPos b = o.offset(boss.x(), boss.y(), boss.z());
		boolean hasBoss = !level.getEntitiesOfClass(Mob.class, new AABB(b).inflate(12), m -> DungeonBosses.themeOf(m) == plan.theme()).isEmpty();
		if (!hasBoss) {
			out.append("sem mini-chefe; ");
		}
		return out.toString();
	}

	/** Do canto da sala (perto do teto) olhando o canto oposto. */
	private static void roomShot(ClientGameTestContext context, TestSingleplayerContext real, BlockPos o, Room room, String name) {
		if (room == null) {
			return;
		}
		double[] eye = {room.x0() + 1.0, room.fy() + Math.min(room.h() - 1.4, 3.0), room.z0() + 1.0};
		double[] look = {room.x1() - 0.5, room.fy() + 1.0, room.z1() - 0.5};
		shot(context, real, o, eye, look, name);
	}

	/** Do meio de uma parede, na altura do peito, atravessando o centro da sala (entre os pilares). */
	private static void centerShot(ClientGameTestContext context, TestSingleplayerContext real, BlockPos o, Room room, String name) {
		double[] eye = {room.x0() + 0.6, room.fy() + 2.2, room.cz() + 0.5};
		double[] look = {room.x1() + 0.5, room.fy() + 1.5, room.cz() + 0.5};
		shot(context, real, o, eye, look, name);
	}

	private static void shot(ClientGameTestContext context, TestSingleplayerContext real, BlockPos o, double[] eye, double[] look, String name) {
		double ex = o.getX() + eye[0];
		double ey = o.getY() + eye[1];
		double ez = o.getZ() + eye[2];
		double dx = o.getX() + look[0] - ex;
		double dz = o.getZ() + look[2] - ez;
		double dy = o.getY() + look[1] - (ey + 1.62);
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		real.getServer().runCommand(String.format(Locale.ROOT, "tp @p %.2f %.2f %.2f %.1f %.1f", ex, ey, ez, yaw, pitch));
		context.waitTicks(40);
		real.getConnection().waitForChunksRender();
		context.waitTicks(20);
		context.takeScreenshot(name);
	}
}
