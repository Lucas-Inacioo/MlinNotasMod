package com.mlin.notas.client.waystone;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.minimap.MinimapHud;
import com.mlin.notas.minimap.Waypoint;
import com.mlin.notas.waystone.WaystoneInfo;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.DoubleToIntFunction;

/** Pedras de teleporte ativadas no minimapa (HUD) e no mapa grande (nome ao passar o mouse). */
public final class WaystoneMapIcons {
	public static final Identifier ICON = MlinNotas.id("textures/item/waystone.png");

	private WaystoneMapIcons() {
	}

	private static List<Waypoint> asWaypoints(String dimension) {
		return WaystoneClient.inDimension(dimension).stream()
				.map(w -> new Waypoint(w.name(), w.x(), w.y(), w.z(), 0xFFFFFFFF, w.dimension()))
				.toList();
	}

	/** HUD: mesmo desenho dos waypoints (so as que estao dentro do minimapa, sem prender na borda). */
	public static void drawHud(GuiGraphicsExtractor graphics, String dimension, int x, int y, int size, float zoom, double px, double pz) {
		MinimapHud.drawWaypoints(graphics, asWaypoints(dimension), x, y, size, zoom, px, pz, ICON, false);
	}

	/** Mapa grande: icone de 10 px; devolve o nome da pedra sob o mouse (ou null). */
	public static Component drawOnWorldMap(GuiGraphicsExtractor graphics, Font font, String dimension, DoubleToIntFunction screenX,
			DoubleToIntFunction screenZ, int mapWidth, int mapHeight, int mouseX, int mouseY) {
		int icon = 10;
		WaystoneInfo hovered = null;
		for (WaystoneInfo w : WaystoneClient.inDimension(dimension)) {
			int sx = screenX.applyAsInt(w.x() + 0.5);
			int sy = screenZ.applyAsInt(w.z() + 0.5);
			if (sx < -icon || sy < -icon || sx > mapWidth + icon || sy > mapHeight + icon) {
				continue;
			}
			graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, sx - icon / 2, sy - icon / 2, 0, 0, icon, icon, 16, 16, 16, 16);
			if (Math.abs(mouseX - sx) <= icon / 2 && Math.abs(mouseY - sy) <= icon / 2) {
				hovered = w;
			}
		}
		if (hovered == null) {
			return null;
		}
		return Component.literal(hovered.name()).withColor(0xC890FF)
				.append(Component.literal(" (" + hovered.x() + ", " + hovered.y() + ", " + hovered.z() + ")").withColor(0xA0A0A0));
	}
}
