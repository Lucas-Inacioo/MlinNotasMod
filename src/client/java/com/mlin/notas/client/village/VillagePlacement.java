package com.mlin.notas.client.village;

import com.mlin.notas.client.ModKeys;
import com.mlin.notas.village.VillageLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Escolha do lugar de uma obra (estilo ferramenta de construcao do MineColonies, escrito do zero): a planta aparece
 * como um "fantasma" no chao seguindo a mira, verde se cabe e vermelho se nao; R ou a roda do mouse giram, botao
 * direito confirma e botao esquerdo cancela. Mirar um lote ja planejado do mesmo tipo encaixa nele. O servidor confere
 * de novo (terreno, acesso) e responde no chat.
 */
public final class VillagePlacement {
	private static boolean active;
	private static String kind;
	private static BlockPos board;
	private static BlockPos center;
	private static VillageLayout layout;
	private static int cw;
	private static int cd;
	private static int door;
	private static int height;
	private static int exclude;
	private static int rot;
	private static int lastSlot = -1;
	/** Ultimo fantasma calculado (x, z relativos, chao) e o lote encaixado (-1 = lugar novo). */
	private static int ghostX;
	private static int ghostZ;
	private static int ghostGround;
	private static int snapped = -1;
	private static boolean hasGhost;
	private static VillageLayout.Problem problem;

	private VillagePlacement() {
	}

	public static boolean active() {
		return active;
	}

	/** Comeca a escolher o lugar da proxima obra do tipo {@code kind} (usa o tamanho do lote livre dele). */
	public static boolean start(String kind, BlockPos board, BlockPos center, VillageLayout layout, com.mlin.notas.village.VillageStyle style) {
		VillageLayout.Lot lot = layout.firstFree(kind);
		if (lot == null) {
			return false;
		}
		VillagePlacement.kind = kind;
		VillagePlacement.board = board;
		VillagePlacement.center = center;
		VillagePlacement.layout = layout;
		cw = lot.cw();
		cd = lot.cd();
		door = lot.door();
		exclude = lot.id();
		rot = lot.rot();
		height = Math.max(3, VillageLayout.canvas(kind, style, lot.seed()).height());
		active = true;
		hasGhost = false;
		lastSlot = -1;
		VillageClient.send(board, "overlay", "");
		return true;
	}

	public static void stop() {
		active = false;
		hasGhost = false;
	}

	/** Gira (testes de cliente). */
	public static void rotate(int times) {
		rot = Math.floorMod(rot + times, 4);
	}

	/** Antes do jogo tratar os cliques: confirma/cancela e impede o uso normal dos botoes. */
	static void startTick(Minecraft mc) {
		if (!active || mc.player == null) {
			return;
		}
		if (mc.gui.screen() != null) {
			return;
		}
		boolean confirm = false;
		boolean cancel = false;
		while (mc.options.keyUse.consumeClick()) {
			confirm = true;
		}
		while (mc.options.keyAttack.consumeClick()) {
			cancel = true;
		}
		mc.options.keyUse.setDown(false);
		mc.options.keyAttack.setDown(false);
		while (ModKeys.VILLAGE_ROTATE.consumeClick()) {
			rotate(1);
		}
		int slot = mc.player.getInventory().getSelectedSlot();
		if (lastSlot >= 0 && slot != lastSlot) {
			// roda do mouse (troca de slot) gira o fantasma e devolve o slot
			int delta = Math.floorMod(slot - lastSlot, 9) <= 4 ? 1 : -1;
			rotate(delta);
			mc.player.getInventory().setSelectedSlot(lastSlot);
		} else {
			lastSlot = slot;
		}
		if (cancel) {
			stop();
			mc.player.sendOverlayMessage(Component.translatable("gui.mlinnotas.village.place.cancelled"));
			return;
		}
		update(mc);
		if (confirm && hasGhost) {
			if (snapped >= 0) {
				VillageClient.send(board, "pick", String.valueOf(snapped));
			} else {
				VillageClient.send(board, "place", kind + ";" + ghostX + ";" + ghostZ + ";" + rot);
			}
			stop();
		}
	}

	/**
	 * Ponto do chao na mira, atravessando folhas (a copa das arvores nao esconde o lote): anda pelo raio da camera ate
	 * ficar abaixo do topo do chao sem folhas. Null = nada a ate 96 blocos.
	 */
	static BlockPos groundHit(Minecraft mc) {
		Vec3 eye = mc.player.getEyePosition(1.0F);
		Vec3 look = mc.player.getViewVector(1.0F);
		for (double t = 0; t <= 96; t += 0.25) {
			Vec3 p = eye.add(look.scale(t));
			int x = (int) Math.floor(p.x);
			int z = (int) Math.floor(p.z);
			int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (p.y < ground) {
				return new BlockPos(x, ground - 1, z);
			}
		}
		HitResult hit = mc.player.pick(96, 1.0F, false);
		return hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK ? block.getBlockPos() : null;
	}

	/** Recalcula o fantasma pela mira. */
	static void update(Minecraft mc) {
		hasGhost = false;
		BlockPos pos = groundHit(mc);
		if (pos == null) {
			return;
		}
		int hx = pos.getX() - center.getX();
		int hz = pos.getZ() - center.getZ();
		VillageLayout current = VillageOverlay.current() != null && VillageOverlay.current().center().equals(center) ? VillageOverlay.current().layout()
				: layout;
		VillageLayout.Lot there = current.lotAt(hx, hz);
		if (there != null && there.kind().equals(kind) && !there.built()) {
			snapped = there.id();
			ghostX = there.x();
			ghostZ = there.z();
			rot = there.rot();
			problem = null;
		} else {
			snapped = -1;
			int w = rot % 2 == 0 ? cw : cd;
			int d = rot % 2 == 0 ? cd : cw;
			ghostX = hx - w / 2;
			ghostZ = hz - d / 2;
			problem = current.geometry(ghostX, ghostZ, rot, cw, cd, exclude);
		}
		// debaixo de copa de arvore: o fantasma fica no chao, nao nas folhas
		ghostGround = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.getX(), pos.getZ()) - 1;
		hasGhost = true;
	}

	/** Fantasma no mundo ({@code END_EXTRACTION}). */
	public static void render() {
		if (!active || !hasGhost) {
			return;
		}
		int w = rot % 2 == 0 ? cw : cd;
		int d = rot % 2 == 0 ? cd : cw;
		int color = problem == null ? VillageOverlay.FREE : VillageOverlay.BLOCKED;
		double x0 = center.getX() + ghostX;
		double z0 = center.getZ() + ghostZ;
		double y = ghostGround + 1.0;
		Gizmos.cuboid(new AABB(x0, y, z0, x0 + w, y + height, z0 + d), GizmoStyle.fill((color & 0x00FFFFFF) | 0x30000000));
		Gizmos.cuboid(new AABB(x0, y, z0, x0 + w, y + height, z0 + d), GizmoStyle.stroke(color, 3.0F)).setAlwaysOnTop();
		int[] front = VillageLayout.Lot.frontOf(ghostX, ghostZ, rot, cw, cd, door);
		Gizmos.arrow(new Vec3(x0 + w / 2.0, y + 0.4, z0 + d / 2.0), new Vec3(center.getX() + front[0] + 0.5, y + 0.4, center.getZ() + front[1] + 0.5),
				color, 4.0F);
		String text = problem == null ? Component.translatable(snapped >= 0 ? "gui.mlinnotas.village.place.snap" : "gui.mlinnotas.village.place.ok")
				.getString() : Component.translatable("gui.mlinnotas.village.place.problem." + problem.name().toLowerCase(java.util.Locale.ROOT)).getString();
		Gizmos.billboardText(text, new Vec3(x0 + w / 2.0, y + height + 0.8, z0 + d / 2.0), TextGizmo.Style.forColorAndCentered(color).withScale(1.4F))
				.setAlwaysOnTop();
	}

	/** Dica no topo da tela enquanto escolhe. */
	static void hud(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft mc = Minecraft.getInstance();
		if (!active || mc.player == null || mc.gui.screen() != null) {
			return;
		}
		int cx = graphics.guiWidth() / 2;
		Component title = Component.translatable("gui.mlinnotas.village.place.title", VillageOverlay.kindName(kind)).withStyle(ChatFormatting.GOLD);
		Component keys = Component.translatable("gui.mlinnotas.village.place.keys", ModKeys.VILLAGE_ROTATE.getTranslatedKeyMessage());
		int w = Math.max(mc.font.width(title), mc.font.width(keys)) + 12;
		int top = graphics.guiHeight() - 92;
		graphics.fill(cx - w / 2, top, cx + w / 2, top + 26, 0xA0000000);
		graphics.centeredText(mc.font, title, cx, top + 3, 0xFFFFFFFF);
		graphics.centeredText(mc.font, keys, cx, top + 14, 0xFFD0D0D0);
	}
}
