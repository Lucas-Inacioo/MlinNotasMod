package com.mlin.notas.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Escolhe o proximo ataque especial de um chefe (logica pura): cada ataque tem recarga propria, fase minima e faixa
 * de distancia ao alvo; entre dois ataques ha uma pausa global (menor na fase 2). Evita repetir o mesmo ataque
 * quando existe outro disponivel e sorteia por peso.
 */
public final class AttackScheduler {
	/** Um ataque: recarga em ticks, fase minima (1 ou 2), distancia ao alvo aceita e peso no sorteio. */
	public record Attack(String id, int cooldown, int minPhase, double minRange, double maxRange, int weight) {
		public boolean fits(int phase, double distance) {
			return phase >= minPhase && distance >= minRange && distance <= maxRange;
		}
	}

	private final List<Attack> attacks;
	private final int[] cooldowns;
	private final int gap;
	private int delay;
	private String last;

	/** @param gap pausa global (ticks) depois de cada ataque na fase 1; na fase 2 cai para 2/3. */
	public AttackScheduler(int gap, List<Attack> attacks) {
		this.gap = gap;
		this.attacks = List.copyOf(attacks);
		this.cooldowns = new int[attacks.size()];
		this.delay = gap;
	}

	public List<Attack> attacks() {
		return attacks;
	}

	/** Um tick: diminui recargas e a pausa global. */
	public void tick() {
		if (delay > 0) {
			delay--;
		}
		for (int i = 0; i < cooldowns.length; i++) {
			if (cooldowns[i] > 0) {
				cooldowns[i]--;
			}
		}
	}

	public int delay() {
		return delay;
	}

	public int cooldown(String id) {
		for (int i = 0; i < attacks.size(); i++) {
			if (attacks.get(i).id().equals(id)) {
				return cooldowns[i];
			}
		}
		throw new IllegalArgumentException(id);
	}

	/** Ataques que poderiam ser usados agora (ignorando a pausa global). */
	public List<Attack> available(int phase, double distance) {
		List<Attack> out = new ArrayList<>();
		for (int i = 0; i < attacks.size(); i++) {
			if (cooldowns[i] == 0 && attacks.get(i).fits(phase, distance)) {
				out.add(attacks.get(i));
			}
		}
		return out;
	}

	/** Proximo ataque, ou vazio se ainda em pausa / nada disponivel. Escolher ja aplica recarga e pausa. */
	public Optional<Attack> next(int phase, double distance, RandomGenerator random) {
		if (delay > 0) {
			return Optional.empty();
		}
		List<Attack> options = available(phase, distance);
		if (options.size() > 1 && last != null) {
			options.removeIf(a -> a.id().equals(last));
		}
		if (options.isEmpty()) {
			return Optional.empty();
		}
		int total = 0;
		for (Attack a : options) {
			total += Math.max(1, a.weight());
		}
		int roll = random.nextInt(total);
		Attack chosen = options.getLast();
		for (Attack a : options) {
			roll -= Math.max(1, a.weight());
			if (roll < 0) {
				chosen = a;
				break;
			}
		}
		cooldowns[attacks.indexOf(chosen)] = chosen.cooldown();
		delay = phase >= 2 ? gap * 2 / 3 : gap;
		last = chosen.id();
		return Optional.of(chosen);
	}
}
