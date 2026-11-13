package com.mlin.notas.client.compass;

import com.mlin.notas.compass.CompassCatalog;
import com.mlin.notas.compass.CompassKind;
import com.mlin.notas.compass.CompassPayloads;
import com.mlin.notas.compass.CompassTarget;
import com.mlin.notas.compass.Compasses;
import com.mlin.notas.compass.ExplorerCompassItem;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.LodestoneTracker;

/** Lado cliente das bussolas: abre a lista e poe distancia/direcao no tooltip. */
public final class CompassClient {
	private CompassClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(CompassPayloads.Open.TYPE, (payload, context) ->
				context.client().gui.setScreen(new CompassScreen(CompassKind.byId(payload.kind()), payload.entries(), payload.current())));
		ItemTooltipCallback.EVENT.register((stack, tooltipContext, flag, lines) -> {
			if (!(stack.getItem() instanceof ExplorerCompassItem)) {
				return;
			}
			CompassTarget target = stack.get(Compasses.TARGET);
			LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
			Minecraft mc = Minecraft.getInstance();
			if (target == null || target.status() != CompassTarget.FOUND || tracker == null || tracker.target().isEmpty() || mc.player == null) {
				return;
			}
			GlobalPos pos = tracker.target().get();
			Component line;
			if (!pos.dimension().equals(mc.player.level().dimension())) {
				line = Component.translatable("compass.mlinnotas.other_dimension").withStyle(ChatFormatting.GOLD);
			} else {
				double tx = pos.pos().getX() + 0.5;
				double tz = pos.pos().getZ() + 0.5;
				int distance = CompassCatalog.horizontalDistance(mc.player.getX(), mc.player.getZ(), tx, tz);
				String dir = CompassCatalog.direction(mc.player.getX(), mc.player.getZ(), tx, tz);
				line = (dir.equals("here") ? Component.translatable("compass.mlinnotas.arrived")
						: Component.translatable("compass.mlinnotas.distance", distance, Component.translatable("compass.mlinnotas.dir." + dir)))
						.withStyle(ChatFormatting.AQUA);
			}
			// logo depois da linha do alvo (linha 0 = nome do item, 1 = alvo)
			lines.add(Math.min(2, lines.size()), line);
		});
	}

	public static void select(CompassKind kind, String id) {
		if (ClientPlayNetworking.canSend(CompassPayloads.Select.TYPE)) {
			ClientPlayNetworking.send(new CompassPayloads.Select(kind.id(), id));
		}
	}
}
