package com.mlin.notas.gating;

import com.mlin.notas.combat.BossWeapon;
import com.mlin.notas.combat.WeaponClass;
import com.mlin.notas.tier.GearKind;
import com.mlin.notas.tier.Tier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Trava de ferramentas e armas pelo nivel de Mineracao do mundo (logica pura). Uma ferramenta/arma de nivel T so
 * funciona (quebrar, atacar, usar com botao direito) se a Mineracao do mundo ja chegou em T; madeira sempre vale.
 * Armadura nao entra: achar um peitoral de diamante num bau continua valendo.
 *
 * <p>O nivel de cada item vem das tags de item {@code mlinnotas:tool_tier/<nivel>} (geradas pelo
 * {@code scripts/GatingContent.java} a partir de {@link #itemIds}), entao outro mod ou datapack pode por os itens
 * dele numa dessas tags.</p>
 */
public final class ToolGate {
	/** Caminho das tags de item por nivel: {@code mlinnotas:tool_tier/<id do nivel>}. */
	public static final String TAG_PREFIX = "tool_tier/";
	/** Intervalo minimo entre dois avisos "Requer Mineracao" para o mesmo jogador. */
	public static final long MESSAGE_COOLDOWN_TICKS = 30;

	private ToolGate() {
	}

	/** Nivel de Mineracao (1 = Madeira .. 12 = Celestio) que libera usar ferramentas do nivel {@code tier}. */
	public static int requiredLevel(Tier tier) {
		return tier.level();
	}

	/** Pode usar uma ferramenta de nivel {@code tier} (null = item sem nivel) com a Mineracao do mundo em {@code miningLevel}? */
	public static boolean allowed(Tier tier, int miningLevel) {
		return tier == null || tier == Tier.WOOD || miningLevel >= requiredLevel(tier);
	}

	/** Nivel que falta (null = liberado). Criativo e espectador nunca ficam travados. */
	public static Tier missing(Tier tier, int miningLevel, boolean bypass) {
		return bypass || allowed(tier, miningLevel) ? null : tier;
	}

	/**
	 * Nivel do item a partir da pertinencia nas tags (o mais alto vence, caso um item esteja em duas). {@code inTag}
	 * responde se o item esta na tag do nivel.
	 */
	public static Tier tierOf(Predicate<Tier> inTag) {
		Tier[] tiers = Tier.values();
		for (int i = tiers.length - 1; i >= 0; i--) {
			if (inTag.test(tiers[i])) {
				return tiers[i];
			}
		}
		return null;
	}

	/** Pode mostrar outro aviso agora? ({@code last} = tick do ultimo aviso, ou negativo se nunca). */
	public static boolean shouldNotify(long last, long now) {
		return last < 0 || now - last >= MESSAGE_COOLDOWN_TICKS || now < last;
	}

	/** Id da tag do nivel (sem namespace): {@code tool_tier/diamond}. */
	public static String tagPath(Tier tier) {
		return TAG_PREFIX + tier.id();
	}

	/**
	 * Itens do proprio jogo e do mod em cada nivel: ferramentas (espada, pa, picareta, machado, enxada, lanca), as 5
	 * classes de arma do combate, armas de chefe (no nivel em que os numeros delas se baseiam) e varinhas.
	 */
	public static List<String> itemIds(Tier tier) {
		List<String> ids = new ArrayList<>();
		String namespace = tier.vanilla() ? "minecraft:" : "mlinnotas:";
		for (GearKind kind : GearKind.values()) {
			ids.add(namespace + kind.itemName(tier));
		}
		for (WeaponClass weaponClass : WeaponClass.ALL) {
			ids.add("mlinnotas:" + weaponClass.itemName(tier));
		}
		for (BossWeapon weapon : BossWeapon.values()) {
			if (weapon.powerTier() == tier) {
				ids.add("mlinnotas:" + weapon.id());
			}
		}
		ids.add("mlinnotas:" + (tier == Tier.WOOD ? "wooden_wand" : tier.id() + "_wand"));
		return ids;
	}
}
