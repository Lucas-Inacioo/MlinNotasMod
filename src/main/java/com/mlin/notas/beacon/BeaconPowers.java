package com.mlin.notas.beacon;

import com.mlin.notas.registry.ModEffects;
import com.mlin.notas.tier.Tier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ponte entre as regras puras de {@link BeaconTiers} e o sinalizador vanilla. O mixin {@code BeaconBlockEntityMixin}
 * troca a aplicacao dos efeitos ({@code applyEffects}) por {@link #applyEffects}: mesmo ritmo do vanilla (a cada 80
 * ticks), mas com alcance e nivel do primario pelo metal da piramide e os poderes extras (Garimpo, Voo).
 */
public final class BeaconPowers {
	private BeaconPowers() {
	}

	/** Metal mais fraco das {@code levels} camadas da piramide sob o sinalizador (null se {@code levels} = 0). */
	public static Tier pyramidTier(Level level, BlockPos beacon, int levels) {
		if (levels <= 0) {
			return null;
		}
		List<Tier> metals = new ArrayList<>();
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		for (int step = 1; step <= levels; step++) {
			int y = beacon.getY() - step;
			for (int x = beacon.getX() - step; x <= beacon.getX() + step; x++) {
				for (int z = beacon.getZ() - step; z <= beacon.getZ() + step; z++) {
					pos.set(x, y, z);
					metals.add(BeaconTiers.metalOf(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).toString()));
				}
			}
		}
		return BeaconTiers.pyramidTier(metals);
	}

	/** Poder primario vanilla correspondente ao efeito (null para efeitos que nao sao primarios). */
	public static BeaconTiers.Primary primaryOf(Holder<MobEffect> effect) {
		if (effect == null) {
			return null;
		}
		if (effect.equals(MobEffects.SPEED)) {
			return BeaconTiers.Primary.SPEED;
		}
		if (effect.equals(MobEffects.HASTE)) {
			return BeaconTiers.Primary.HASTE;
		}
		if (effect.equals(MobEffects.RESISTANCE)) {
			return BeaconTiers.Primary.RESISTANCE;
		}
		if (effect.equals(MobEffects.JUMP_BOOST)) {
			return BeaconTiers.Primary.JUMP_BOOST;
		}
		if (effect.equals(MobEffects.STRENGTH)) {
			return BeaconTiers.Primary.STRENGTH;
		}
		if (effect.equals(MobEffects.REGENERATION)) {
			return BeaconTiers.Primary.REGENERATION;
		}
		return null;
	}

	/** Caixa de alcance (igual a do vanilla: cubo em volta do sinalizador estendido ate o teto do mundo). */
	public static AABB rangeBox(Level level, BlockPos pos, int range) {
		return new AABB(pos).inflate(range).expandTowards(0.0, level.getHeight(), 0.0);
	}

	/**
	 * Escolha de poder extra como secundario: precisa das 4 camadas e de um primario valido (das tres primeiras
	 * fileiras). O metal minimo e conferido no menu ({@code BeaconMenuMixin}) e de novo ao aplicar.
	 */
	public static boolean validExtraSelection(Holder<MobEffect> primary, int levels) {
		if (levels < 4 || primary == null) {
			return false;
		}
		for (int i = 0; i < 3; i++) {
			if (BeaconBlockEntity.BEACON_EFFECTS.get(i).contains(primary)) {
				return true;
			}
		}
		return false;
	}

	/** Substitui o {@code applyEffects} vanilla. */
	public static void applyEffects(Level level, BlockPos pos, int levels, Holder<MobEffect> primary, Holder<MobEffect> secondary) {
		if (level.isClientSide() || primary == null) {
			return;
		}
		apply(level, pos, levels, pyramidTier(level, pos, levels), primary, secondary);
	}

	/** Aplica os efeitos de uma piramide de {@code levels} camadas do metal {@code tier}. Devolve os jogadores afetados. */
	public static List<Player> apply(Level level, BlockPos pos, int levels, Tier tier, Holder<MobEffect> primary, Holder<MobEffect> secondary) {
		int range = BeaconTiers.range(tier, levels);
		boolean boosted = levels >= 4 && Objects.equals(primary, secondary);
		int amplifier = BeaconTiers.primaryAmplifier(primaryOf(primary), tier, boosted);
		int duration = BeaconTiers.durationTicks(levels);
		AABB box = rangeBox(level, pos, range);
		List<Player> players = level.getEntitiesOfClass(Player.class, box);
		for (Player player : players) {
			player.addEffect(new MobEffectInstance(primary, duration, amplifier, true, true));
		}
		if (levels >= 4 && secondary != null && !boosted) {
			BeaconTiers.Extra extra = ModEffects.extraOf(secondary);
			if (extra == null) {
				for (Player player : players) {
					player.addEffect(new MobEffectInstance(secondary, duration, 0, true, true));
				}
			} else if (BeaconTiers.unlocks(extra, tier, levels)) {
				for (Player player : players) {
					player.addEffect(new MobEffectInstance(secondary, duration, 0, true, true));
				}
				if (extra == BeaconTiers.Extra.FLIGHT) {
					BeaconFlight.register(level, pos, box);
				}
			}
		}
		return players;
	}
}
