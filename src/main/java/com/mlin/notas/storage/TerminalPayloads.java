package com.mlin.notas.storage;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Pacotes do terminal de armazenamento. */
public final class TerminalPayloads {
	private TerminalPayloads() {
	}

	/** Uma linha da sincronizacao: {@code stack} vazio quando o cliente ja conhece o numero; {@code count} 0 = sumiu. */
	public record Change(int id, ItemStack stack, long count) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Change> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Change::id,
				ItemStack.OPTIONAL_STREAM_CODEC, Change::stack,
				ByteBufCodecs.VAR_LONG, Change::count,
				Change::new);
	}

	/**
	 * Servidor -> cliente: mudancas no conteudo da rede desde o ultimo envio + resumo da rede (unidades, baus, se a
	 * busca parou no limite de tamanho, tipos livres nas unidades).
	 */
	public record Sync(int containerId, List<Change> changes, int units, int containers, boolean truncated, int freeTypes)
			implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(MlinNotas.id("terminal_sync"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Sync::containerId,
				Change.STREAM_CODEC.apply(ByteBufCodecs.list()), Sync::changes,
				ByteBufCodecs.VAR_INT, Sync::units,
				ByteBufCodecs.VAR_INT, Sync::containers,
				ByteBufCodecs.BOOL, Sync::truncated,
				ByteBufCodecs.VAR_INT, Sync::freeTypes,
				Sync::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Cliente -> servidor: clique na lista do terminal ({@code id} = numero do item, -1 = espaco vazio). */
	public record Action(int containerId, int action, int id) implements CustomPacketPayload {
		public static final int LEFT = 0;
		public static final int RIGHT = 1;
		public static final int SHIFT = 2;
		public static final int CLEAR_GRID = 3;

		public static final Type<Action> TYPE = new Type<>(MlinNotas.id("terminal_action"));
		public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Action::containerId,
				ByteBufCodecs.VAR_INT, Action::action,
				ByteBufCodecs.VAR_INT, Action::id,
				Action::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
