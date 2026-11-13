package com.mlin.notas.client.render;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.boss.AbyssalGuardian;
import com.mlin.notas.boss.AstralHerald;
import com.mlin.notas.boss.CobaltGolem;
import com.mlin.notas.boss.IgneousColossus;
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
import net.minecraft.util.Mth;

/**
 * Modelos proprios dos chefes. Coordenadas em pixels, y para baixo, chao em y = 24. As texturas sao pintadas por
 * scripts/BossTexGen.java seguindo exatamente os {@code texOffs} daqui: mudou um cubo, mude o BossTexGen junto.
 */
public final class BossModels {
	public static final ModelLayerLocation GOLEM = layer("cobalt_golem");
	public static final ModelLayerLocation GUARDIAN = layer("abyssal_guardian");
	public static final ModelLayerLocation COLOSSUS = layer("igneous_colossus");
	public static final ModelLayerLocation HERALD = layer("astral_herald");

	private BossModels() {
	}

	private static ModelLayerLocation layer(String name) {
		return new ModelLayerLocation(MlinNotas.id(name), "main");
	}

	public static void init() {
		ModelLayerRegistry.registerModelLayer(GOLEM, GolemModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(GUARDIAN, GuardianModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(COLOSSUS, ColossusModel::createBodyLayer);
		ModelLayerRegistry.registerModelLayer(HERALD, HeraldModel::createBodyLayer);
	}

	/** Estado comum: animacao do ataque (ticks restantes e qual), fase, progresso do surgimento e se dorme. */
	public static class BossState extends LivingEntityRenderState {
		public float animTicks;
		public int animKind;
		public int phase = 1;
		/** 0 = comecando a surgir, 1 = pronto. */
		public float spawnProgress = 1.0F;
		public boolean dormant;

		/** 0..1 ao longo da animacao do ataque {@code kind}, ou -1 se nao estiver nela. */
		public float progress(int kind) {
			return animTicks > 0 && animKind == kind ? 1.0F - animTicks / 20.0F : -1.0F;
		}
	}

	private static float swing(float progress) {
		return progress < 0 ? 0.0F : Mth.sin(progress * Mth.PI);
	}

	// ------------------------------------------------------------------------------------------------ golem

	/**
	 * Golem de Cobalto 128x128: construto corcunda de pedra que anda apoiado nos punhos. Tronco 26x18x16 (0,0), cabeca
	 * afundada 10x9x10 (84,0) com uma fenda de olho, ombreiras 12x5x12 (0,61), cristais de cobalto nas costas 3x8x3
	 * (84,19) e 4x11x4 (96,19), bracos 9x18x9 (0,34) com punhos enormes 11x10x11 (36,34) e pernas curtas 8x12x8 (80,34).
	 */
	public static class GolemModel extends EntityModel<BossState> {
		private final ModelPart body;
		private final ModelPart head;
		private final ModelPart rightArm;
		private final ModelPart leftArm;
		private final ModelPart rightLeg;
		private final ModelPart leftLeg;

		public GolemModel(ModelPart root) {
			super(root);
			this.body = root.getChild("body");
			this.head = body.getChild("head");
			this.rightArm = root.getChild("right_arm");
			this.leftArm = root.getChild("left_arm");
			this.rightLeg = root.getChild("right_leg");
			this.leftLeg = root.getChild("left_leg");
		}

		public static LayerDefinition createBodyLayer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0).addBox(-13.0F, -18.0F, -8.0F, 26, 18, 16),
					PartPose.offsetAndRotation(0.0F, 12.0F, 1.0F, 0.32F, 0.0F, 0.0F));
			body.addOrReplaceChild("head", CubeListBuilder.create().texOffs(84, 0).addBox(-5.0F, -8.0F, -8.0F, 10, 9, 10),
					PartPose.offsetAndRotation(0.0F, -17.0F, -5.0F, -0.32F, 0.0F, 0.0F));
			CubeListBuilder plate = CubeListBuilder.create().texOffs(0, 61).addBox(-6.0F, -3.0F, -6.0F, 12, 5, 12);
			body.addOrReplaceChild("right_plate", plate, PartPose.offsetAndRotation(-14.0F, -16.0F, 0.0F, 0.0F, 0.0F, -0.25F));
			body.addOrReplaceChild("left_plate", plate, PartPose.offsetAndRotation(14.0F, -16.0F, 0.0F, 0.0F, 0.0F, 0.25F));
			CubeListBuilder small = CubeListBuilder.create().texOffs(84, 19).addBox(-1.5F, -8.0F, -1.5F, 3, 8, 3);
			CubeListBuilder big = CubeListBuilder.create().texOffs(96, 19).addBox(-2.0F, -11.0F, -2.0F, 4, 11, 4);
			body.addOrReplaceChild("crystal0", big, PartPose.offsetAndRotation(2.0F, -17.0F, 4.0F, -0.55F, 0.0F, 0.2F));
			body.addOrReplaceChild("crystal1", small, PartPose.offsetAndRotation(-5.0F, -17.0F, 5.0F, -0.65F, 0.0F, -0.35F));
			body.addOrReplaceChild("crystal2", small, PartPose.offsetAndRotation(8.0F, -13.0F, 7.0F, -1.0F, 0.0F, 0.6F));
			body.addOrReplaceChild("crystal3", big, PartPose.offsetAndRotation(-9.0F, -11.0F, 7.0F, -1.1F, 0.0F, -0.5F));
			body.addOrReplaceChild("crystal4", small, PartPose.offsetAndRotation(1.0F, -7.0F, 8.0F, -1.3F, 0.0F, 0.1F));
			body.addOrReplaceChild("crystal5", big, PartPose.offsetAndRotation(-12.0F, -17.0F, -2.0F, -0.3F, 0.0F, -0.7F));
			body.addOrReplaceChild("crystal6", big, PartPose.offsetAndRotation(12.0F, -17.0F, -1.0F, -0.3F, 0.0F, 0.7F));
			body.addOrReplaceChild("crystal7", small, PartPose.offsetAndRotation(-3.0F, -14.0F, 8.0F, -1.2F, 0.0F, -0.2F));
			CubeListBuilder arm = CubeListBuilder.create().texOffs(0, 34).addBox(-4.5F, -2.0F, -4.5F, 9, 18, 9);
			CubeListBuilder fist = CubeListBuilder.create().texOffs(36, 34).addBox(-5.5F, 0.0F, -5.5F, 11, 10, 11);
			root.addOrReplaceChild("right_arm", arm, PartPose.offset(-17.0F, -4.0F, -2.0F)).addOrReplaceChild("fist", fist, PartPose.offset(0.0F, 16.0F, 0.0F));
			root.addOrReplaceChild("left_arm", arm, PartPose.offset(17.0F, -4.0F, -2.0F)).addOrReplaceChild("fist", fist, PartPose.offset(0.0F, 16.0F, 0.0F));
			CubeListBuilder leg = CubeListBuilder.create().texOffs(80, 34).addBox(-4.0F, 0.0F, -4.0F, 8, 12, 8);
			root.addOrReplaceChild("right_leg", leg, PartPose.offset(-6.5F, 12.0F, 2.0F));
			root.addOrReplaceChild("left_leg", leg, PartPose.offset(6.5F, 12.0F, 2.0F));
			return LayerDefinition.create(mesh, 128, 128);
		}

		@Override
		public void setupAnim(BossState state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			float walk = state.walkAnimationPos * 0.5F;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			rightLeg.xRot = Mth.cos(walk) * 0.9F * speed;
			leftLeg.xRot = -Mth.cos(walk) * 0.9F * speed;
			rightArm.xRot = -Mth.cos(walk) * 0.5F * speed;
			leftArm.xRot = Mth.cos(walk) * 0.5F * speed;
			body.yScale = 1.0F + Mth.sin(age * 0.08F) * 0.015F;
			head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.6F;
			head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5F;
			rightArm.zRot = 0.08F;
			leftArm.zRot = -0.08F;

			float slam = swing(state.progress(CobaltGolem.ANIM_SLAM));
			if (slam > 0) {
				rightArm.xRot = leftArm.xRot = -2.9F * slam;
				body.xRot += 0.25F * slam;
			}
			float toss = swing(state.progress(CobaltGolem.ANIM_THROW));
			if (toss > 0) {
				rightArm.xRot = -3.0F * toss;
				rightArm.zRot = 0.3F * toss;
			}
			float charge = state.progress(CobaltGolem.ANIM_CHARGE) >= 0 ? 1.0F : 0.0F;
			if (charge > 0) {
				body.xRot += 0.35F;
				rightArm.xRot = leftArm.xRot = 0.9F;
				rightLeg.xRot = Mth.cos(age * 1.2F) * 1.1F;
				leftLeg.xRot = -rightLeg.xRot;
			}
			float punch = swing(state.progress(CobaltGolem.ANIM_PUNCH));
			if (punch > 0) {
				rightArm.xRot = -1.7F * punch;
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ guardiao

	/**
	 * Guardiao das Profundezas 128x128: magro e alto, sem rosto. Caixa toracica 16x12x10 (0,0) com o coracao de sculk
	 * 6x6x2 (52,0) brilhando, ombreiras 6x4x6 (72,0), cabeca 8x10x8 (0,22) com galhadas 2x10x2 (32,22) e ramos 2x5x2
	 * (40,22), dois tentaculos nas costas 2x14x2 (48,22), coluna 6x8x5 (58,64), bacia 12x4x7 (20,64), pernas 5x20x5
	 * (0,64), bracos longos 4x24x4 (0,90) com garras 5x6x5 (16,90).
	 */
	public static class GuardianModel extends EntityModel<BossState> {
		private final ModelPart chest;
		private final ModelPart core;
		private final ModelPart head;
		private final ModelPart rightArm;
		private final ModelPart leftArm;
		private final ModelPart rightLeg;
		private final ModelPart leftLeg;
		private final ModelPart rightTendril;
		private final ModelPart leftTendril;

		public GuardianModel(ModelPart root) {
			super(root);
			this.chest = root.getChild("chest");
			this.core = chest.getChild("core");
			this.head = chest.getChild("head");
			this.rightTendril = chest.getChild("right_tendril");
			this.leftTendril = chest.getChild("left_tendril");
			this.rightArm = root.getChild("right_arm");
			this.leftArm = root.getChild("left_arm");
			this.rightLeg = root.getChild("right_leg");
			this.leftLeg = root.getChild("left_leg");
		}

		public static LayerDefinition createBodyLayer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 64).addBox(-2.5F, 0.0F, -2.5F, 5, 20, 5);
			root.addOrReplaceChild("right_leg", leg, PartPose.offset(-4.0F, 4.0F, 0.0F));
			root.addOrReplaceChild("left_leg", leg, PartPose.offset(4.0F, 4.0F, 0.0F));
			root.addOrReplaceChild("pelvis", CubeListBuilder.create().texOffs(20, 64).addBox(-6.0F, 0.0F, -3.5F, 12, 4, 7), PartPose.offset(0.0F, 0.0F, 0.0F));
			root.addOrReplaceChild("spine", CubeListBuilder.create().texOffs(58, 64).addBox(-3.0F, -8.0F, -2.5F, 6, 8, 5), PartPose.offset(0.0F, 0.0F, 0.5F));
			PartDefinition chest = root.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 0).addBox(-8.0F, -12.0F, -5.0F, 16, 12, 10),
					PartPose.offsetAndRotation(0.0F, -8.0F, 0.0F, 0.12F, 0.0F, 0.0F));
			chest.addOrReplaceChild("core", CubeListBuilder.create().texOffs(52, 0).addBox(-3.0F, -3.0F, -1.0F, 6, 6, 2), PartPose.offset(0.0F, -6.0F, -5.0F));
			CubeListBuilder pad = CubeListBuilder.create().texOffs(72, 0).addBox(-3.0F, -2.0F, -3.0F, 6, 4, 6);
			chest.addOrReplaceChild("right_pad", pad, PartPose.offsetAndRotation(-8.0F, -11.0F, 0.0F, 0.0F, 0.0F, -0.3F));
			chest.addOrReplaceChild("left_pad", pad, PartPose.offsetAndRotation(8.0F, -11.0F, 0.0F, 0.0F, 0.0F, 0.3F));
			PartDefinition head = chest.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 22).addBox(-4.0F, -10.0F, -4.0F, 8, 10, 8),
					PartPose.offsetAndRotation(0.0F, -12.0F, -1.0F, -0.12F, 0.0F, 0.0F));
			CubeListBuilder horn = CubeListBuilder.create().texOffs(32, 22).addBox(-1.0F, -10.0F, -1.0F, 2, 10, 2);
			CubeListBuilder branch = CubeListBuilder.create().texOffs(40, 22).addBox(-1.0F, -5.0F, -1.0F, 2, 5, 2);
			head.addOrReplaceChild("right_horn", horn, PartPose.offsetAndRotation(-3.0F, -9.0F, 0.0F, -0.2F, 0.0F, -0.55F))
					.addOrReplaceChild("branch", branch, PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, 0.0F, 0.0F, 0.9F));
			head.addOrReplaceChild("left_horn", horn, PartPose.offsetAndRotation(3.0F, -9.0F, 0.0F, -0.2F, 0.0F, 0.55F))
					.addOrReplaceChild("branch", branch, PartPose.offsetAndRotation(0.0F, -5.0F, 0.0F, 0.0F, 0.0F, -0.9F));
			CubeListBuilder tendril = CubeListBuilder.create().texOffs(48, 22).addBox(-1.0F, 0.0F, -1.0F, 2, 14, 2);
			chest.addOrReplaceChild("right_tendril", tendril, PartPose.offsetAndRotation(-4.0F, -10.0F, 5.0F, 0.7F, 0.0F, 0.2F));
			chest.addOrReplaceChild("left_tendril", tendril, PartPose.offsetAndRotation(4.0F, -10.0F, 5.0F, 0.7F, 0.0F, -0.2F));
			CubeListBuilder arm = CubeListBuilder.create().texOffs(0, 90).addBox(-2.0F, -1.0F, -2.0F, 4, 24, 4);
			CubeListBuilder claw = CubeListBuilder.create().texOffs(16, 90).addBox(-2.5F, 0.0F, -2.5F, 5, 6, 5);
			root.addOrReplaceChild("right_arm", arm, PartPose.offset(-10.5F, -18.0F, 0.0F)).addOrReplaceChild("claw", claw, PartPose.offset(0.0F, 23.0F, 0.0F));
			root.addOrReplaceChild("left_arm", arm, PartPose.offset(10.5F, -18.0F, 0.0F)).addOrReplaceChild("claw", claw, PartPose.offset(0.0F, 23.0F, 0.0F));
			return LayerDefinition.create(mesh, 128, 128);
		}

		@Override
		public void setupAnim(BossState state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			float walk = state.walkAnimationPos * 0.55F;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			rightLeg.xRot = Mth.cos(walk) * 0.8F * speed;
			leftLeg.xRot = -Mth.cos(walk) * 0.8F * speed;
			rightArm.xRot = -Mth.cos(walk) * 0.35F * speed + Mth.sin(age * 0.05F) * 0.05F;
			leftArm.xRot = Mth.cos(walk) * 0.35F * speed - Mth.sin(age * 0.05F) * 0.05F;
			rightArm.zRot = 0.12F;
			leftArm.zRot = -0.12F;
			chest.zRot = Mth.sin(age * 0.04F) * 0.04F;
			head.yRot = state.yRot * Mth.DEG_TO_RAD;
			head.zRot = Mth.sin(age * 0.03F) * 0.12F; // inclina a cabeca "escutando"
			float beat = Mth.abs(Mth.sin(age * (state.phase >= 2 ? 0.3F : 0.15F)));
			core.xScale = core.yScale = 1.0F + beat * 0.25F;
			rightTendril.xRot = 0.7F + Mth.sin(age * 0.1F) * 0.25F;
			leftTendril.xRot = 0.7F + Mth.sin(age * 0.1F + 1.5F) * 0.25F;
			rightTendril.zRot = 0.2F + Mth.cos(age * 0.07F) * 0.15F;
			leftTendril.zRot = -0.2F - Mth.cos(age * 0.07F + 1.0F) * 0.15F;

			float pulse = swing(state.progress(AbyssalGuardian.ANIM_PULSE));
			if (pulse > 0) {
				head.xRot -= 0.6F * pulse;
				chest.xScale = chest.zScale = 1.0F + 0.12F * pulse;
				rightArm.zRot = 0.9F * pulse;
				leftArm.zRot = -0.9F * pulse;
			}
			float roar = swing(state.progress(AbyssalGuardian.ANIM_ROAR));
			if (roar > 0) {
				head.xRot -= 0.9F * roar;
				rightArm.xRot = leftArm.xRot = -2.4F * roar;
				rightArm.zRot = 0.6F * roar;
				leftArm.zRot = -0.6F * roar;
			}
			float tendrils = swing(state.progress(AbyssalGuardian.ANIM_TENDRILS));
			if (tendrils > 0) {
				rightArm.xRot = leftArm.xRot = -1.2F * tendrils;
				chest.xRot += 0.4F * tendrils;
				rightTendril.xRot = leftTendril.xRot = 0.7F - 1.6F * tendrils;
			}
			float punch = swing(state.progress(AbyssalGuardian.ANIM_PUNCH));
			if (punch > 0) {
				leftArm.xRot = -1.9F * punch;
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ colosso

	/**
	 * Colosso Igneo 128x128: titã de basalto com rachaduras de magma. Peito 32x20x18 (0,0), cabeca 12x12x12 (0,38) com
	 * chifres 3x8x3 (100,0), colunas de basalto nos ombros 4x14x4 (112,0), cintura 22x6x14 (44,64), pernas 11x22x11
	 * (0,64), braco 10x16x10 (48,38) e antebraco 12x16x12 (0,98). Adormecido: sentado com as pernas para frente,
	 * cabeca baixa e bracos apoiados.
	 */
	public static class ColossusModel extends EntityModel<BossState> {
		private final ModelPart chest;
		private final ModelPart head;
		private final ModelPart waist;
		private final ModelPart rightArm;
		private final ModelPart leftArm;
		private final ModelPart rightLeg;
		private final ModelPart leftLeg;

		public ColossusModel(ModelPart root) {
			super(root);
			this.chest = root.getChild("chest");
			this.head = chest.getChild("head");
			this.waist = root.getChild("waist");
			this.rightArm = root.getChild("right_arm");
			this.leftArm = root.getChild("left_arm");
			this.rightLeg = root.getChild("right_leg");
			this.leftLeg = root.getChild("left_leg");
		}

		public static LayerDefinition createBodyLayer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			CubeListBuilder leg = CubeListBuilder.create().texOffs(0, 64).addBox(-5.5F, 0.0F, -5.5F, 11, 22, 11);
			root.addOrReplaceChild("right_leg", leg, PartPose.offset(-8.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("left_leg", leg, PartPose.offset(8.0F, 2.0F, 0.0F));
			root.addOrReplaceChild("waist", CubeListBuilder.create().texOffs(44, 64).addBox(-11.0F, -6.0F, -7.0F, 22, 6, 14), PartPose.offset(0.0F, 2.0F, 0.0F));
			PartDefinition chest = root.addOrReplaceChild("chest", CubeListBuilder.create().texOffs(0, 0).addBox(-16.0F, -20.0F, -9.0F, 32, 20, 18),
					PartPose.offsetAndRotation(0.0F, -4.0F, 0.0F, 0.1F, 0.0F, 0.0F));
			PartDefinition head = chest.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 38).addBox(-6.0F, -11.0F, -7.0F, 12, 12, 12),
					PartPose.offsetAndRotation(0.0F, -18.0F, -6.0F, -0.1F, 0.0F, 0.0F));
			CubeListBuilder horn = CubeListBuilder.create().texOffs(100, 0).addBox(-1.5F, -8.0F, -1.5F, 3, 8, 3);
			head.addOrReplaceChild("right_horn", horn, PartPose.offsetAndRotation(-5.0F, -10.0F, -2.0F, -0.35F, 0.0F, -0.6F));
			head.addOrReplaceChild("left_horn", horn, PartPose.offsetAndRotation(5.0F, -10.0F, -2.0F, -0.35F, 0.0F, 0.6F));
			CubeListBuilder pillar = CubeListBuilder.create().texOffs(112, 0).addBox(-2.0F, -14.0F, -2.0F, 4, 14, 4);
			float[][] pillars = {{13.0F, -15.0F, -4.0F, 0.1F}, {17.0F, -12.0F, 2.0F, 0.25F}, {10.5F, -13.0F, 5.0F, -0.05F}};
			for (int i = 0; i < pillars.length; i++) {
				float[] p = pillars[i];
				chest.addOrReplaceChild("right_pillar" + i, pillar, PartPose.offsetAndRotation(-p[0], p[1], p[2], 0.0F, 0.0F, -p[3]));
				chest.addOrReplaceChild("left_pillar" + i, pillar, PartPose.offsetAndRotation(p[0], p[1], p[2], 0.0F, 0.0F, p[3]));
			}
			CubeListBuilder arm = CubeListBuilder.create().texOffs(48, 38).addBox(-5.0F, -3.0F, -5.0F, 10, 16, 10);
			CubeListBuilder forearm = CubeListBuilder.create().texOffs(0, 98).addBox(-6.0F, 0.0F, -6.0F, 12, 16, 12);
			root.addOrReplaceChild("right_arm", arm, PartPose.offset(-21.0F, -20.0F, 0.0F))
					.addOrReplaceChild("forearm", forearm, PartPose.offset(0.0F, 13.0F, 0.0F));
			root.addOrReplaceChild("left_arm", arm, PartPose.offset(21.0F, -20.0F, 0.0F))
					.addOrReplaceChild("forearm", forearm, PartPose.offset(0.0F, 13.0F, 0.0F));
			return LayerDefinition.create(mesh, 128, 128);
		}

		@Override
		public void setupAnim(BossState state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			if (state.dormant) {
				// sentado no trono: tudo desce 16 px, pernas para frente, cabeca baixa, bracos apoiados nos joelhos
				float drop = 16.0F;
				for (ModelPart part : new ModelPart[] {chest, waist, rightArm, leftArm}) {
					part.y += drop;
				}
				rightLeg.y += drop;
				leftLeg.y += drop;
				rightLeg.xRot = leftLeg.xRot = -1.45F;
				rightLeg.yRot = 0.15F;
				leftLeg.yRot = -0.15F;
				chest.xRot = 0.35F + Mth.sin(age * 0.03F) * 0.03F;
				head.xRot = 0.6F;
				rightArm.xRot = leftArm.xRot = -0.9F;
				rightArm.zRot = 0.15F;
				leftArm.zRot = -0.15F;
				return;
			}
			float walk = state.walkAnimationPos * 0.45F;
			float speed = Math.min(1.0F, state.walkAnimationSpeed);
			rightLeg.xRot = Mth.cos(walk) * 0.7F * speed;
			leftLeg.xRot = -Mth.cos(walk) * 0.7F * speed;
			rightArm.xRot = -Mth.cos(walk) * 0.4F * speed;
			leftArm.xRot = Mth.cos(walk) * 0.4F * speed;
			rightArm.zRot = 0.1F;
			leftArm.zRot = -0.1F;
			chest.yScale = 1.0F + Mth.sin(age * 0.07F) * 0.02F;
			head.yRot = state.yRot * Mth.DEG_TO_RAD * 0.7F;
			head.xRot += state.xRot * Mth.DEG_TO_RAD * 0.5F;

			float wave = swing(state.progress(IgneousColossus.ANIM_WAVE));
			if (wave > 0) {
				rightArm.xRot = leftArm.xRot = -2.6F * wave;
				chest.xRot += 0.3F * wave;
			}
			float meteor = swing(state.progress(IgneousColossus.ANIM_METEOR));
			if (meteor > 0) {
				rightArm.xRot = leftArm.xRot = -3.0F * meteor;
				rightArm.zRot = -0.4F * meteor;
				leftArm.zRot = 0.4F * meteor;
				head.xRot -= 0.5F * meteor;
			}
			float lava = swing(state.progress(IgneousColossus.ANIM_LAVA));
			if (lava > 0) {
				rightArm.zRot = 1.2F * lava;
				leftArm.zRot = -1.2F * lava;
				rightLeg.xRot = -0.8F * lava;
			}
			float punch = swing(state.progress(IgneousColossus.ANIM_PUNCH));
			if (punch > 0) {
				rightArm.xRot = -1.6F * punch;
			}
		}
	}

	// ------------------------------------------------------------------------------------------------ arauto

	/**
	 * Arauto Astral 128x64: figura de manto que flutua, sem pernas. Capuz 9x9x9 (0,0) com rosto de estrelas no escuro,
	 * manto 10x10x6 (0,18), saia 12x8x8 (0,34) e barra 14x6x10 (40,34), mangas 4x10x4 (36,0) com maos 3x3x3 (52,0),
	 * aureola atras da cabeca (barras 16x1x1 em (64,0) e 1x14x1 em (64,4)) e 4 estilhacos de estrela 3x3x3 (72,4)
	 * orbitando.
	 */
	public static class HeraldModel extends EntityModel<BossState> {
		private static final int SHARDS = 4;
		private final ModelPart hood;
		private final ModelPart robe;
		private final ModelPart skirt;
		private final ModelPart hem;
		private final ModelPart halo;
		private final ModelPart rightArm;
		private final ModelPart leftArm;
		private final ModelPart[] shards = new ModelPart[SHARDS];

		public HeraldModel(ModelPart root) {
			super(root);
			this.hood = root.getChild("hood");
			this.robe = root.getChild("robe");
			this.skirt = robe.getChild("skirt");
			this.hem = skirt.getChild("hem");
			this.halo = root.getChild("halo");
			this.rightArm = root.getChild("right_arm");
			this.leftArm = root.getChild("left_arm");
			for (int i = 0; i < SHARDS; i++) {
				shards[i] = root.getChild("shard" + i);
			}
		}

		public static LayerDefinition createBodyLayer() {
			MeshDefinition mesh = new MeshDefinition();
			PartDefinition root = mesh.getRoot();
			root.addOrReplaceChild("hood", CubeListBuilder.create().texOffs(0, 0).addBox(-4.5F, -9.0F, -4.5F, 9, 9, 9), PartPose.offset(0.0F, -6.0F, 0.0F));
			PartDefinition robe = root.addOrReplaceChild("robe", CubeListBuilder.create().texOffs(0, 18).addBox(-5.0F, 0.0F, -3.0F, 10, 10, 6),
					PartPose.offset(0.0F, -6.0F, 0.0F));
			PartDefinition skirt = robe.addOrReplaceChild("skirt", CubeListBuilder.create().texOffs(0, 34).addBox(-6.0F, 0.0F, -4.0F, 12, 8, 8),
					PartPose.offset(0.0F, 10.0F, 0.0F));
			skirt.addOrReplaceChild("hem", CubeListBuilder.create().texOffs(40, 34).addBox(-7.0F, 0.0F, -5.0F, 14, 6, 10), PartPose.offset(0.0F, 8.0F, 0.0F));
			root.addOrReplaceChild("halo", CubeListBuilder.create()
					.texOffs(64, 0).addBox(-8.0F, -8.0F, 0.0F, 16, 1, 1)
					.texOffs(64, 0).addBox(-8.0F, 7.0F, 0.0F, 16, 1, 1)
					.texOffs(64, 4).addBox(-8.0F, -7.0F, 0.0F, 1, 14, 1)
					.texOffs(64, 4).addBox(7.0F, -7.0F, 0.0F, 1, 14, 1), PartPose.offset(0.0F, -11.0F, 6.0F));
			CubeListBuilder sleeve = CubeListBuilder.create().texOffs(36, 0).addBox(-2.0F, 0.0F, -2.0F, 4, 10, 4)
					.texOffs(52, 0).addBox(-1.5F, 10.0F, -1.5F, 3, 3, 3);
			root.addOrReplaceChild("right_arm", sleeve, PartPose.offsetAndRotation(-6.5F, -5.0F, 0.0F, 0.0F, 0.0F, 0.35F));
			root.addOrReplaceChild("left_arm", sleeve, PartPose.offsetAndRotation(6.5F, -5.0F, 0.0F, 0.0F, 0.0F, -0.35F));
			for (int i = 0; i < SHARDS; i++) {
				root.addOrReplaceChild("shard" + i, CubeListBuilder.create().texOffs(72, 4).addBox(-1.5F, -1.5F, -1.5F, 3, 3, 3),
						PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.785F, 0.785F, 0.0F));
			}
			return LayerDefinition.create(mesh, 128, 64);
		}

		@Override
		public void setupAnim(BossState state) {
			super.setupAnim(state);
			float age = state.ageInTicks;
			float bob = Mth.sin(age * 0.08F) * 1.5F;
			for (ModelPart part : new ModelPart[] {hood, robe, halo, rightArm, leftArm}) {
				part.y += bob;
			}
			hood.yRot = state.yRot * Mth.DEG_TO_RAD;
			hood.xRot = state.xRot * Mth.DEG_TO_RAD * 0.5F;
			halo.zRot = age * (state.phase >= 2 ? 0.06F : 0.02F);
			float drift = Math.min(1.0F, state.walkAnimationSpeed);
			skirt.xRot = 0.15F * drift + Mth.sin(age * 0.1F) * 0.05F;
			hem.xRot = 0.2F * drift + Mth.sin(age * 0.1F - 0.8F) * 0.08F;
			rightArm.xRot = Mth.sin(age * 0.06F) * 0.1F;
			leftArm.xRot = -Mth.sin(age * 0.06F) * 0.1F;
			float orbitSpeed = state.phase >= 2 ? 0.12F : 0.06F;
			for (int i = 0; i < SHARDS; i++) {
				float a = age * orbitSpeed + i * Mth.TWO_PI / SHARDS;
				shards[i].x = Mth.cos(a) * 12.0F;
				shards[i].z = Mth.sin(a) * 12.0F;
				shards[i].y = -2.0F + bob + Mth.sin(age * 0.1F + i) * 3.0F;
				shards[i].yRot = age * 0.2F;
			}

			float cast = swing(state.progress(AstralHerald.ANIM_CAST));
			if (cast > 0) {
				rightArm.xRot = leftArm.xRot = -1.6F * cast;
				rightArm.zRot = leftArm.zRot = 0.0F;
			}
			float blink = swing(state.progress(AstralHerald.ANIM_BLINK));
			if (blink > 0) {
				robe.xScale = robe.zScale = 1.0F - 0.5F * blink;
				hood.yScale = 1.0F + 0.3F * blink;
			}
			float summon = swing(state.progress(AstralHerald.ANIM_SUMMON));
			if (summon > 0) {
				rightArm.xRot = leftArm.xRot = -2.9F * summon;
				rightArm.zRot = 0.5F * summon;
				leftArm.zRot = -0.5F * summon;
				halo.xScale = halo.yScale = 1.0F + 0.4F * summon;
			}
		}
	}
}
