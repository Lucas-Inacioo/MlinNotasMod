package com.mlin.notas.smithing;

import com.mlin.notas.tier.Tier;

import java.util.List;

/**
 * Cadeia de ferraria pos-netherite (logica pura): cada nivel acima do netherite e um upgrade na mesa de ferraria
 * (molde + peca do nivel anterior + 1 lingote do nivel novo), como o netherite vanilla. O molde sai do chefe que libera
 * o nivel e se duplica na mesa de trabalho (molde + 7 do material + bloco base = 2 moldes), para o casal ter um cada.
 *
 * <p>Os JSONs (receitas de ferraria para cada ferramenta/arma/armadura e a duplicacao) vem de
 * {@code scripts/ProgressionContent.java} ({@code smithingUpgrade}); o teste {@code SmithingChainResourcesTest}
 * confere que toda peca do nivel tem a receita.</p>
 */
public enum SmithingChain {
	INFERNAL("infernal", Tier.NETHERITE, Tier.INFERNITE, "igneous_colossus", "mlinnotas:raw_infernite", "minecraft:blackstone"),
	END("end", Tier.INFERNITE, Tier.ENDITE, "ender_dragon", "mlinnotas:raw_endite", "minecraft:end_stone"),
	ASTRAL("astral", Tier.ENDITE, Tier.ASTRALITE, "astral_herald", "mlinnotas:raw_astralite", "minecraft:purpur_block"),
	CELESTIAL("celestial", Tier.ASTRALITE, Tier.CELESTIUM, "wither", "mlinnotas:astralite_ingot", "minecraft:crying_obsidian");

	private final String id;
	private final Tier from;
	private final Tier to;
	private final String boss;
	private final String duplicationMaterial;
	private final String duplicationBlock;

	SmithingChain(String id, Tier from, Tier to, String boss, String duplicationMaterial, String duplicationBlock) {
		this.id = id;
		this.from = from;
		this.to = to;
		this.boss = boss;
		this.duplicationMaterial = duplicationMaterial;
		this.duplicationBlock = duplicationBlock;
	}

	/** Caminho do id do molde: {@code infernal_upgrade_smithing_template}. */
	public String templateId() {
		return id + "_upgrade_smithing_template";
	}

	public String id() {
		return id;
	}

	/** Nivel da peca que entra na mesa de ferraria. */
	public Tier from() {
		return from;
	}

	/** Nivel da peca que sai. */
	public Tier to() {
		return to;
	}

	/** Quem solta o molde (id da entidade, sem namespace). */
	public String boss() {
		return boss;
	}

	/** Item da duplicacao (7 por receita). */
	public String duplicationMaterial() {
		return duplicationMaterial;
	}

	public String duplicationBlock() {
		return duplicationBlock;
	}

	/** Lingote do nivel novo (o "adicional" da mesa de ferraria). */
	public String ingot() {
		return "mlinnotas:" + to.id() + "_ingot";
	}

	/** O nivel e feito na ferraria (nao tem receita de mesa de trabalho para as pecas). */
	public static SmithingChain forTier(Tier tier) {
		for (SmithingChain step : values()) {
			if (step.to == tier) {
				return step;
			}
		}
		return null;
	}

	/** Sufixos das pecas que passam pela cadeia (ferramentas/armas e armaduras dos niveis do mod). */
	public static final List<String> PIECES = List.of("sword", "shovel", "pickaxe", "axe", "hoe", "spear", "helmet", "chestplate", "leggings", "boots");
}
