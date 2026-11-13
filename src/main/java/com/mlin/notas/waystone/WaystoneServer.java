package com.mlin.notas.waystone;

import com.mlin.notas.registry.ModSounds;
import com.mlin.notas.registry.ModBlocks;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.Set;

/**
 * Lado servidor das pedras de teleporte: ativar (para o mundo todo), renomear, viajar cobrando XP, Pergaminho de
 * Retorno e sincronizacao da lista com os clientes (tela e minimapa).
 */
public final class WaystoneServer {
	/** Distancia maxima (blocos) entre o jogador e a pedra para os pedidos vindos do cliente. */
	public static final double USE_RANGE = 8.0;

	public enum TravelResult {
		OK, NO_XP, UNKNOWN_SOURCE, UNKNOWN_TARGET, TOO_FAR, SAME, NO_LEVEL
	}

	private WaystoneServer() {
	}

	public static void init() {
		PayloadTypeRegistry.clientboundPlay().register(WaystonePayloads.Sync.TYPE, WaystonePayloads.Sync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(WaystonePayloads.Open.TYPE, WaystonePayloads.Open.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WaystonePayloads.Travel.TYPE, WaystonePayloads.Travel.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(WaystonePayloads.Rename.TYPE, WaystonePayloads.Rename.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(WaystonePayloads.Travel.TYPE, (payload, context) ->
				travel(context.player(), payload.source(), payload.target()));
		ServerPlayNetworking.registerGlobalReceiver(WaystonePayloads.Rename.TYPE, (payload, context) ->
				rename(context.player(), payload.pos(), payload.name()));
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sendList(handler.player));
	}

	public static String dimension(Level level) {
		return level.dimension().identifier().toString();
	}

	public static Optional<WaystoneInfo> at(ServerLevel level, BlockPos lower) {
		return WaystoneData.get(level.getServer()).registry().get(dimension(level), lower.getX(), lower.getY(), lower.getZ());
	}

	/** Botao direito na pedra: ativa na primeira vez e abre a tela. */
	public static void use(ServerPlayer player, ServerLevel level, BlockPos lower) {
		WaystoneData data = WaystoneData.get(level.getServer());
		Optional<WaystoneInfo> existing = at(level, lower);
		boolean activated = existing.isEmpty();
		WaystoneInfo info = existing.orElseGet(() -> activate(level, lower, null));
		if (activated) {
			player.sendSystemMessage(Component.translatable("message.mlinnotas.waystone.activated", info.name()));
			level.playSound(null, lower, ModSounds.WAYSTONE_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);
		}
		data.setLastUsed(player.getStringUUID(), info);
		if (ServerPlayNetworking.canSend(player, WaystonePayloads.Open.TYPE)) {
			sendList(player);
			ServerPlayNetworking.send(player, new WaystonePayloads.Open(info, activated));
		}
	}

	/** Ativa (sem abrir tela) com o nome dado ou um sorteado; devolve a pedra. */
	public static WaystoneInfo activate(ServerLevel level, BlockPos lower, String name) {
		String dim = dimension(level);
		String clean = WaystoneInfo.cleanName(name);
		WaystoneInfo info = new WaystoneInfo(dim, lower.getX(), lower.getY(), lower.getZ(),
				clean != null ? clean : WaystoneNames.generate(WaystoneNames.seed(dim, lower.getX(), lower.getY(), lower.getZ())));
		WaystoneData data = WaystoneData.get(level.getServer());
		if (!data.activate(info)) {
			return data.registry().get(dim, lower.getX(), lower.getY(), lower.getZ()).orElse(info);
		}
		broadcast(level.getServer());
		return info;
	}

	/** Etiqueta com nome: renomeia (ou ativa ja com o nome). */
	public static boolean renameWithTag(ServerPlayer player, ServerLevel level, BlockPos lower, String name) {
		if (at(level, lower).isEmpty()) {
			WaystoneInfo info = activate(level, lower, name);
			player.sendSystemMessage(Component.translatable("message.mlinnotas.waystone.activated", info.name()));
			WaystoneData.get(level.getServer()).setLastUsed(player.getStringUUID(), info);
			return true;
		}
		return doRename(player, level, lower, name);
	}

	/** Pedido de renomear vindo da tela. */
	public static boolean rename(ServerPlayer player, BlockPos lower, String name) {
		if (!player.blockPosition().closerThan(lower, USE_RANGE)) {
			return false;
		}
		return doRename(player, player.level(), lower, name);
	}

	private static boolean doRename(ServerPlayer player, ServerLevel level, BlockPos lower, String name) {
		WaystoneData data = WaystoneData.get(level.getServer());
		if (!data.rename(dimension(level), lower.getX(), lower.getY(), lower.getZ(), name)) {
			return false;
		}
		String clean = data.registry().get(dimension(level), lower.getX(), lower.getY(), lower.getZ()).map(WaystoneInfo::name).orElse(name);
		player.sendSystemMessage(Component.translatable("message.mlinnotas.waystone.renamed", clean));
		broadcast(level.getServer());
		return true;
	}

	/** A metade de baixo saiu do mundo: a pedra deixa de existir para todos. */
	public static void onRemoved(ServerLevel level, BlockPos lower) {
		WaystoneData data = WaystoneData.get(level.getServer());
		if (data.remove(dimension(level), lower.getX(), lower.getY(), lower.getZ())) {
			broadcast(level.getServer());
		}
	}

	/** Viagem a partir da pedra em {@code sourcePos} (na dimensao do jogador) ate a pedra de chave {@code targetKey}. */
	public static TravelResult travel(ServerPlayer player, BlockPos sourcePos, String targetKey) {
		ServerLevel level = player.level();
		WaystoneData data = WaystoneData.get(level.getServer());
		Optional<WaystoneInfo> source = at(level, sourcePos);
		if (source.isEmpty()) {
			return TravelResult.UNKNOWN_SOURCE;
		}
		if (!player.blockPosition().closerThan(sourcePos, USE_RANGE)) {
			return TravelResult.TOO_FAR;
		}
		Optional<WaystoneInfo> target = data.registry().byKey(targetKey);
		if (target.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.waystone.missing"));
			return TravelResult.UNKNOWN_TARGET;
		}
		if (target.get().key().equals(source.get().key())) {
			return TravelResult.SAME;
		}
		int cost = WaystoneCost.charged(source.get(), target.get(), player.hasInfiniteMaterials());
		if (!WaystoneCost.canAfford(player.experienceLevel, cost)) {
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.waystone.no_xp", cost));
			return TravelResult.NO_XP;
		}
		if (!teleport(player, target.get())) {
			return TravelResult.NO_LEVEL;
		}
		if (cost > 0) {
			player.giveExperienceLevels(-cost);
		}
		data.setLastUsed(player.getStringUUID(), target.get());
		player.sendOverlayMessage(cost > 0
				? Component.translatable("message.mlinnotas.waystone.traveled", target.get().name(), cost)
				: Component.translatable("message.mlinnotas.waystone.traveled_free", target.get().name()));
		return TravelResult.OK;
	}

	/** Pergaminho de Retorno: volta para a ultima pedra usada. Devolve true se teleportou (o pergaminho e gasto). */
	public static boolean useScroll(ServerPlayer player) {
		WaystoneData data = WaystoneData.get(player.level().getServer());
		Optional<WaystoneInfo> last = data.registry().lastUsed(player.getStringUUID());
		if (last.isEmpty()) {
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.waystone.scroll_none"));
			return false;
		}
		if (!teleport(player, last.get())) {
			return false;
		}
		player.sendOverlayMessage(Component.translatable("message.mlinnotas.waystone.scroll_used", last.get().name()));
		return true;
	}

	public static ServerLevel levelOf(MinecraftServer server, String dimension) {
		Identifier id = Identifier.tryParse(dimension);
		return id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
	}

	/** Teleporta para um lugar livre ao lado da pedra (ou em cima dela). */
	public static boolean teleport(ServerPlayer player, WaystoneInfo target) {
		ServerLevel level = levelOf(player.level().getServer(), target.dimension());
		if (level == null) {
			return false;
		}
		BlockPos lower = new BlockPos(target.x(), target.y(), target.z());
		BlockPos spot = safeSpot(level, lower);
		level.playSound(null, player.blockPosition(), ModSounds.WAYSTONE_TRAVEL, SoundSource.PLAYERS, 0.8F, 1.0F);
		boolean ok = player.teleportTo(level, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, Set.of(), player.getYRot(), player.getXRot(), true);
		if (ok) {
			level.playSound(null, spot, ModSounds.WAYSTONE_TRAVEL, SoundSource.PLAYERS, 0.8F, 1.0F);
		}
		return ok;
	}

	static BlockPos safeSpot(ServerLevel level, BlockPos lower) {
		for (Direction dir : new Direction[] {Direction.SOUTH, Direction.NORTH, Direction.EAST, Direction.WEST}) {
			for (int dy : new int[] {0, 1, -1}) {
				BlockPos feet = lower.relative(dir).above(dy);
				if (free(level, feet) && free(level, feet.above()) && level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP)) {
					return feet;
				}
			}
		}
		return level.getBlockState(lower.above()).is(ModBlocks.WAYSTONE) ? lower.above(2) : lower.above();
	}

	private static boolean free(ServerLevel level, BlockPos pos) {
		return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
	}

	public static void sendList(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, WaystonePayloads.Sync.TYPE)) {
			ServerPlayNetworking.send(player, new WaystonePayloads.Sync(WaystoneData.get(player.level().getServer()).registry().all()));
		}
	}

	public static void broadcast(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sendList(player);
		}
	}
}
