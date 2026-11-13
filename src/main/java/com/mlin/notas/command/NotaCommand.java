package com.mlin.notas.command;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.notes.Note;
import com.mlin.notas.notes.Notebook;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /nota add &lt;texto&gt; | /nota list | /nota remove &lt;id&gt;
 */
public final class NotaCommand {
	private NotaCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("nota")
				.then(Commands.literal("add")
						.then(Commands.argument("texto", StringArgumentType.greedyString())
								.executes(NotaCommand::add)))
				.then(Commands.literal("list")
						.executes(NotaCommand::list))
				.then(Commands.literal("remove")
						.then(Commands.argument("id", IntegerArgumentType.integer(1))
								.executes(NotaCommand::remove))));
	}

	private static Notebook notebookOf(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		ServerPlayer player = ctx.getSource().getPlayerOrException();
		return MlinNotas.NOTEBOOKS.of(player.getUUID());
	}

	private static int add(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		String text = StringArgumentType.getString(ctx, "texto");
		try {
			Note note = notebookOf(ctx).add(text);
			ctx.getSource().sendSuccess(() -> Component.literal("Nota #" + note.id() + " salva."), false);
			return note.id();
		} catch (IllegalArgumentException e) {
			ctx.getSource().sendFailure(Component.literal(e.getMessage()));
			return 0;
		}
	}

	private static int list(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		Notebook notebook = notebookOf(ctx);
		if (notebook.size() == 0) {
			ctx.getSource().sendSuccess(() -> Component.literal("Nenhuma nota."), false);
			return 0;
		}
		for (Note note : notebook.all()) {
			ctx.getSource().sendSuccess(() -> Component.literal("#" + note.id() + " " + note.text()), false);
		}
		return notebook.size();
	}

	private static int remove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		int id = IntegerArgumentType.getInteger(ctx, "id");
		boolean removed = notebookOf(ctx).remove(id);
		if (removed) {
			ctx.getSource().sendSuccess(() -> Component.literal("Nota #" + id + " removida."), false);
			return 1;
		}
		ctx.getSource().sendFailure(Component.literal("Nota #" + id + " nao existe."));
		return 0;
	}
}
