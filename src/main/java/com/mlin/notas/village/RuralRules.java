package com.mlin.notas.village;

import java.util.ArrayList;
import java.util.List;

/**
 * Protecao de quem trabalha no anel rural (logica pura; Vilas 2.0 fase 8). Com o anel aplainado: monstro de nascimento
 * natural nao nasce nos campos, na faixa da muralha externa nem na borda (o refugio da vila estendido; gerador, ovo e
 * comando continuam valendo); os guardas do quartel patrulham ate a cerca viva, sempre em passos curtos (o caminho
 * passa pelos portoes); a torre de vigia mostra os monstros dos campos; e de noite ou com ataque de saqueadores os
 * aldeoes que estao fora voltam para dentro da muralha pelo portao mais perto. As construcoes rurais nao tem camas: os
 * trabalhadores dormem nas casas dentro da muralha (rotina vanilla).
 */
public final class RuralRules {
	/** Passo maximo de patrulha de um guarda (o caminho ate o ponto sorteado vai em pedacos). */
	public static final int PATROL_STEP = 24;

	private RuralRules() {
	}

	/** Celula (relativa ao quadro) do anel rural: faixa da muralha externa, campos ou borda. */
	public static boolean ruralCell(int rx, int rz) {
		return RuralRing.outsideOld(rx, rz) && RuralRing.insideRural(rx, rz);
	}

	/** Monstro de nascimento natural nao nasce no anel rural aplainado. */
	public static boolean blocksSpawn(boolean ringPrepared, boolean natural, int rx, int rz) {
		return ringPrepared && natural && ruralCell(rx, rz);
	}

	/** Raio da patrulha noturna dos guardas: o da vila, ou ate perto da cerca viva com o anel rural aplainado. */
	public static int patrolRadius(int levelRadius, boolean ringPrepared) {
		return ringPrepared ? Math.max(levelRadius, RuralRing.HALF - 4) : levelRadius;
	}

	/**
	 * Proximo passo da patrulha: o ponto sorteado (tx, tz) se estiver a ate {@link #PATROL_STEP} de onde o guarda esta,
	 * senao o ponto a {@code PATROL_STEP} na direcao dele.
	 */
	public static int[] patrolStep(int fromX, int fromZ, int tx, int tz) {
		double dx = tx - fromX;
		double dz = tz - fromZ;
		double d = Math.hypot(dx, dz);
		if (d <= PATROL_STEP) {
			return new int[] {tx, tz};
		}
		return new int[] {fromX + (int) Math.round(dx / d * PATROL_STEP), fromZ + (int) Math.round(dz / d * PATROL_STEP)};
	}

	/** Monstro (x, z relativos) que a torre de vigia mostra: dentro do raio da vila ou, com o anel aplainado, nos campos. */
	public static boolean watched(int rx, int rz, int levelRadius, boolean ringPrepared) {
		return (long) rx * rx + (long) rz * rz <= (long) levelRadius * levelRadius || ringPrepared && ruralCell(rx, rz);
	}

	/** Aldeao nos campos volta para dentro da muralha: de noite ou com ataque de saqueadores. */
	public static boolean evacuate(boolean night, boolean raid, int rx, int rz) {
		return (night || raid) && ruralCell(rx, rz);
	}

	/**
	 * Abrigos: uma celula de avenida logo por dentro da muralha externa em cada portao (a ~6 da linha da muralha), na
	 * altura da rua.
	 */
	public static List<int[]> shelters(MasterPlan plan) {
		List<int[]> out = new ArrayList<>();
		if (!plan.rural()) {
			return out;
		}
		StreetPlanner.Octagon inner = StreetPlanner.Octagon.of(RuralRing.WALL - 6, null);
		int e = RuralRing.WALL;
		for (int x = -e; x <= e; x++) {
			for (int z = -e; z <= e; z++) {
				double f = inner.f(x, z);
				if (f > -0.5 && f <= 0.5 && plan.streetKind(x, z) == StreetPlanner.AVENUE && plan.ground(x, z)) {
					boolean near = false;
					for (int[] p : out) {
						if (Math.abs(p[0] - x) + Math.abs(p[1] - z) < 12) {
							near = true;
							break;
						}
					}
					if (!near) {
						out.add(new int[] {x, z, plan.streetHeight(x, z)});
					}
				}
			}
		}
		return out;
	}

	/** Abrigo mais perto de (rx, rz); null se nao ha. */
	public static int[] nearestShelter(List<int[]> shelters, int rx, int rz) {
		int[] best = null;
		long bestD = Long.MAX_VALUE;
		for (int[] s : shelters) {
			long d = (long) (s[0] - rx) * (s[0] - rx) + (long) (s[1] - rz) * (s[1] - rz);
			if (d < bestD) {
				bestD = d;
				best = s;
			}
		}
		return best;
	}
}
