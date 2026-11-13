package com.mlin.notas.client.variants;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.variants.MobVariant;
import net.fabricmc.fabric.api.client.rendering.v1.RenderStateDataKey;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Texturas das variantes de bioma: a textura vanilla devolvida pelo renderer do mob base
 * ({@code minecraft:textures/entity/zombie/zombie.png}) vira a recolorida da variante
 * ({@code mlinnotas:textures/entity/variant/mossy_zombie/zombie.png}), gerada por scripts/VariantTexGen.java.
 * A variante chega ao render state pela chave {@link #VARIANT} (mixin {@code LivingEntityRendererVariantMixin}).
 */
public final class MobVariantTextures {
	/** Id da variante (attachment {@code mlinnotas:mob_variant}) copiado para o render state. */
	public static final RenderStateDataKey<String> VARIANT = RenderStateDataKey.create(() -> "mlinnotas:mob_variant");

	private MobVariantTextures() {
	}

	/** Textura da variante para a textura vanilla, ou a propria vanilla se a variante nao tiver aquela textura. */
	public static Identifier textureFor(@Nullable String variantId, Identifier vanilla) {
		if (variantId == null || !"minecraft".equals(vanilla.getNamespace())) {
			return vanilla;
		}
		MobVariant variant = MobVariant.byId(variantId).orElse(null);
		if (variant == null) {
			return vanilla;
		}
		String path = vanilla.getPath();
		String file = path.substring(path.lastIndexOf('/') + 1);
		if (!expectedFiles(variant).contains(file)) {
			return vanilla;
		}
		return MlinNotas.id("textures/entity/variant/" + variant.id() + "/" + file);
	}

	/** Arquivos que o VariantTexGen gera para cada variante (adulto e filhote quando existe). */
	public static java.util.List<String> expectedFiles(MobVariant variant) {
		return switch (variant) {
			case MOSSY_ZOMBIE -> java.util.List.of("zombie.png", "zombie_baby.png");
			case SNOW_SPIDER -> java.util.List.of("spider.png");
			case BLAZING_SKELETON -> java.util.List.of("skeleton.png");
			case CRYSTAL_CREEPER -> java.util.List.of("creeper.png");
			case MANGROVE_DROWNED -> java.util.List.of("drowned.png", "drowned_baby.png", "drowned_outer_layer.png", "drowned_outer_layer_baby.png");
			case ASHEN_WITHER_SKELETON -> java.util.List.of("wither_skeleton.png");
		};
	}
}
