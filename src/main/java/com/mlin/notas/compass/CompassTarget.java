package com.mlin.notas.compass;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Data component {@code mlinnotas:compass_target} das bussolas: o que foi escolhido na lista e em que pe esta a busca.
 * A posicao achada fica no {@code minecraft:lodestone_tracker} (sem rastrear magnetita), que o modelo vanilla de
 * bussola ja usa para apontar a agulha.
 */
public record CompassTarget(String id, int status) {
	public static final int SEARCHING = 0;
	public static final int FOUND = 1;
	public static final int NOT_FOUND = 2;

	public static final Codec<CompassTarget> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(CompassTarget::id),
			Codec.INT.optionalFieldOf("status", FOUND).forGetter(CompassTarget::status)
	).apply(i, CompassTarget::new));

	public static final StreamCodec<ByteBuf, CompassTarget> STREAM_CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, CompassTarget::id,
			ByteBufCodecs.VAR_INT, CompassTarget::status,
			CompassTarget::new);

	public CompassTarget withStatus(int newStatus) {
		return new CompassTarget(id, newStatus);
	}
}
