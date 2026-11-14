package com.mlin.notas.gating;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.skills.SkillBranch;
import com.mlin.notas.skills.SkillServer;
import com.mlin.notas.tier.Tier;
import com.mlin.notas.wand.BuildingWandItem;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;

/**
 * Ponte da trava de ferramentas ({@link ToolGate}) com o jogo. Com uma ferramenta/arma de nivel acima da Mineracao do
 * mundo na mao:
 * <ul>
 *   <li>quebrar: nem comeca ({@link AttackBlockCallback}, nos dois lados) e o servidor recusa de novo no
 *   {@link PlayerBlockBreakEvents#BEFORE} (cobre veio e o derrubador de arvores, que vem depois da quebra);</li>
 *   <li>atacar: o ataque e cancelado (mixin {@code PlayerToolGateMixin} em {@code Player.cannotAttackWithItem}, o mesmo
 *   ponto que o jogo usa para a carga minima: vale para golpe em entidade, estocada da lanca e clique no ar, nos dois
 *   lados). Cancelar em vez de reduzir ao soco evita que encantamentos (Aspecto Flamejante, Repulsao), arcos/ondas do
 *   combate e efeitos das armas de chefe passem;</li>
 *   <li>botao direito: lanca e outros itens de uso continuo recusados no {@link UseItemCallback}; arar, descascar,
 *   abrir caminho e a varinha nao fazem nada (mixin {@code ItemStackToolGateMixin} em {@code ItemStack.useOn}, que
 *   devolve PASS para a outra mao ainda funcionar).</li>
 * </ul>
 * Aviso "Requer Mineracao: X" na barra de acao, no maximo a cada {@link ToolGate#MESSAGE_COOLDOWN_TICKS}.
 */
public final class ToolLocks {
	/** Fase que roda antes das outras ouvintes (combate, colheita...) nos eventos compartilhados. */
	public static final Identifier PHASE = MlinNotas.id("tool_gate");

	private static final Map<Tier, TagKey<Item>> TAGS = new EnumMap<>(Tier.class);
	/** Nivel de Mineracao do mundo visto pelo cliente (trocado pelo entrypoint do cliente). */
	private static volatile IntSupplier clientMiningLevel = () -> SkillBranch.MINING.startLevel();
	private static final Map<UUID, Long> LAST_SERVER_MESSAGE = new HashMap<>();
	private static final Map<UUID, Long> LAST_CLIENT_MESSAGE = new HashMap<>();

	static {
		for (Tier tier : Tier.values()) {
			TAGS.put(tier, TagKey.create(Registries.ITEM, MlinNotas.id(ToolGate.tagPath(tier))));
		}
	}

	private ToolLocks() {
	}

	public static void init() {
		AttackBlockCallback.EVENT.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
		AttackBlockCallback.EVENT.register(PHASE, (player, level, hand, pos, direction) ->
				refuse(player, player.getItemInHand(hand)) ? InteractionResult.FAIL : InteractionResult.PASS);
		UseItemCallback.EVENT.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
		UseItemCallback.EVENT.register(PHASE, (player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);
			return hasHeldUse(player, stack) && refuse(player, stack) ? InteractionResult.FAIL : InteractionResult.PASS;
		});
		PlayerBlockBreakEvents.BEFORE.addPhaseOrdering(PHASE, Event.DEFAULT_PHASE);
		PlayerBlockBreakEvents.BEFORE.register(PHASE, (level, player, pos, state, blockEntity) ->
				!refuse(player, player.getMainHandItem()));
	}

	/** Liga a leitura do nivel do mundo no cliente (progresso sincronizado da arvore de habilidades). */
	public static void setClientMiningLevel(IntSupplier supplier) {
		clientMiningLevel = supplier;
	}

	public static TagKey<Item> tag(Tier tier) {
		return TAGS.get(tier);
	}

	/** Nivel da ferramenta/arma pelas tags {@code mlinnotas:tool_tier/*} (null = item sem nivel). */
	public static Tier tier(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		return ToolGate.tierOf(tier -> stack.is(TAGS.get(tier)));
	}

	/** Nivel de Mineracao do mundo neste lado (servidor: dado salvo; cliente: o ultimo sincronizado). */
	public static int miningLevel(Level level) {
		if (level instanceof ServerLevel serverLevel) {
			return SkillServer.progress(serverLevel.getServer()).level(SkillBranch.MINING);
		}
		return clientMiningLevel.getAsInt();
	}

	/** Nivel que falta para o jogador usar {@code stack} (null = pode usar). */
	public static Tier missing(Player player, ItemStack stack) {
		Tier tier = tier(stack);
		if (tier == null) {
			return null;
		}
		boolean bypass = player.isCreative() || player.isSpectator();
		return ToolGate.missing(tier, miningLevel(player.level()), bypass);
	}

	/** {@code stack} esta travado para o jogador? Se sim, avisa (com intervalo minimo). */
	public static boolean refuse(Player player, ItemStack stack) {
		Tier missing = missing(player, stack);
		if (missing == null) {
			return false;
		}
		notify(player, missing);
		return true;
	}

	/** Botao direito que o item faria num bloco (arar, descascar, caminho, varinha): so esses sao travados no useOn. */
	public static boolean hasBlockUse(ItemStack stack) {
		return stack.has(DataComponents.BLOCK_TRANSFORMER) || stack.getItem() instanceof BuildingWandItem;
	}

	/** Uso continuo do item (lanca carregada, escudo...) que o {@link UseItemCallback} deve recusar. */
	private static boolean hasHeldUse(Player player, ItemStack stack) {
		return stack.has(DataComponents.KINETIC_WEAPON) || stack.getUseDuration(player) > 0;
	}

	public static Component message(Tier tier) {
		return Component.translatable("message.mlinnotas.tool_locked", SkillServer.tierName(tier)).withStyle(ChatFormatting.RED);
	}

	private static void notify(Player player, Tier tier) {
		if (player instanceof ServerPlayer serverPlayer && serverPlayer.connection == null) {
			return;
		}
		Map<UUID, Long> last = player.level().isClientSide() ? LAST_CLIENT_MESSAGE : LAST_SERVER_MESSAGE;
		long now = player.level().getGameTime();
		if (!ToolGate.shouldNotify(last.getOrDefault(player.getUUID(), -1L), now)) {
			return;
		}
		last.put(player.getUUID(), now);
		player.sendOverlayMessage(message(tier));
	}
}
