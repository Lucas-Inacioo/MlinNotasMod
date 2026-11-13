package com.mlin.notas.skills;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Progresso compartilhado do mundo (logica pura): nivel de cada ramo e depositos parciais por no (itens e XP ja
 * entregues por qualquer jogador). Quando o deposito cobre o custo inteiro, o nivel e liberado para todos.
 */
public final class SkillProgress {
	public enum Status {
		/** Ja comprado. */
		UNLOCKED,
		/** Pre-requisitos cumpridos, aceita deposito. */
		AVAILABLE,
		/** Falta pre-requisito. */
		LOCKED
	}

	public enum Outcome {
		/** Recusado: no ja liberado. */
		ALREADY_UNLOCKED,
		/** Recusado: falta pre-requisito. */
		LOCKED,
		/** Nada a entregar (o jogador nao tem nada do que falta). */
		NOTHING,
		/** Deposito parcial aceito. */
		PARTIAL,
		/** Deposito completou o custo e liberou o nivel. */
		UNLOCKED
	}

	/** Resultado de um deposito: o que tirar do jogador. */
	public record Deposit(Outcome outcome, Map<String, Integer> taken, int xpTaken) {
		public boolean accepted() {
			return outcome == Outcome.PARTIAL || outcome == Outcome.UNLOCKED;
		}
	}

	private final EnumMap<SkillBranch, Integer> levels = new EnumMap<>(SkillBranch.class);
	private final Map<String, Map<String, Integer>> itemDeposits = new HashMap<>();
	private final Map<String, Integer> xpDeposits = new HashMap<>();

	public SkillProgress() {
		for (SkillBranch branch : SkillBranch.values()) {
			levels.put(branch, branch.startLevel());
		}
	}

	/** Reconstroi a partir do formato salvo (chaves desconhecidas sao ignoradas). */
	public static SkillProgress of(Map<String, Integer> levels, Map<String, Map<String, Integer>> items, Map<String, Integer> xp) {
		SkillProgress progress = new SkillProgress();
		levels.forEach((id, level) -> {
			SkillBranch branch = SkillBranch.byId(id);
			if (branch != null) {
				progress.levels.put(branch, Math.max(branch.startLevel(), Math.min(branch.maxLevel(), level)));
			}
		});
		items.forEach((key, map) -> {
			if (SkillTree.byKey(key) != null) {
				progress.itemDeposits.put(key, new LinkedHashMap<>(map));
			}
		});
		xp.forEach((key, value) -> {
			if (SkillTree.byKey(key) != null && value > 0) {
				progress.xpDeposits.put(key, value);
			}
		});
		return progress;
	}

	public int level(SkillBranch branch) {
		return levels.get(branch);
	}

	public Map<String, Integer> levelsById() {
		Map<String, Integer> out = new LinkedHashMap<>();
		levels.forEach((branch, level) -> out.put(branch.id(), level));
		return out;
	}

	public Map<String, Map<String, Integer>> itemDeposits() {
		Map<String, Map<String, Integer>> out = new LinkedHashMap<>();
		itemDeposits.forEach((key, map) -> {
			if (!map.isEmpty()) {
				out.put(key, Map.copyOf(map));
			}
		});
		return out;
	}

	public Map<String, Integer> xpDeposits() {
		return Map.copyOf(xpDeposits);
	}

	/** Liga um nivel direto (testes, comandos); niveis abaixo no mesmo ramo ficam implicitos. */
	public void setLevel(SkillBranch branch, int level) {
		levels.put(branch, Math.max(branch.startLevel(), Math.min(branch.maxLevel(), level)));
	}

	/** Volta ao mundo novo (so Madeira, sem depositos). */
	public void clear() {
		for (SkillBranch branch : SkillBranch.values()) {
			levels.put(branch, branch.startLevel());
		}
		itemDeposits.clear();
		xpDeposits.clear();
	}

	public boolean has(SkillBranch branch, int level) {
		return level(branch) >= level;
	}

	public Status status(SkillNode node) {
		if (level(node.branch()) >= node.level()) {
			return Status.UNLOCKED;
		}
		return SkillTree.missingPrerequisites(node, this::level).isEmpty() ? Status.AVAILABLE : Status.LOCKED;
	}

	public int deposited(SkillNode node, String costId) {
		Map<String, Integer> map = itemDeposits.get(node.key());
		return map == null ? 0 : map.getOrDefault(costId, 0);
	}

	public int depositedXp(SkillNode node) {
		return xpDeposits.getOrDefault(node.key(), 0);
	}

	public int remaining(SkillNode node, String costId) {
		return Math.max(0, node.itemCount(costId) - deposited(node, costId));
	}

	public int remainingXp(SkillNode node) {
		return Math.max(0, node.xpLevels() - depositedXp(node));
	}

	public boolean complete(SkillNode node) {
		for (SkillNode.Cost cost : node.items()) {
			if (remaining(node, cost.id()) > 0) {
				return false;
			}
		}
		return remainingXp(node) == 0;
	}

	/**
	 * Entrega para {@code node} tudo o que o jogador tem do que falta. {@code available} = quanto o jogador tem de cada
	 * id de custo; {@code xpLevels} = niveis de XP dele. O progresso so muda se o deposito for aceito.
	 */
	public Deposit deposit(SkillNode node, Map<String, Integer> available, int xpLevels) {
		Status status = status(node);
		if (status == Status.UNLOCKED) {
			return new Deposit(Outcome.ALREADY_UNLOCKED, Map.of(), 0);
		}
		if (status == Status.LOCKED) {
			return new Deposit(Outcome.LOCKED, Map.of(), 0);
		}
		Map<String, Integer> taken = new LinkedHashMap<>();
		for (SkillNode.Cost cost : node.items()) {
			int give = Math.min(remaining(node, cost.id()), Math.max(0, available.getOrDefault(cost.id(), 0)));
			if (give > 0) {
				taken.put(cost.id(), give);
			}
		}
		int xp = Math.min(remainingXp(node), Math.max(0, xpLevels));
		if (taken.isEmpty() && xp == 0) {
			return new Deposit(Outcome.NOTHING, Map.of(), 0);
		}
		Map<String, Integer> deposits = itemDeposits.computeIfAbsent(node.key(), k -> new LinkedHashMap<>());
		taken.forEach((id, count) -> deposits.merge(id, count, Integer::sum));
		if (xp > 0) {
			xpDeposits.merge(node.key(), xp, Integer::sum);
		}
		if (complete(node)) {
			levels.put(node.branch(), node.level());
			itemDeposits.remove(node.key());
			xpDeposits.remove(node.key());
			return new Deposit(Outcome.UNLOCKED, taken, xp);
		}
		return new Deposit(Outcome.PARTIAL, taken, xp);
	}
}
