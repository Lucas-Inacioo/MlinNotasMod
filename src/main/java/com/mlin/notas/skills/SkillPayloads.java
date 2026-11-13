package com.mlin.notas.skills;

import com.mlin.notas.MlinNotas;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.HashMap;
import java.util.Map;

/** Pacotes da arvore de habilidades. */
public final class SkillPayloads {
	private SkillPayloads() {
	}

	private static final StreamCodec<ByteBuf, Map<String, Integer>> INT_MAP =
			ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);

	/** Servidor -> cliente: progresso do mundo inteiro. Enviado ao entrar e a cada mudanca. */
	public record Sync(Map<String, Integer> levels, Map<String, Map<String, Integer>> items, Map<String, Integer> xp)
			implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(MlinNotas.id("skill_sync"));
		public static final StreamCodec<ByteBuf, Sync> CODEC = StreamCodec.composite(
				INT_MAP, Sync::levels,
				ByteBufCodecs.<ByteBuf, String, Map<String, Integer>, Map<String, Map<String, Integer>>>map(HashMap::new, ByteBufCodecs.STRING_UTF8, INT_MAP),
				Sync::items,
				INT_MAP, Sync::xp,
				Sync::new);

		public static Sync of(SkillProgress progress) {
			return new Sync(progress.levelsById(), progress.itemDeposits(), progress.xpDeposits());
		}

		public SkillProgress toProgress() {
			return SkillProgress.of(levels, items, xp);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Cliente -> servidor: depositar o que o jogador tiver no no {@code node} ({@code mining/3}). */
	public record Deposit(String node) implements CustomPacketPayload {
		public static final Type<Deposit> TYPE = new Type<>(MlinNotas.id("skill_deposit"));
		public static final StreamCodec<ByteBuf, Deposit> CODEC = ByteBufCodecs.STRING_UTF8.map(Deposit::new, Deposit::node);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}
}
