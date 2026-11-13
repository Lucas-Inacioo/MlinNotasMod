package com.mlin.notas.skills;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.tier.Tier;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Lado servidor da arvore de habilidades: depositos (validados aqui), sincronizacao com os clientes, trava de
 * minerios por nivel de Mineracao, atributos dos ramos laterais e o tipo de receita travada.
 */
public final class SkillServer {
	public static final Identifier COMBAT_MODIFIER = MlinNotas.id("skill_combat");
	public static final Identifier VIGOR_MODIFIER = MlinNotas.id("skill_vigor");
	public static final Identifier SPEED_MODIFIER = MlinNotas.id("skill_agility_speed");
	public static final Identifier FALL_MODIFIER = MlinNotas.id("skill_agility_fall");

	private SkillServer() {
	}

	public static void init() {
		Registry.register(BuiltInRegistries.RECIPE_SERIALIZER, MlinNotas.id("skill_gated_shapeless"), SkillGatedRecipe.SERIALIZER);

		PayloadTypeRegistry.clientboundPlay().register(SkillPayloads.Sync.TYPE, SkillPayloads.Sync.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(SkillPayloads.Deposit.TYPE, SkillPayloads.Deposit.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(SkillPayloads.Deposit.TYPE, (payload, context) -> {
			SkillNode node = SkillTree.byKey(payload.node());
			if (node != null) {
				deposit(context.player(), node);
			}
		});

		ServerPlayerEvents.JOIN.register(player -> {
			applyAttributes(player);
			sync(player);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> applyAttributes(newPlayer));
		PlayerBlockBreakEvents.BEFORE.register(SkillServer::beforeBreak);
	}

	public static SkillProgress progress(MinecraftServer server) {
		return SkillData.get(server).progress();
	}

	// ---- trava de minerios ----

	/** Tier de Mineracao que falta para quebrar {@code state} (null = pode quebrar). */
	public static Tier missingTier(MinecraftServer server, BlockState state) {
		Tier required = OreTiers.required(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
		if (required == null || progress(server).has(SkillBranch.MINING, required.level())) {
			return null;
		}
		return required;
	}

	private static boolean beforeBreak(Level level, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity) {
		if (!(level instanceof ServerLevel serverLevel) || player.isCreative()) {
			return true;
		}
		Tier missing = missingTier(serverLevel.getServer(), state);
		if (missing == null) {
			return true;
		}
		if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection != null) {
			serverPlayer.sendOverlayMessage(Component.translatable("message.mlinnotas.skills.need_mining", tierName(missing))
					.withStyle(ChatFormatting.RED));
		}
		return false;
	}

	public static Component tierName(Tier tier) {
		return Component.translatable("skill.mlinnotas.tier." + tier.id());
	}

	// ---- depositos ----

	/** O item serve para a parte {@code costId} do custo (id de item ou {@code #tag}). */
	public static Predicate<ItemStack> matcher(String costId) {
		if (costId.startsWith("#")) {
			TagKey<net.minecraft.world.item.Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(costId.substring(1)));
			return stack -> !stack.isEmpty() && stack.is(tag);
		}
		Identifier id = Identifier.parse(costId);
		return stack -> !stack.isEmpty() && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(id);
	}

	/**
	 * Entrega para {@code node} o que o jogador tiver do que falta (itens do inventario e niveis de XP). Liberou: todos
	 * ganham o nivel, mensagem no chat de todo mundo e sincroniza.
	 */
	public static SkillProgress.Outcome deposit(ServerPlayer player, SkillNode node) {
		MinecraftServer server = player.level().getServer();
		SkillData data = SkillData.get(server);
		Map<String, Integer> available = new LinkedHashMap<>();
		for (SkillNode.Cost cost : node.items()) {
			available.put(cost.id(), ContainerHelper.clearOrCountMatchingItems(player.getInventory(), matcher(cost.id()), Integer.MAX_VALUE, true));
		}
		SkillProgress.Deposit result = data.deposit(node, available, player.experienceLevel);
		if (result.accepted()) {
			result.taken().forEach((id, count) ->
					ContainerHelper.clearOrCountMatchingItems(player.getInventory(), matcher(id), count, false));
			player.getInventory().setChanged();
			if (result.xpTaken() > 0) {
				player.giveExperienceLevels(-result.xpTaken());
			}
		}
		Component nodeName = nodeName(node);
		switch (result.outcome()) {
			case UNLOCKED -> {
				server.getPlayerList().broadcastSystemMessage(Component.translatable("message.mlinnotas.skills.unlocked",
						player.getDisplayName(), nodeName).withStyle(ChatFormatting.GOLD), false);
				for (ServerPlayer other : server.getPlayerList().getPlayers()) {
					applyAttributes(other);
				}
				player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
			}
			case PARTIAL -> notify(player, Component.translatable("message.mlinnotas.skills.deposited", nodeName));
			case NOTHING -> notify(player, Component.translatable("message.mlinnotas.skills.nothing", nodeName));
			case LOCKED -> notify(player, Component.translatable("message.mlinnotas.skills.locked", nodeName));
			case ALREADY_UNLOCKED -> notify(player, Component.translatable("message.mlinnotas.skills.already", nodeName));
		}
		if (result.accepted()) {
			syncAll(server);
		}
		return result.outcome();
	}

	private static void notify(ServerPlayer player, Component message) {
		if (player.connection != null) {
			player.sendOverlayMessage(message);
		}
	}

	/** "Mineracao: Ferro" / "Combate II". */
	public static Component nodeName(SkillNode node) {
		Component branch = Component.translatable("skill.mlinnotas.branch." + node.branch().id());
		if (node.branch() == SkillBranch.MINING) {
			return Component.translatable("skill.mlinnotas.node.mining", branch, tierName(node.tier()));
		}
		return Component.translatable("skill.mlinnotas.node.side", branch, Component.translatable("enchantment.level." + node.level()));
	}

	/** Liga um nivel direto (testes, admin) e propaga para todos. */
	/** Garante pelo menos {@code level} em {@code branch} (nunca abaixa). */
	public static void ensureLevel(MinecraftServer server, SkillBranch branch, int level) {
		if (progress(server).level(branch) < level) {
			setLevel(server, branch, level);
		}
	}

	/** Volta o mundo ao inicio (testes) e propaga para todos. */
	public static void reset(MinecraftServer server) {
		SkillData.get(server).reset();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			applyAttributes(player);
		}
		syncAll(server);
	}

	public static void setLevel(MinecraftServer server, SkillBranch branch, int level) {
		SkillData.get(server).setLevel(branch, level);
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			applyAttributes(player);
		}
		syncAll(server);
	}

	// ---- sincronizacao ----

	public static void sync(ServerPlayer player) {
		if (player.connection != null && ServerPlayNetworking.canSend(player, SkillPayloads.Sync.TYPE)) {
			ServerPlayNetworking.send(player, SkillPayloads.Sync.of(progress(player.level().getServer())));
		}
	}

	public static void syncAll(MinecraftServer server) {
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sync(player);
		}
	}

	// ---- atributos ----

	/** Aplica os bonus de Combate, Vigor e Agilidade do mundo ao jogador. */
	public static void applyAttributes(ServerPlayer player) {
		SkillProgress progress = progress(player.level().getServer());
		apply(player, Attributes.ATTACK_DAMAGE, COMBAT_MODIFIER, SkillEffects.meleeBonus(progress.level(SkillBranch.COMBAT)),
				AttributeModifier.Operation.ADD_VALUE);
		apply(player, Attributes.MAX_HEALTH, VIGOR_MODIFIER, SkillEffects.healthBonus(progress.level(SkillBranch.VIGOR)),
				AttributeModifier.Operation.ADD_VALUE);
		int agility = progress.level(SkillBranch.AGILITY);
		apply(player, Attributes.MOVEMENT_SPEED, SPEED_MODIFIER, SkillEffects.speedBonus(agility), AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
		apply(player, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_MODIFIER, -SkillEffects.fallReduction(agility),
				AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	}

	private static void apply(ServerPlayer player, Holder<Attribute> attribute, Identifier id, double amount, AttributeModifier.Operation op) {
		AttributeInstance instance = player.getAttribute(attribute);
		if (instance == null) {
			return;
		}
		if (amount == 0) {
			instance.removeModifier(id);
		} else {
			instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, op));
		}
	}

	/** Chance extra de minerio da Prospeccao do mundo (fonte para {@code tier/OreYield}). */
	public static double prospectingChance(MinecraftServer server) {
		return SkillEffects.prospectingChance(progress(server).level(SkillBranch.PROSPECTING));
	}
}
