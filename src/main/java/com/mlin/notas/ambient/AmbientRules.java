package com.mlin.notas.ambient;

import com.mlin.notas.season.Season;

/**
 * Escolha do ambiente (sons e particulas) a partir de uma amostra barata do que esta em volta do jogador: tipo de
 * bioma, altura, ceu aberto/caverna, hora, chuva, estacao e quanta agua tem perto. Logica pura; o cliente amostra
 * uma vez por segundo ({@code client/ambient/AmbientClient}).
 */
public final class AmbientRules {
	/** Sons continuos (loop) tocados sem posicao, com volume suave. */
	public enum Loop {
		WIND, DESERT_WIND, RIVER, CRICKETS
	}

	/** Sons avulsos, sorteados por segundo. */
	public enum OneShot {
		BIRDS, CAVE_DRIP, CAVE_RUMBLE, FROG
	}

	public enum Particle {
		LEAVES, FIREFLIES, DUST, SNOWFLAKES
	}

	/**
	 * Amostra do ambiente. {@code season}/{@code phase}: estacao atual (com as estacoes desligadas o cliente passa
	 * verao/meio). {@code water}: blocos de agua na grade de amostra; {@code flowingWater}: os que nao sao fonte.
	 * {@code temperate}: bioma com estacoes visuais (neva no inverno). {@code cave}: sem luz do ceu, abaixo do mar.
	 */
	public record Env(boolean forest, boolean swamp, boolean desert, boolean mountain, boolean river, boolean plains,
			boolean temperate, boolean cave, boolean openSky, int y, boolean night, boolean raining, Season season,
			Season.Phase phase, int water, int flowingWater) {
	}

	private AmbientRules() {
	}

	/** Quanto a estacao favorece os bichos de verao (grilos, vaga-lumes, sapos): 0..1. */
	public static float summerness(Season season, Season.Phase phase) {
		return switch (season) {
			case SUMMER -> 1.0F;
			case SPRING -> phase == Season.Phase.LATE ? 0.5F : phase == Season.Phase.MID ? 0.2F : 0.0F;
			case AUTUMN -> phase == Season.Phase.EARLY ? 0.5F : phase == Season.Phase.MID ? 0.15F : 0.0F;
			case WINTER -> 0.0F;
		};
	}

	/** Volume alvo (0..1) do loop. */
	public static float loopVolume(Loop loop, Env env) {
		return switch (loop) {
			case WIND -> {
				if (env.cave() || !env.openSky()) {
					yield 0;
				}
				float height = clamp((env.y() - 95) / 80.0F);
				if (!env.mountain() && height <= 0) {
					yield 0;
				}
				float v = (env.mountain() ? 0.3F : 0.15F) + 0.4F * height;
				if (env.season() == Season.WINTER) {
					v *= 1.3F;
				}
				if (env.raining()) {
					v *= 1.2F;
				}
				yield Math.min(0.8F, v);
			}
			case DESERT_WIND -> env.desert() && env.openSky() && !env.cave() ? (env.night() ? 0.3F : 0.45F) : 0;
			case RIVER -> {
				float v = 0;
				if (env.river() && env.water() >= 4) {
					v = 0.25F + 0.3F * clamp(env.water() / 40.0F);
				}
				if (env.flowingWater() >= 3) {
					v = Math.max(v, 0.2F + 0.4F * clamp(env.flowingWater() / 20.0F));
				}
				yield v;
			}
			case CRICKETS -> {
				if (!env.night() || env.raining() || env.cave() || !env.openSky()) {
					yield 0;
				}
				if (!(env.forest() || env.plains() || env.swamp() || env.temperate())) {
					yield 0;
				}
				yield 0.35F * summerness(env.season(), env.phase());
			}
		};
	}

	/** Chance por segundo de tocar o som avulso. */
	public static float oneShotChance(OneShot shot, Env env) {
		return switch (shot) {
			case BIRDS -> {
				if (env.night() || env.raining() || env.cave() || !env.openSky() || env.season() == Season.WINTER) {
					yield 0;
				}
				float base = env.forest() ? 0.14F : env.plains() || env.swamp() ? 0.05F : 0;
				yield env.season() == Season.SPRING ? base * 1.4F : base;
			}
			case CAVE_DRIP -> env.cave() ? 0.12F : 0;
			case CAVE_RUMBLE -> env.cave() ? (env.y() < 0 ? 0.012F : 0.005F) : 0;
			case FROG -> {
				if (!env.swamp() || env.cave() || !(env.night() || env.raining())) {
					yield 0;
				}
				float season = env.season() == Season.WINTER ? 0 : Math.max(0.3F, summerness(env.season(), env.phase()));
				yield 0.16F * season;
			}
		};
	}

	/** Densidade relativa (0..1) da particula em volta do jogador. */
	public static float particleRate(Particle particle, Env env) {
		return switch (particle) {
			case LEAVES -> {
				if (env.cave()) {
					yield 0;
				}
				yield switch (env.season()) {
					case AUTUMN -> env.phase() == Season.Phase.EARLY ? 0.6F : 1.0F;
					case SUMMER -> 0.15F;
					case SPRING -> 0.1F;
					case WINTER -> 0.03F;
				};
			}
			case FIREFLIES -> {
				if (!env.night() || env.raining() || env.cave() || !env.openSky()) {
					yield 0;
				}
				float place = env.swamp() ? 1.0F : env.forest() ? 0.6F : env.plains() ? 0.3F : 0;
				yield place * summerness(env.season(), env.phase());
			}
			case DUST -> env.cave() ? 0.5F : env.desert() && env.openSky() && !env.night() && !env.raining() ? 0.6F : 0;
			case SNOWFLAKES -> env.season() == Season.WINTER && env.temperate() && env.openSky() && !env.cave()
					? (env.raining() ? 0.2F : 0.6F) : 0;
		};
	}

	private static float clamp(float v) {
		return Math.max(0, Math.min(1, v));
	}
}
