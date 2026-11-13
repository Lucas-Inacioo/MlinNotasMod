package com.mlin.notas.boss;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/**
 * Chave da Forja: botao direito no Colosso Igneo adormecido ou no trono dele (qualquer bloco a ate
 * {@link #THRONE_RADIUS} blocos do colosso) o acorda e e gasta. Sem a chave o colosso nao acorda nem apanha.
 */
public class ForgeKeyItem extends Item {
	/** Distancia do bloco clicado ate o colosso para contar como "o trono". */
	public static final double THRONE_RADIUS = 6.0;

	public ForgeKeyItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
		if (target instanceof IgneousColossus colossus && colossus.dormant()) {
			if (player.level() instanceof ServerLevel level) {
				use(level, player, stack, colossus);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		if (player == null) {
			return InteractionResult.PASS;
		}
		if (!(context.getLevel() instanceof ServerLevel level)) {
			return InteractionResult.SUCCESS;
		}
		IgneousColossus colossus = sleepingNear(level, Vec3.atCenterOf(context.getClickedPos()));
		if (colossus == null) {
			if (player instanceof ServerPlayer sp && sp.connection != null) {
				sp.sendOverlayMessage(Component.translatable("message.mlinnotas.forge_key.nothing").withStyle(ChatFormatting.GRAY));
			}
			return InteractionResult.FAIL;
		}
		use(level, player, context.getItemInHand(), colossus);
		return InteractionResult.SUCCESS;
	}

	/** Colosso adormecido mais perto de {@code at}, a ate {@link #THRONE_RADIUS} blocos. */
	public static @Nullable IgneousColossus sleepingNear(ServerLevel level, Vec3 at) {
		return level.getEntitiesOfClass(IgneousColossus.class, new AABB(at, at).inflate(THRONE_RADIUS),
						c -> c.isAlive() && c.dormant() && c.distanceToSqr(at) <= THRONE_RADIUS * THRONE_RADIUS).stream()
				.min(Comparator.comparingDouble(c -> c.distanceToSqr(at))).orElse(null);
	}

	/** Gasta a chave (menos no criativo) e acorda o colosso. */
	public static void use(ServerLevel level, Player player, ItemStack stack, IgneousColossus colossus) {
		if (!player.hasInfiniteMaterials()) {
			stack.shrink(1);
		}
		level.playSound(null, colossus.blockPosition(), SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.HOSTILE, 2.0F, 0.6F);
		colossus.wake(level);
	}
}
