package com.mlin.notas.client.ambient;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.ambient.AmbientOptions;
import com.mlin.notas.ambient.AmbientRules;
import com.mlin.notas.client.season.SeasonClient;
import com.mlin.notas.registry.ModSounds;
import com.mlin.notas.season.Season;
import com.mlin.notas.season.SeasonBiomes;
import com.mlin.notas.season.SeasonDate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TintedParticleLeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Ambiente no cliente: uma vez por segundo amostra o lugar (bioma do jogador e dos arredores, luz do ceu, hora,
 * chuva, estacao e uma grade esparsa de 125 blocos para achar agua) e {@link AmbientRules} decide os volumes dos
 * loops, as chances dos sons avulsos e as densidades das particulas. As particulas sao sorteadas em poucos pontos
 * aleatorios perto do jogador por tick (nada varre chunks). Tudo desliga por {@code config/mlinnotas/ambient.json}.
 */
public final class AmbientClient {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(MlinNotas.MOD_ID).resolve("ambient.json");
	private static final Map<AmbientRules.Loop, SoundEvent> LOOP_SOUNDS = Map.of(
			AmbientRules.Loop.WIND, ModSounds.AMBIENT_MOUNTAIN_WIND,
			AmbientRules.Loop.DESERT_WIND, ModSounds.AMBIENT_DESERT_WIND,
			AmbientRules.Loop.RIVER, ModSounds.AMBIENT_RIVER,
			AmbientRules.Loop.CRICKETS, ModSounds.AMBIENT_CRICKETS);

	private static AmbientOptions options = new AmbientOptions();
	private static AmbientRules.Env env;
	private static final EnumMap<AmbientRules.Loop, LoopSound> LOOPS = new EnumMap<>(AmbientRules.Loop.class);
	private static int ticks;

	private AmbientClient() {
	}

	public static void init() {
		options = load();
		ClientTickEvents.END_CLIENT_TICK.register(AmbientClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			env = null;
			LOOPS.values().forEach(LoopSound::fadeOutNow);
			LOOPS.clear();
		});
	}

	public static AmbientOptions options() {
		return options;
	}

	/** Loops tocando agora (para testes). */
	public static java.util.Set<AmbientRules.Loop> activeLoops() {
		return java.util.Set.copyOf(LOOPS.keySet());
	}

	/** Ultima amostra do ambiente (para testes e depuracao). */
	public static AmbientRules.Env env() {
		return env;
	}

	public static AmbientOptions load() {
		try {
			if (Files.exists(FILE)) {
				return AmbientOptions.fromJson(Files.readString(FILE, StandardCharsets.UTF_8));
			}
			AmbientOptions defaults = new AmbientOptions();
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, defaults.toJson(), StandardCharsets.UTF_8);
			return defaults;
		} catch (IOException e) {
			MlinNotas.LOGGER.warn("[ambiente] nao consegui ler {}: {}", FILE, e.toString());
			return new AmbientOptions();
		}
	}

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		ClientLevel level = minecraft.level;
		if (player == null || level == null || minecraft.isPaused()) {
			return;
		}
		ticks++;
		if (ticks % 20 == 0 || env == null) {
			env = sample(level, player);
			if (options.sounds) {
				playOneShots(minecraft, level, player, env);
			}
		}
		updateLoops(minecraft, options.sounds ? env : null);
		if (options.particles && options.particleDensity > 0) {
			spawnParticles(level, player, env);
		}
	}

	// ---------------------------------------------------------------- amostra

	static AmbientRules.Env sample(ClientLevel level, LocalPlayer player) {
		BlockPos pos = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
		Holder<Biome> biome = level.getBiome(pos);
		int skyLight = level.getBrightness(LightLayer.SKY, pos);
		boolean cave = skyLight == 0 && pos.getY() < level.getSeaLevel();
		boolean openSky = skyLight >= 10;
		boolean river = biome.is(BiomeTags.IS_RIVER) || riverNearby(level, pos);
		int water = 0;
		int flowing = 0;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int dx = -8; dx <= 8; dx += 4) {
			for (int dz = -8; dz <= 8; dz += 4) {
				for (int dy = -4; dy <= 4; dy += 2) {
					FluidState fluid = level.getFluidState(cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz));
					if (fluid.is(Fluids.WATER) || fluid.is(Fluids.FLOWING_WATER)) {
						water++;
						if (!fluid.isSource()) {
							flowing++;
						}
					}
				}
			}
		}
		SeasonDate date = SeasonClient.date();
		Season season = date == null ? Season.SUMMER : date.season();
		Season.Phase phase = date == null ? Season.Phase.MID : date.phase();
		return new AmbientRules.Env(
				biome.is(BiomeTags.IS_FOREST) || biome.is(BiomeTags.IS_TAIGA) || biome.is(BiomeTags.IS_JUNGLE),
				biome.is(ConventionalBiomeTags.IS_SWAMP),
				biome.is(ConventionalBiomeTags.IS_DESERT) || biome.is(BiomeTags.IS_BADLANDS),
				biome.is(BiomeTags.IS_MOUNTAIN) || biome.is(ConventionalBiomeTags.IS_MOUNTAIN),
				river,
				biome.is(ConventionalBiomeTags.IS_PLAINS) || biome.is(BiomeTags.IS_SAVANNA),
				SeasonBiomes.temperate(biome.value()),
				cave, openSky, pos.getY(), level.isDarkOutside(), level.getRainLevel(1.0F) > 0.2F, season, phase, water, flowing);
	}

	private static boolean riverNearby(ClientLevel level, BlockPos pos) {
		int[][] offsets = {{12, 0}, {-12, 0}, {0, 12}, {0, -12}};
		for (int[] o : offsets) {
			if (level.getBiome(pos.offset(o[0], 0, o[1])).is(BiomeTags.IS_RIVER)) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- sons

	private static void updateLoops(Minecraft minecraft, AmbientRules.Env env) {
		for (AmbientRules.Loop loop : AmbientRules.Loop.values()) {
			float target = env == null ? 0 : AmbientRules.loopVolume(loop, env) * options.soundVolume;
			LoopSound sound = LOOPS.get(loop);
			if (sound != null && (sound.isStopped() || !minecraft.getSoundManager().isActive(sound) && sound.age() > 40)) {
				LOOPS.remove(loop);
				sound = null;
			}
			if (sound == null) {
				if (target <= 0.01F) {
					continue;
				}
				sound = new LoopSound(LOOP_SOUNDS.get(loop));
				LOOPS.put(loop, sound);
				minecraft.getSoundManager().play(sound);
			}
			sound.setTarget(Math.min(1.0F, target));
		}
	}

	private static void playOneShots(Minecraft minecraft, ClientLevel level, LocalPlayer player, AmbientRules.Env env) {
		RandomSource random = level.getRandom();
		for (AmbientRules.OneShot shot : AmbientRules.OneShot.values()) {
			float chance = AmbientRules.oneShotChance(shot, env);
			if (chance <= 0 || random.nextFloat() >= chance) {
				continue;
			}
			SoundEvent event;
			double dist;
			double dy;
			float volume;
			switch (shot) {
				case BIRDS -> {
					event = ModSounds.AMBIENT_BIRDS;
					dist = 6 + random.nextDouble() * 10;
					dy = 3 + random.nextDouble() * 6;
					volume = 0.45F;
				}
				case CAVE_DRIP -> {
					event = ModSounds.AMBIENT_CAVE_DRIP;
					dist = 3 + random.nextDouble() * 8;
					dy = 1 + random.nextDouble() * 3;
					volume = 0.5F;
				}
				case CAVE_RUMBLE -> {
					event = ModSounds.AMBIENT_CAVE_RUMBLE;
					dist = 10;
					dy = -6;
					volume = 0.8F;
				}
				default -> {
					event = ModSounds.AMBIENT_FROG;
					dist = 5 + random.nextDouble() * 9;
					dy = -1 + random.nextDouble() * 2;
					volume = 0.5F;
				}
			}
			double angle = random.nextDouble() * Math.PI * 2;
			double x = player.getX() + Math.cos(angle) * dist;
			double z = player.getZ() + Math.sin(angle) * dist;
			float pitch = 0.88F + random.nextFloat() * 0.24F;
			minecraft.getSoundManager().play(new SimpleSoundInstance(event, SoundSource.AMBIENT, Math.min(1.0F, volume * options.soundVolume),
					pitch, SoundInstance.createUnseededRandom(), x, player.getEyeY() + dy, z));
		}
	}

	/** Loop sem posicao com volume que desliza ate o alvo (fade de ~2 s) e para sozinho quando fica mudo. */
	static final class LoopSound extends AbstractTickableSoundInstance {
		private float target;
		private int silentTicks;
		private int age;

		LoopSound(SoundEvent event) {
			super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
			this.looping = true;
			this.delay = 0;
			this.volume = 0.001F;
			this.relative = true;
			this.attenuation = SoundInstance.Attenuation.NONE;
		}

		void setTarget(float target) {
			this.target = target;
		}

		int age() {
			return age;
		}

		void fadeOutNow() {
			this.stop();
		}

		@Override
		public boolean canStartSilent() {
			return true;
		}

		@Override
		public void tick() {
			age++;
			float step = 0.012F;
			if (volume < target) {
				volume = Math.min(target, volume + step);
			} else {
				volume = Math.max(target, volume - step);
			}
			silentTicks = volume <= 0.002F && target <= 0.002F ? silentTicks + 1 : 0;
			if (silentTicks > 40) {
				stop();
			}
		}
	}

	// ---------------------------------------------------------------- particulas

	private static void spawnParticles(ClientLevel level, LocalPlayer player, AmbientRules.Env env) {
		if (env == null) {
			return;
		}
		RandomSource random = level.getRandom();
		float density = options.particleDensity;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		double px = player.getX();
		double py = player.getEyeY();
		double pz = player.getZ();

		int leafTries = attempts(AmbientRules.particleRate(AmbientRules.Particle.LEAVES, env) * 24 * density, random);
		for (int i = 0; i < leafTries; i++) {
			cursor.set(px + random.nextInt(29) - 14, py + random.nextInt(17) - 6, pz + random.nextInt(29) - 14);
			BlockState state = level.getBlockState(cursor);
			if (state.getBlock() instanceof TintedParticleLeavesBlock && level.getBlockState(cursor.below()).isAir()) {
				int color = level.getClientLeafTintColor(cursor);
				level.addParticle(ColorParticleOption.create(ParticleTypes.TINTED_LEAVES, color), cursor.getX() + random.nextDouble(),
						cursor.getY() - 0.05, cursor.getZ() + random.nextDouble(), 0, 0, 0);
			}
		}

		int fireflyTries = attempts(AmbientRules.particleRate(AmbientRules.Particle.FIREFLIES, env) * 1.2F * density, random);
		for (int i = 0; i < fireflyTries; i++) {
			int x = (int) Math.floor(px) + random.nextInt(25) - 12;
			int z = (int) Math.floor(pz) + random.nextInt(25) - 12;
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + random.nextInt(4);
			cursor.set(x, y, z);
			if (Math.abs(y - player.getY()) < 12 && level.getBlockState(cursor).isAir() && level.getBrightness(LightLayer.BLOCK, cursor) < 13) {
				level.addParticle(ParticleTypes.FIREFLY, cursor.getX() + random.nextDouble(), cursor.getY() + 0.3 + random.nextDouble(),
						cursor.getZ() + random.nextDouble(), 0, 0, 0);
			}
		}

		int dustTries = attempts(AmbientRules.particleRate(AmbientRules.Particle.DUST, env) * 2 * density, random);
		for (int i = 0; i < dustTries; i++) {
			cursor.set(px + random.nextInt(17) - 8, py + random.nextInt(9) - 4, pz + random.nextInt(17) - 8);
			if (!level.getBlockState(cursor).isAir()) {
				continue;
			}
			double x = cursor.getX() + random.nextDouble();
			double y = cursor.getY() + random.nextDouble();
			double z = cursor.getZ() + random.nextDouble();
			if (env.cave()) {
				level.addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0, 0, 0);
			} else {
				BlockState sand = env.desert() && level.getBiome(cursor).is(BiomeTags.IS_BADLANDS) ? Blocks.RED_SAND.defaultBlockState()
						: Blocks.SAND.defaultBlockState();
				level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, sand), x, y, z, 0, 0, 0);
			}
		}

		int snowTries = attempts(AmbientRules.particleRate(AmbientRules.Particle.SNOWFLAKES, env) * 4 * density, random);
		for (int i = 0; i < snowTries; i++) {
			cursor.set(px + random.nextInt(25) - 12, py + 2 + random.nextInt(9), pz + random.nextInt(25) - 12);
			if (level.getBlockState(cursor).isAir() && level.getBrightness(LightLayer.SKY, cursor) >= 14) {
				level.addParticle(ParticleTypes.SNOWFLAKE, cursor.getX() + random.nextDouble(), cursor.getY() + random.nextDouble(),
						cursor.getZ() + random.nextDouble(), (random.nextDouble() - 0.5) * 0.04, -0.03, (random.nextDouble() - 0.5) * 0.04);
			}
		}
	}

	/** Parte inteira + sorteio da fracao. */
	private static int attempts(float expected, RandomSource random) {
		if (expected <= 0) {
			return 0;
		}
		int whole = (int) expected;
		return whole + (random.nextFloat() < expected - whole ? 1 : 0);
	}
}
