package com.mlin.notas.food;

import java.util.ArrayList;
import java.util.List;

/**
 * Numeros e regras da comida, todos aqui (logica pura) para o balanceamento ficar num lugar so:
 * <ul>
 *   <li><b>Variedade</b>: o jogador lembra as ultimas {@link #VARIETY_WINDOW} comidas. Repetir a mesma 3+ vezes nessa
 *   janela rende 75%; 5+ vezes, 50%. Comer outra coisa ja "limpa" a janela aos poucos.</li>
 *   <li><b>Frescor</b> (so produtos da horta, saladas e queijo fresco, e so com as estacoes ligadas): conta quantas
 *   estacoes passaram desde a colheita. Mesma estacao ou a seguinte = fresco; 2 estacoes = murchando (75%);
 *   3+ = passado (50%). Dentro do Barril de Conserva o tempo nao conta. Conservas, graos e pratos cozidos nao
 *   estragam.</li>
 *   <li><b>Da estacao</b>: produto fresco (colhido nesta estacao) de uma cultura que esta no auge agora
 *   (multiplicador de crescimento &gt;= 1,15) da +1 de fome e +1 de saturacao.</li>
 *   <li><b>Bem Alimentado</b>: refeicoes fartas (nivel I) e banquetes (nivel II) dao vida maxima extra e uma
 *   regeneracao lenta enquanto durarem.</li>
 * </ul>
 */
public final class FoodRules {
	/** Quantas refeicoes o jogador "lembra". */
	public static final int VARIETY_WINDOW = 8;
	/** A partir de quantas repeticoes na janela a comida rende menos. */
	public static final int REPEAT_MILD = 3;
	public static final int REPEAT_HARSH = 5;
	public static final float VARIETY_MILD = 0.75F;
	public static final float VARIETY_HARSH = 0.5F;

	public static final float AGING_MULTIPLIER = 0.75F;
	public static final float STALE_MULTIPLIER = 0.5F;

	/** Multiplicador de crescimento a partir do qual a cultura esta "no auge" (mesmo corte do HUD: prosperando). */
	public static final float IN_SEASON_THRESHOLD = 1.15F;
	public static final int IN_SEASON_NUTRITION = 1;
	public static final float IN_SEASON_SATURATION = 1.0F;

	/** Bem Alimentado I (refeicao farta) e II (banquete): duracao em ticks. */
	public static final int WELL_FED_MEAL_TICKS = 3 * 60 * 20;
	public static final int WELL_FED_FEAST_TICKS = 5 * 60 * 20;
	/** Vida maxima extra por nivel do efeito (2 = um coracao). */
	public static final double WELL_FED_MAX_HEALTH_PER_LEVEL = 2.0;
	/** Cura 1 ponto de vida a cada N ticks (nivel I, nivel II). */
	public static final int WELL_FED_REGEN_TICKS_I = 200;
	public static final int WELL_FED_REGEN_TICKS_II = 140;

	/** Tempo de cozimento padrao da panela e tempo de cura no barril (dias de jogo). */
	public static final int POT_COOK_TICKS = 200;
	public static final int TICKS_PER_DAY = 24000;

	/** Estado de frescor de um produto. */
	public enum Freshness {
		FRESH(1.0F),
		AGING(AGING_MULTIPLIER),
		STALE(STALE_MULTIPLIER);

		private final float multiplier;

		Freshness(float multiplier) {
			this.multiplier = multiplier;
		}

		public float multiplier() {
			return multiplier;
		}

		public String id() {
			return name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Fome e saturacao finais de uma mordida. */
	public record Portion(int nutrition, float saturation) {
	}

	private FoodRules() {
	}

	// --- variedade ---

	/** Quantas vezes {@code id} aparece no historico. */
	public static int timesEaten(List<String> history, String id) {
		int n = 0;
		for (String s : history) {
			if (s.equals(id)) {
				n++;
			}
		}
		return n;
	}

	/** Rendimento pela variedade, dado quantas vezes a comida ja aparece na janela (antes de comer). */
	public static float varietyMultiplier(int timesInWindow) {
		if (timesInWindow >= REPEAT_HARSH) {
			return VARIETY_HARSH;
		}
		if (timesInWindow >= REPEAT_MILD) {
			return VARIETY_MILD;
		}
		return 1.0F;
	}

	/** Novo historico depois de comer {@code id}: entra no fim, o mais antigo sai quando passa da janela. */
	public static List<String> remember(List<String> history, String id) {
		List<String> out = new ArrayList<>(history);
		out.add(id);
		while (out.size() > VARIETY_WINDOW) {
			out.removeFirst();
		}
		return out;
	}

	// --- frescor ---

	/** Frescor pela idade em estacoes (estacao de agora - estacao da colheita). */
	public static Freshness freshness(long harvestedSeason, long nowSeason) {
		long age = nowSeason - harvestedSeason;
		if (age <= 1) {
			return Freshness.FRESH;
		}
		return age == 2 ? Freshness.AGING : Freshness.STALE;
	}

	// --- estacao ---

	/** A cultura esta no auge com este multiplicador de crescimento. */
	public static boolean inSeason(float growthMultiplier) {
		return growthMultiplier >= IN_SEASON_THRESHOLD;
	}

	/**
	 * Bonus "da estacao": produto colhido nesta mesma estacao ({@code harvestedSeason == nowSeason}) de uma cultura
	 * no auge.
	 */
	public static boolean seasonalBonus(float growthMultiplier, long harvestedSeason, long nowSeason) {
		return inSeason(growthMultiplier) && harvestedSeason == nowSeason;
	}

	// --- porcao final ---

	/**
	 * Porcao depois de todos os ajustes: {@code multiplier} = variedade x frescor; o bonus da estacao soma depois.
	 * Comida que da fome nunca cai abaixo de 1.
	 */
	public static Portion adjust(int nutrition, float saturation, float multiplier, boolean seasonalBonus) {
		int n = nutrition <= 0 ? 0 : Math.max(1, Math.round(nutrition * multiplier));
		float s = saturation * multiplier;
		if (seasonalBonus) {
			n += IN_SEASON_NUTRITION;
			s += IN_SEASON_SATURATION;
		}
		return new Portion(n, s);
	}

	// --- Bem Alimentado ---

	/** Duracao do efeito para o tipo de comida (0 = nenhum). */
	public static int wellFedTicks(FoodCatalog.Kind kind) {
		return switch (kind) {
			case MEAL -> WELL_FED_MEAL_TICKS;
			case FEAST -> WELL_FED_FEAST_TICKS;
			default -> 0;
		};
	}

	/** Amplificador do efeito (0 = nivel I). */
	public static int wellFedAmplifier(FoodCatalog.Kind kind) {
		return kind == FoodCatalog.Kind.FEAST ? 1 : 0;
	}

	/** Intervalo de cura do efeito pelo amplificador. */
	public static int wellFedRegenInterval(int amplifier) {
		return amplifier >= 1 ? WELL_FED_REGEN_TICKS_II : WELL_FED_REGEN_TICKS_I;
	}

	/** Ticks de cura no barril para {@code days} dias de jogo. */
	public static int fermentTicks(float days) {
		return Math.max(1, Math.round(days * TICKS_PER_DAY));
	}
}
