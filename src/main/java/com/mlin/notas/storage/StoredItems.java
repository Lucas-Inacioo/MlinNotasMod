package com.mlin.notas.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conteudo de uma Unidade de Armazenamento como dado: no block entity e no item (data component
 * {@code mlinnotas:storage_contents}, como a caixa de shulker). Cada entrada e um modelo com quantidade 1 + a
 * quantidade real (pode passar muito de 64).
 */
public record StoredItems(List<Entry> entries) {
	public static final StoredItems EMPTY = new StoredItems(List.of());

	public record Entry(ItemStack item, long count) {
		public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
				ItemStack.CODEC.fieldOf("item").forGetter(Entry::item),
				Codec.LONG.fieldOf("count").forGetter(Entry::count)
		).apply(i, Entry::new));
		public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
				ItemStack.STREAM_CODEC, Entry::item,
				ByteBufCodecs.VAR_LONG, Entry::count,
				Entry::new);

		@Override
		public boolean equals(Object other) {
			return other instanceof Entry e && count == e.count && ItemStack.isSameItemSameComponents(item, e.item);
		}

		@Override
		public int hashCode() {
			return 31 * ItemStack.hashItemAndComponents(item) + Long.hashCode(count);
		}
	}

	public static final Codec<StoredItems> CODEC = Entry.CODEC.listOf().xmap(StoredItems::new, StoredItems::entries);
	public static final StreamCodec<RegistryFriendlyByteBuf, StoredItems> STREAM_CODEC =
			Entry.STREAM_CODEC.apply(ByteBufCodecs.list()).map(StoredItems::new, StoredItems::entries);

	public StoredItems {
		entries = List.copyOf(entries);
	}

	public static StoredItems of(Map<ItemKey, Long> contents) {
		List<Entry> list = new ArrayList<>(contents.size());
		contents.forEach((key, count) -> {
			if (count > 0) {
				list.add(new Entry(key.template().copy(), count));
			}
		});
		return list.isEmpty() ? EMPTY : new StoredItems(list);
	}

	public Map<ItemKey, Long> toMap() {
		Map<ItemKey, Long> map = new LinkedHashMap<>();
		for (Entry entry : entries) {
			if (!entry.item().isEmpty() && entry.count() > 0) {
				map.merge(ItemKey.of(entry.item()), entry.count(), Long::sum);
			}
		}
		return map;
	}

	public boolean isEmpty() {
		return entries.isEmpty();
	}

	public long totalCount() {
		long total = 0;
		for (Entry entry : entries) {
			total += entry.count();
		}
		return total;
	}
}
