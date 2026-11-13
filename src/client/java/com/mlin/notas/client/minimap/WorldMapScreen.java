package com.mlin.notas.client.minimap;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.minimap.Waypoint;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

import java.util.List;

/**
 * Mapa em tela cheia: arrastar com o mouse, zoom com a roda, botao direito cria waypoint,
 * painel a direita com a lista de waypoints (ir, editar, remover) e acesso as opcoes.
 */
public class WorldMapScreen extends Screen {
	/** Tamanho (blocos) da textura do mapa grande. */
	public static final int TEXTURE_SIZE = 1536;
	public static final int PANEL_WIDTH = 150;
	public static final int ROW_HEIGHT = 22;
	private static final float MIN_ZOOM = 0.25F;
	private static final float MAX_ZOOM = 8.0F;

	private static MapTexture texture;

	private final Screen parent;
	private double centerX;
	private double centerZ;
	private float zoom = 1.0F;
	private int page;
	private List<Waypoint> waypoints = List.of();
	private boolean dragging;

	public WorldMapScreen(Screen parent) {
		super(Component.translatable("gui.mlinnotas.map.title"));
		this.parent = parent;
		LocalPlayer player = minecraftPlayer();
		if (player != null) {
			centerX = player.getX();
			centerZ = player.getZ();
		}
	}

	private static LocalPlayer minecraftPlayer() {
		return net.minecraft.client.Minecraft.getInstance().player;
	}

	private int mapWidth() {
		return width - PANEL_WIDTH;
	}

	private int mapHeight() {
		return height;
	}

	/** Recarrega a lista de waypoints e reconstroi os botoes (apos criar/editar/remover). */
	public void refresh() {
		rebuildWidgets();
	}

	@Override
	protected void init() {
		waypoints = MinimapClient.waypoints().inDimension(MinimapClient.currentDimension(minecraft));
		int panelX = mapWidth() + 6;
		int rows = Math.max(1, (height - 90) / ROW_HEIGHT);
		int pages = Math.max(1, (waypoints.size() + rows - 1) / rows);
		page = Math.min(page, pages - 1);

		int y = 26;
		for (int i = page * rows; i < Math.min(waypoints.size(), (page + 1) * rows); i++) {
			Waypoint w = waypoints.get(i);
			String label = w.name().length() > 12 ? w.name().substring(0, 11) + "…" : w.name();
			addRenderableWidget(Button.builder(Component.literal(label).withColor(w.color() & 0xFFFFFF), b -> centerOn(w))
					.bounds(panelX, y, 78, 20)
					.tooltip(Tooltip.create(Component.literal(w.name() + " (" + w.x() + ", " + w.y() + ", " + w.z() + ")")))
					.build());
			addRenderableWidget(Button.builder(Component.translatable("gui.mlinnotas.map.teleport"), b -> teleport(w))
					.bounds(panelX + 80, y, 20, 20)
					.tooltip(Tooltip.create(Component.translatable("gui.mlinnotas.map.teleport.tooltip")))
					.build());
			addRenderableWidget(Button.builder(Component.translatable("gui.mlinnotas.map.edit"), b ->
							minecraft.gui.setScreen(WaypointEditScreen.edit(this, w)))
					.bounds(panelX + 102, y, 20, 20).build());
			addRenderableWidget(Button.builder(Component.translatable("gui.mlinnotas.map.remove"), b -> {
				MinimapClient.waypoints().remove(w);
				refresh();
			}).bounds(panelX + 124, y, 20, 20).build());
			y += ROW_HEIGHT;
		}

		int bottom = height - 26;
		if (pages > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), b -> {
				page = (page + pages - 1) % pages;
				refresh();
			}).bounds(panelX, bottom - 24, 20, 20).build());
			addRenderableWidget(Button.builder(Component.literal(">"), b -> {
				page = (page + 1) % pages;
				refresh();
			}).bounds(panelX + 124, bottom - 24, 20, 20).build());
		}
		addRenderableWidget(Button.builder(Component.translatable("gui.mlinnotas.map.new_waypoint"), b -> {
			LocalPlayer player = minecraft.player;
			BlockPos pos = player == null ? BlockPos.ZERO : player.blockPosition();
			minecraft.gui.setScreen(WaypointEditScreen.createNew(this, pos, MinimapClient.currentDimension(minecraft)));
		}).bounds(panelX, bottom, 70, 20).build());
		addRenderableWidget(Button.builder(Component.translatable("gui.mlinnotas.map.options"), b ->
				minecraft.gui.setScreen(new MinimapOptionsScreen(this))).bounds(panelX + 74, bottom, 70, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("gui.mlinnotas.map.center"), b -> {
			LocalPlayer player = minecraft.player;
			if (player != null) {
				centerX = player.getX();
				centerZ = player.getZ();
			}
		}).bounds(4, height - 24, 60, 20).build());
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(68, height - 24, 60, 20).build());
	}

	private void centerOn(Waypoint w) {
		centerX = w.x() + 0.5;
		centerZ = w.z() + 0.5;
	}

	private void teleport(Waypoint w) {
		MinimapClient.requestTeleport(w.x() + 0.5, w.y(), w.z() + 0.5);
		onClose();
	}

	@Override
	public void onClose() {
		minecraft.gui.setScreen(parent);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static MapTexture texture() {
		if (texture == null) {
			texture = new MapTexture(MlinNotas.id("minimap_world"), TEXTURE_SIZE);
		}
		texture.ensureRegistered();
		return texture;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		int mw = mapWidth();
		int mh = mapHeight();
		graphics.fill(0, 0, width, height, 0xFF000000);

		if (minecraft.level != null) {
			float effectiveZoom = effectiveZoom();
			MapTexture tex = texture();
			// recentra a textura quando a vista se afasta do centro dela
			int cx = tex.centerX();
			int cz = tex.centerZ();
			boolean far = cx == Integer.MIN_VALUE || Math.abs(centerX - cx) > TEXTURE_SIZE / 6 || Math.abs(centerZ - cz) > TEXTURE_SIZE / 6;
			tex.update(MinimapClient.cache(), far ? Mth.floor(centerX) : cx, far ? Mth.floor(centerZ) : cz, false);

			float visibleW = mw / effectiveZoom;
			float visibleH = mh / effectiveZoom;
			float u = (float) (centerX - tex.originX()) - visibleW / 2.0F;
			float v = (float) (centerZ - tex.originZ()) - visibleH / 2.0F;
			graphics.enableScissor(0, 0, mw, mh);
			graphics.blit(RenderPipelines.GUI_TEXTURED, tex.id(), 0, 0, u, v, mw, mh, Math.round(visibleW), Math.round(visibleH), tex.size(), tex.size());

			drawWaypoints(graphics, effectiveZoom, mouseX, mouseY);
			Entity hoveredEntity = drawEntities(graphics, effectiveZoom, mouseX, mouseY);
			drawPlayers(graphics, effectiveZoom);
			graphics.disableScissor();
			if (hoveredEntity != null) {
				graphics.setTooltipForNextFrame(font, hoveredEntity.getDisplayName(), mouseX, mouseY);
			}

			String coords = Component.translatable("gui.mlinnotas.map.cursor", blockX(mouseX), blockZ(mouseY)).getString();
			graphics.text(font, coords, 4, 4, 0xFFFFFFFF);
			graphics.text(font, Component.translatable("gui.mlinnotas.map.zoom", String.format("%.2f", effectiveZoom)), 4, 4 + font.lineHeight + 2, 0xFFC0C0C0);
		}

		graphics.fill(mw, 0, width, height, 0xFF202020);
		graphics.fill(mw, 0, mw + 1, height, 0xFF606060);
		graphics.centeredText(font, Component.translatable("gui.mlinnotas.map.waypoints"), mw + PANEL_WIDTH / 2, 10, 0xFFFFFFFF);
		if (waypoints.isEmpty()) {
			graphics.centeredText(font, Component.translatable("gui.mlinnotas.map.no_waypoints"), mw + PANEL_WIDTH / 2, 40, 0xFF909090);
		}
	}

	private void drawWaypoints(GuiGraphicsExtractor graphics, float effectiveZoom, int mouseX, int mouseY) {
		int icon = 10;
		Waypoint hovered = null;
		for (Waypoint w : waypoints) {
			int sx = screenX(w.x() + 0.5, effectiveZoom);
			int sy = screenZ(w.z() + 0.5, effectiveZoom);
			if (sx < -icon || sy < -icon || sx > mapWidth() + icon || sy > mapHeight() + icon) {
				continue;
			}
			graphics.blit(RenderPipelines.GUI_TEXTURED, MinimapHud.WAYPOINT_ICON, sx - icon / 2, sy - icon, 0, 0, icon, icon, 16, 16, 16, 16, w.color());
			if (Math.abs(mouseX - sx) <= icon / 2 && mouseY >= sy - icon && mouseY <= sy) {
				hovered = w;
			}
		}
		if (hovered != null) {
			graphics.setTooltipForNextFrame(font, Component.literal(hovered.name() + " (" + hovered.x() + ", " + hovered.y() + ", " + hovered.z() + ")"), mouseX, mouseY);
		}
	}

	private @org.jspecify.annotations.Nullable Entity drawEntities(GuiGraphicsExtractor graphics, float effectiveZoom, int mouseX, int mouseY) {
		if (minecraft.player == null) {
			return null;
		}
		return EntityMarkers.draw(graphics, minecraft.level, minecraft.player, MinimapClient.options(), new EntityMarkers.Projection() {
			@Override
			public double screenX(double worldX) {
				return mapWidth() / 2.0 + (worldX - centerX) * effectiveZoom;
			}

			@Override
			public double screenY(double worldZ) {
				return mapHeight() / 2.0 + (worldZ - centerZ) * effectiveZoom;
			}
		}, 0, 0, mapWidth(), mapHeight(), 10, mouseX, mouseY);
	}

	private void drawPlayers(GuiGraphicsExtractor graphics, float effectiveZoom) {
		for (Entity entity : minecraft.level.players()) {
			int sx = screenX(entity.getX(), effectiveZoom);
			int sy = screenZ(entity.getZ(), effectiveZoom);
			if (sx < 0 || sy < 0 || sx > mapWidth() || sy > mapHeight()) {
				continue;
			}
			if (entity == minecraft.player) {
				MinimapHud.drawArrow(graphics, sx, sy, entity.getYRot());
			} else {
				graphics.centeredText(font, entity.getName(), sx, sy + 7, 0xFFFFFFFF);
			}
		}
	}

	/** Zoom limitado para que a area visivel caiba na textura. */
	private float effectiveZoom() {
		float min = Math.max(MIN_ZOOM, Math.max(mapWidth(), mapHeight()) / (float) TEXTURE_SIZE);
		return Mth.clamp(zoom, min, MAX_ZOOM);
	}

	private int screenX(double blockX, float effectiveZoom) {
		return mapWidth() / 2 + (int) Math.round((blockX - centerX) * effectiveZoom);
	}

	private int screenZ(double blockZ, float effectiveZoom) {
		return mapHeight() / 2 + (int) Math.round((blockZ - centerZ) * effectiveZoom);
	}

	private int blockX(double screenX) {
		return Mth.floor(centerX + (screenX - mapWidth() / 2.0) / effectiveZoom());
	}

	private int blockZ(double screenY) {
		return Mth.floor(centerZ + (screenY - mapHeight() / 2.0) / effectiveZoom());
	}

	private boolean inMap(double mx, double my) {
		return mx >= 0 && mx < mapWidth() && my >= 0 && my < mapHeight() - 28;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}
		if (!inMap(event.x(), event.y())) {
			return false;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
			int bx = blockX(event.x());
			int bz = blockZ(event.y());
			int by = minecraft.player == null ? 64 : minecraft.player.blockPosition().getY();
			if (minecraft.level != null && minecraft.level.hasChunk(bx >> 4, bz >> 4)) {
				by = minecraft.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, bx, bz);
			}
			minecraft.gui.setScreen(WaypointEditScreen.createNew(this, new BlockPos(bx, by, bz), MinimapClient.currentDimension(minecraft)));
			return true;
		}
		if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			dragging = true;
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		if (dragging && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
			float effectiveZoom = effectiveZoom();
			centerX -= dx / effectiveZoom;
			centerZ -= dy / effectiveZoom;
			return true;
		}
		return super.mouseDragged(event, dx, dy);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (super.mouseScrolled(x, y, scrollX, scrollY)) {
			return true;
		}
		if (!inMap(x, y) || scrollY == 0) {
			return false;
		}
		float factor = scrollY > 0 ? 1.25F : 0.8F;
		zoom = Mth.clamp(zoom * factor, MIN_ZOOM, MAX_ZOOM);
		return true;
	}
}
