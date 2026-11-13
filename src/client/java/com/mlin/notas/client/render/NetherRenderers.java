package com.mlin.notas.client.render;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.nether.MagmaSlug;
import com.mlin.notas.nether.NetherRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Criatura do Nether: Lesma de Magma, com modelo proprio e textura pintada por scripts/NetherTexGen.java no layout UV
 * do modelo (mudou um cubo aqui, mude o NetherTexGen junto).
 */
public final class NetherRenderers {
	public static final ModelLayerLocation MAGMA_SLUG = new ModelLayerLocation(MlinNotas.id("magma_slug"), "main");

	private NetherRenderers() {
	}

	public static void init() {
		ModelLayerRegistry.registerModelLayer(MAGMA_SLUG, MagmaSlugModel::createBodyLayer);
		EntityRenderers.register(NetherRegistry.MAGMA_SLUG, MagmaSlugRenderer::new);
	}

	/**
	 * Lesma de Magma 64x32: tres gomos de crosta com rachaduras de magma (cabeca (0,0) 6x5x5, meio (0,10) 6x4x5, cauda
	 * (0,19) 4x3x5), dois pedunculos (24,0) 1x4x1 com olho (30,0) 2x2x2. Rasteja ondulando os gomos.
	 */
	public static class MagmaSlugModel extends EntityModel<LivingEntityRenderState> {
		private final ModelPart head;
		private final ModelPart middle;
		private final ModelPart tail;
		private final ModelPart rightStalk;
		private final ModelPart leftStalk;

		public MagmaSlugModel(ModelPart root) {
			super(root);
			this.head = root.getChild("head");
			this.middle = root.getChild("middle");
			this.tail = root.getChild("tail");
			this.rightStalk = head.getChild("right_stalk");
			this.leftStalk = head.getChild("left_stalk");
		}

		public static LayerDefinition createBodyLayer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			// frente em -z; chao em y = 24
			PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -5.0F, -3.0F, 6, 5, 5),
					PartPose.offset(0.0F, 24.0F, -4.0F));
			root.addOrReplaceChild("middle", CubeListBuilder.create().texOffs(0, 10).addBox(-3.0F, -4.0F, -2.5F, 6, 4, 5),
					PartPose.offset(0.0F, 24.0F, 0.5F));
			root.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 19).addBox(-2.0F, -3.0F, -1.0F, 4, 3, 5),
					PartPose.offset(0.0F, 24.0F, 3.0F));
			CubeListBuilder stalk = CubeListBuilder.create().texOffs(24, 0).addBox(-0.5F, -4.0F, -0.5F, 1, 4, 1)
					.texOffs(30, 0).addBox(-1.0F, -6.0F, -1.0F, 2, 2, 2);
			head.addOrReplaceChild("right_stalk", stalk, PartPose.offsetAndRotation(-1.5F, -5.0F, -1.5F, -0.25F, 0.0F, -0.2F));
			head.addOrReplaceChild("left_stalk", stalk, PartPose.offsetAndRotation(1.5F, -5.0F, -1.5F, -0.25F, 0.0F, 0.2F));
			return LayerDefinition.create(mesh, 64, 32);
		}

		@Override
		public void setupAnim(LivingEntityRenderState state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			float crawl = state.walkAnimationPos * 1.2F;
			float speed = Math.min(1.0F, state.walkAnimationSpeed * 2.0F);
			// onda de contracao da cabeca para a cauda; parada, so "respira"
			float wave = speed > 0.05F ? speed : 0.25F;
			float phase = speed > 0.05F ? crawl : age * 0.08F;
			head.yScale = 1.0F + Mth.sin(phase) * 0.08F * wave;
			middle.yScale = 1.0F + Mth.sin(phase - 1.2F) * 0.14F * wave;
			middle.zScale = 1.0F - Mth.sin(phase - 1.2F) * 0.08F * wave;
			tail.yScale = 1.0F + Mth.sin(phase - 2.4F) * 0.12F * wave;
			tail.yRot = Mth.sin(phase * 0.5F) * 0.15F * wave;
			head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.4F;
			rightStalk.xRot += Mth.sin(age * 0.11F) * 0.18F;
			leftStalk.xRot += Mth.sin(age * 0.11F + 1.7F) * 0.18F;
			rightStalk.zRot -= Mth.cos(age * 0.07F) * 0.1F;
			leftStalk.zRot += Mth.cos(age * 0.07F + 0.9F) * 0.1F;
		}
	}

	/** Brilha como magma (luz de bloco minima 12). */
	public static class MagmaSlugRenderer extends MobRenderer<MagmaSlug, LivingEntityRenderState, MagmaSlugModel> {
		private static final Identifier TEXTURE = MlinNotas.id("textures/entity/magma_slug.png");

		public MagmaSlugRenderer(EntityRendererProvider.Context context) {
			super(context, new MagmaSlugModel(context.bakeLayer(MAGMA_SLUG)), 0.6F);
		}

		/** O modelo e desenhado 1,6x maior (a lesma tem ~1,5 bloco de comprimento). */
		@Override
		protected void scale(LivingEntityRenderState state, com.mojang.blaze3d.vertex.PoseStack poseStack) {
			poseStack.scale(1.6F, 1.6F, 1.6F);
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
		protected int getBlockLightLevel(MagmaSlug entity, BlockPos blockPos) {
			return Math.max(12, super.getBlockLightLevel(entity, blockPos));
		}
	}
}
