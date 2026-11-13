package com.mlin.notas.client.minimap;

import com.mlin.notas.minimap.Waypoint;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Tela de criar/editar waypoint: nome, coordenadas e cor.
 */
public class WaypointEditScreen extends Screen {
	private final Screen parent;
	private final Waypoint existing;
	private final String dimension;
	private int x;
	private int y;
	private int z;
	private int color;
	private String name;

	private EditBox nameBox;
	private EditBox xBox;
	private EditBox yBox;
	private EditBox zBox;
	private Button colorButton;

	private WaypointEditScreen(Screen parent, Waypoint existing, String name, int x, int y, int z, int color, String dimension) {
		super(Component.translatable(existing == null ? "gui.mlinnotas.waypoint.new" : "gui.mlinnotas.waypoint.edit"));
		this.parent = parent;
		this.existing = existing;
		this.name = name;
		this.x = x;
		this.y = y;
		this.z = z;
		this.color = color;
		this.dimension = dimension;
	}

	public static WaypointEditScreen createNew(Screen parent, BlockPos pos, String dimension) {
		int count = MinimapClient.waypoints().store().size() + 1;
		return new WaypointEditScreen(parent, null, "Waypoint " + count, pos.getX(), pos.getY(), pos.getZ(), Waypoint.PALETTE[0], dimension);
	}

	public static WaypointEditScreen edit(Screen parent, Waypoint waypoint) {
		return new WaypointEditScreen(parent, waypoint, waypoint.name(), waypoint.x(), waypoint.y(), waypoint.z(), waypoint.color(), waypoint.dimension());
	}

	@Override
	protected void init() {
		int centerX = width / 2;
		int top = height / 2 - 60;

		nameBox = new EditBox(font, centerX - 100, top, 200, 20, Component.translatable("gui.mlinnotas.waypoint.name"));
		nameBox.setMaxLength(32);
		nameBox.setValue(name);
		nameBox.setResponder(value -> name = value);
		addRenderableWidget(nameBox);

		xBox = coordBox(centerX - 100, top + 36, x, v -> x = v);
		yBox = coordBox(centerX - 32, top + 36, y, v -> y = v);
		zBox = coordBox(centerX + 36, top + 36, z, v -> z = v);

		colorButton = Button.builder(colorLabel(), b -> {
			color = Waypoint.nextColor(color);
			b.setMessage(colorLabel());
		}).bounds(centerX - 100, top + 64, 200, 20).build();
		addRenderableWidget(colorButton);

		int buttonsY = top + 96;
		addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> save()).bounds(centerX - 100, buttonsY, 64, 20).build());
		if (existing != null) {
			addRenderableWidget(Button.builder(Component.translatable("gui.mlinnotas.waypoint.delete"), b -> delete())
					.bounds(centerX - 32, buttonsY, 64, 20).build());
		}
		addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> onClose()).bounds(centerX + 36, buttonsY, 64, 20).build());
		setInitialFocus(nameBox);
	}

	private EditBox coordBox(int bx, int by, int initial, java.util.function.IntConsumer setter) {
		EditBox box = new EditBox(font, bx, by, 64, 20, Component.literal("coord"));
		box.setMaxLength(9);
		box.setValue(Integer.toString(initial));
		box.setResponder(value -> {
			try {
				setter.accept(Integer.parseInt(value.trim()));
			} catch (NumberFormatException ignored) {
				// mantem o ultimo valor valido
			}
		});
		addRenderableWidget(box);
		return box;
	}

	private Component colorLabel() {
		return Component.translatable("gui.mlinnotas.waypoint.color").append(": ")
				.append(Component.literal("■■■").withColor(color & 0xFFFFFF));
	}

	private void save() {
		String trimmed = name == null ? "" : name.strip();
		if (trimmed.isEmpty()) {
			nameBox.setFocused(true);
			return;
		}
		Waypoint waypoint = new Waypoint(trimmed, x, y, z, color, dimension);
		if (existing == null) {
			MinimapClient.waypoints().add(waypoint);
		} else {
			MinimapClient.waypoints().replace(existing, waypoint);
		}
		onClose();
	}

	private void delete() {
		if (existing != null) {
			MinimapClient.waypoints().remove(existing);
		}
		onClose();
	}

	@Override
	public void onClose() {
		if (parent instanceof WorldMapScreen map) {
			map.refresh();
		}
		minecraft.gui.setScreen(parent);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
		super.extractBackground(graphics, mouseX, mouseY, a);
		int centerX = width / 2;
		int top = height / 2 - 60;
		graphics.centeredText(font, title, centerX, top - 20, 0xFFFFFFFF);
		graphics.text(font, Component.translatable("gui.mlinnotas.waypoint.name"), centerX - 100, top - 10, 0xFFA0A0A0);
		graphics.text(font, "X", centerX - 100, top + 26, 0xFFA0A0A0);
		graphics.text(font, "Y", centerX - 32, top + 26, 0xFFA0A0A0);
		graphics.text(font, "Z", centerX + 36, top + 26, 0xFFA0A0A0);
	}
}
