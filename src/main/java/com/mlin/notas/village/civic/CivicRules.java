package com.mlin.notas.village.civic;

import java.util.List;

/**
 * Regras puras das funcoes civicas e de defesa (Vilas 2.0, fase 7): Escola, Torre de vigia, Quartel, Farol comunitario,
 * Pedra de teleporte da vila, Refugio e Seguro de morte. Cada bloco aqui e so numero e decisao; o lado do jogo fica em
 * {@link CivicServer}.
 */
public final class CivicRules {
	private CivicRules() {
	}

	// ---------------------------------------------------------------- escola

	/** Tiques de idade a mais por segundo (20 tiques) para filhotes: nivel I cresce 2x (+1 por tique), nivel II 3x. */
	public static int babyExtraTicksPerSecond(int schoolLevel) {
		return schoolLevel <= 0 ? 0 : 20 * Math.min(2, schoolLevel);
	}

	/** Intervalo (tiques) entre pontos de experiencia de oficio dados aos novatos; 0 = a escola ainda nao faz isso (so no II). */
	public static int noviceXpInterval(int schoolLevel) {
		return schoolLevel >= 2 ? 1200 : 0;
	}

	/** Novatos sobem de nivel com 10 de experiencia de troca; o ponto da escola nunca passa disso (so aproxima). */
	public static int noviceXpCap() {
		return 9;
	}

	// ---------------------------------------------------------------- torre de vigia

	/** Postos de arqueiro ativos: so a torre de nivel III os tem. */
	public static int archers(int towerLevel) {
		return towerLevel >= 3 ? 4 : 0;
	}

	/** Alcance dos arqueiros em blocos. */
	public static double archerRange(int towerLevel) {
		return towerLevel >= 3 ? 28.0 : 0.0;
	}

	/** Tiques entre flechas do mesmo arqueiro. */
	public static int archerCooldown() {
		return 40;
	}

	/** Dano da flecha dos arqueiros (de um arco de aldeia: 2 de dano base da flecha x velocidade). */
	public static double arrowDamage() {
		return 3.0;
	}

	/** Noite para o minimapa/guardas: do fim da tarde ao nascer do sol (tempo do dia 0..23999). */
	public static boolean isNight(long dayTime) {
		long t = Math.floorMod(dayTime, 24_000L);
		return t >= 13_000L && t < 23_000L;
	}

	/** O sino toca uma vez por ataque: so quando o ataque e novo. */
	public static boolean shouldRing(boolean raidNear, boolean alreadyRang) {
		return raidNear && !alreadyRang;
	}

	/** Torre de vigia funcionando no raio da vila marca monstros no minimapa (so de noite). */
	public static boolean towerMarksMonsters(int towerLevel, long dayTime) {
		return towerLevel >= 1 && isNight(dayTime);
	}

	// ---------------------------------------------------------------- quartel

	/** Guardas de armadura do quartel: 2, 4, 6 por nivel. */
	public static int guards(int barracksLevel) {
		return barracksLevel <= 0 ? 0 : 2 * Math.min(3, barracksLevel);
	}

	/** Armadura do guarda por nivel do quartel: ferro, diamante nos de cima. 0 = capacete..3 = botas. */
	public static String guardArmor(int barracksLevel, int slot) {
		String material = barracksLevel >= 3 ? "diamond" : barracksLevel >= 2 ? "iron" : "chainmail";
		return "minecraft:" + material + "_" + new String[] {"helmet", "chestplate", "leggings", "boots"}[Math.floorMod(slot, 4)];
	}

	public static String guardWeapon(int barracksLevel) {
		return barracksLevel >= 3 ? "minecraft:diamond_sword" : barracksLevel >= 2 ? "minecraft:iron_sword" : "minecraft:stone_sword";
	}

	/** Os guardas patrulham a noite; de dia ficam perto dos postos do patio. */
	public static boolean patrolNow(long dayTime) {
		return isNight(dayTime);
	}

	/** Ponto aleatorio da patrulha (x, z relativos ao centro) num anel entre {@code inner} e {@code outer} blocos. */
	public static int[] patrolPoint(java.util.function.DoubleSupplier random, int inner, int outer) {
		double angle = random.getAsDouble() * Math.PI * 2;
		double r = inner + random.getAsDouble() * Math.max(1, outer - inner);
		return new int[] {(int) Math.round(Math.cos(angle) * r), (int) Math.round(Math.sin(angle) * r)};
	}

	// ---------------------------------------------------------------- farol comunitario

	public record Effect(String id, int amplifier) {
	}

	/** Efeitos para todos dentro da muralha, acumulando por nivel do Farol. */
	public static List<Effect> beaconEffects(int level) {
		List<Effect> out = new java.util.ArrayList<>();
		if (level >= 1) {
			out.add(new Effect("minecraft:speed", 0));
		}
		if (level >= 2) {
			out.add(new Effect("minecraft:haste", 0));
		}
		if (level >= 3) {
			out.add(new Effect("minecraft:resistance", 0));
		}
		if (level >= 4) {
			out.add(new Effect("minecraft:strength", 0));
		}
		if (level >= 5) {
			out.add(new Effect("minecraft:regeneration", 0));
		}
		return out;
	}

	/** Metal da piramide do nivel (ferro, ouro, diamante, netherite, celestio), na escala de {@code beacon/BeaconTiers}. */
	public static com.mlin.notas.tier.Tier beaconMetal(int level) {
		return switch (Math.max(1, Math.min(5, level))) {
			case 1 -> com.mlin.notas.tier.Tier.IRON;
			case 2 -> com.mlin.notas.tier.Tier.GOLD;
			case 3 -> com.mlin.notas.tier.Tier.DIAMOND;
			case 4 -> com.mlin.notas.tier.Tier.NETHERITE;
			default -> com.mlin.notas.tier.Tier.CELESTIUM;
		};
	}

	/** Raio horizontal do efeito: cobre a vila toda (raio da vila + folga da muralha) ou o alcance do sinalizador do metal, o que for maior. */
	public static int beaconRange(int villageRadius, int level) {
		return Math.max(villageRadius + 8, com.mlin.notas.beacon.BeaconTiers.range(beaconMetal(level), Math.min(4, Math.max(1, level))));
	}

	/** Duracao do efeito em tiques (renovado a cada 4 s). */
	public static int beaconDuration() {
		return 220;
	}

	// ---------------------------------------------------------------- pedra de teleporte da vila

	/** Custo (niveis de experiencia) entre duas pedras de vilas suas: metade do normal, minimo 1. */
	public static int halfCost(int levels) {
		return Math.max(1, (levels + 1) / 2);
	}

	// ---------------------------------------------------------------- refugio e seguro de morte

	/** Monstros nao nascem dentro das construcoes de uma vila de muralha completa. */
	public static boolean refugeBlocksSpawn(boolean wallComplete, boolean insideBuilding, boolean natural) {
		return wallComplete && insideBuilding && natural;
	}

	/** Seguro de morte: Estalagem II e morte a ate {@code radius + 24} blocos do centro. */
	public static boolean insured(int innLevel, double distanceToCenter, int villageRadius) {
		return innLevel >= 2 && distanceToCenter <= villageRadius + 24;
	}

	/** Dia de jogo (tiques) ate o qual os itens ficam no achados e perdidos antes de virar lapide: 1 dia. */
	public static long lostFoundTicks() {
		return 24_000L;
	}
}
