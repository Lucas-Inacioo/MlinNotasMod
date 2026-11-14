package com.mlin.notas.client.grave;

import com.mlin.notas.grave.GraveBlock;
import com.mlin.notas.grave.GraveBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Grava o nome do dono e o dia da morte na frente da lapide (texto escuro, como entalhado na pedra). */
public class GraveRenderer implements BlockEntityRenderer<GraveBlockEntity, GraveRenderer.State> {
	/** Largura util dentro da moldura entalhada (blocos). */
	private static final float FACE_WIDTH = 6.0F / 16.0F;
	private static final float MAX_SCALE = 0.0105F;
	private static final float DAY_SCALE = 0.0065F;
	/** Frente da pedra: 2 px a frente do centro do bloco. */
	private static final float FACE_Z = 2.0F / 16.0F + 0.004F;
	private static final int NAME_COLOR = 0xFF2B2B2E;
	private static final int DAY_COLOR = 0xFF3E3E42;

	private final Font font;

	public static class State extends BlockEntityRenderState {
		Direction facing = Direction.NORTH;
		String name = "";
		long day;
	}

	public GraveRenderer(BlockEntityRendererProvider.Context context) {
		this.font = context.font();
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(GraveBlockEntity grave, State state, float partialTicks, Vec3 cameraPosition,
			ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
		BlockEntityRenderer.super.extractRenderState(grave, state, partialTicks, cameraPosition, breakProgress);
		BlockState blockState = grave.getBlockState();
		state.facing = blockState.hasProperty(GraveBlock.FACING) ? blockState.getValue(GraveBlock.FACING) : Direction.NORTH;
		state.name = grave.ownerName();
		state.day = grave.day();
	}

	@Override
	public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		if (state.name.isEmpty()) {
			return;
		}
		poseStack.pushPose();
		poseStack.translate(0.5F, 0.0F, 0.5F);
		// depois da rotacao, +z local aponta para a frente da lapide
		poseStack.mulPose(new org.joml.Matrix4f().rotate(Axis.YP.rotationDegrees(-state.facing.toYRot())));
		poseStack.translate(0.0F, 0.0F, FACE_Z);

		FormattedCharSequence name = Component.literal(state.name).getVisualOrderText();
		float scale = Math.min(MAX_SCALE, FACE_WIDTH / Math.max(1, font.width(name)));
		poseStack.pushPose();
		poseStack.translate(0.0F, 8.6F / 16.0F, 0.0F);
		poseStack.scale(scale, -scale, scale);
		collector.submitText(poseStack, -font.width(name) / 2.0F, -font.lineHeight / 2.0F, name, false, Font.DisplayMode.POLYGON_OFFSET,
				state.lightCoords, NAME_COLOR, 0, 0);
		poseStack.popPose();

		FormattedCharSequence day = Component.translatable("block.mlinnotas.gravestone.day", state.day).getVisualOrderText();
		poseStack.pushPose();
		poseStack.translate(0.0F, 6.4F / 16.0F, 0.0F);
		poseStack.scale(DAY_SCALE, -DAY_SCALE, DAY_SCALE);
		collector.submitText(poseStack, -font.width(day) / 2.0F, -font.lineHeight / 2.0F, day, false, Font.DisplayMode.POLYGON_OFFSET,
				state.lightCoords, DAY_COLOR, 0, 0);
		poseStack.popPose();
		poseStack.popPose();
	}
}
