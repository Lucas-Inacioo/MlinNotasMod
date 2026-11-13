package com.mlin.notas.client.minimap;

import com.mlin.notas.minimap.MinimapOptions;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Desenha as entidades no minimapa e no mapa grande com icones reconheciveis:
 * rosto do jogador, ovo de spawn do mob (cor propria de cada mob), o proprio item para itens no chao.
 * Cada icone tem uma moldura na cor da categoria (vermelho = hostil, verde = passivo, roxo = aldeao...).
 * Entidades sem ovo de spawn caem para um ponto colorido.
 */
public final class EntityMarkers {
	/** Converte coordenadas do mundo para a tela. */
	public interface Projection {
		double screenX(double worldX);

		double screenY(double worldZ);
	}

	private static final Map<EntityType<?>, ItemStack> EGGS = new HashMap<>();

	private EntityMarkers() {
	}

	/**
	 * Desenha as entidades visiveis na area [x0,x1)x[y0,y1). Devolve a entidade sob o mouse (ou null),
	 * para a tela mostrar o nome.
	 */
	public static @Nullable Entity draw(GuiGraphicsExtractor graphics, ClientLevel level, LocalPlayer self, MinimapOptions options,
			Projection projection, int x0, int y0, int x1, int y1, int iconSize, double mouseX, double mouseY) {
		Entity hovered = null;
		int half = iconSize / 2;
		for (Entity entity : level.entitiesForRendering()) {
			if (entity == self || !entity.isAlive() || entity.isInvisible()) {
				continue;
			}
			int color = MinimapHud.colorFor(entity, options);
			if (color == 0) {
				continue;
			}
			int sx = (int) Math.round(projection.screenX(entity.getX()));
			int sy = (int) Math.round(projection.screenY(entity.getZ()));
			if (sx < x0 - half || sy < y0 - half || sx >= x1 + half || sy >= y1 + half) {
				continue;
			}
			drawIcon(graphics, entity, sx - half, sy - half, iconSize, color);
			if (Math.abs(mouseX - sx) <= half && Math.abs(mouseY - sy) <= half) {
				hovered = entity;
			}
		}
		return hovered;
	}

	/** Icone de uma entidade com moldura de 1px na cor da categoria. */
	public static void drawIcon(GuiGraphicsExtractor graphics, Entity entity, int x, int y, int size, int frameColor) {
		graphics.fill(x - 1, y - 1, x + size + 1, y + size + 1, frameColor);
		graphics.fill(x, y, x + size, y + size, 0xC0000000);
		if (entity instanceof AbstractClientPlayer player) {
			PlayerFaceExtractor.extractRenderState(graphics, player.getSkin(), x, y, size);
			return;
		}
		ItemStack icon = iconFor(entity);
		if (icon.isEmpty()) {
			int dot = Math.max(2, size / 2);
			int dx = x + (size - dot) / 2;
			int dy = y + (size - dot) / 2;
			graphics.fill(dx, dy, dx + dot, dy + dot, frameColor);
			return;
		}
		float scale = size / 16.0F;
		graphics.pose().pushMatrix();
		graphics.pose().translate(x, y);
		graphics.pose().scale(scale, scale);
		graphics.item(icon, 0, 0);
		graphics.pose().popMatrix();
	}

	static ItemStack iconFor(Entity entity) {
		if (entity instanceof ItemEntity itemEntity) {
			return itemEntity.getItem();
		}
		return EGGS.computeIfAbsent(entity.getType(), type -> SpawnEggItem.byId(type)
				.map(holder -> new ItemStack(holder.value()))
				.orElse(ItemStack.EMPTY));
	}
}
