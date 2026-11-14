package com.mlin.notas.skills;

import com.mlin.notas.tier.Tier;

import java.util.List;

/**
 * Um nivel comprado da arvore (logica pura).
 *
 * @param branch         ramo
 * @param level          nivel que este no libera (Mineracao 2..12, ramos laterais 1..5)
 * @param items          custo em itens (id de item ou {@code #tag})
 * @param xpLevels       custo de XP em niveis nominais (cobrado em pontos, {@link #xpPoints()})
 * @param miningRequired nivel de Mineracao minimo para comprar (ramos laterais); null no tronco
 * @param icon           item mostrado na tela
 */
public record SkillNode(SkillBranch branch, int level, List<Cost> items, int xpLevels, Tier miningRequired, String icon) {
	/**
	 * Uma parte do custo.
	 *
	 * @param id      id do item ({@code minecraft:diamond}) ou da tag de item ({@code #mlinnotas:skill_cost/coal})
	 * @param count   quantidade
	 * @param display item mostrado na tela/tooltip (o proprio item, ou um exemplo da tag)
	 */
	public record Cost(String id, int count, String display) {
		public Cost {
			if (count <= 0) {
				throw new IllegalArgumentException("quantidade invalida: " + count);
			}
		}

		public boolean isTag() {
			return id.startsWith("#");
		}
	}

	public SkillNode {
		items = List.copyOf(items);
	}

	/** Custo de XP em pontos: os pontos para ir do nivel 0 a {@link #xpLevels()}. */
	public int xpPoints() {
		return com.mlin.notas.util.XpMath.pointsForLevels(xpLevels);
	}

	/** Chave estavel ({@code mining/3}) usada no save, nos pacotes e no lang. */
	public String key() {
		return key(branch, level);
	}

	public static String key(SkillBranch branch, int level) {
		return branch.id() + "/" + level;
	}

	/** Tier liberado por um no de Mineracao (null nos ramos laterais). */
	public Tier tier() {
		return branch == SkillBranch.MINING ? Tier.values()[level - 1] : null;
	}

	public int itemCount(String id) {
		for (Cost cost : items) {
			if (cost.id().equals(id)) {
				return cost.count();
			}
		}
		return 0;
	}
}
