package com.mlin.notas.client.dynlight;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.dynlight.DynamicLightOptions;
import com.mlin.notas.dynlight.DynamicLightRules;
import com.mlin.notas.dynlight.LightField;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Luz dinamica (so cliente): a cada tick junta as fontes perto da camera (itens de luz na mao de jogadores, itens de
 * luz no chao, entidades pegando fogo, mobs que brilham), fica com as {@code maxSources} mais perto e, se alguma fonte
 * entrou, saiu, mudou de bloco ou de brilho, troca o retrato {@link LightField} e marca para refazer so as secoes de
 * chunk que essas fontes alcancam. Os mixins {@code LightCoordsUtilMixin} (malha dos blocos) e
 * {@code EntityRendererLightMixin} (entidades) somam a luz na hora de desenhar; o motor de luz do mundo nao muda.
 */
public final class DynamicLightsClient {
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve(MlinNotas.MOD_ID).resolve("dynamic_lights.json");
	private static final double SCAN_RADIUS = 64.0;

	private static DynamicLightOptions options = new DynamicLightOptions();
	private static volatile LightField field = LightField.EMPTY;
	private static List<DynamicLightRules.Source> current = List.of();
	private static ClientLevel lastLevel;
	private static final Map<Item, Integer> BLOCK_EMISSION = new IdentityHashMap<>();
	private static int rebuilds;

	private DynamicLightsClient() {
	}

	public static void init() {
		options = load();
		ClientTickEvents.END_CLIENT_TICK.register(DynamicLightsClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			current = List.of();
			field = LightField.EMPTY;
			lastLevel = null;
		});
	}

	public static DynamicLightOptions options() {
		return options;
	}

	/** Para testes: troca as opcoes (nao grava no arquivo). */
	public static void setOptions(DynamicLightOptions newOptions) {
		options = newOptions;
	}

	/** Fontes ativas agora (para testes e depuracao). */
	public static List<DynamicLightRules.Source> sources() {
		return current;
	}

	/** Quantas secoes ja foram marcadas para refazer (para testes). */
	public static int rebuilds() {
		return rebuilds;
	}

	public static DynamicLightOptions load() {
		try {
			if (Files.exists(FILE)) {
				return DynamicLightOptions.fromJson(Files.readString(FILE, StandardCharsets.UTF_8));
			}
			DynamicLightOptions defaults = new DynamicLightOptions();
			Files.createDirectories(FILE.getParent());
			Files.writeString(FILE, defaults.toJson(), StandardCharsets.UTF_8);
			return defaults;
		} catch (IOException e) {
			MlinNotas.LOGGER.warn("[luz dinamica] nao consegui ler {}: {}", FILE, e.toString());
			return new DynamicLightOptions();
		}
	}

	/** Coordenadas de lightmap com a luz dinamica somada (chamado pelas threads que montam os chunks). */
	public static int applyToPacked(BlockPos pos, int packed) {
		LightField f = field;
		if (f.isEmpty()) {
			return packed;
		}
		return LightField.applyToPacked(packed, f.levelAt(pos.getX(), pos.getY(), pos.getZ()));
	}

	/** Luz de bloco (0..15) de uma entidade com a luz dinamica somada. */
	public static int blockLevel(BlockPos pos, int original) {
		LightField f = field;
		if (f.isEmpty() || original >= 15) {
			return original;
		}
		return Math.max(original, (int) f.levelAt(pos.getX(), pos.getY(), pos.getZ()));
	}

	/** Luz dinamica no bloco (0..15 com fracao). */
	public static double levelAt(BlockPos pos) {
		return field.levelAt(pos.getX(), pos.getY(), pos.getZ());
	}

	private static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level != lastLevel) {
			current = List.of();
			field = LightField.EMPTY;
			lastLevel = level;
		}
		if (level == null || mc.player == null) {
			return;
		}
		List<DynamicLightRules.Source> selected = List.of();
		if (options.enabled) {
			Entity camera = mc.getCameraEntity() != null ? mc.getCameraEntity() : mc.player;
			double px = camera.getX();
			double py = camera.getY();
			double pz = camera.getZ();
			List<DynamicLightRules.Source> candidates = new ArrayList<>();
			for (Entity entity : level.entitiesForRendering()) {
				if (entity.distanceToSqr(px, py, pz) > SCAN_RADIUS * SCAN_RADIUS) {
					continue;
				}
				int luminance = luminance(mc, entity);
				if (luminance > 0) {
					double y = entity instanceof ItemEntity ? entity.getY() + 0.25 : entity.getY() + entity.getBbHeight() * 0.75;
					candidates.add(new DynamicLightRules.Source(Mth.floor(entity.getX()), Mth.floor(y), Mth.floor(entity.getZ()), luminance));
				}
			}
			selected = DynamicLightRules.select(candidates, px, py, pz, options.maxSources);
		}
		Set<Long> changed = DynamicLightRules.changedSections(current, selected);
		if (changed.isEmpty()) {
			return;
		}
		current = List.copyOf(selected);
		field = new LightField(current);
		for (long section : changed) {
			mc.levelExtractor.setSectionDirty(DynamicLightRules.sectionX(section), DynamicLightRules.sectionY(section), DynamicLightRules.sectionZ(section));
		}
		rebuilds += changed.size();
	}

	private static int luminance(Minecraft mc, Entity entity) {
		int light = 0;
		if (entity instanceof Player player) {
			if (options.heldItems && !player.isSpectator() && (player == mc.player || options.otherPlayers)) {
				boolean underwater = player.isUnderWater();
				light = Math.max(itemLight(player.getMainHandItem(), underwater), itemLight(player.getOffhandItem(), underwater));
			}
		} else if (entity instanceof ItemEntity item) {
			if (options.droppedItems) {
				light = itemLight(item.getItem(), item.isUnderWater());
			}
		} else if (options.glowingMobs) {
			light = DynamicLightRules.entityLuminance(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString());
		}
		if (options.burningEntities && entity.displayFireAnimation()) {
			light = Math.max(light, DynamicLightRules.BURNING);
		}
		return light;
	}

	private static int itemLight(ItemStack stack, boolean underwater) {
		if (stack.isEmpty()) {
			return 0;
		}
		Item item = stack.getItem();
		int emission = BLOCK_EMISSION.computeIfAbsent(item, i -> i instanceof BlockItem block ? block.getBlock().defaultBlockState().getLightEmission() : 0);
		return DynamicLightRules.itemLuminance(BuiltInRegistries.ITEM.getKey(item).toString(), emission, underwater);
	}
}
