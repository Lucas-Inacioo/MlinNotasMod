package com.mlin.notas.client.render;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.boss.BossMob;
import com.mlin.notas.boss.BossRegistry;
import com.mlin.notas.boss.BossRules;
import com.mlin.notas.boss.IgneousColossus;
import com.mlin.notas.client.render.BossModels.BossState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * Renderers dos chefes (modelos de {@link BossModels}, texturas de scripts/BossTexGen.java) e dos projeteis (o item
 * voando, maior que um item normal). Durante o surgimento o chefe sobe de dentro do chao.
 */
public final class BossRenderers {
	private BossRenderers() {
	}

	public static void init() {
		BossModels.init();
		EntityRenderers.register(BossRegistry.COBALT_GOLEM, c -> new BossRenderer<>(c, new BossModels.GolemModel(c.bakeLayer(BossModels.GOLEM)),
				"cobalt_golem", 1.3F, 0.95F, 0));
		EntityRenderers.register(BossRegistry.ABYSSAL_GUARDIAN, c -> new BossRenderer<>(c, new BossModels.GuardianModel(c.bakeLayer(BossModels.GUARDIAN)),
				"abyssal_guardian", 0.9F, 0.95F, 0));
		EntityRenderers.register(BossRegistry.IGNEOUS_COLOSSUS, c -> new BossRenderer<>(c, new BossModels.ColossusModel(c.bakeLayer(BossModels.COLOSSUS)),
				"igneous_colossus", 1.6F, 1.1F, 11));
		EntityRenderers.register(BossRegistry.ASTRAL_HERALD, c -> new BossRenderer<>(c, new BossModels.HeraldModel(c.bakeLayer(BossModels.HERALD)),
				"astral_herald", 0.6F, 1.1F, 13));
		EntityRenderers.register(BossRegistry.COBALT_ROCK, c -> new ThrownItemRenderer<>(c, 2.2F, false));
		EntityRenderers.register(BossRegistry.MAGMA_METEOR, c -> new ThrownItemRenderer<>(c, 2.6F, true));
		EntityRenderers.register(BossRegistry.STAR_BOLT, c -> new ThrownItemRenderer<>(c, 1.4F, true));
		EntityRenderers.register(BossRegistry.VOID_METEOR, c -> new ThrownItemRenderer<>(c, 2.8F, true));
	}

	/** Renderer generico: textura, sombra, escala do modelo e luz minima (brilho proprio). */
	public static class BossRenderer<E extends BossMob, M extends EntityModel<BossState>> extends MobRenderer<E, BossState, M> {
		private final Identifier texture;
		private final float modelScale;
		private final int minLight;

		public BossRenderer(EntityRendererProvider.Context context, M model, String name, float shadow, float modelScale, int minLight) {
			super(context, model, shadow);
			this.texture = MlinNotas.id("textures/entity/" + name + ".png");
			this.modelScale = modelScale;
			this.minLight = minLight;
		}

		@Override
		public Identifier getTextureLocation(BossState state) {
			return texture;
		}

		@Override
		public BossState createRenderState() {
			return new BossState();
		}

		@Override
		public void extractRenderState(E entity, BossState state, float partialTicks) {
			super.extractRenderState(entity, state, partialTicks);
			state.animTicks = entity.animTicks() > 0 ? entity.animTicks() - partialTicks : 0.0F;
			state.animKind = entity.animKind();
			state.phase = entity.phase();
			state.dormant = entity.dormant();
			float spawn = entity.spawnTicks() > 0 ? entity.spawnTicks() - partialTicks : 0.0F;
			state.spawnProgress = 1.0F - Math.max(0.0F, spawn) / BossRules.SPAWN_TICKS;
		}

		/** Escala do modelo e o "subir do chao" do surgimento (em espaco invertido: y positivo e para baixo). */
		@Override
		protected void scale(BossState state, PoseStack poseStack) {
			poseStack.scale(modelScale, modelScale, modelScale);
			if (state.spawnProgress < 1.0F) {
				float sink = (1.0F - state.spawnProgress) * state.boundingBoxHeight / modelScale;
				poseStack.translate(0.0F, sink, 0.0F);
			}
		}

		@Override
		protected int getBlockLightLevel(E entity, BlockPos pos) {
			int light = super.getBlockLightLevel(entity, pos);
			if (entity instanceof IgneousColossus colossus && colossus.dormant()) {
				return Math.max(light, 6);
			}
			return Math.max(light, minLight);
		}
	}
}
