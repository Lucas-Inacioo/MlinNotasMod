package com.mlin.notas.beacon;

import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;

import java.util.function.IntSupplier;

/**
 * Data slot do servidor com o metal da piramide ({@link BeaconTiers#encode}); recalculado no maximo uma vez por
 * segundo enquanto o menu do sinalizador esta aberto. No cliente o menu usa um {@link DataSlot#standalone()}.
 */
public final class BeaconTierSlot extends DataSlot {
	private final ContainerLevelAccess access;
	private final IntSupplier levels;
	private boolean computed;
	private long checkedAt;
	private int checkedLevels;
	private int cached;

	public BeaconTierSlot(ContainerLevelAccess access, IntSupplier levels) {
		this.access = access;
		this.levels = levels;
	}

	@Override
	public int get() {
		return access.evaluate((level, pos) -> {
			long now = level.getGameTime();
			int layers = levels.getAsInt();
			if (!computed || layers != checkedLevels || now - checkedAt >= 20 || now < checkedAt) {
				computed = true;
				checkedAt = now;
				checkedLevels = layers;
				cached = BeaconTiers.encode(BeaconPowers.pyramidTier(level, pos, layers));
			}
			return cached;
		}, 0);
	}

	@Override
	public void set(int value) {
	}
}
