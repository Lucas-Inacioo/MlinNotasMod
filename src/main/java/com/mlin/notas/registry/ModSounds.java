package com.mlin.notas.registry;

import com.mlin.notas.MlinNotas;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.SoundType;

/**
 * Sons proprios do mod. Os arquivos ({@code sounds/**.ogg}), o {@code sounds.json} e as legendas
 * ({@code subtitles.mlinnotas.*}) sao sintetizados por {@code scripts/SoundGen.java} ({@code ./gradlew soundGen}); todo
 * id registrado aqui precisa existir la ({@code ModSoundsResourcesTest} confere).
 */
public final class ModSounds {
	// ---- conjuntos de bloco (break/step/place/hit/fall) ----
	private static final BlockSounds ETHER_CRYSTAL_SET = blockSounds("ether_crystal");
	private static final BlockSounds RESONANT_STONE_SET = blockSounds("resonant_stone");
	private static final BlockSounds END_MOSS_SET = blockSounds("end_moss");
	private static final BlockSounds VOID_ASH_SET = blockSounds("void_ash");
	private static final BlockSounds ALLOY_SET = blockSounds("alloy");
	private static final BlockSounds GLINT_ORE_SET = blockSounds("glint_ore");

	/** Cristal etereo: tilintar inarmonico. */
	public static final SoundType ETHER_CRYSTAL = ETHER_CRYSTAL_SET.type(1.0F, 1.0F);
	/** Aglomerado de cristal etereo: o mesmo cristal, mais agudo e baixo. */
	public static final SoundType ETHER_CRYSTAL_CLUSTER = ETHER_CRYSTAL_SET.type(0.85F, 1.3F);
	/** Tijolos do vazio, elevador de purpur: pedra com ressonancia de salao. */
	public static final SoundType RESONANT_STONE = RESONANT_STONE_SET.type(1.0F, 1.0F);
	/** Pedra de teleporte: pedra ressonante mais grave. */
	public static final SoundType WAYSTONE = RESONANT_STONE_SET.type(1.0F, 0.85F);
	/** Altares de chefe: pedra ressonante bem grave. */
	public static final SoundType BOSS_ALTAR = RESONANT_STONE_SET.type(1.0F, 0.7F);
	public static final SoundType END_MOSS = END_MOSS_SET.type(1.0F, 1.0F);
	public static final SoundType VOID_ASH = VOID_ASH_SET.type(1.0F, 1.0F);
	/** Minerios do End (endita, astralita): pedra seca com faiscas. */
	public static final SoundType GLINT_ORE = GLINT_ORE_SET.type(1.0F, 1.0F);

	// ---- blocos com acao ----
	public static final SoundEvent ELEVATOR_UP = sound("block.purpur_elevator.up");
	public static final SoundEvent ELEVATOR_DOWN = sound("block.purpur_elevator.down");
	public static final SoundEvent WAYSTONE_ACTIVATE = sound("block.waystone.activate");
	public static final SoundEvent WAYSTONE_TRAVEL = sound("block.waystone.travel");
	public static final SoundEvent BOSS_ALTAR_SUMMON = sound("block.boss_altar.summon");

	// ---- criaturas do End ----
	public static final SoundEvent PURPUR_SENTINEL_AMBIENT = sound("entity.purpur_sentinel.ambient");
	public static final SoundEvent PURPUR_SENTINEL_HURT = sound("entity.purpur_sentinel.hurt");
	public static final SoundEvent PURPUR_SENTINEL_DEATH = sound("entity.purpur_sentinel.death");
	public static final SoundEvent PURPUR_SENTINEL_ATTACK = sound("entity.purpur_sentinel.attack");
	public static final SoundEvent PURPUR_SENTINEL_SHOOT = sound("entity.purpur_sentinel.shoot");
	public static final SoundEvent VOID_JELLY_AMBIENT = sound("entity.void_jelly.ambient");
	public static final SoundEvent VOID_JELLY_HURT = sound("entity.void_jelly.hurt");
	public static final SoundEvent VOID_JELLY_DEATH = sound("entity.void_jelly.death");
	public static final SoundEvent CRYSTAL_MITE_AMBIENT = sound("entity.crystal_mite.ambient");
	public static final SoundEvent CRYSTAL_MITE_HURT = sound("entity.crystal_mite.hurt");
	public static final SoundEvent CRYSTAL_MITE_DEATH = sound("entity.crystal_mite.death");
	public static final SoundEvent CRYSTAL_MITE_BITE = sound("entity.crystal_mite.bite");
	public static final SoundEvent CHORUS_HOPPER_AMBIENT = sound("entity.chorus_hopper.ambient");
	public static final SoundEvent CHORUS_HOPPER_HURT = sound("entity.chorus_hopper.hurt");
	public static final SoundEvent CHORUS_HOPPER_DEATH = sound("entity.chorus_hopper.death");
	public static final SoundEvent CHORUS_HOPPER_TELEPORT = sound("entity.chorus_hopper.teleport");

	// ---- Nether ----
	public static final SoundEvent MAGMA_SLUG_AMBIENT = sound("entity.magma_slug.ambient");
	public static final SoundEvent MAGMA_SLUG_HURT = sound("entity.magma_slug.hurt");
	public static final SoundEvent MAGMA_SLUG_DEATH = sound("entity.magma_slug.death");

	// ---- Golem de Cobalto ----
	public static final SoundEvent COBALT_GOLEM_AMBIENT = sound("entity.cobalt_golem.ambient");
	public static final SoundEvent COBALT_GOLEM_HURT = sound("entity.cobalt_golem.hurt");
	public static final SoundEvent COBALT_GOLEM_DEATH = sound("entity.cobalt_golem.death");
	public static final SoundEvent COBALT_GOLEM_ROAR = sound("entity.cobalt_golem.roar");
	public static final SoundEvent COBALT_GOLEM_ATTACK = sound("entity.cobalt_golem.attack");
	public static final SoundEvent COBALT_GOLEM_SLAM_WINDUP = sound("entity.cobalt_golem.slam_windup");
	public static final SoundEvent COBALT_GOLEM_SLAM = sound("entity.cobalt_golem.slam");
	public static final SoundEvent COBALT_GOLEM_CRASH = sound("entity.cobalt_golem.crash");
	public static final SoundEvent COBALT_GOLEM_THROW = sound("entity.cobalt_golem.throw");
	public static final SoundEvent COBALT_GOLEM_CHARGE = sound("entity.cobalt_golem.charge");
	public static final SoundEvent COBALT_ROCK_IMPACT = sound("entity.cobalt_rock.impact");

	// ---- Guardiao das Profundezas ----
	public static final SoundEvent ABYSSAL_GUARDIAN_AMBIENT = sound("entity.abyssal_guardian.ambient");
	public static final SoundEvent ABYSSAL_GUARDIAN_HURT = sound("entity.abyssal_guardian.hurt");
	public static final SoundEvent ABYSSAL_GUARDIAN_DEATH = sound("entity.abyssal_guardian.death");
	public static final SoundEvent ABYSSAL_GUARDIAN_ROAR = sound("entity.abyssal_guardian.roar");
	public static final SoundEvent ABYSSAL_GUARDIAN_ATTACK = sound("entity.abyssal_guardian.attack");
	public static final SoundEvent ABYSSAL_GUARDIAN_PULSE_CHARGE = sound("entity.abyssal_guardian.pulse_charge");
	public static final SoundEvent ABYSSAL_GUARDIAN_PULSE = sound("entity.abyssal_guardian.pulse");
	public static final SoundEvent ABYSSAL_GUARDIAN_TENDRILS = sound("entity.abyssal_guardian.tendrils");

	// ---- Colosso Igneo ----
	public static final SoundEvent IGNEOUS_COLOSSUS_AMBIENT = sound("entity.igneous_colossus.ambient");
	public static final SoundEvent IGNEOUS_COLOSSUS_HURT = sound("entity.igneous_colossus.hurt");
	public static final SoundEvent IGNEOUS_COLOSSUS_DEATH = sound("entity.igneous_colossus.death");
	public static final SoundEvent IGNEOUS_COLOSSUS_ROAR = sound("entity.igneous_colossus.roar");
	public static final SoundEvent IGNEOUS_COLOSSUS_ATTACK = sound("entity.igneous_colossus.attack");
	public static final SoundEvent IGNEOUS_COLOSSUS_FIRE_WAVE = sound("entity.igneous_colossus.fire_wave");
	public static final SoundEvent IGNEOUS_COLOSSUS_METEOR = sound("entity.igneous_colossus.meteor");
	public static final SoundEvent IGNEOUS_COLOSSUS_LAVA_POOL = sound("entity.igneous_colossus.lava_pool");
	public static final SoundEvent MAGMA_METEOR_IMPACT = sound("entity.magma_meteor.impact");

	// ---- Arauto Astral ----
	public static final SoundEvent ASTRAL_HERALD_AMBIENT = sound("entity.astral_herald.ambient");
	public static final SoundEvent ASTRAL_HERALD_HURT = sound("entity.astral_herald.hurt");
	public static final SoundEvent ASTRAL_HERALD_DEATH = sound("entity.astral_herald.death");
	public static final SoundEvent ASTRAL_HERALD_ROAR = sound("entity.astral_herald.roar");
	public static final SoundEvent ASTRAL_HERALD_CAST = sound("entity.astral_herald.cast");
	public static final SoundEvent ASTRAL_HERALD_TELEPORT = sound("entity.astral_herald.teleport");
	public static final SoundEvent ASTRAL_HERALD_SUMMON = sound("entity.astral_herald.summon");
	public static final SoundEvent STAR_BOLT_IMPACT = sound("entity.star_bolt.impact");

	// ---- ambiente (tocados so no cliente, client/ambient/AmbientClient) ----
	/** Loop: vento uivando (montanhas, picos, lugares altos). */
	public static final SoundEvent AMBIENT_MOUNTAIN_WIND = sound("ambient.wind.mountain");
	/** Loop: vento seco com areia (deserto, badlands). */
	public static final SoundEvent AMBIENT_DESERT_WIND = sound("ambient.desert.wind");
	/** Loop: agua correndo (rios, cachoeiras). */
	public static final SoundEvent AMBIENT_RIVER = sound("ambient.river");
	/** Loop: grilos (noites de verao). */
	public static final SoundEvent AMBIENT_CRICKETS = sound("ambient.forest.crickets");
	public static final SoundEvent AMBIENT_BIRDS = sound("ambient.forest.birds");
	public static final SoundEvent AMBIENT_CAVE_DRIP = sound("ambient.cave.drip");
	public static final SoundEvent AMBIENT_CAVE_RUMBLE = sound("ambient.cave.rumble");
	public static final SoundEvent AMBIENT_FROG = sound("ambient.swamp.frog");

	private ModSounds() {
	}

	private static SoundEvent sound(String name) {
		Identifier id = MlinNotas.id(name);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	private static BlockSounds blockSounds(String name) {
		String prefix = "block." + name + ".";
		return new BlockSounds(sound(prefix + "break"), sound(prefix + "step"), sound(prefix + "place"), sound(prefix + "hit"),
				sound(prefix + "fall"));
	}

	/** Blocos dos metais novos: o mesmo conjunto "liga", mais agudo nos niveis altos. */
	public static SoundType alloy(float pitch) {
		return ALLOY_SET.type(1.0F, pitch);
	}

	private record BlockSounds(SoundEvent breakSound, SoundEvent step, SoundEvent place, SoundEvent hit, SoundEvent fall) {
		SoundType type(float volume, float pitch) {
			return new SoundType(volume, pitch, breakSound, step, place, hit, fall);
		}
	}

	public static void init() {
	}
}
