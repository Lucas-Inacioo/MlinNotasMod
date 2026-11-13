package com.mlin.notas.accessories;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Pacotes dos acessorios (cliente -> servidor). */
public final class AccessoryPayloads {
	private AccessoryPayloads() {
	}

	/** Botao da tela de inventario: abre o menu de acessorios. */
	public record Open() implements CustomPacketPayload {
		public static final Open INSTANCE = new Open();
		public static final Type<Open> TYPE = new Type<>(MlinNotas.id("open_accessories"));
		public static final StreamCodec<ByteBuf, Open> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** O cliente deu o pulo duplo (o movimento e do cliente; o servidor valida, zera a queda e mostra particulas). */
	public record DoubleJump() implements CustomPacketPayload {
		public static final DoubleJump INSTANCE = new DoubleJump();
		public static final Type<DoubleJump> TYPE = new Type<>(MlinNotas.id("double_jump"));
		public static final StreamCodec<ByteBuf, DoubleJump> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
