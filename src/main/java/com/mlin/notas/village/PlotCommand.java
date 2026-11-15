package com.mlin.notas.village;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/**
 * {@code /village admin plot add <x1> <z1> <x2> <z2> <chaoY>} (registra um terreno do jogador numa vila que ja existe, ex.:
 * em volta da casa), {@code plot remove <id>} e {@code plot list} (operador). Regras em {@link PlotPlanner}, mundo em
 * {@link PlotServer}.
 */
final class PlotCommand {
	private PlotCommand() {
	}

	static LiteralArgumentBuilder<CommandSourceStack> node() {
		return Commands.literal("plot")
				.then(Commands.literal("list").executes(PlotCommand::list))
				.then(Commands.literal("remove").then(Commands.argument("id", IntegerArgumentType.integer(0)).executes(PlotCommand::remove)))
				.then(Commands.literal("add")
						.then(Commands.argument("x1", IntegerArgumentType.integer())
								.then(Commands.argument("z1", IntegerArgumentType.integer())
										.then(Commands.argument("x2", IntegerArgumentType.integer())
												.then(Commands.argument("z2", IntegerArgumentType.integer())
														.then(Commands.argument("floorY", IntegerArgumentType.integer())
																.executes(PlotCommand::add)))))));
	}

	/** A vila inicial (a unica que o mod gerencia). */
	private static VillageState village(CommandContext<CommandSourceStack> ctx) {
		VillageState starter = StartingVillage.starter(VillageData.get(ctx.getSource().getServer()));
		if (starter == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.mlinnotas.village.plot.no_village"));
		}
		return starter;
	}

	private static int add(CommandContext<CommandSourceStack> ctx) {
		VillageState village = village(ctx);
		if (village == null) {
			return 0;
		}
		ServerLevel level = VillageServer.levelOf(ctx.getSource().getServer(), village);
		int x1 = IntegerArgumentType.getInteger(ctx, "x1");
		int z1 = IntegerArgumentType.getInteger(ctx, "z1");
		int x2 = IntegerArgumentType.getInteger(ctx, "x2");
		int z2 = IntegerArgumentType.getInteger(ctx, "z2");
		int y = IntegerArgumentType.getInteger(ctx, "floorY");
		PlotServer.Outcome out = PlotServer.add(level, village, x1, z1, x2, z2, y);
		if (!out.ok()) {
			ctx.getSource().sendFailure(Component.translatable("commands.mlinnotas.village.plot.fail." + out.problem().name().toLowerCase(java.util.Locale.ROOT),
					out.x(), out.z()));
			return 0;
		}
		MasterPlan.Parcel p = out.plot();
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.mlinnotas.village.plot.added", p.id(), village.x() + p.rect().x0(),
				village.z() + p.rect().z0(), village.x() + p.rect().x1(), village.z() + p.rect().z1(), y), true);
		if (!out.fieldCovers()) {
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.mlinnotas.village.plot.outside_field"), false);
		}
		return p.id();
	}

	private static int remove(CommandContext<CommandSourceStack> ctx) {
		VillageState village = village(ctx);
		if (village == null) {
			return 0;
		}
		int id = IntegerArgumentType.getInteger(ctx, "id");
		if (!PlotServer.remove(VillageServer.levelOf(ctx.getSource().getServer(), village), village, id)) {
			ctx.getSource().sendFailure(Component.translatable("commands.mlinnotas.village.plot.unknown", id));
			return 0;
		}
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.mlinnotas.village.plot.removed", id), true);
		return 1;
	}

	private static int list(CommandContext<CommandSourceStack> ctx) {
		VillageState village = village(ctx);
		if (village == null) {
			return 0;
		}
		List<MasterPlan.Parcel> plots = village.layout() == null || village.layout().master() == null ? List.of() : village.layout().master().plots();
		if (plots.isEmpty()) {
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.mlinnotas.village.plot.none"), false);
			return 0;
		}
		for (MasterPlan.Parcel p : plots) {
			String state = p.terraformPending() ? "…" : "";
			ctx.getSource().sendSuccess(() -> Component.translatable("commands.mlinnotas.village.plot.entry", p.id(), village.x() + p.rect().x0(),
					village.z() + p.rect().z0(), village.x() + p.rect().x1(), village.z() + p.rect().z1(), village.layout().master().plotFloor(p), state),
					false);
		}
		return plots.size();
	}
}
