package com.mlin.notas.vein;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillServer;
import com.mlin.notas.util.Pos;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mineracao em veio: segurando a tecla de veio (cliente manda {@link Payload}) e quebrando um minerio ({@code #c:ores})
 * com picareta, os minerios do mesmo grupo conectados tambem quebram ({@link VeinPlanner}). Cada bloco extra passa por
 * {@code gameMode.destroyBlock}, entao valem a trava de Mineracao, Garimpo, Fortuna, durabilidade e fome. Para antes
 * de a picareta quebrar. Tamanho maximo pelo nivel de Mineracao do mundo.
 */
public final class VeinMineServer {
	private static final Set<UUID> HOLDING = ConcurrentHashMap.newKeySet();
	private static final Set<UUID> MINING = ConcurrentHashMap.newKeySet();

	/** Cliente -> servidor: tecla de veio pressionada/solta. */
	public record Payload(boolean active) implements CustomPacketPayload {
		public static final Type<Payload> TYPE = new Type<>(MlinNotas.id("vein_mine"));
		public static final StreamCodec<ByteBuf, Payload> CODEC = ByteBufCodecs.BOOL.map(Payload::new, Payload::active);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	private VeinMineServer() {
	}

	public static void init() {
		PayloadTypeRegistry.serverboundPlay().register(Payload.TYPE, Payload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(Payload.TYPE, (payload, context) -> setActive(context.player(), payload.active()));
		ServerPlayerEvents.LEAVE.register(player -> HOLDING.remove(player.getUUID()));
		PlayerBlockBreakEvents.AFTER.register(VeinMineServer::afterBreak);
	}

	public static void setActive(ServerPlayer player, boolean active) {
		if (active) {
			HOLDING.add(player.getUUID());
		} else {
			HOLDING.remove(player.getUUID());
		}
	}

	public static boolean isActive(Player player) {
		return HOLDING.contains(player.getUUID());
	}

	private static void afterBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer) || !isActive(player)) {
			return;
		}
		if (!state.is(ConventionalBlockTags.ORES) || !player.getMainHandItem().is(ItemTags.PICKAXES)) {
			return;
		}
		if (!MINING.add(player.getUUID())) {
			return; // quebras disparadas pelo proprio veio
		}
		try {
			mineVein(serverLevel, serverPlayer, pos, state);
		} finally {
			MINING.remove(player.getUUID());
		}
	}

	/** Quebra o veio de {@code brokenState} a partir de {@code origin} (ja quebrado). Devolve quantos blocos extras caíram. */
	public static int mineVein(ServerLevel level, ServerPlayer player, BlockPos origin, BlockState brokenState) {
		String group = VeinPlanner.group(id(brokenState));
		int max = VeinPlanner.maxBlocks(SkillServer.progress(level.getServer()).level(SkillBranch.MINING));
		List<Pos> plan = VeinPlanner.plan(new Pos(origin.getX(), origin.getY(), origin.getZ()), group, p -> {
			BlockState s = level.getBlockState(new BlockPos(p.x(), p.y(), p.z()));
			return s.is(ConventionalBlockTags.ORES) ? VeinPlanner.group(id(s)) : null;
		}, max);
		int broken = 0;
		for (Pos p : plan) {
			ItemStack tool = player.getMainHandItem();
			if (!tool.is(ItemTags.PICKAXES) || (tool.isDamageableItem() && tool.getMaxDamage() - tool.getDamageValue() <= 1)) {
				break;
			}
			if (player.gameMode.destroyBlock(new BlockPos(p.x(), p.y(), p.z()))) {
				broken++;
			}
		}
		return broken;
	}

	private static String id(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}
}
