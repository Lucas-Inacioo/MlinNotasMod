package com.mlin.notas.season;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

/**
 * {@code /season} (data atual), {@code /season set <spring|summer|autumn|winter> [early|mid|late]} e
 * {@code /season reset} (operador).
 */
public final class SeasonCommand {
	private SeasonCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("season")
				.executes(SeasonCommand::info)
				.then(Commands.literal("info").executes(SeasonCommand::info))
				.then(Commands.literal("reset")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(ctx -> {
							SeasonServer.reset(ctx.getSource().getServer());
							return info(ctx);
						}))
				.then(Commands.literal("set")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.then(Commands.argument("season", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Arrays.stream(Season.values()).map(Season::id), builder))
								.executes(ctx -> set(ctx, Season.Phase.EARLY))
								.then(Commands.argument("phase", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
												Arrays.stream(Season.Phase.values()).map(Season.Phase::id), builder))
										.executes(ctx -> {
											Season.Phase phase = Season.Phase.byId(StringArgumentType.getString(ctx, "phase"));
											if (phase == null) {
												ctx.getSource().sendFailure(Component.translatable("command.mlinnotas.season.bad_phase"));
												return 0;
											}
											return set(ctx, phase);
										})))));
	}

	private static int set(CommandContext<CommandSourceStack> ctx, Season.Phase phase) {
		Season season = Season.byId(StringArgumentType.getString(ctx, "season"));
		if (season == null) {
			ctx.getSource().sendFailure(Component.translatable("command.mlinnotas.season.bad_season"));
			return 0;
		}
		SeasonServer.setSeason(ctx.getSource().getServer(), season, phase);
		return info(ctx);
	}

	private static int info(CommandContext<CommandSourceStack> ctx) {
		SeasonDate date = SeasonServer.date(ctx.getSource().getServer());
		if (date == null) {
			ctx.getSource().sendSuccess(() -> Component.translatable("command.mlinnotas.season.disabled"), false);
			return 0;
		}
		ctx.getSource().sendSuccess(() -> describe(date), false);
		return date.season().ordinal() + 1;
	}

	/** "Outono (meio), dia 4 de 8". */
	public static Component describe(SeasonDate date) {
		return Component.translatable("season.mlinnotas.date", name(date.season()), phaseName(date.phase()), date.displayDay(),
				date.lengthDays());
	}

	public static Component name(Season season) {
		return Component.translatable("season.mlinnotas." + season.id());
	}

	public static Component phaseName(Season.Phase phase) {
		return Component.translatable("season.mlinnotas.phase." + phase.id());
	}
}
