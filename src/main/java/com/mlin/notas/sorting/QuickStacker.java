package com.mlin.notas.sorting;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Guardar rapido (logica pura): leva do inventario do jogador para o container toda pilha cujo tipo (id + variante)
 * ja existe no container. Primeiro completa as pilhas do mesmo tipo, depois usa espacos vazios; o que nao couber
 * fica com o jogador. Slots vazios sao {@code null}.
 */
public final class QuickStacker {
	private QuickStacker() {
	}

	/** Resultado: novos conteudos e quantos itens foram guardados. */
	public record Result(List<SlotItem> container, List<SlotItem> player, int moved) {
	}

	public static Result plan(List<SlotItem> container, List<SlotItem> player) {
		List<SlotItem> box = new ArrayList<>(container);
		List<SlotItem> inv = new ArrayList<>(player);
		Set<String> kinds = new HashSet<>();
		for (SlotItem item : box) {
			if (item != null) {
				kinds.add(key(item));
			}
		}
		int moved = 0;
		for (int p = 0; p < inv.size(); p++) {
			SlotItem stack = inv.get(p);
			if (stack == null || !kinds.contains(key(stack))) {
				continue;
			}
			int left = stack.count();
			// 1) completa as pilhas do mesmo tipo
			for (int c = 0; c < box.size() && left > 0; c++) {
				SlotItem target = box.get(c);
				if (target != null && target.sameKind(stack) && target.count() < target.max()) {
					int add = Math.min(left, target.max() - target.count());
					box.set(c, target.withCount(target.count() + add));
					left -= add;
				}
			}
			// 2) espacos vazios
			for (int c = 0; c < box.size() && left > 0; c++) {
				if (box.get(c) == null) {
					int add = Math.min(left, stack.max());
					box.set(c, stack.withCount(add));
					left -= add;
				}
			}
			moved += stack.count() - left;
			inv.set(p, left > 0 ? stack.withCount(left) : null);
		}
		return new Result(box, inv, moved);
	}

	private static String key(SlotItem item) {
		return item.id() + "|" + item.variant();
	}
}
