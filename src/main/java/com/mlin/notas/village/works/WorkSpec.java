package com.mlin.notas.village.works;

import com.mlin.notas.village.VillageLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Uma obra publica gratuita (logica pura): id estavel salvo ({@code streets:3}), categoria e o nivel da vila em que
 * entra na fila. A fila e do mundo da vila; quem executa e o servidor, so quando nao ha obra paga rodando.
 */
public record WorkSpec(String id, WorkCategory category, VillageLevel level) {
	/** Chave de traducao do nome ({@code village.mlinnotas.work.streets.3}). */
	public String langKey() {
		return "village.mlinnotas.work." + id.replace(':', '.');
	}

	/** Parte depois dos dois pontos (estagio, anel ou nome). */
	public String arg() {
		return id.substring(id.indexOf(':') + 1);
	}

	public String kind() {
		return id.substring(0, id.indexOf(':'));
	}

	public int argInt() {
		try {
			return Integer.parseInt(arg());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** Obras que entram na fila exatamente ao chegar em {@code level}. */
	public static List<WorkSpec> introducedAt(VillageLevel level) {
		List<WorkSpec> out = new ArrayList<>();
		VillageLevel before = level.ordinal() == 0 ? null : VillageLevel.values()[level.ordinal() - 1];
		int fromRing = before == null ? 1 : before.rings() + 1;
		for (int ring = fromRing; ring <= level.rings(); ring++) {
			out.add(new WorkSpec("bridges:" + ring, WorkCategory.BRIDGES, level));
			out.add(new WorkSpec("stakes:" + ring, WorkCategory.SUBDIVISION, level));
		}
		switch (level) {
			case CAMP -> {
			}
			case HAMLET -> {
				out.add(new WorkSpec("streets:1", WorkCategory.STREETS, level));
				out.add(new WorkSpec("wall:palisade", WorkCategory.WALL, level));
			}
			case VILLAGE -> {
				out.add(new WorkSpec("streets:2", WorkCategory.STREETS, level));
				out.add(new WorkSpec("plaza:1", WorkCategory.PLAZA, level));
				out.add(new WorkSpec("trees:1", WorkCategory.GREEN, level));
				out.add(new WorkSpec("wall:gates", WorkCategory.WALL, level));
				out.add(new WorkSpec("roads:1", WorkCategory.ROADS, level));
			}
			case TOWN -> {
				out.add(new WorkSpec("streets:3", WorkCategory.STREETS, level));
				out.add(new WorkSpec("plaza:2", WorkCategory.PLAZA, level));
				out.add(new WorkSpec("gardens:1", WorkCategory.GREEN, level));
				out.add(new WorkSpec("decor:1", WorkCategory.DECOR, level));
				// fase 8: anel rural (estacas, pontes, cerca viva e postes so quando o anel 7 estiver aplainado)
				out.add(new WorkSpec("stakes:7", WorkCategory.SUBDIVISION, level));
				out.add(new WorkSpec("bridges:7", WorkCategory.BRIDGES, level));
				out.add(new WorkSpec("fields:hedge", WorkCategory.RURAL, level));
				out.add(new WorkSpec("fields:lamps", WorkCategory.RURAL, level));
			}
			case CAPITAL -> {
				out.add(new WorkSpec("streets:4", WorkCategory.STREETS, level));
				out.add(new WorkSpec("plaza:3", WorkCategory.PLAZA, level));
				out.add(new WorkSpec("rural:1", WorkCategory.RURAL, level));
				out.add(new WorkSpec("wall:battlements", WorkCategory.WALL, level));
			}
			case METROPOLIS -> {
				out.add(new WorkSpec("streets:5", WorkCategory.STREETS, level));
				out.add(new WorkSpec("plaza:4", WorkCategory.PLAZA, level));
				// fase 8: a muralha de pedra vira centro historico (arcos abertos nos portoes, passeio em cima)
				out.add(new WorkSpec("wall:historic", WorkCategory.WALL, level));
			}
			case CROWN_CITY -> {
				out.add(new WorkSpec("plaza:5", WorkCategory.PLAZA, level));
				// fase 8: barbacas na frente dos portoes dos eixos da muralha externa (obra paga OUTER_WALL)
				out.add(new WorkSpec("wall:barbican", WorkCategory.WALL, level));
			}
		}
		return out;
	}

	/** Todas as obras dos niveis ate {@code level}, na ordem em que entram. */
	public static List<WorkSpec> upTo(VillageLevel level) {
		List<WorkSpec> out = new ArrayList<>();
		for (VillageLevel l : VillageLevel.values()) {
			if (l.ordinal() > level.ordinal()) {
				break;
			}
			out.addAll(introducedAt(l));
		}
		return out;
	}

	private static java.util.Map<String, WorkSpec> index;

	public static synchronized WorkSpec byId(String id) {
		if (index == null) {
			index = new java.util.HashMap<>();
			for (WorkSpec spec : upTo(VillageLevel.CROWN_CITY)) {
				index.put(spec.id, spec);
			}
		}
		return index.get(id);
	}

	private static WorkSpec byIdSlow(String id) {
		for (WorkSpec spec : upTo(VillageLevel.CROWN_CITY)) {
			if (spec.id.equals(id)) {
				return spec;
			}
		}
		return null;
	}
}
