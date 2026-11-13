package com.mlin.notas.client.render;

import com.mlin.notas.MlinNotas;
import net.fabricmc.fabric.api.client.rendering.v1.ModelLayerRegistry;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.Mth;

/**
 * Modelos proprios das criaturas do End. Coordenadas em pixels (1/16 de bloco), y para baixo, chao em y = 24.
 * As texturas (64x64 / 64x32) sao pintadas por scripts/EndTexGen.java seguindo exatamente os {@code texOffs}
 * daqui: mudou um cubo, mude o EndTexGen junto.
 */
public final class EndCreatureModels {
	public static final ModelLayerLocation SENTINEL = layer("purpur_sentinel");
	public static final ModelLayerLocation JELLY = layer("void_jelly");
	public static final ModelLayerLocation MITE = layer("crystal_mite");
	public static final ModelLayerLocation HOPPER = layer("chorus_hopper");

	private EndCreatureModels() {
	}

	private static ModelLayerLocation layer(String name) {
		return new ModelLayerLocation(MlinNotas.id(name), "main");
	}

	public static void init() {
		ModelLayerRegistry.registerModelLayer(SENTINEL, SentinelModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(JELLY, JellyModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(MITE, MiteModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(HOPPER, HopperModel::createBodyLayer);
	}

	/** Estado da sentinela: ticks restantes do golpe. */
	public static class SentinelState extends LivingEntityRenderState {
		public float attackTicks;
	}

	/** Estado do saltador: no ar (pulando) ou nao. */
	public static class HopperState extends LivingEntityRenderState {
		public boolean hopping;
	}

	/**
	 * Sentinela de Purpur: construto flutuante. Tronco de purpur, cabeca com casca de shulker e um olho, dois bracos
	 * soltos (ombro, antebraco e punho) orbitando ao lado do corpo e um propulsor brilhante no lugar das pernas.
	 */
	public static class SentinelModel extends EntityModel<SentinelState> {
		private final ModelPart head;
		private final ModelPart torso;
		private final ModelPart thruster;
		private final ModelPart rightArm;
		private final ModelPart leftArm;

		public SentinelModel(ModelPart root) {
			super(root);
			this.head = root.getChild("head");
			this.torso = root.getChild("torso");
			this.thruster = root.getChild("thruster");
			this.rightArm = root.getChild("right_arm");
			this.leftArm = root.getChild("left_arm");
		}

		public static LayerDefinition createBodyLayer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			// cabeca 8x8x8 em (0,0) + casca 9x4x9 em (0,43), pescoco em y = 3
			root.addOrReplaceChild("head", CubeListBuilder.create()
					.texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8, 8, 8)
					.texOffs(0, 43).addBox(-4.5F, -9.0F, -4.5F, 9, 4, 9), PartPose.offset(0.0F, 3.0F, 0.0F));
			// tronco 10x11x7 em (0,16), de y 4 a 15
			root.addOrReplaceChild("torso", CubeListBuilder.create()
					.texOffs(0, 16).addBox(-5.0F, 0.0F, -3.5F, 10, 11, 7), PartPose.offset(0.0F, 4.0F, 0.0F));
			// propulsor 6x4x5 em (0,34) e ponta 3x2x3 em (22,34)
			root.addOrReplaceChild("thruster", CubeListBuilder.create()
					.texOffs(0, 34).addBox(-3.0F, 0.0F, -2.5F, 6, 4, 5)
					.texOffs(22, 34).addBox(-1.5F, 4.0F, -1.5F, 3, 2, 3), PartPose.offset(0.0F, 15.0F, 0.0F));
			// bracos: ombro 4x4x4 (36,16), antebraco 3x9x3 (36,24), punho 4x3x4 (48,24)
			CubeListBuilder arm = CubeListBuilder.create()
					.texOffs(36, 16).addBox(-2.0F, -2.0F, -2.0F, 4, 4, 4)
					.texOffs(36, 24).addBox(-1.5F, 2.5F, -1.5F, 3, 9, 3)
					.texOffs(48, 24).addBox(-2.0F, 11.0F, -2.0F, 4, 3, 4);
			root.addOrReplaceChild("right_arm", arm, PartPose.offset(-8.5F, 5.0F, 0.0F));
			root.addOrReplaceChild("left_arm", arm.mirror(), PartPose.offset(8.5F, 5.0F, 0.0F));
			return LayerDefinition.create(mesh, 64, 64);
		}

		@Override
		public void setupAnim(SentinelState state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			float bob = Mth.sin(age * 0.1F) * 1.2F;
			head.y += bob;
			torso.y += bob;
			thruster.y += bob;
			thruster.yRot = age * 0.15F;
			head.yRot = state.yRot * Mth.DEG_TO_RAD;
			head.xRot = state.xRot * Mth.DEG_TO_RAD;

			// bracos soltos: sobem e descem fora de fase e orbitam um pouco para frente e para tras
			float orbit = Mth.sin(age * 0.05F) * 0.35F;
			for (int side = 0; side < 2; side++) {
				ModelPart arm = side == 0 ? rightArm : leftArm;
				float sign = side == 0 ? -1.0F : 1.0F;
				float angle = orbit * (side == 0 ? 1.0F : -1.0F);
				arm.x = sign * 8.5F * Mth.cos(angle);
				arm.z = 8.5F * Mth.sin(angle);
				arm.y += bob + Mth.sin(age * 0.13F + side * Mth.PI) * 1.3F;
				arm.zRot = sign * (0.08F + Mth.sin(age * 0.09F + side) * 0.05F);
				arm.xRot = Mth.cos(state.walkAnimationPos * 0.6662F + side * Mth.PI) * 0.4F * state.walkAnimationSpeed;
			}
			if (state.attackTicks > 0.0F) {
				// golpe: os dois bracos sobem e descem juntos
				float t = state.attackTicks / 10.0F;
				float swing = -2.2F * Mth.sin(t * Mth.PI);
				rightArm.xRot = swing;
				leftArm.xRot = swing;
			}
		}
	}

	/**
	 * Agua-viva do Vazio: sino translucido (topo + sino), nucleo brilhante dentro, 8 abas onduladas na borda e 8
	 * tentaculos de dois segmentos que balancam em onda.
	 */
	public static class JellyModel extends EntityModel<LivingEntityRenderState> {
		private static final int COUNT = 8;
		private final ModelPart bell;
		private final ModelPart[] flaps = new ModelPart[COUNT];
		private final ModelPart[] tentacles = new ModelPart[COUNT];
		private final ModelPart[] tips = new ModelPart[COUNT];

		public JellyModel(ModelPart root) {
			super(root, RenderTypes::entityTranslucentEmissive);
			this.bell = root.getChild("bell");
			for (int i = 0; i < COUNT; i++) {
				flaps[i] = root.getChild("flap" + i);
				tentacles[i] = root.getChild("tentacle" + i);
				tips[i] = tentacles[i].getChild("tip");
			}
		}

		public static LayerDefinition createBodyLayer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			// topo 8x3x8 (0,0), sino 12x5x12 (0,11), nucleo 4x3x4 (32,0); borda do sino em y = 18
			root.addOrReplaceChild("bell", CubeListBuilder.create()
					.texOffs(0, 0).addBox(-4.0F, -3.0F, -4.0F, 8, 3, 8)
					.texOffs(0, 11).addBox(-6.0F, 0.0F, -6.0F, 12, 5, 12)
					.texOffs(32, 0).addBox(-2.0F, 1.0F, -2.0F, 4, 3, 4), PartPose.offset(0.0F, 13.0F, 0.0F));
			for (int i = 0; i < COUNT; i++) {
				float angle = i * Mth.TWO_PI / COUNT;
				// aba 4x3x1 (0,28) na borda, virada para fora
				root.addOrReplaceChild("flap" + i, CubeListBuilder.create().texOffs(0, 28).addBox(-2.0F, 0.0F, -0.5F, 4, 3, 1),
						PartPose.offsetAndRotation(Mth.sin(angle) * 5.5F, 18.0F, Mth.cos(angle) * 5.5F, 0.3F, angle, 0.0F));
				// tentaculo 1x6x1 (48,0) + ponta 1x6x1 (52,0)
				float tentacleAngle = angle + Mth.PI / COUNT;
				PartDefinition tentacle = root.addOrReplaceChild("tentacle" + i, CubeListBuilder.create().texOffs(48, 0)
						.addBox(-0.5F, 0.0F, -0.5F, 1, 6, 1), PartPose.offsetAndRotation(Mth.sin(tentacleAngle) * 3.0F, 17.0F,
						Mth.cos(tentacleAngle) * 3.0F, 0.0F, tentacleAngle, 0.0F));
				tentacle.addOrReplaceChild("tip", CubeListBuilder.create().texOffs(52, 0).addBox(-0.5F, 0.0F, -0.5F, 1, 6, 1),
						PartPose.offset(0.0F, 6.0F, 0.0F));
			}
			return LayerDefinition.create(mesh, 64, 64);
		}

		@Override
		public void setupAnim(LivingEntityRenderState state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			float pulse = Mth.sin(age * 0.12F);
			bell.yScale = 1.0F + pulse * 0.06F;
			bell.xScale = 1.0F - pulse * 0.05F;
			bell.zScale = bell.xScale;
			bell.y -= pulse * 0.5F;
			for (int i = 0; i < COUNT; i++) {
				float wave = age * 0.16F - i * Mth.PI / 4.0F;
				flaps[i].xRot = 0.35F + 0.3F * Mth.sin(wave) - pulse * 0.15F;
				tentacles[i].xRot = 0.12F + 0.22F * Mth.sin(wave);
				tips[i].xRot = 0.35F * Mth.sin(wave - 1.1F);
			}
		}
	}

	/**
	 * Acaro Cristalino: corpo baixo de caranguejo com 6 pernas, duas pincas na frente e espinhos de cristal nas costas.
	 */
	public static class MiteModel extends EntityModel<LivingEntityRenderState> {
		private final ModelPart[] legs = new ModelPart[6];
		private final ModelPart rightClaw;
		private final ModelPart leftClaw;
		private final ModelPart spikes;

		public MiteModel(ModelPart root) {
			super(root);
			for (int i = 0; i < 6; i++) {
				legs[i] = root.getChild("leg" + i);
			}
			this.rightClaw = root.getChild("right_claw");
			this.leftClaw = root.getChild("left_claw");
			this.spikes = root.getChild("spikes");
		}

		public static LayerDefinition createBodyLayer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			// corpo 7x3x8 (0,0) de y 19 a 22; cabeca 5x2x3 (0,11)
			root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -3.0F, -4.0F, 7, 3, 8)
					.texOffs(0, 11).addBox(-2.5F, -2.5F, -7.0F, 5, 2, 3), PartPose.offset(0.0F, 22.0F, 0.0F));
			// pincas 2x2x3 (16,11)
			CubeListBuilder claw = CubeListBuilder.create().texOffs(16, 11).addBox(-1.0F, -1.0F, -3.0F, 2, 2, 3);
			root.addOrReplaceChild("right_claw", claw, PartPose.offsetAndRotation(-2.5F, 21.0F, -6.5F, 0.0F, 0.3F, 0.0F));
			root.addOrReplaceChild("left_claw", claw.mirror(), PartPose.offsetAndRotation(2.5F, 21.0F, -6.5F, 0.0F, -0.3F, 0.0F));
			// pernas 5x1x1 (30,0): 0-2 do lado direito (-x), 3-5 do esquerdo (+x)
			for (int i = 0; i < 6; i++) {
				boolean right = i < 3;
				float z = -2.5F + (i % 3) * 2.5F;
				CubeListBuilder leg = CubeListBuilder.create().texOffs(30, 0).mirror(!right)
						.addBox(right ? -5.0F : 0.0F, -0.5F, -0.5F, 5, 1, 1);
				root.addOrReplaceChild("leg" + i, leg, PartPose.offsetAndRotation(right ? -3.5F : 3.5F, 21.0F, z, 0.0F, 0.0F,
						right ? -0.6F : 0.6F));
			}
			// espinhos de cristal: grande 2x5x2 (46,0) no meio, pequenos 1x4x1 (42,0) inclinados
			root.addOrReplaceChild("spikes", CubeListBuilder.create()
					.texOffs(46, 0).addBox(-1.0F, -5.0F, -1.0F, 2, 5, 2)
					.texOffs(42, 0).addBox(-2.5F, -3.5F, -2.5F, 1, 4, 1)
					.texOffs(42, 0).addBox(1.5F, -3.5F, -2.0F, 1, 4, 1)
					.texOffs(42, 0).addBox(-2.0F, -3.0F, 2.0F, 1, 4, 1)
					.texOffs(42, 0).addBox(1.0F, -4.0F, 2.5F, 1, 4, 1), PartPose.offset(0.0F, 19.0F, 0.0F));
			return LayerDefinition.create(mesh, 64, 32);
		}

		@Override
		public void setupAnim(LivingEntityRenderState state) {
			super.setupAnim(state);
			float walk = state.walkAnimationPos * 1.8F;
			float speed = Math.min(1.0F, state.walkAnimationSpeed * 1.5F);
			for (int i = 0; i < 6; i++) {
				boolean right = i < 3;
				// tripode alternado: pernas 0, 2, 4 juntas e 1, 3, 5 juntas
				float phase = (i % 2 == 0) ? 0.0F : Mth.PI;
				legs[i].yRot = Mth.cos(walk + phase) * 0.45F * speed * (right ? 1.0F : -1.0F);
				legs[i].zRot += Math.max(0.0F, Mth.sin(walk + phase)) * 0.35F * speed * (right ? 1.0F : -1.0F);
			}
			float snip = Mth.sin(state.ageInTicks * 0.25F) * 0.15F;
			rightClaw.yRot += snip;
			leftClaw.yRot -= snip;
			spikes.y += Mth.sin(state.ageInTicks * 0.1F) * 0.15F;
		}
	}

	/**
	 * Saltador de Coro: bola de fruta do coro com flor de coro na cabeca, duas anteninhas e pes grandes. No ar o
	 * corpo estica e os pes vao para tras; no chao ele "respira" achatando.
	 */
	public static class HopperModel extends EntityModel<HopperState> {
		private final ModelPart body;
		private final ModelPart flower;
		private final ModelPart rightFoot;
		private final ModelPart leftFoot;
		private final ModelPart rightAntenna;
		private final ModelPart leftAntenna;

		public HopperModel(ModelPart root) {
			super(root);
			this.body = root.getChild("body");
			this.flower = body.getChild("flower");
			this.rightFoot = root.getChild("right_foot");
			this.leftFoot = root.getChild("left_foot");
			this.rightAntenna = body.getChild("right_antenna");
			this.leftAntenna = body.getChild("left_antenna");
		}

		public static LayerDefinition createBodyLayer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			// corpo 7x7x7 (0,0) de y 15 a 22 (pivo na base)
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, -7.0F, -3.5F, 7, 7, 7),
					PartPose.offset(0.0F, 22.0F, 0.0F));
			// flor: petalas 4x3x4 (28,0) e botao 2x2x2 (44,0)
			body.addOrReplaceChild("flower", CubeListBuilder.create().texOffs(28, 0).addBox(-2.0F, -3.0F, -2.0F, 4, 3, 4)
					.texOffs(44, 0).addBox(-1.0F, -5.0F, -1.0F, 2, 2, 2), PartPose.offset(0.0F, -7.0F, 0.0F));
			// antenas 1x4x1 (16,14)
			body.addOrReplaceChild("right_antenna", CubeListBuilder.create().texOffs(16, 14).addBox(-0.5F, -4.0F, -0.5F, 1, 4, 1),
					PartPose.offsetAndRotation(-2.5F, -7.0F, -1.5F, 0.0F, 0.0F, -0.35F));
			body.addOrReplaceChild("left_antenna", CubeListBuilder.create().texOffs(16, 14).mirror().addBox(-0.5F, -4.0F, -0.5F, 1, 4, 1),
					PartPose.offsetAndRotation(2.5F, -7.0F, -1.5F, 0.0F, 0.0F, 0.35F));
			// pes 3x2x5 (0,14)
			root.addOrReplaceChild("right_foot", CubeListBuilder.create().texOffs(0, 14).addBox(-1.5F, 0.0F, -3.0F, 3, 2, 5),
					PartPose.offset(-2.0F, 22.0F, 0.0F));
			root.addOrReplaceChild("left_foot", CubeListBuilder.create().texOffs(0, 14).mirror().addBox(-1.5F, 0.0F, -3.0F, 3, 2, 5),
					PartPose.offset(2.0F, 22.0F, 0.0F));
			return LayerDefinition.create(mesh, 64, 32);
		}

		@Override
		public void setupAnim(HopperState state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			if (state.hopping) {
				body.yScale = 1.15F;
				body.xScale = 0.9F;
				body.zScale = 0.9F;
				rightFoot.xRot = 0.7F;
				leftFoot.xRot = 0.7F;
				flower.xRot = -0.25F;
			} else {
				float breathe = Mth.sin(age * 0.2F) * 0.04F;
				body.yScale = 0.96F + breathe;
				body.xScale = 1.04F - breathe;
				body.zScale = body.xScale;
			}
			body.yRot = state.yRot * Mth.DEG_TO_RAD * 0.5F;
			flower.yRot = Mth.sin(age * 0.07F) * 0.2F;
			rightAntenna.zRot += Mth.sin(age * 0.15F) * 0.12F;
			leftAntenna.zRot -= Mth.sin(age * 0.15F + 0.8F) * 0.12F;
		}
	}
}
