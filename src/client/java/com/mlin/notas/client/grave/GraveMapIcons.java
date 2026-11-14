package com.mlin.notas.client.grave;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.minimap.MinimapHud;
import com.mlin.notas.grave.GraveInfo;
import com.mlin.notas.minimap.Waypoint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.DoubleToIntFunction;

/**
 * Lapides no minimapa (presas na borda quando longe, para guiar ate elas) e no mapa grande (dono, coordenadas e
 * conteudo ao passar o mouse). As do proprio jogador ficam com a cor cheia; as dos outros, acinzentadas.
 */
public final class GraveMapIcons {
	public static final Identifier ICON = MlinNotas.id("textures/gui/grave_marker.png");
	private static final int OTHERS_TINT = 0xFFA8A8B8;

	private GraveMapIcons() {
	}

	private static boolean mine(GraveInfo grave) {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.player != null && minecraft.player.getStringUUID().equals(grave.owner());
	}

	public static void drawHud(GuiGraphicsExtractor graphics, String dimension, int x, int y, int size, float zoom, double px, double pz) {
		List<GraveInfo> graves = GraveClient.inDimension(dimension);
		if (graves.isEmpty()) {
			return;
		}
		// as dos outros primeiro: a propria fica por cima
		List<Waypoint> others = graves.stream().filter(g -> !mine(g))
				.map(g -> new Waypoint(g.ownerName(), g.x(), g.y(), g.z(), OTHERS_TINT, g.dimension())).toList();
		List<Waypoint> own = graves.stream().filter(GraveMapIcons::mine)
				.map(g -> new Waypoint(g.ownerName(), g.x(), g.y(), g.z(), 0xFFFFFFFF, g.dimension())).toList();
		MinimapHud.drawWaypoints(graphics, others, x, y, size, zoom, px, pz, ICON, true);
		MinimapHud.drawWaypoints(graphics, own, x, y, size, zoom, px, pz, ICON, true);
	}

	/** Mapa grande: icone de 12 px; devolve a dica da lapide sob o mouse (ou null). */
	public static Component drawOnWorldMap(GuiGraphicsExtractor graphics, Font font, String dimension, DoubleToIntFunction screenX,
			DoubleToIntFunction screenZ, int mapWidth, int mapHeight, int mouseX, int mouseY) {
		int icon = 12;
		GraveInfo hovered = null;
		for (GraveInfo g : GraveClient.inDimension(dimension)) {
			int sx = screenX.applyAsInt(g.x() + 0.5);
			int sy = screenZ.applyAsInt(g.z() + 0.5);
			if (sx < -icon || sy < -icon || sx > mapWidth + icon || sy > mapHeight + icon) {
				continue;
			}
			graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, sx - icon / 2, sy - icon / 2, 0, 0, icon, icon, 16, 16, 16, 16,
					mine(g) ? 0xFFFFFFFF : OTHERS_TINT);
			if (Math.abs(mouseX - sx) <= icon / 2 && Math.abs(mouseY - sy) <= icon / 2) {
				hovered = g;
			}
		}
		if (hovered == null) {
			return null;
		}
		return Component.translatable("gui.mlinnotas.grave.map_tooltip", hovered.ownerName(), hovered.x(), hovered.y(), hovered.z(),
				hovered.items(), hovered.xp()).withColor(0xE0C8A0);
	}
}
