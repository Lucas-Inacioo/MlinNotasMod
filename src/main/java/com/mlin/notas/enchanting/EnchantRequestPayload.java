package com.mlin.notas.enchanting;

import com.mlin.notas.MlinNotas;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.enchantment.Enchantment;

/** Cliente -> servidor: aplicar {@code enchantment} no nivel {@code level} ao item da mesa aberta. */
public record EnchantRequestPayload(Holder<Enchantment> enchantment, int level) implements CustomPacketPayload {
	public static final Type<EnchantRequestPayload> TYPE = new Type<>(MlinNotas.id("enchant_request"));
	public static final StreamCodec<RegistryFriendlyByteBuf, EnchantRequestPayload> CODEC = StreamCodec.composite(
			Enchantment.STREAM_CODEC, EnchantRequestPayload::enchantment,
			ByteBufCodecs.VAR_INT, EnchantRequestPayload::level,
			EnchantRequestPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
