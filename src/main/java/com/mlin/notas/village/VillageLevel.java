package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Niveis da vila (logica pura). Sobe sozinho quando a prosperidade, os pontos de obra, as obras exigidas e a
 * terraplenagem dos aneis do nivel chegam no minimo; nunca desce. Cada nivel define o teto de nivel de profissao dos
 * aldeoes que moram na vila, o desconto nas trocas, o alcance da vila, quantos aneis do plano diretor ficam abertos e
 * o que o mercado da vila vende.
 *
 * <p>Ids salvos pelo ordinal: niveis novos so entram no fim ({@code metropolis}, {@code crown_city}). As obras exigidas
 * sao ids de projeto; id que ainda nao existe (prefeitura, celeiro, torre de vigia... das fases seguintes) e ignorado
 * ate ser registrado em {@link VillageProject}. Prefeitura com nivel: {@code town_hall:2}.
 *
 * <p>Inspirado no nivel da prefeitura do MineColonies (limita o nivel das outras construcoes) e nos tamanhos
 * aldeia/vila/cidade do ChoiceTheorem's Overhauled Village.
 */
public enum VillageLevel {
	CAMP(0, 0, new String[] {}, 2, 0, 22, 0, 2),
	HAMLET(80, 3, new String[] {"plaza"}, 3, 0, 30, 0, 3),
	VILLAGE(250, 8, new String[] {"farm", "smithy", "town_hall:1"}, 4, 1, 38, 0, 4),
	TOWN(600, 16, new String[] {"wall", "market", "granary"}, 5, 2, 46, 1, 5),
	CAPITAL(1100, 26, new String[] {"inn", "library", "town_hall:2", "watchtower"}, 5, 3, 52, 1, 6),
	METROPOLIS(1800, 38, new String[] {"barracks", "school", "post_office", "town_hall:3"}, 5, 3, 64, 2, 6),
	CROWN_CITY(2800, 52, new String[] {"outer_wall", "museum", "beacon:2", "town_hall:4"}, 5, 4, 76, 3, 6);

	/** Teto de nivel de profissao de aldeoes fora de qualquer vila fundada (jornaleiro): mestre so em vila do mod. */
	public static final int WILD_TRADE_CAP = 3;

	private final int prosperity;
	private final int projects;
	private final String[] required;
	private final int tradeCap;
	private final int discount;
	private final int radius;
	private final int golems;
	private final int rings;

	VillageLevel(int prosperity, int projects, String[] required, int tradeCap, int discount, int radius, int golems, int rings) {
		this.prosperity = prosperity;
		this.projects = projects;
		this.required = required;
		this.tradeCap = tradeCap;
		this.discount = discount;
		this.radius = radius;
		this.golems = golems;
		this.rings = rings;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Prosperidade minima. */
	public int prosperity() {
		return prosperity;
	}

	/** Pontos de obra da tabela (obras prontas + niveis de construcao). */
	public int projects() {
		return projects;
	}

	/**
	 * Pontos de obra exigidos de verdade: a tabela, mas nunca mais do que as obras que existem hoje deixam fazer no
	 * nivel anterior (assim a vila continua subindo enquanto as construcoes novas nao chegam).
	 */
	public int projectsNeeded() {
		VillageLevel before = ordinal() == 0 ? this : values()[ordinal() - 1];
		int possible = 0;
		int hall = BuildingTypes.townHallCap(before);
		for (VillageProject p : VillageProject.values()) {
			// cada obra pronta vale 1 ponto e cada nivel de melhoria mais 1
			possible += p.maxBuilds(before) * Math.max(1, BuildingTypes.of(p.id()) == null ? 1 : BuildingTypes.cap(p.id(), before, hall));
		}
		return Math.min(projects, possible);
	}

	/** Ids exigidos como estao na tabela (inclusive os que ainda nao existem). */
	public List<String> requiredIds() {
		return List.of(required);
	}

	/** Obras que precisam existir (pelo menos uma vez); ids desconhecidos ficam de fora. */
	public List<VillageProject> required() {
		List<VillageProject> out = new ArrayList<>();
		for (String id : required) {
			VillageProject project = VillageProject.byId(id.contains(":") ? id.substring(0, id.indexOf(':')) : id);
			if (project != null && !out.contains(project)) {
				out.add(project);
			}
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

	/** Alcance (blocos a partir do quadro) da vila: moradores, contorno. */
	public int radius() {
		return radius;
	}

	/** Golens de guarda que chegam quando a vila alcanca este nivel. */
	public int golems() {
		return golems;
	}

	/** Aneis do plano diretor abertos neste nivel (1..6); cada um precisa da terraplenagem pronta para subir. */
	public int rings() {
		return rings;
	}

	public VillageLevel next() {
		return ordinal() + 1 < values().length ? values()[ordinal() + 1] : null;
	}

	public static VillageLevel byOrdinal(int ordinal) {
		return values()[Math.max(0, Math.min(values().length - 1, ordinal))];
	}

	/**
	 * Situacao da vila para conferir o nivel: prosperidade, obras prontas, aneis com terraplenagem pronta (bit
	 * {@code 1 << (anel - 1)}; {@code -1} = nao confere aneis, vila sem plano) e pontos extras (casas de jogador).
	 */
	public record Context(int prosperity, Map<VillageProject, Integer> built, int ringsDone, int extraPoints, Map<String, Integer> levels,
			int minNeed) {
		/**
		 * Sem niveis de construcao (toda obra pronta conta como nivel I) nem necessidades. {@code levels}: melhor nivel de
		 * cada tipo ({@link VillageBuildings#bestLevels}); {@code minNeed}: menor necessidade (0..100, -1 = nao confere).
		 */
		public Context(int prosperity, Map<VillageProject, Integer> built, int ringsDone, int extraPoints) {
			this(prosperity, built, ringsDone, extraPoints, Map.of(), -1);
		}

		public static Context legacy(int prosperity, Map<VillageProject, Integer> built) {
			return new Context(prosperity, built, -1, 0);
		}

		/** Nivel do tipo para as exigencias: o registrado, ou I se ha obra pronta, ou 0. */
		public int level(VillageProject project) {
			int n = built.getOrDefault(project, 0);
			return n < 1 ? 0 : Math.max(1, levels.getOrDefault(project.id(), 1));
		}
	}

	/** Nivel exigido de uma exigencia ({@code town_hall:2} = 2; sem sufixo = 1). */
	public static int requiredLevel(String requirement) {
		int i = requirement.indexOf(':');
		if (i < 0) {
			return 1;
		}
		try {
			return Math.max(1, Integer.parseInt(requirement.substring(i + 1)));
		} catch (NumberFormatException e) {
			return 1;
		}
	}

	/**
	 * O que falta para chegar neste nivel: {@code prosperity}, {@code projects}, {@code project:<id>} ou
	 * {@code ring:<n>} (terraplenagem). Vazio = pode.
	 */
	public List<String> missing(Context ctx) {
		List<String> out = new ArrayList<>();
		if (ctx.prosperity() < prosperity) {
			out.add("prosperity");
		}
		int total = ctx.built().values().stream().mapToInt(Integer::intValue).sum() + ctx.extraPoints();
		if (total < projectsNeeded()) {
			out.add("projects");
		}
		for (String id : required) {
			VillageProject project = VillageProject.byId(id.contains(":") ? id.substring(0, id.indexOf(':')) : id);
			if (project == null) {
				continue;
			}
			int need = requiredLevel(id);
			if (ctx.built().getOrDefault(project, 0) < 1) {
				if (!out.contains("project:" + project.id())) {
					out.add("project:" + project.id());
				}
			} else if (ctx.level(project) < need) {
				out.add("level:" + project.id() + ":" + need);
			}
		}
		if (ordinal() >= TOWN.ordinal() && ctx.minNeed() >= 0 && ctx.minNeed() <= VillageNeeds.LEVEL_UP) {
			out.add("needs");
		}
		if (ctx.ringsDone() >= 0) {
			for (int ring = 1; ring <= rings; ring++) {
				if ((ctx.ringsDone() & (1 << (ring - 1))) == 0) {
					out.add("ring:" + ring);
				}
			}
		}
		return out;
	}

	/** Como {@link #missing(Context)} sem conferir aneis (vila sem plano diretor). */
	public List<String> missing(int prosperityNow, Map<VillageProject, Integer> built) {
		return missing(Context.legacy(prosperityNow, built));
	}

	/** Maior nivel que a vila alcanca, sem descer de {@code current}. */
	public static VillageLevel reached(VillageLevel current, Context ctx) {
		VillageLevel level = current;
		while (level.next() != null && level.next().missing(ctx).isEmpty()) {
			level = level.next();
		}
		return level;
	}

	public static VillageLevel reached(VillageLevel current, int prosperityNow, Map<VillageProject, Integer> built) {
		return reached(current, Context.legacy(prosperityNow, built));
	}
}
