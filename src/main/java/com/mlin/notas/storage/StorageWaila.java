package com.mlin.notas.storage;

import com.mlin.notas.waila.ContainerPeekPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Conteudo de uma Unidade de Armazenamento para o HUD WAILA (mesmo pacote dos baus, um grupo por tipo). */
public final class StorageWaila {
	private StorageWaila() {
	}

	public static ContainerPeekPayload peek(BlockPos pos, StorageUnitBlockEntity unit) {
		Map<ItemKey, Long> contents = unit.cell().contents();
		int sent = Math.min(contents.size(), ContainerPeekPayload.MAX_GROUPS);
		List<ItemStack> items = new ArrayList<>(sent);
		List<Integer> counts = new ArrayList<>(sent);
		for (Map.Entry<ItemKey, Long> entry : contents.entrySet()) {
			if (items.size() >= sent) {
				break;
			}
			items.add(entry.getKey().template().copy());
			counts.add((int) Math.min(Integer.MAX_VALUE, entry.getValue()));
		}
		return new ContainerPeekPayload(pos.immutable(), items, counts, contents.size() - sent, -1);
	}
}
