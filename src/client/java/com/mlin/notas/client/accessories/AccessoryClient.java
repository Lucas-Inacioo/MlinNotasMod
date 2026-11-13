package com.mlin.notas.client.accessories;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.accessories.AccessoryAbility;
import com.mlin.notas.accessories.AccessoryBudget;
import com.mlin.notas.accessories.AccessoryData;
import com.mlin.notas.accessories.AccessoryDef;
import com.mlin.notas.accessories.AccessoryItem;
import com.mlin.notas.accessories.AccessoryPayloads;
import com.mlin.notas.accessories.AccessoryRegistry;
import com.mlin.notas.accessories.AccessoryStage;
import com.mlin.notas.accessories.AccessoryStat;
import com.mlin.notas.accessories.PocketWatch;
import com.mlin.notas.client.mixin.AbstractContainerScreenAccessor;
import com.mlin.notas.client.season.SeasonClient;
import com.mlin.notas.client.skills.SkillClient;
import com.mlin.notas.season.SeasonCommand;
import com.mlin.notas.season.SeasonDate;
import com.mlin.notas.skills.SkillBranch;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Lado cliente dos acessorios: tela do menu, botao (aba) na tela de inventario, aviso de teto do estagio no tooltip,
 * pulo duplo, escalada e o relogio de bolso no canto da tela.
 */
public final class AccessoryClient {
	/** Aba em cima da janela (relativa a leftPos/topPos), igual nas duas telas. */
	static final int BUTTON_X = 4;
	static final int BUTTON_Y = -19;
	static final int BUTTON_W = 22;
	static final int BUTTON_H = 20;

	private static boolean jumpWasDown;
	private static boolean airJumpUsed;

	private AccessoryClient() {
	}

	public static void init() {
		MenuScreens.register(AccessoryRegistry.MENU, AccessoryScreen::new);

		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof InventoryScreen inventoryScreen)) {
				return;
			}
			AbstractContainerScreenAccessor accessor = (AbstractContainerScreenAccessor) inventoryScreen;
			IconButton button = new IconButton(accessor.mlinnotas$leftPos() + BUTTON_X, accessor.mlinnotas$topPos() + BUTTON_Y,
					new ItemStack(AccessoryRegistry.item("gold_ring")), Component.translatable("button.mlinnotas.accessories"), b -> openMenu());
			Screens.getWidgets(screen).add(button);
			// o livro de receitas empurra a janela: acompanha a posicao a cada quadro
			ScreenEvents.beforeExtract(screen).register((s, graphics, mouseX, mouseY, delta) ->
					button.setX(accessor.mlinnotas$leftPos() + BUTTON_X));
		});

		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			if (stack.getItem() instanceof AccessoryItem item && Minecraft.getInstance().player != null) {
				appendCapWarning(item.def(), lines);
			}
		});

		ClientTickEvents.END_CLIENT_TICK.register(AccessoryClient::tick);
		HudElementRegistry.addLast(MlinNotas.id("pocket_watch"), AccessoryClient::renderWatch);
	}

	public static void openMenu() {
		if (ClientPlayNetworking.canSend(AccessoryPayloads.Open.TYPE)) {
			ClientPlayNetworking.send(AccessoryPayloads.Open.INSTANCE);
		}
	}

	static void backToInventory() {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null) {
			minecraft.player.closeContainer();
			minecraft.gui.setScreen(new InventoryScreen(minecraft.player));
		}
	}

	// ---- totais vistos do cliente ----

	public static AccessoryStage stage() {
		return AccessoryStage.fromMiningLevel(SkillClient.progress().level(SkillBranch.MINING));
	}

	public static List<AccessoryDef> equipped() {
		LocalPlayer player = Minecraft.getInstance().player;
		return player == null ? List.of() : AccessoryData.defs(player);
	}

	public static AccessoryBudget.Totals totals() {
		return AccessoryBudget.compute(equipped(), stage());
	}

	/** Linhas vermelhas no tooltip quando o atributo (somado com este item, se ainda nao estiver equipado) passa do teto. */
	static void appendCapWarning(AccessoryDef def, List<Component> lines) {
		List<AccessoryDef> set = new ArrayList<>(equipped());
		if (!set.contains(def)) {
			set.add(def);
		}
		AccessoryBudget.Totals totals = AccessoryBudget.compute(set, stage());
		Component stageName = Component.translatable("accessory.mlinnotas.stage." + totals.stage().id());
		boolean any = false;
		for (AccessoryStat stat : def.stats().keySet()) {
			if (def.stat(stat) > 0 && totals.capped(stat)) {
				lines.add(Component.translatable("accessory.mlinnotas.capped", Component.translatable("accessory.mlinnotas.stat_name." + stat.id()),
						stat.format(stat.cap(totals.stage())), stageName).withStyle(ChatFormatting.RED));
				any = true;
			}
		}
		if (any) {
			lines.add(Component.translatable("accessory.mlinnotas.capped_hint").withStyle(ChatFormatting.DARK_GRAY));
		}
	}

	// ---- pulo duplo e escalada ----

	private static void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		if (player == null) {
			return;
		}
		boolean jumpDown = minecraft.options.keyJump.isDown();
		if (player.onGround() || player.onClimbable() || player.isInWater() || player.getAbilities().flying) {
			airJumpUsed = false;
		}
		List<AccessoryDef> defs = AccessoryData.defs(player);
		boolean doubleJump = has(defs, AccessoryAbility.DOUBLE_JUMP);
		boolean climb = has(defs, AccessoryAbility.CLIMB);
		if (doubleJump && jumpDown && !jumpWasDown && !airJumpUsed && minecraft.gui.screen() == null && !player.onGround()
				&& !player.onClimbable() && !player.isInWater() && !player.isPassenger() && !player.getAbilities().flying
				&& !player.isFallFlying() && !(climb && player.horizontalCollision)) {
			player.jumpFromGround();
			player.resetFallDistance();
			airJumpUsed = true;
			if (ClientPlayNetworking.canSend(AccessoryPayloads.DoubleJump.TYPE)) {
				ClientPlayNetworking.send(AccessoryPayloads.DoubleJump.INSTANCE);
			}
		}
		jumpWasDown = jumpDown;

		if (climb && player.horizontalCollision && !player.getAbilities().flying && !player.isPassenger() && !player.isInWater()
				&& !(player.onGround() && player.isShiftKeyDown())) {
			Vec3 motion = player.getDeltaMovement();
			double y = player.isShiftKeyDown() ? 0.0 : 0.2;
			player.setDeltaMovement(motion.x, y, motion.z);
			player.resetFallDistance();
		}
	}

	private static boolean has(List<AccessoryDef> defs, AccessoryAbility ability) {
		for (AccessoryDef def : defs) {
			if (def != null && def.has(ability)) {
				return true;
			}
		}
		return false;
	}

	// ---- relogio de bolso ----

	private static void renderWatch(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.level == null || minecraft.getDebugOverlay().showDebugScreen()) {
			return;
		}
		if (minecraft.gui.screen() != null && !(minecraft.gui.screen() instanceof ChatScreen)) {
			return;
		}
		if (!has(AccessoryData.defs(player), AccessoryAbility.WATCH)) {
			return;
		}
		long clock = minecraft.level.getOverworldClockTime();
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("hud.mlinnotas.pocket_watch.time", PocketWatch.day(clock), PocketWatch.time(clock)));
		SeasonDate season = SeasonClient.date();
		int seasonColor = 0xFFFFFFFF;
		if (season != null) {
			lines.add(Component.translatable("hud.mlinnotas.season", SeasonCommand.name(season.season()), SeasonCommand.phaseName(season.phase()),
					season.displayDay(), season.lengthDays()));
			seasonColor = SeasonClient.argb(season.season());
		}
		int width = 0;
		for (Component line : lines) {
			width = Math.max(width, minecraft.font.width(line));
		}
		int x = 4;
		int y = 4;
		int lineHeight = minecraft.font.lineHeight + 2;
		graphics.fill(x - 2, y - 2, x + 20 + width + 2, y + lines.size() * lineHeight, 0x90000000);
		graphics.item(new ItemStack(AccessoryRegistry.item("pocket_watch")), x, y - 1 + (lines.size() * lineHeight - 16) / 2);
		for (int i = 0; i < lines.size(); i++) {
			graphics.text(minecraft.font, lines.get(i), x + 20, y + 1 + i * lineHeight, i == 0 ? 0xFFFFFFFF : seasonColor, true);
		}
	}

	/** Botao do tamanho de uma aba com o icone de um item. */
	public static final class IconButton extends Button {
		private final ItemStack icon;

		IconButton(int x, int y, ItemStack icon, Component tooltip, OnPress onPress) {
			super(x, y, BUTTON_W, BUTTON_H, tooltip, onPress, DEFAULT_NARRATION);
			this.icon = icon;
			setTooltip(Tooltip.create(tooltip));
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
			extractDefaultSprite(graphics);
			graphics.item(icon, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
		}
	}
}
