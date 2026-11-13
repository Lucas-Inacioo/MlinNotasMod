package com.mlin.notas.waila;

import com.mlin.notas.MlinNotas;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Servidor -> cliente: conteudo resumido de um container (pilhas iguais agrupadas, quantidade total em
 * {@code counts}), quantos grupos ficaram de fora e o nivel do upgrade de fornalha (-1 se nao for fornalha).
 */
public record ContainerPeekPayload(BlockPos pos, List<ItemStack> items, List<Integer> counts, int more, int furnaceTier)
		implements CustomPacketPayload {
	public static final Type<ContainerPeekPayload> TYPE = new Type<>(MlinNotas.id("container_peek"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ContainerPeekPayload> CODEC = StreamCodec.of(ContainerPeekPayload::write, ContainerPeekPayload::read);
	/** Grupos enviados no maximo (6 linhas de 9). */
	public static final int MAX_GROUPS = 54;

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	private static void write(RegistryFriendlyByteBuf buf, ContainerPeekPayload payload) {
		BlockPos.STREAM_CODEC.encode(buf, payload.pos);
		buf.writeVarInt(payload.items.size());
		for (int i = 0; i < payload.items.size(); i++) {
			ItemStack.STREAM_CODEC.encode(buf, payload.items.get(i).copyWithCount(1));
			buf.writeVarInt(payload.counts.get(i));
		}
		buf.writeVarInt(payload.more);
		buf.writeVarInt(payload.furnaceTier + 1);
	}

	private static ContainerPeekPayload read(RegistryFriendlyByteBuf buf) {
		BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
		int size = buf.readVarInt();
		List<ItemStack> items = new ArrayList<>(Math.min(size, MAX_GROUPS));
		List<Integer> counts = new ArrayList<>(Math.min(size, MAX_GROUPS));
		for (int i = 0; i < size; i++) {
			items.add(ItemStack.STREAM_CODEC.decode(buf));
			counts.add(buf.readVarInt());
		}
		int more = buf.readVarInt();
		int furnaceTier = buf.readVarInt() - 1;
		return new ContainerPeekPayload(pos, items, counts, more, furnaceTier);
	}
}
