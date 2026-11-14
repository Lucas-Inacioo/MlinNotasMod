package com.mlin.notas.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Vinculo do Tablet de Rede: onde esta o terminal (dimensao + posicao) e se e o de crafting (componente do item). */
public record TabletLink(GlobalPos terminal, boolean crafting) {
	public static final Codec<TabletLink> CODEC = RecordCodecBuilder.create(i -> i.group(
			GlobalPos.CODEC.fieldOf("terminal").forGetter(TabletLink::terminal),
			Codec.BOOL.optionalFieldOf("crafting", false).forGetter(TabletLink::crafting)
	).apply(i, TabletLink::new));

	public static final StreamCodec<ByteBuf, TabletLink> STREAM_CODEC = StreamCodec.composite(
			GlobalPos.STREAM_CODEC, TabletLink::terminal,
			ByteBufCodecs.BOOL, TabletLink::crafting,
			TabletLink::new);
}
