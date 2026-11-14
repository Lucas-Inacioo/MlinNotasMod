package com.mlin.notas.client.civic;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModEntities;
import com.mlin.notas.village.civic.CivicPayloads;
import com.mlin.notas.village.civic.VillageGuard;
import com.mlin.notas.waystone.WaystoneCost;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.ArmorModelSet;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Set;

/**
 * Lado cliente das construcoes civicas: renderer do guarda da vila (modelo humanoide com armadura e a textura propria),
 * lista de pedras de vila (preco da viagem pela metade) e os monstros que a Torre de vigia mostra no minimapa a noite.
 */
public final class CivicClient {
	private static final Identifier GUARD_TEXTURE = MlinNotas.id("textures/entity/village_guard.png");
	private static volatile List<Integer> hostiles = List.of();
	private static volatile long hostilesAt;

	private CivicClient() {
	}

	public static void init() {
		EntityRenderers.register(ModEntities.VILLAGE_GUARD, GuardRenderer::new);
		ClientPlayNetworking.registerGlobalReceiver(CivicPayloads.VillageWaystones.TYPE, (payload, context) ->
				context.client().execute(() -> WaystoneCost.setVillageKeys(Set.copyOf(payload.keys()))));
		ClientPlayNetworking.registerGlobalReceiver(CivicPayloads.Hostiles.TYPE, (payload, context) -> {
			hostiles = List.copyOf(payload.coords());
			hostilesAt = System.currentTimeMillis();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			hostiles = List.of();
			WaystoneCost.setVillageKeys(Set.of());
		});
	}

	/** Monstros enxergados pela Torre de vigia (pares x, z), vazio se o aviso ja ficou velho (mais de 4 s). */
	public static List<Integer> hostiles() {
		return System.currentTimeMillis() - hostilesAt > 4000 ? List.of() : hostiles;
	}

	/** Pontos vermelhos dos monstros da torre no minimapa (a projecao leva mundo -> tela). */
	public static void drawHostiles(GuiGraphicsExtractor graphics, double cx, double cy, double px, double pz, float zoom, int x0, int y0, int x1, int y1) {
		List<Integer> list = hostiles();
		for (int i = 0; i + 1 < list.size(); i += 2) {
			int sx = (int) Math.round(cx + (list.get(i) + 0.5 - px) * zoom);
			int sy = (int) Math.round(cy + (list.get(i + 1) + 0.5 - pz) * zoom);
			if (sx < x0 + 1 || sy < y0 + 1 || sx >= x1 - 1 || sy >= y1 - 1) {
				continue;
			}
			graphics.fill(sx - 1, sy - 1, sx + 2, sy + 2, 0xFF300000);
			graphics.fill(sx - 1, sy - 1, sx + 1, sy + 1, 0xFFFF3030);
		}
	}

	/** Guarda: humanoide com a armadura do equipamento e a textura azul. */
	public static class GuardRenderer extends HumanoidMobRenderer<VillageGuard, HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
		public GuardRenderer(EntityRendererProvider.Context context) {
			super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
			ArmorModelSet<HumanoidModel<HumanoidRenderState>> armor = ArmorModelSet.bake(ModelLayers.ZOMBIE_ARMOR, context.getModelSet(), HumanoidModel::new);
			addLayer(new HumanoidArmorLayer<>(this, armor, armor, context.getEquipmentRenderer()));
		}

		@Override
		public HumanoidRenderState createRenderState() {
			return new HumanoidRenderState();
		}

		@Override
		public Identifier getTextureLocation(HumanoidRenderState state) {
			return GUARD_TEXTURE;
		}
	}
}
