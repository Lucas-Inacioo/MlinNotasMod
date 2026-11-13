package com.mlin.notas.backpack;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Codecs (disco e rede) do estado da mochila. Separados do record para mante-lo puro. */
public final class BackpackCodecs {
	public static final Codec<SmeltingProgress> SMELTING_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.optionalFieldOf("burn_remaining", 0).forGetter(SmeltingProgress::burnRemaining),
			Codec.INT.optionalFieldOf("burn_total", 0).forGetter(SmeltingProgress::burnTotal),
			Codec.INT.optionalFieldOf("cook_progress", 0).forGetter(SmeltingProgress::cookProgress)
	).apply(i, SmeltingProgress::new));

	public static final Codec<BackpackUpgrades> UPGRADES_CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.intRange(0, BackpackUpgrades.MAX_SIZE_LEVEL).optionalFieldOf("size_level", 0).forGetter(BackpackUpgrades::sizeLevel),
			Codec.BOOL.optionalFieldOf("crafting", false).forGetter(BackpackUpgrades::crafting),
			Codec.BOOL.optionalFieldOf("furnace", false).forGetter(BackpackUpgrades::furnace),
			Codec.BOOL.optionalFieldOf("water", false).forGetter(BackpackUpgrades::water),
			Codec.BOOL.optionalFieldOf("lava", false).forGetter(BackpackUpgrades::lava),
			Codec.BOOL.optionalFieldOf("water_filled", false).forGetter(BackpackUpgrades::waterFilled),
			Codec.intRange(0, BackpackUpgrades.MAX_LAVA_CHARGES).optionalFieldOf("lava_charges", 0).forGetter(BackpackUpgrades::lavaCharges),
			Codec.BOOL.optionalFieldOf("lava_selected", false).forGetter(BackpackUpgrades::lavaSelected),
			SMELTING_CODEC.optionalFieldOf("smelting", SmeltingProgress.IDLE).forGetter(BackpackUpgrades::smelting)
	).apply(i, BackpackUpgrades::new));

	public static final StreamCodec<ByteBuf, BackpackUpgrades> UPGRADES_STREAM_CODEC = ByteBufCodecs.fromCodec(UPGRADES_CODEC);

	private BackpackCodecs() {
	}
}
