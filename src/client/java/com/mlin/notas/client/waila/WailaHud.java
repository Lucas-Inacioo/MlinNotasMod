package com.mlin.notas.client.waila;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.furnace.FurnaceUpgrades;
import com.mlin.notas.harvest.CropHarvester;
import com.mlin.notas.registry.ModItems;
import com.mlin.notas.tier.GearKind;
import com.mlin.notas.tier.Tier;
import com.mlin.notas.waila.ContainerPeekPayload;
import com.mlin.notas.waila.ItemGrid;
import com.mlin.notas.waila.WailaRules;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Caixa no topo central da tela descrevendo o que a mira aponta (estilo WAILA/Jade): bloco (icone, nome, mod,
 * ferramenta exigida, crescimento, upgrade de fornalha, conteudo de containers) ou entidade (vida, armadura, dono).
 */
public final class WailaHud {
	private static final int PADDING = 4;
	private static final int ICON = 16;
	private static final int TOP = 2;
	private static final int BACKGROUND = 0xE0100010;
	private static final int BORDER = 0xFF3A0F7A;
	private static final int WHITE = 0xFFFFFFFF;
	private static final int GREEN = 0xFF55FF55;
	private static final int RED = 0xFFFF5555;
	private static final int GRAY = 0xFFAAAAAA;
	private static final int GOLD = 0xFFFFAA00;
	/** Linhas da grade de conteudo no HUD (o resto vira "+N"). */
	private static final int CONTENT_ROWS = 3;
	/** Vida ate a qual os coracoes sao desenhados (20 coracoes); acima disso so texto. */
	private static final float MAX_DRAWN_HEALTH = 40.0F;

	private static final Identifier HEART_CONTAINER = Identifier.withDefaultNamespace("hud/heart/container");
	private static final Identifier HEART_FULL = Identifier.withDefaultNamespace("hud/heart/full");
	private static final Identifier HEART_HALF = Identifier.withDefaultNamespace("hud/heart/half");
	private static final Identifier ARMOR_FULL = Identifier.withDefaultNamespace("hud/armor_full");

	private static final Map<String, TagKey<Block>> TAGS = new HashMap<>();

	private WailaHud() {
	}

	@FunctionalInterface
	private interface Painter {
		void paint(GuiGraphicsExtractor graphics, Font font, int x, int y);
	}

	/** Uma linha da caixa com tamanho fixo. */
	private record Row(int width, int height, Painter painter) {
		static Row text(Font font, Component text, int color) {
			return new Row(font.width(text), font.lineHeight + 1, (graphics, f, x, y) -> graphics.text(f, text, x, y, color));
		}
	}

	/** O que sera desenhado: icone, titulo, mod e linhas extras. */
	private record Info(ItemStack icon, Component title, Component mod, List<Row> rows) {
	}

	public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
		Minecraft minecraft = Minecraft.getInstance();
		if (!WailaClient.isEnabled() || minecraft.player == null || minecraft.level == null || minecraft.getDebugOverlay().showDebugScreen()) {
			return;
		}
		Screen screen = minecraft.gui.screen();
		if (screen != null && !(screen instanceof ChatScreen)) {
			return;
		}
		Info info = describe(minecraft, minecraft.font);
		if (info == null) {
			return;
		}
		draw(graphics, minecraft.font, info, minecraft.getWindow().getGuiScaledWidth());
	}

	private static void draw(GuiGraphicsExtractor graphics, Font font, Info info, int screenWidth) {
		int iconWidth = info.icon().isEmpty() ? 0 : ICON + 4;
		int contentWidth = Math.max(font.width(info.title()), font.width(info.mod()));
		int rowsHeight = 0;
		for (Row row : info.rows()) {
			contentWidth = Math.max(contentWidth, row.width());
			rowsHeight += row.height();
		}
		int headerHeight = Math.max(iconWidth > 0 ? ICON : 0, font.lineHeight * 2 + 1);
		int width = PADDING * 2 + iconWidth + contentWidth;
		int height = PADDING * 2 + headerHeight + (rowsHeight > 0 ? rowsHeight + 2 : 0);
		int x = (screenWidth - width) / 2;
		int y = TOP;

		graphics.fill(x, y, x + width, y + height, BACKGROUND);
		graphics.fill(x, y, x + width, y + 1, BORDER);
		graphics.fill(x, y + height - 1, x + width, y + height, BORDER);
		graphics.fill(x, y, x + 1, y + height, BORDER);
		graphics.fill(x + width - 1, y, x + width, y + height, BORDER);

		int textX = x + PADDING + iconWidth;
		if (iconWidth > 0) {
			graphics.item(info.icon(), x + PADDING, y + PADDING + (headerHeight - ICON) / 2);
		}
		graphics.text(font, info.title(), textX, y + PADDING, WHITE);
		graphics.text(font, info.mod(), textX, y + PADDING + font.lineHeight + 1, WHITE);
		int rowY = y + PADDING + headerHeight + 2;
		for (Row row : info.rows()) {
			row.painter().paint(graphics, font, textX, rowY);
			rowY += row.height();
		}
	}

	private static Info describe(Minecraft minecraft, Font font) {
		HitResult hit = minecraft.hitResult;
		if (hit == null || hit.getType() == HitResult.Type.MISS) {
			return null;
		}
		if (hit instanceof EntityHitResult entityHit) {
			return describeEntity(minecraft, font, entityHit.getEntity());
		}
		if (hit instanceof BlockHitResult blockHit) {
			return describeBlock(minecraft, font, minecraft.level, blockHit.getBlockPos());
		}
		return null;
	}

	// ---------------------------------------------------------------- blocos

	private static Info describeBlock(Minecraft minecraft, Font font, Level level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (state.isAir()) {
			return null;
		}
		Block block = state.getBlock();
		List<Row> rows = new ArrayList<>();
		rows.add(harvestRow(minecraft, font, level, pos, state));

		IntegerProperty age = CropHarvester.ageProperty(state);
		if (age != null && (block instanceof CropBlock || block instanceof NetherWartBlock || block instanceof CocoaBlock
				|| block instanceof SweetBerryBushBlock || block instanceof StemBlock)) {
			int percent = WailaRules.growthPercent(state.getValue(age), CropHarvester.maxAge(age));
			rows.add(percent >= 100
					? Row.text(font, Component.translatable("hud.mlinnotas.waila.mature"), GREEN)
					: Row.text(font, Component.translatable("hud.mlinnotas.waila.growth", percent), WHITE));
		}
		Row season = seasonRow(font, level, pos, block);
		if (season != null) {
			rows.add(season);
		}

		ContainerPeekPayload contents = level.getBlockEntity(pos) instanceof BaseContainerBlockEntity || level.getBlockEntity(pos) instanceof com.mlin.notas.storage.StorageUnitBlockEntity ? WailaClient.contentsFor(pos) : null;
		if (contents != null) {
			if (contents.furnaceTier() > 0 && contents.furnaceTier() < Tier.values().length) {
				Tier tier = Tier.values()[contents.furnaceTier()];
				Item upgrade = ModItems.furnaceUpgrade(tier);
				Component name = upgrade != null ? new ItemStack(upgrade).getHoverName() : Component.literal(tier.id());
				rows.add(Row.text(font, Component.translatable("hud.mlinnotas.waila.furnace_upgrade", name,
						FurnaceUpgrades.formatMultiplier(tier.furnaceSpeed())), GOLD));
			}
			rows.add(contentsRow(font, contents));
		}
		return new Info(new ItemStack(block.asItem()), block.getName(), modName(BuiltInRegistries.BLOCK.getKey(block).getNamespace()), rows);
	}

	/** Estacoes: se a cultura esta na estacao certa (e se esta numa estufa). Null = planta sem estacao aqui. */
	private static Row seasonRow(Font font, Level level, BlockPos pos, Block block) {
		float multiplier = com.mlin.notas.season.SeasonGrowth.multiplier(level, pos, block);
		if (multiplier < 0) {
			return null;
		}
		com.mlin.notas.season.CropSeasons.Status status = com.mlin.notas.season.CropSeasons.Status.of(multiplier);
		int color = switch (status) {
			case THRIVING, GOOD -> GREEN;
			case SLOW -> GOLD;
			case POOR, DORMANT -> RED;
		};
		com.mlin.notas.season.SeasonDate date = com.mlin.notas.season.SeasonState.current();
		boolean greenhouse = date != null && multiplier >= 1.0F
				&& com.mlin.notas.season.CropSeasons.seasonal(com.mlin.notas.season.SeasonGrowth.profile(block), date,
						com.mlin.notas.season.SeasonGrowth.climate(level, pos)) < 1.0F;
		Component text = Component.translatable(greenhouse ? "hud.mlinnotas.waila.season.greenhouse" : "hud.mlinnotas.waila.season." + status.id(),
				String.format(java.util.Locale.ROOT, "%.2f", multiplier));
		return Row.text(font, text, greenhouse ? GREEN : color);
	}

	private static Row harvestRow(Minecraft minecraft, Font font, Level level, BlockPos pos, BlockState state) {
		if (state.getDestroySpeed(level, pos) < 0) {
			return Row.text(font, Component.translatable("hud.mlinnotas.waila.unbreakable"), GRAY);
		}
		Tier toolLocked = com.mlin.notas.gating.ToolLocks.missing(minecraft.player, minecraft.player.getMainHandItem());
		if (toolLocked != null) {
			// ferramenta acima da Mineracao do mundo: nao quebra nada
			return Row.text(font, Component.literal("✘ ").append(com.mlin.notas.gating.ToolLocks.message(toolLocked)), RED);
		}
		boolean canHarvest = !state.requiresCorrectToolForDrops() || minecraft.player.getMainHandItem().isCorrectToolForDrops(state);
		String mark = canHarvest ? "✔ " : "✘ ";
		int color = canHarvest ? GREEN : RED;
		if (!state.requiresCorrectToolForDrops()) {
			return Row.text(font, Component.literal(mark).append(Component.translatable("hud.mlinnotas.waila.harvestable")), color);
		}
		Tier tier = WailaRules.requiredTier(tag -> hasTag(state, tag));
		GearKind kind = WailaRules.toolKind(tag -> hasTag(state, tag));
		Component tool;
		if (kind != null) {
			String path = kind.itemName(tier);
			Item item = BuiltInRegistries.ITEM.getValue(tier.vanilla() ? Identifier.withDefaultNamespace(path) : MlinNotas.id(path));
			tool = new ItemStack(item).getHoverName();
		} else {
			tool = Component.translatable("hud.mlinnotas.waila.right_tool");
		}
		return Row.text(font, Component.literal(mark).append(Component.translatable("hud.mlinnotas.waila.requires", tool)), color);
	}

	private static boolean hasTag(BlockState state, String id) {
		TagKey<Block> tag = TAGS.computeIfAbsent(id, key -> TagKey.create(Registries.BLOCK, Identifier.parse(key)));
		return state.is(tag);
	}

	private static Row contentsRow(Font font, ContainerPeekPayload contents) {
		if (contents.items().isEmpty()) {
			return Row.text(font, Component.translatable("hud.mlinnotas.waila.empty"), GRAY);
		}
		List<ItemGrid.Group<ItemStack>> groups = new ArrayList<>(contents.items().size());
		for (int i = 0; i < contents.items().size(); i++) {
			groups.add(new ItemGrid.Group<>(contents.items().get(i), contents.counts().get(i)));
		}
		int width = ItemGridDrawer.width(groups.size(), CONTENT_ROWS);
		int height = ItemGridDrawer.height(groups.size(), CONTENT_ROWS, contents.more(), font) + 2;
		return new Row(width, height, (graphics, f, x, y) -> ItemGridDrawer.draw(graphics, f, groups, x, y + 1, CONTENT_ROWS, contents.more()));
	}

	// ---------------------------------------------------------------- entidades

	private static Info describeEntity(Minecraft minecraft, Font font, Entity entity) {
		List<Row> rows = new ArrayList<>();
		if (entity instanceof LivingEntity living) {
			rows.add(healthRow(font, living));
			int armor = living.getArmorValue();
			if (armor > 0) {
				Component text = Component.literal(Integer.toString(armor));
				rows.add(new Row(11 + font.width(text), 10, (graphics, f, x, y) -> {
					graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOR_FULL, x, y, 9, 9);
					graphics.text(f, text, x + 11, y + 1, WHITE);
				}));
			}
		}
		if (entity instanceof OwnableEntity ownable) {
			EntityReference<LivingEntity> owner = ownable.getOwnerReference();
			if (owner != null) {
				rows.add(Row.text(font, Component.translatable("hud.mlinnotas.waila.owner", ownerName(minecraft, owner)), GRAY));
			}
		}
		ItemStack icon;
		if (entity instanceof ItemEntity item) {
			icon = item.getItem();
		} else {
			icon = SpawnEggItem.byId(entity.getType()).map(holder -> new ItemStack(holder.value())).orElse(ItemStack.EMPTY);
		}
		return new Info(icon, entity.getDisplayName(), modName(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getNamespace()), rows);
	}

	private static Row healthRow(Font font, LivingEntity living) {
		float health = living.getHealth();
		float max = living.getMaxHealth();
		if (max > MAX_DRAWN_HEALTH) {
			Component text = Component.literal("❤ " + formatHealth(health) + " / " + formatHealth(max));
			return Row.text(font, text, RED);
		}
		int containers = (int) Math.ceil(max / 2.0F);
		int[] hearts = WailaRules.hearts(health, containers);
		int perRow = 10;
		int rows = (containers + perRow - 1) / perRow;
		int width = Math.min(containers, perRow) * 8 + 1;
		return new Row(width, rows * 10, (graphics, f, x, y) -> {
			for (int i = 0; i < containers; i++) {
				int hx = x + (i % perRow) * 8;
				int hy = y + (i / perRow) * 10;
				graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_CONTAINER, hx, hy, 9, 9);
				if (i < hearts[0]) {
					graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_FULL, hx, hy, 9, 9);
				} else if (i == hearts[0] && hearts[1] > 0) {
					graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HEART_HALF, hx, hy, 9, 9);
				}
			}
		});
	}

	private static String formatHealth(float value) {
		return value == (int) value ? Integer.toString((int) value) : String.format(java.util.Locale.ROOT, "%.1f", value);
	}

	private static Component ownerName(Minecraft minecraft, EntityReference<LivingEntity> owner) {
		Entity entity = minecraft.level == null ? null : minecraft.level.getEntity(owner.getUUID());
		if (entity != null) {
			return entity.getName();
		}
		if (minecraft.getConnection() != null) {
			PlayerInfo info = minecraft.getConnection().getPlayerInfo(owner.getUUID());
			if (info != null) {
				return Component.literal(info.getProfile().name());
			}
		}
		return Component.literal(owner.getUUID().toString().substring(0, 8));
	}

	private static Component modName(String namespace) {
		String name = FabricLoader.getInstance().getModContainer(namespace)
				.map(container -> container.getMetadata().getName())
				.orElse(namespace);
		return Component.literal(name).withStyle(ChatFormatting.BLUE, ChatFormatting.ITALIC);
	}
}
