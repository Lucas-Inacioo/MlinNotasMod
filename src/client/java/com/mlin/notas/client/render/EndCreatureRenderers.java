package com.mlin.notas.client.render;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.client.render.EndCreatureModels.HopperModel;
import com.mlin.notas.client.render.EndCreatureModels.HopperState;
import com.mlin.notas.client.render.EndCreatureModels.JellyModel;
import com.mlin.notas.client.render.EndCreatureModels.MiteModel;
import com.mlin.notas.client.render.EndCreatureModels.SentinelModel;
import com.mlin.notas.client.render.EndCreatureModels.SentinelState;
import com.mlin.notas.end.ChorusHopper;
import com.mlin.notas.end.CrystalMite;
import com.mlin.notas.end.PurpurSentinel;
import com.mlin.notas.end.VoidJelly;
import com.mlin.notas.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * Renderers das criaturas do End, com modelos proprios ({@link EndCreatureModels}) e texturas pintadas por
 * scripts/EndTexGen.java no layout UV desses modelos.
 */
public final class EndCreatureRenderers {
	private EndCreatureRenderers() {
	}

	public static void init() {
		EndCreatureModels.init();
		EntityRenderers.register(ModEntities.PURPUR_SENTINEL, SentinelRenderer::new);
		EntityRenderers.register(ModEntities.VOID_JELLY, JellyRenderer::new);
		EntityRenderers.register(ModEntities.CRYSTAL_MITE, MiteRenderer::new);
		EntityRenderers.register(ModEntities.CHORUS_HOPPER, HopperRenderer::new);
	}

	private static Identifier texture(String name) {
		return MlinNotas.id("textures/entity/" + name + ".png");
	}

	/** Sentinela: construto flutuante, golpe com os dois bracos. */
	public static class SentinelRenderer extends MobRenderer<PurpurSentinel, SentinelState, SentinelModel> {
		private static final Identifier TEXTURE = texture("purpur_sentinel");

		public SentinelRenderer(EntityRendererProvider.Context context) {
			super(context, new SentinelModel(context.bakeLayer(EndCreatureModels.SENTINEL)), 0.5F);
		}

		@Override
		public Identifier getTextureLocation(SentinelState state) {
			return TEXTURE;
		}

		@Override
		public SentinelState createRenderState() {
			return new SentinelState();
		}

		@Override
		public void extractRenderState(PurpurSentinel entity, SentinelState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			state.attackTicks = entity.getAttackAnimationTick() > 0 ? entity.getAttackAnimationTick() - partialTicks : 0.0F;
		}
	}

	/** Agua-viva: sino translucido que brilha no escuro. */
	public static class JellyRenderer extends MobRenderer<VoidJelly, LivingEntityRenderState, JellyModel> {
		private static final Identifier TEXTURE = texture("void_jelly");

		public JellyRenderer(EntityRendererProvider.Context context) {
			super(context, new JellyModel(context.bakeLayer(EndCreatureModels.JELLY)), 0.3F);
		}

		@Override
		public Identifier getTextureLocation(LivingEntityRenderState state) {
			return TEXTURE;
		}

		@Override
		public LivingEntityRenderState createRenderState() {
			return new LivingEntityRenderState();
		}

		@Override
		protected int getBlockLightLevel(VoidJelly entity, BlockPos blockPos) {
			return 15;
		}
	}

	/** Acaro: caranguejinho de cristal. */
	public static class MiteRenderer extends MobRenderer<CrystalMite, LivingEntityRenderState, MiteModel> {
		private static final Identifier TEXTURE = texture("crystal_mite");

		public MiteRenderer(EntityRendererProvider.Context context) {
			super(context, new MiteModel(context.bakeLayer(EndCreatureModels.MITE)), 0.35F);
		}

		@Override
		protected float getFlipDegrees() {
			return 180.0F;
		}

		@Override
		public Identifier getTextureLocation(LivingEntityRenderState state) {
			return TEXTURE;
		}

		@Override
		public LivingEntityRenderState createRenderState() {
			return new LivingEntityRenderState();
		}

		@Override
		protected int getBlockLightLevel(CrystalMite entity, BlockPos blockPos) {
			return Math.max(8, super.getBlockLightLevel(entity, blockPos));
		}
	}

	/** Saltador de Coro: estica no ar, respira no chao. */
	public static class HopperRenderer extends MobRenderer<ChorusHopper, HopperState, HopperModel> {
		private static final Identifier TEXTURE = texture("chorus_hopper");

		public HopperRenderer(EntityRendererProvider.Context context) {
			super(context, new HopperModel(context.bakeLayer(EndCreatureModels.HOPPER)), 0.35F);
		}

		@Override
		public Identifier getTextureLocation(HopperState state) {
			return TEXTURE;
		}

		@Override
		public HopperState createRenderState() {
			return new HopperState();
		}

		@Override
		public void extractRenderState(ChorusHopper entity, HopperState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			state.hopping = entity.isHopping();
		}
	}
}
