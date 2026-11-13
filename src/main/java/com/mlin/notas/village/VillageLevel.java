package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Niveis da vila (logica pura). Sobe sozinho quando a prosperidade, o numero de obras prontas e as obras exigidas
 * chegam no minimo do nivel; nunca desce. Cada nivel define o teto de nivel de profissao dos aldeoes que moram na
 * vila, o desconto nas trocas, o raio onde cabem obras novas e o que o mercado da vila vende.
 *
 * <p>Inspirado no nivel da prefeitura do MineColonies (limita o nivel das outras construcoes) e nos tamanhos
 * aldeia/vila/cidade do ChoiceTheorem's Overhauled Village.
 */
public enum VillageLevel {
	CAMP(0, 0, new String[] {}, 2, 0, 22, 0),
	HAMLET(80, 3, new String[] {"plaza"}, 3, 0, 28, 0),
	VILLAGE(250, 7, new String[] {"farm", "smithy"}, 4, 1, 34, 0),
	TOWN(550, 12, new String[] {"wall", "market"}, 5, 2, 40, 1),
	CAPITAL(1000, 17, new String[] {"inn", "library"}, 5, 3, 44, 1);

	private final int prosperity;
	private final int projects;
	private final String[] required;
	private final int tradeCap;
	private final int discount;
	private final int radius;
	private final int golems;

	VillageLevel(int prosperity, int projects, String[] required, int tradeCap, int discount, int radius, int golems) {
		this.prosperity = prosperity;
		this.projects = projects;
		this.required = required;
		this.tradeCap = tradeCap;
		this.discount = discount;
		this.radius = radius;
		this.golems = golems;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Prosperidade minima. */
	public int prosperity() {
		return prosperity;
	}

	/** Obras prontas (somando repeticoes) minimas. */
	public int projects() {
		return projects;
	}

	/** Obras que precisam existir (pelo menos uma vez). */
	public List<VillageProject> required() {
		List<VillageProject> out = new ArrayList<>();
		for (String id : required) {
			out.add(VillageProject.byId(id));
		}
		return out;
	}

	/** Nivel maximo de profissao (1 novato .. 5 mestre) dos aldeoes da vila. */
	public int tradeCap() {
		return tradeCap;
	}

	/** Esmeraldas a menos em cada troca com aldeoes da vila. */
	public int discount() {
		return discount;
	}

	/** Raio (blocos a partir do quadro) onde cabem obras novas. */
	public int radius() {
		return radius;
	}

	/** Golens de guarda que chegam quando a vila alcanca este nivel. */
	public int golems() {
		return golems;
	}

	public VillageLevel next() {
		return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
	}

	public static VillageLevel byOrdinal(int ordinal) {
		return values()[Math.max(0, Math.min(values().length - 1, ordinal))];
	}

	/** O que falta para chegar neste nivel: {@code prosperity}, {@code projects} ou {@code project:<id>}. Vazio = pode. */
	public List<String> missing(int prosperityNow, Map<VillageProject, Integer> built) {
		List<String> out = new ArrayList<>();
		if (prosperityNow < prosperity) {
			out.add("prosperity");
		}
		int total = built.values().stream().mapToInt(Integer::intValue).sum();
		if (total < projects) {
			out.add("projects");
		}
		for (VillageProject project : required()) {
			if (built.getOrDefault(project, 0) < 1) {
				out.add("project:" + project.id());
			}
		}
		return out;
	}

	/** Maior nivel que a vila alcanca com esses numeros, sem descer de {@code current}. */
	public static VillageLevel reached(VillageLevel current, int prosperityNow, Map<VillageProject, Integer> built) {
		VillageLevel level = current;
		while (level.next() != null && level.next().missing(prosperityNow, built).isEmpty()) {
			level = level.next();
		}
		return level;
	}
}
