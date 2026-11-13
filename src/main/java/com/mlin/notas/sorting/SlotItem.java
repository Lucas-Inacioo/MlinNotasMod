package com.mlin.notas.sorting;

/**
 * Item de um slot, sem dependencia do Minecraft.
 *
 * @param id      identificador do item (ex.: "minecraft:stone")
 * @param variant discriminador de componentes/NBT; itens com mesmo id e variant podem ser fundidos
 * @param count   quantidade no slot
 * @param max     tamanho maximo da pilha
 */
public record SlotItem(String id, String variant, int count, int max) {
	public SlotItem {
		if (count <= 0) {
			throw new IllegalArgumentException("count deve ser positivo: " + count);
		}
		if (max <= 0) {
			throw new IllegalArgumentException("max deve ser positivo: " + max);
		}
	}

	public SlotItem withCount(int newCount) {
		return new SlotItem(id, variant, newCount, max);
	}

	public boolean sameKind(SlotItem other) {
		return other != null && id.equals(other.id) && variant.equals(other.variant);
	}
}
