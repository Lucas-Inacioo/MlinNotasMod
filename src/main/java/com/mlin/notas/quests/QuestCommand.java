package com.mlin.notas.quests;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * {@code /quests reset} (zera o livro do mundo e os resgates), {@code /quests complete <capitulo/quest>} (completa a quest
 * e as dependencias que faltam), {@code /quests complete_chapter <capitulo>}. So operador (testes).
 */
public final class QuestCommand {
	private QuestCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("quests")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("reset").executes(ctx -> {
					QuestServer.reset(ctx.getSource().getServer());
					ctx.getSource().sendSuccess(() -> Component.translatable("commands.mlinnotas.quests.reset"), true);
					return 1;
				}))
				.then(Commands.literal("complete")
						.then(Commands.argument("quest", StringArgumentType.greedyString())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
										QuestServer.book().quests().stream().map(Quest::key), builder))
								.executes(QuestCommand::complete)))
				.then(Commands.literal("complete_chapter")
						.then(Commands.argument("chapter", StringArgumentType.word())
								.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
										QuestServer.book().chapters().stream().map(QuestChapter::id), builder))
								.executes(QuestCommand::completeChapter))));
	}

	private static int complete(CommandContext<CommandSourceStack> ctx) {
		String key = StringArgumentType.getString(ctx, "quest");
		Quest quest = QuestServer.book().quest(key);
		if (quest == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.mlinnotas.quests.unknown", key));
			return 0;
		}
		List<Quest> done = QuestServer.forceComplete(ctx.getSource().getServer(), quest);
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.mlinnotas.quests.completed", done.size()), true);
		return done.size();
	}

	private static int completeChapter(CommandContext<CommandSourceStack> ctx) {
		String id = StringArgumentType.getString(ctx, "chapter");
		QuestChapter chapter = QuestServer.book().chapter(id);
		if (chapter == null) {
			ctx.getSource().sendFailure(Component.translatable("commands.mlinnotas.quests.unknown", id));
			return 0;
		}
		int total = 0;
		for (Quest quest : chapter.quests()) {
			total += QuestServer.forceComplete(ctx.getSource().getServer(), quest).size();
		}
		int count = total;
		ctx.getSource().sendSuccess(() -> Component.translatable("commands.mlinnotas.quests.completed", count), true);
		return count;
	}
}
