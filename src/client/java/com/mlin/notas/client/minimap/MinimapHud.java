package com.mlin.notas.client.minimap;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.minimap.MinimapOptions;
import com.mlin.notas.minimap.Waypoint;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Desenho do minimapa no HUD (canto superior direito). Norte para cima.
 */
public final class MinimapHud {
	public static final Identifier ARROW = MlinNotas.id("textures/item/map_arrow.png");
	public static final Identifier WAYPOINT_ICON = MlinNotas.id("textures/item/waypoint.png");
	public static final int MARGIN = 8;
	public static final int BORDER = 0xFF202020;
	public static final int BACKGROUND = 0xFF000000;

	public static final int COLOR_PLAYER = 0xFFFFFFFF;
	public static final int COLOR_HOSTILE = 0xFFFF4040;
	public static final int COLOR_PASSIVE = 0xFF40FF40;
	public static final int COLOR_ITEM = 0xFFFFFF40;
	public static final int COLOR_VILLAGER = 0xFFC060FF;

	private MinimapHud() {
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		MinimapOptions options = MinimapClient.options();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (!options.enabled || level == null || player == null || minecraft.getDebugOverlay().showDebugScreen()) {
			return;
		}
		// Com uma tela aberta (inventario, bau, NEI...) o minimapa sairia por cima dos widgets; o chat continua mostrando.
		if (minecraft.gui.screen() != null && !(minecraft.gui.screen() instanceof net.minecraft.client.gui.screens.ChatScreen)) {
			return;
		}
		int size = options.size;
		float zoom = options.zoom;
		int screenWidth = minecraft.getWindow().getGuiScaledWidth();
		int x = screenWidth - size - MARGIN;
		int y = MARGIN;

		float partial = delta.getGameTimeDeltaPartialTick(true);
		double px = Mth.lerp(partial, player.xOld, player.getX());
		double pz = Mth.lerp(partial, player.zOld, player.getZ());

		MapTexture texture = MinimapClient.hudTexture();
		texture.update(MinimapClient.cache(), Mth.floor(px), Mth.floor(pz), false);

		graphics.fill(x - 2, y - 2, x + size + 2, y + size + 2, BORDER);
		graphics.fill(x, y, x + size, y + size, BACKGROUND);
		graphics.enableScissor(x, y, x + size, y + size);

		drawTerrain(graphics, texture, x, y, size, zoom, px, pz);
		drawEntities(graphics, level, player, options, x, y, size, zoom, px, pz);
		if (options.showWaypoints) {
			drawWaypoints(graphics, MinimapClient.waypoints().inDimension(MinimapClient.currentDimension(minecraft)), x, y, size, zoom, px, pz);
		}
		com.mlin.notas.client.waystone.WaystoneMapIcons.drawHud(graphics, MinimapClient.currentDimension(minecraft), x, y, size, zoom, px, pz);
		drawArrow(graphics, x + size / 2.0F, y + size / 2.0F, player.getViewYRot(partial));
		graphics.disableScissor();

		if (options.showCoordinates) {
			drawInfo(graphics, minecraft, player, x, y + size + 4, size);
		}
	}

	/** Blit da regiao visivel da textura (1 pixel de textura = 1 bloco) escalada pelo zoom. */
	static void drawTerrain(GuiGraphicsExtractor graphics, MapTexture texture, int x, int y, int size, float zoom, double px, double pz) {
		float visible = size / zoom;
		float u = (float) (px - texture.originX()) - visible / 2.0F;
		float v = (float) (pz - texture.originZ()) - visible / 2.0F;
		int src = Math.round(visible);
		graphics.blit(RenderPipelines.GUI_TEXTURED, texture.id(), x, y, u, v, size, size, src, src, texture.size(), texture.size());
	}

	static void drawEntities(GuiGraphicsExtractor graphics, ClientLevel level, LocalPlayer player, MinimapOptions options,
							 int x, int y, int size, float zoom, double px, double pz) {
		if (!options.showPlayers && !options.showHostiles && !options.showPassives && !options.showItems && !options.showVillagers) {
			return;
		}
		int icon = size >= 80 ? 8 : 6;
		double cx = x + size / 2.0;
		double cy = y + size / 2.0;
		EntityMarkers.draw(graphics, level, player, options, new EntityMarkers.Projection() {
			@Override
			public double screenX(double worldX) {
				return cx + (worldX - px) * zoom;
			}

			@Override
			public double screenY(double worldZ) {
				return cy + (worldZ - pz) * zoom;
			}
		}, x, y, x + size, y + size, icon, -1, -1);
	}

	/** Cor do ponto para a entidade conforme as opcoes, ou 0 para nao desenhar. */
	public static int colorFor(Entity entity, MinimapOptions options) {
		if (entity instanceof Player) {
			return options.showPlayers ? COLOR_PLAYER : 0;
		}
		if (entity instanceof ItemEntity) {
			return options.showItems ? COLOR_ITEM : 0;
		}
		if (entity instanceof AbstractVillager) {
			return options.showVillagers ? COLOR_VILLAGER : 0;
		}
		MobCategory category = entity.getType().getCategory();
		if (category == MobCategory.MONSTER) {
			return options.showHostiles ? COLOR_HOSTILE : 0;
		}
		if (category == MobCategory.CREATURE || category == MobCategory.AMBIENT || category == MobCategory.WATER_CREATURE
				|| category == MobCategory.WATER_AMBIENT || category == MobCategory.AXOLOTLS || category == MobCategory.UNDERGROUND_WATER_CREATURE) {
			return options.showPassives ? COLOR_PASSIVE : 0;
		}
		return 0;
	}

	static void drawWaypoints(GuiGraphicsExtractor graphics, List<Waypoint> waypoints, int x, int y, int size, float zoom, double px, double pz) {
		drawWaypoints(graphics, waypoints, x, y, size, zoom, px, pz, WAYPOINT_ICON, true);
	}

	/** Icones no minimapa; {@code clampToEdge}: os de fora ficam presos na borda (senao nao aparecem). */
	public static void drawWaypoints(GuiGraphicsExtractor graphics, List<Waypoint> waypoints, int x, int y, int size, float zoom, double px, double pz,
			Identifier iconTexture, boolean clampToEdge) {
		int half = size / 2;
		int icon = 8;
		for (Waypoint w : waypoints) {
			double dx = (w.x() + 0.5 - px) * zoom;
			double dz = (w.z() + 0.5 - pz) * zoom;
			// fora do mapa: prende na borda, mantendo a direcao
			double limit = half - icon / 2.0;
			double scale = Math.max(Math.abs(dx) / limit, Math.abs(dz) / limit);
			if (scale > 1) {
				if (!clampToEdge) {
					continue;
				}
				dx /= scale;
				dz /= scale;
			}
			int sx = x + half + (int) Math.round(dx);
			int sy = y + half + (int) Math.round(dz);
			graphics.blit(RenderPipelines.GUI_TEXTURED, iconTexture, sx - icon / 2, sy - icon / 2, 0, 0, icon, icon, 16, 16, 16, 16, w.color());
		}
	}

	/** Seta do jogador no centro, girada pelo yaw (0 = sul; textura aponta para cima/norte). */
	static void drawArrow(GuiGraphicsExtractor graphics, float cx, float cy, float yaw) {
		int arrow = 12;
		graphics.pose().pushMatrix();
		graphics.pose().translate(cx, cy);
		graphics.pose().rotate((float) Math.toRadians(yaw + 180.0F));
		graphics.blit(RenderPipelines.GUI_TEXTURED, ARROW, -arrow / 2, -arrow / 2, 0, 0, arrow, arrow, 16, 16, 16, 16);
		graphics.pose().popMatrix();
	}

	static void drawInfo(GuiGraphicsExtractor graphics, Minecraft minecraft, LocalPlayer player, int x, int y, int size) {
		BlockPos pos = player.blockPosition();
		String coords = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
		int centerX = x + size / 2;
		graphics.centeredText(minecraft.font, coords, centerX, y, 0xFFFFFFFF);
		Component biome = minecraft.level.getBiome(pos).unwrapKey()
				.map(key -> (Component) Component.translatable(key.identifier().toLanguageKey("biome")))
				.orElse(Component.empty());
		graphics.centeredText(minecraft.font, biome, centerX, y + minecraft.font.lineHeight + 1, 0xFFC0C0C0);
	}
}
