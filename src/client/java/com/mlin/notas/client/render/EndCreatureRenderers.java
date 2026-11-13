package com.mlin.notas.client.render;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.end.CrystalMite;
import com.mlin.notas.end.PurpurSentinel;
import com.mlin.notas.end.VoidJelly;
import com.mlin.notas.registry.ModEntities;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.animal.golem.IronGolemModel;
import net.minecraft.client.model.animal.squid.SquidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.monster.endermite.EndermiteModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.IronGolemRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.SquidRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Renderers das criaturas do End. Reaproveitam modelos vanilla com texturas proprias (geradas por
 * scripts/EndTexGen.java): sentinela = golem, agua-viva = lula, acaro = endermite.
 */
public final class EndCreatureRenderers {
	private EndCreatureRenderers() {
	}

	public static void init() {
		EntityRenderers.register(ModEntities.PURPUR_SENTINEL, SentinelRenderer::new);
		EntityRenderers.register(ModEntities.VOID_JELLY, JellyRenderer::new);
		EntityRenderers.register(ModEntities.CRYSTAL_MITE, MiteRenderer::new);
	}

	private static Identifier texture(String name) {
		return MlinNotas.id("textures/entity/" + name + ".png");
	}

	/** Sentinela: modelo de golem em escala menor, com animacao de golpe. */
	public static class SentinelRenderer extends MobRenderer<PurpurSentinel, IronGolemRenderState, IronGolemModel> {
		private static final Identifier TEXTURE = texture("purpur_sentinel");

		public SentinelRenderer(EntityRendererProvider.Context context) {
			super(context, new IronGolemModel(context.bakeLayer(ModelLayers.IRON_GOLEM)), 0.5F);
		}

		@Override
		public Identifier getTextureLocation(IronGolemRenderState state) {
			return TEXTURE;
		}

		@Override
		public IronGolemRenderState createRenderState() {
			return new IronGolemRenderState();
		}

		@Override
		public void extractRenderState(PurpurSentinel entity, IronGolemRenderState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			state.attackTicksRemaining = entity.getAttackAnimationTick() > 0 ? entity.getAttackAnimationTick() - partialTicks : 0.0F;
		}

		@Override
		protected void scale(IronGolemRenderState state, PoseStack poseStack) {
			poseStack.scale(0.74F, 0.74F, 0.74F);
		}
	}

	/** Agua-viva: modelo de lula sempre de pe, tentaculos pulsando, brilha no escuro. */
	public static class JellyRenderer extends MobRenderer<VoidJelly, SquidRenderState, SquidModel> {
		private static final Identifier TEXTURE = texture("void_jelly");

		public JellyRenderer(EntityRendererProvider.Context context) {
			super(context, new SquidModel(context.bakeLayer(ModelLayers.GLOW_SQUID)), 0.3F);
		}

		@Override
		public Identifier getTextureLocation(SquidRenderState state) {
			return TEXTURE;
		}

		@Override
		public SquidRenderState createRenderState() {
			return new SquidRenderState();
		}

		@Override
		public void extractRenderState(VoidJelly entity, SquidRenderState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			float pulse = Mth.sin(state.ageInTicks * 0.12F);
			state.tentacleAngle = (pulse * pulse) * Mth.PI * 0.2F;
			state.xBodyRot = 0.0F;
			state.zBodyRot = 0.0F;
		}

		@Override
		protected void setupRotations(SquidRenderState state, PoseStack poseStack, float bodyRot, float entityScale) {
			poseStack.translate(0.0F, 0.5F, 0.0F);
			poseStack.rotateDegrees(Axis.YP, 180.0F - bodyRot);
			poseStack.translate(0.0F, -1.2F, 0.0F);
		}

		@Override
		protected int getBlockLightLevel(VoidJelly entity, BlockPos blockPos) {
			return 15;
		}
	}

	/** Acaro: modelo de endermite. */
	public static class MiteRenderer extends MobRenderer<CrystalMite, LivingEntityRenderState, EndermiteModel> {
		private static final Identifier TEXTURE = texture("crystal_mite");

		public MiteRenderer(EntityRendererProvider.Context context) {
			super(context, new EndermiteModel(context.bakeLayer(ModelLayers.ENDERMITE)), 0.3F);
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
	}
}
