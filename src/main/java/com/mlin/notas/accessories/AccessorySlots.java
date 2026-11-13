package com.mlin.notas.accessories;

import java.util.List;

/**
 * Encaixes do jogador e regras de colocacao: 2 aneis, 1 colar, 1 cinto, 1 par de luvas e 1 amuleto. Cada encaixe
 * aceita so o seu tipo; artefatos e reliquias sao unicos (nao da para usar dois iguais). Logica pura.
 */
public final class AccessorySlots {
	/** Tipo de cada encaixe, na ordem do container (e da tela). */
	public static final List<AccessorySlotType> LAYOUT = List.of(AccessorySlotType.RING, AccessorySlotType.RING,
			AccessorySlotType.NECKLACE, AccessorySlotType.BELT, AccessorySlotType.GLOVES, AccessorySlotType.CHARM);
	public static final int SIZE = LAYOUT.size();

	private AccessorySlots() {
	}

	public static AccessorySlotType type(int index) {
		return LAYOUT.get(index);
	}

	/** O acessorio cabe no tipo do encaixe. */
	public static boolean fits(int index, AccessoryDef def) {
		return def != null && index >= 0 && index < SIZE && LAYOUT.get(index) == def.slot();
	}

	/**
	 * Pode por {@code def} no encaixe {@code index}, com {@code equipped} (tamanho {@link #SIZE}, null = vazio) do jogador.
	 * O que ja esta no proprio encaixe sera trocado, entao nao conta como repetido.
	 */
	public static boolean canPlace(List<AccessoryDef> equipped, int index, AccessoryDef def) {
		if (!fits(index, def)) {
			return false;
		}
		if (def.unique()) {
			for (int i = 0; i < equipped.size(); i++) {
				AccessoryDef other = equipped.get(i);
				if (i != index && other != null && other.id().equals(def.id())) {
					return false;
				}
			}
		}
		return true;
	}

	/** Primeiro encaixe vazio onde {@code def} pode ir, ou -1. */
	public static int firstFree(List<AccessoryDef> equipped, AccessoryDef def) {
		for (int i = 0; i < SIZE; i++) {
			if ((i >= equipped.size() || equipped.get(i) == null) && canPlace(equipped, i, def)) {
				return i;
			}
		}
		return -1;
	}
}
