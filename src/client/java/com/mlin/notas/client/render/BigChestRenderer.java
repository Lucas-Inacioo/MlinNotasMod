package com.mlin.notas.client.render;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.chest.BigChestBlock;
import com.mlin.notas.chest.BigChestBlockEntity;
import com.mlin.notas.chest.ChestTier;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.object.chest.ChestModel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.ChestRenderer;
import net.minecraft.client.renderer.blockentity.state.ChestRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.sprite.SpriteGetter;
import net.minecraft.client.resources.model.sprite.SpriteId;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Desenha os baus grandes com o modelo do bau vanilla (tampa animada) e a textura do tier
 * ({@code assets/mlinnotas/textures/entity/chest/<iron|gold|diamond>.png}, no atlas de baus).
 */
public class BigChestRenderer implements BlockEntityRenderer<BigChestBlockEntity, BigChestRenderer.State> {
	/** Estado de render do bau vanilla + o tier, para escolher a textura. */
	public static class State extends ChestRenderState {
		public ChestTier tier = ChestTier.IRON;
	}

	private static final Map<ChestTier, SpriteId> SPRITES = new EnumMap<>(ChestTier.class);

	static {
		for (ChestTier tier : ChestTier.values()) {
			SPRITES.put(tier, Sheets.CHEST_MAPPER.apply(MlinNotas.id(textureName(tier))));
		}
	}

	private final SpriteGetter sprites;
	private final ChestModel model;

	public BigChestRenderer(BlockEntityRendererProvider.Context context) {
		this.sprites = context.sprites();
		this.model = new ChestModel(context.bakeLayer(ModelLayers.CHEST));
	}

	/** Nome da textura em entity/chest para o tier (iron, gold, diamond). */
	public static String textureName(ChestTier tier) {
		return tier.blockName().replace("_chest", "");
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(BigChestBlockEntity blockEntity, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
		BlockState blockState = blockEntity.getBlockState();
		state.facing = blockState.hasProperty(BigChestBlock.FACING) ? blockState.getValue(BigChestBlock.FACING) : Direction.SOUTH;
		state.tier = blockEntity.tier();
		state.open = blockEntity.getOpenNess(partialTicks);
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		poseStack.pushPose();
		poseStack.mulPose(ChestRenderer.modelTransformation(state.facing));
		// mesma curva do bau vanilla: abre rapido e desacelera no fim
		float open = 1.0F - state.open;
		open = 1.0F - open * open * open;
		SpriteId sprite = SPRITES.get(state.tier);
		collector.submitModel(model, open, poseStack, state.lightCoords, OverlayTexture.NO_OVERLAY, -1, sprite, sprites, 0);
		if (state.breakProgress != null) {
			collector.order(1).submitCrumblingOverlay(model, open, poseStack, sprite.renderType(model.renderType()),
					state.lightCoords, OverlayTexture.NO_OVERLAY, -1, state.breakProgress);
		}
		poseStack.popPose();
	}
}
