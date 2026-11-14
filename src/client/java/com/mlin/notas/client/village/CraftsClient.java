package com.mlin.notas.client.village;

import com.mlin.notas.client.minimap.MinimapClient;
import com.mlin.notas.compass.CompassCatalog;
import com.mlin.notas.compass.CompassKind;
import com.mlin.notas.minimap.Waypoint;
import com.mlin.notas.village.CraftsPayloads;
import com.mlin.notas.village.Rumors;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Lado cliente das construcoes de oficios (Vilas 2.0, fase 6): o boato da taverna chega a todos e vira um waypoint
 * compartilhado no minimapa (cor dourada, nome com o prefixo {@link Rumors#WAYPOINT_PREFIX}); o livro de quests lista os
 * ultimos boatos lendo esses waypoints (nada novo para salvar).
 */
public final class CraftsClient {
	private static final int RUMOR_COLOR = 0xFFFFD83A;

	private CraftsClient() {
	}

	public static void init() {
		ClientPlayNetworking.registerGlobalReceiver(CraftsPayloads.Rumor.TYPE, (payload, context) -> {
			String name = Rumors.WAYPOINT_PREFIX + Component.translatable(CompassCatalog.nameKey(CompassKind.STRUCTURE, payload.structure())).getString()
					+ " (" + payload.village() + ")";
			boolean exists = MinimapClient.waypoints().store().all().stream()
					.anyMatch(w -> w.name().equals(name) && w.x() == payload.x() && w.z() == payload.z());
			if (!exists) {
				MinimapClient.waypoints().add(new Waypoint(name, payload.x(), 64, payload.z(), RUMOR_COLOR, payload.dimension()));
			}
			if (context.client().player != null) {
				context.client().player.sendOverlayMessage(Component.translatable("message.mlinnotas.rumor.waypoint", name));
			}
		});
	}

	/** Os ultimos boatos (mais novo primeiro), ate {@code max} linhas, para o livro de quests. */
	public static List<Component> rumorLines(int max) {
		List<Component> out = new ArrayList<>();
		List<Waypoint> all = MinimapClient.waypoints().store().all();
		for (int i = all.size() - 1; i >= 0 && out.size() < max; i--) {
			Waypoint w = all.get(i);
			if (w.name().startsWith(Rumors.WAYPOINT_PREFIX)) {
				out.add(Component.translatable("gui.mlinnotas.quests.rumor_line", w.name().substring(Rumors.WAYPOINT_PREFIX.length()), w.x(), w.z()));
			}
		}
		return out;
	}
}
