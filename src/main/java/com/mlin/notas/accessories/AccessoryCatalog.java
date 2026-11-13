package com.mlin.notas.accessories;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.mlin.notas.accessories.AccessoryAbility.*;
import static com.mlin.notas.accessories.AccessoryKind.*;
import static com.mlin.notas.accessories.AccessorySlotType.*;
import static com.mlin.notas.accessories.AccessoryStat.*;

/**
 * Todos os acessorios do mod (a ordem e a da aba criativa): aneis e colares de metal por nivel (receita), artefatos
 * achados explorando e uma reliquia por chefe. Logica pura; os itens sao registrados a partir daqui.
 *
 * <p>Os numeros sao pequenos de proposito: a soma de tudo que esta equipado passa pelo teto do estagio do mundo
 * ({@link AccessoryBudget}), entao o que conta e escolher o conjunto, nao empilhar.</p>
 */
public final class AccessoryCatalog {
	/** Metais dos aneis e colares basicos, em ordem de nivel. */
	public static final List<String> METALS = List.of("copper", "iron", "gold", "cobalt", "diamond", "netherite");

	private static final Map<String, AccessoryDef> BY_ID = new LinkedHashMap<>();

	static {
		// --- basicos: anel = dano, colar = reducao (e vida a partir do ouro); ouro troca um pouco de dano por sorte ---
		double[] ringDamage = {0.02, 0.03, 0.02, 0.04, 0.05, 0.06};
		double[] necklaceReduction = {0.01, 0.02, 0.02, 0.03, 0.04, 0.05};
		double[] necklaceHealth = {0, 0, 1, 1, 2, 2};
		for (int i = 0; i < METALS.size(); i++) {
			String metal = METALS.get(i);
			AccessoryDef.Builder ring = AccessoryDef.builder(metal + "_ring", RING, BASIC).stat(DAMAGE, ringDamage[i]);
			if (metal.equals("gold")) {
				ring.stat(LUCK, 1);
			}
			add(ring.build());
			AccessoryDef.Builder necklace = AccessoryDef.builder(metal + "_necklace", NECKLACE, BASIC).stat(DAMAGE_REDUCTION, necklaceReduction[i]);
			if (necklaceHealth[i] > 0) {
				necklace.stat(MAX_HEALTH, necklaceHealth[i]);
			}
			add(necklace.build());
		}

		// --- artefatos (so explorando) ---
		add(AccessoryDef.builder("rabbit_foot_charm", CHARM, ARTIFACT).ability(DOUBLE_JUMP).stat(FALL_REDUCTION, 0.15).build());
		add(AccessoryDef.builder("climbing_glove", GLOVES, ARTIFACT).ability(CLIMB).build());
		add(AccessoryDef.builder("miner_lantern", BELT, ARTIFACT).ability(LANTERN).stat(MINING_SPEED, 0.10).build());
		add(AccessoryDef.builder("life_amulet", NECKLACE, ARTIFACT).stat(MAX_HEALTH, 2).build());
		add(AccessoryDef.builder("feather_charm", CHARM, ARTIFACT).stat(FALL_REDUCTION, 0.40).build());
		add(AccessoryDef.builder("thorn_necklace", NECKLACE, ARTIFACT).ability(THORNS).stat(DAMAGE_REDUCTION, 0.01).build());
		add(AccessoryDef.builder("magnet_ring", RING, ARTIFACT).ability(MAGNET).build());
		add(AccessoryDef.builder("pocket_watch", CHARM, ARTIFACT).ability(WATCH).build());
		add(AccessoryDef.builder("anchor_belt", BELT, ARTIFACT).stat(KNOCKBACK_RESISTANCE, 0.25).stat(SPEED, -0.03).build());
		add(AccessoryDef.builder("vampire_fang", RING, ARTIFACT).stat(LIFESTEAL, 0.03).build());
		add(AccessoryDef.builder("diver_ring", RING, ARTIFACT).stat(OXYGEN, 1).stat(SWIM_SPEED, 0.25).build());
		add(AccessoryDef.builder("prospector_glove", GLOVES, ARTIFACT).stat(MINING_SPEED, 0.15).build());
		add(AccessoryDef.builder("lucky_horseshoe", CHARM, ARTIFACT).stat(LUCK, 1.5).build());
		add(AccessoryDef.builder("traveler_belt", BELT, ARTIFACT).stat(SPEED, 0.05).stat(STEP_HEIGHT, 0.5).build());
		add(AccessoryDef.builder("duelist_glove", GLOVES, ARTIFACT).stat(DAMAGE, 0.04).stat(ATTACK_SPEED, 0.05).build());
		add(AccessoryDef.builder("frost_ring", RING, ARTIFACT).ability(FROST).build());
		add(AccessoryDef.builder("sun_pendant", NECKLACE, ARTIFACT).ability(SUNLIGHT).build());

		// --- reliquias: cada uma ajuda contra o PROXIMO chefe, sem imunidade ---
		// Golem -> Guardiao (pulso sonico)
		add(AccessoryDef.builder("resonant_pendant", NECKLACE, RELIC).boss("cobalt_golem").stat(SONIC_REDUCTION, 0.20)
				.stat(DAMAGE_REDUCTION, 0.02).build());
		// Guardiao -> Colosso (fogo)
		add(AccessoryDef.builder("abyssal_shell", CHARM, RELIC).boss("abyssal_guardian").stat(FIRE_REDUCTION, 0.30).build());
		// Colosso -> Dragao (empurroes e mergulhos)
		add(AccessoryDef.builder("colossus_girdle", BELT, RELIC).boss("igneous_colossus").stat(KNOCKBACK_RESISTANCE, 0.25)
				.stat(MAX_HEALTH, 2).build());
		// Dragao -> Arauto (raios estelares magicos)
		add(AccessoryDef.builder("dragonscale_charm", CHARM, RELIC).boss("ender_dragon").stat(MAGIC_REDUCTION, 0.20)
				.stat(DAMAGE_REDUCTION, 0.02).build());
		// Arauto -> Wither (explosoes e murchar)
		add(AccessoryDef.builder("herald_star", NECKLACE, RELIC).boss("astral_herald").stat(BLAST_REDUCTION, 0.25).stat(SPEED, 0.03).build());
		// Wither: fim de jogo
		add(AccessoryDef.builder("wither_ring", RING, RELIC).boss("wither").ability(WITHER_TOUCH).stat(DAMAGE, 0.03).build());
		// mini-chefes das masmorras (raro)
		add(AccessoryDef.builder("champion_seal", RING, RELIC).boss("dungeon").stat(DAMAGE, 0.04).stat(DAMAGE_REDUCTION, 0.02).build());
	}

	private AccessoryCatalog() {
	}

	private static void add(AccessoryDef def) {
		if (BY_ID.put(def.id(), def) != null) {
			throw new IllegalStateException("acessorio repetido: " + def.id());
		}
	}

	public static List<AccessoryDef> all() {
		return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
	}

	/** Definicao pelo caminho do id do item ({@code "life_amulet"}), ou null. */
	public static AccessoryDef byId(String id) {
		return BY_ID.get(id);
	}

	public static List<AccessoryDef> ofKind(AccessoryKind kind) {
		return BY_ID.values().stream().filter(def -> def.kind() == kind).toList();
	}

	/** Reliquia do chefe ({@code "cobalt_golem"}, {@code "wither"}, {@code "dungeon"}...), ou null. */
	public static AccessoryDef relicOf(String boss) {
		return BY_ID.values().stream().filter(def -> def.bossFor().equals(boss)).findFirst().orElse(null);
	}
}
