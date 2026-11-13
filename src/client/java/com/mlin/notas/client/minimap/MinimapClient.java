package com.mlin.notas.client.minimap;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.ModKeys;
import com.mlin.notas.minimap.MinimapOptions;
import com.mlin.notas.minimap.TeleportRequestPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Estado do minimapa no cliente: opcoes, cache de tiles, waypoints, texturas e teclas.
 */
public final class MinimapClient {
	/** Tamanho (blocos) da textura do minimapa: maior area visivel (256) + folga de movimento. */
	public static final int HUD_TEXTURE_SIZE = 320;

	private static MinimapOptions options = new MinimapOptions();
	private static final MapTileCache CACHE = new MapTileCache();
	private static final WaypointManager WAYPOINTS = new WaypointManager();
	private static MapTexture hudTexture;
	private static boolean caveActive;

	private MinimapClient() {
	}

	public static MinimapOptions options() {
		return options;
	}

	public static MapTileCache cache() {
		return CACHE;
	}

	public static WaypointManager waypoints() {
		return WAYPOINTS;
	}

	public static boolean isCaveActive() {
		return caveActive;
	}

	/** Textura do HUD (criada sob demanda, no thread de render). */
	public static MapTexture hudTexture() {
		if (hudTexture == null) {
			hudTexture = new MapTexture(MlinNotas.id("minimap_hud"), HUD_TEXTURE_SIZE);
		}
		hudTexture.ensureRegistered();
		return hudTexture;
	}

	public static String currentDimension(Minecraft minecraft) {
		return minecraft.level == null ? "minecraft:overworld" : minecraft.level.dimension().identifier().toString();
	}

	public static void init() {
		options = loadOptions();
		HudElementRegistry.addLast(MlinNotas.id("minimap"), MinimapHud::render);

		ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> CACHE.onChunkLoaded(chunk));
		ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> CACHE.onChunkUnloaded(chunk));
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
			CACHE.clear();
			WAYPOINTS.load(client);
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			WAYPOINTS.unload();
			CACHE.clear();
		});
		ClientTickEvents.END_CLIENT_TICK.register(MinimapClient::tick);
	}

	private static void tick(Minecraft minecraft) {
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null) {
			return;
		}
		handleKeys(minecraft, player);
		if (!options.enabled && !(minecraft.gui.screen() instanceof WorldMapScreen)) {
			return;
		}
		caveActive = switch (options.caveMode) {
			case ON -> true;
			case OFF -> false;
			case AUTO -> !level.canSeeSky(BlockPos.containing(player.getX(), player.getEyeY(), player.getZ()));
		};
		CACHE.tick(level, player, caveActive);
	}

	private static void handleKeys(Minecraft minecraft, LocalPlayer player) {
		while (ModKeys.MAP.consumeClick()) {
			if (minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(new WorldMapScreen(null));
			}
		}
		while (ModKeys.WAYPOINT.consumeClick()) {
			if (minecraft.gui.screen() == null) {
				minecraft.gui.setScreen(WaypointEditScreen.createNew(null, player.blockPosition(), currentDimension(minecraft)));
			}
		}
		while (ModKeys.CAVE_MODE.consumeClick()) {
			options.caveMode = options.caveMode.next();
			saveOptions();
			player.sendOverlayMessage(Component.translatable("message.mlinnotas.minimap.cave_mode",
					Component.translatable("option.mlinnotas.minimap.cave_mode." + options.caveMode.name().toLowerCase())));
		}
		while (ModKeys.MINIMAP_SMALLER.consumeClick()) {
			options.smaller();
			sizeChanged(player);
		}
		while (ModKeys.MINIMAP_LARGER.consumeClick()) {
			options.larger();
			sizeChanged(player);
		}
		while (ModKeys.MINIMAP_ZOOM_OUT.consumeClick()) {
			options.zoomOut();
			zoomChanged(player);
		}
		while (ModKeys.MINIMAP_ZOOM_IN.consumeClick()) {
			options.zoomIn();
			zoomChanged(player);
		}
		while (ModKeys.TOGGLE_MINIMAP.consumeClick()) {
			options.enabled = !options.enabled;
			saveOptions();
			player.sendOverlayMessage(Component.translatable(options.enabled ? "message.mlinnotas.minimap.on" : "message.mlinnotas.minimap.off"));
		}
	}

	private static void sizeChanged(LocalPlayer player) {
		saveOptions();
		player.sendOverlayMessage(Component.translatable("message.mlinnotas.minimap.size", options.size));
	}

	private static void zoomChanged(LocalPlayer player) {
		saveOptions();
		player.sendOverlayMessage(Component.translatable("message.mlinnotas.minimap.zoom", String.valueOf(options.zoom)));
	}

	public static void requestTeleport(double x, double y, double z) {
		if (ClientPlayNetworking.canSend(TeleportRequestPayload.TYPE)) {
			ClientPlayNetworking.send(new TeleportRequestPayload(x, y, z));
		}
	}

	private static Path optionsFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("mlinnotas").resolve("minimap.json");
	}

	static MinimapOptions loadOptions() {
		Path file = optionsFile();
		if (Files.exists(file)) {
			try {
				return MinimapOptions.fromJson(Files.readString(file, StandardCharsets.UTF_8));
			} catch (IOException | RuntimeException e) {
				MlinNotas.LOGGER.warn("Nao consegui ler {}", file, e);
			}
		}
		return new MinimapOptions();
	}

	public static void saveOptions() {
		Path file = optionsFile();
		try {
			Files.createDirectories(file.getParent());
			Files.writeString(file, options.toJson(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			MlinNotas.LOGGER.warn("Nao consegui salvar {}", file, e);
		}
	}
}
