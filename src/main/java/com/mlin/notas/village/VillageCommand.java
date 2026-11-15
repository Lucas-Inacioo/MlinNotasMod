package com.mlin.notas.village;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * Reparos de vila (operador): {@code /village admin list}, {@code /village admin remove <nome>} (apaga o registro da
 * vila, sem mexer nos blocos) e {@code /village admin restart_starter} (apaga o registro da vila inicial e cria outra
 * agora, num lugar de chao natural perto do spawn) e {@code /village admin field <x> <z>} (campo de alturas da vila
 * inicial na coluna: altura alvo, tipo, zona, peso da mistura e profundidade fechada).
 */
public final class VillageCommand {
	private VillageCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("village")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("admin")
						.then(Commands.literal("list").executes(VillageCommand::list))
						.then(Commands.literal("remove")
								.then(Commands.argument("name", StringArgumentType.greedyString())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
												VillageData.get(ctx.getSource().getServer()).villages().stream().map(VillageState::name), builder))
										.executes(VillageCommand::remove)))
						.then(Commands.literal("restart_starter").executes(VillageCommand::restartStarter))
						.then(Commands.literal("rebuild_walls").executes(VillageCommand::rebuildWalls))
						.then(PlotCommand.node())
						.then(Commands.literal("field")
								.then(Commands.argument("x", IntegerArgumentType.integer())
										.then(Commands.argument("z", IntegerArgumentType.integer())
												.executes(VillageFieldServer::describe))))));
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		List<VillageState> villages = List.copyOf(VillageData.get(ctx.getSource().getServer()).villages());
		for (VillageState v : villages) {
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.mlinnotas.village.entry", v.name(), v.x(), v.y(), v.z(),
					VillageServer.levelName(v.level()), v.starter() ? "*" : ""), false);
		}
		return villages.size();
	}

	private static int remove(CommandContext<CommandSourceStack> ctx) {
		String name = StringArgumentType.getString(ctx, "name");
		VillageData data = VillageData.get(ctx.getSource().getServer());
		int removed = 0;
		for (VillageState v : List.copyOf(data.villages())) {
			if (v.name().equalsIgnoreCase(name.trim())) {
				data.remove(v.key());
				removed++;
			}
		}
		if (removed == 0) {
			ctx.getSource().sendFailure(Component.translatable("commands.mlinnotas.village.unknown", name));
			return 0;
		}
		int n = removed;
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.mlinnotas.village.removed", name, n), true);
		return removed;
	}

	private static int rebuildWalls(CommandContext<CommandSourceStack> ctx) {
		VillageData data = VillageData.get(ctx.getSource().getServer());
		int total = 0;
		for (VillageState v : List.copyOf(data.villages())) {
			if (!v.starter()) {
				continue;
			}
			ServerLevel level = VillageServer.levelOf(ctx.getSource().getServer(), v);
			WallRebuild.Report r = level == null ? null : WallRebuild.run(level, v);
			if (r != null) {
				total++;
				ctx.getSource().sendSuccess(() -> Component.translatable("commands.mlinnotas.village.rebuild_walls", v.name(), r.removed(), r.regraded(),
						r.placed(), r.reopenedWorks()), true);
			}
		}
		if (total == 0) {
			ctx.getSource().sendFailure(Component.translatable("commands.mlinnotas.village.rebuild_walls.none"));
		}
		return total;
	}

	private static int restartStarter(CommandContext<CommandSourceStack> ctx) {
		VillageState village = StartingVillage.restart(ctx.getSource().getServer());
		if (village == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.mlinnotas.village.restart_failed"));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.mlinnotas.village.restarted", village.name(), village.x(), village.y(),
				village.z()), true);
		return 1;
	}
}
