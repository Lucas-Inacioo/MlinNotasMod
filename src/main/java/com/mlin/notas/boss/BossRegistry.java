package com.mlin.notas.boss;

import com.mlin.notas.MlinNotas;
import com.mlin.notas.registry.ModBlocks;
import com.mlin.notas.registry.ModItems;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ItemLore;
import java.util.List;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * Chefes: entidades, projeteis, altares, itens que eles soltam, Escama de Dragao e os covis (estrutura {@code mlinnotas:lair}).
 * Renderers em {@code client/render/BossRenderers}.
 *
 * <p>{@link #COBALT_ALTAR} e o bloco que a sala do altar da mina desativada (ruinas, marcador {@code boss_altar}) deve
 * usar no lugar da magnetita provisoria.</p>
 */
public final class BossRegistry {
	// --- altares (inquebraveis: ficam nas estruturas) ---
	public static final Block COBALT_ALTAR = altar("cobalt_altar", BossKind.COBALT_GOLEM, MapColor.COLOR_BLUE, 6);
	public static final Block ABYSSAL_ALTAR = altar("abyssal_altar", BossKind.ABYSSAL_GUARDIAN, MapColor.COLOR_CYAN, 4);
	public static final Block ASTRAL_ALTAR = altar("astral_altar", BossKind.ASTRAL_HERALD, MapColor.COLOR_MAGENTA, 10);

	// --- chefes ---
	public static final EntityType<CobaltGolem> COBALT_GOLEM = entity("cobalt_golem",
			EntityType.Builder.of(CobaltGolem::new, MobCategory.MONSTER).sized(2.0F, 3.2F).eyeHeight(2.7F).clientTrackingRange(10));
	public static final EntityType<AbyssalGuardian> ABYSSAL_GUARDIAN = entity("abyssal_guardian",
			EntityType.Builder.of(AbyssalGuardian::new, MobCategory.MONSTER).sized(1.4F, 3.4F).eyeHeight(2.9F).clientTrackingRange(10));
	public static final EntityType<IgneousColossus> IGNEOUS_COLOSSUS = entity("igneous_colossus",
			EntityType.Builder.of(IgneousColossus::new, MobCategory.MONSTER).sized(2.6F, 4.4F).eyeHeight(3.8F).fireImmune().clientTrackingRange(10));
	public static final EntityType<AstralHerald> ASTRAL_HERALD = entity("astral_herald",
			EntityType.Builder.of(AstralHerald::new, MobCategory.MONSTER).sized(1.0F, 2.4F).eyeHeight(2.0F).clientTrackingRange(10));

	// --- projeteis ---
	public static final EntityType<BossProjectile> COBALT_ROCK = entity("cobalt_rock",
			EntityType.Builder.<BossProjectile>of(BossProjectile::new, MobCategory.MISC).sized(0.6F, 0.6F).clientTrackingRange(6).updateInterval(5));
	public static final EntityType<BossProjectile> MAGMA_METEOR = entity("magma_meteor",
			EntityType.Builder.<BossProjectile>of(BossProjectile::new, MobCategory.MISC).sized(0.8F, 0.8F).fireImmune().clientTrackingRange(6).updateInterval(5));
	public static final EntityType<BossProjectile> STAR_BOLT = entity("star_bolt",
			EntityType.Builder.<BossProjectile>of(BossProjectile::new, MobCategory.MISC).sized(0.4F, 0.4F).clientTrackingRange(6).updateInterval(2));
	/** Meteoro do vazio (Dragao do Ender reformulado). */
	public static final EntityType<BossProjectile> VOID_METEOR = entity("void_meteor",
			EntityType.Builder.<BossProjectile>of(BossProjectile::new, MobCategory.MISC).sized(0.9F, 0.9F).fireImmune().clientTrackingRange(8).updateInterval(2));

	// --- itens ---
	static {
		for (Block block : new Block[] {COBALT_ALTAR, ABYSSAL_ALTAR, ASTRAL_ALTAR}) {
			ModItems.register(BuiltInRegistries.BLOCK.getKey(block).getPath(), p -> new BlockItem(block, p.useBlockDescriptionPrefix()),
					new Item.Properties().rarity(Rarity.RARE).component(DataComponents.LORE, lore("block.mlinnotas." + BuiltInRegistries.BLOCK.getKey(block).getPath())));
		}
	}

	/** Coracao de Cobalto (Golem de Cobalto). */
	public static final Item COBALT_HEART = ModItems.register("cobalt_heart", Item::new, new Item.Properties().rarity(Rarity.RARE).component(DataComponents.LORE, lore("item.mlinnotas.cobalt_heart")));
	/** Nucleo Abissal (Guardiao das Profundezas). */
	public static final Item ABYSSAL_CORE = ModItems.register("abyssal_core", Item::new, new Item.Properties().rarity(Rarity.RARE).component(DataComponents.LORE, lore("item.mlinnotas.abyssal_core")));
	/** Brasa do Colosso (Colosso Igneo); nao queima na lava. */
	public static final Item COLOSSUS_EMBER = ModItems.register("colossus_ember", Item::new,
			new Item.Properties().rarity(Rarity.EPIC).fireResistant().component(DataComponents.LORE, lore("item.mlinnotas.colossus_ember")));
	/** Fragmento Astral (Arauto Astral). */
	public static final Item ASTRAL_FRAGMENT = ModItems.register("astral_fragment", Item::new, new Item.Properties().rarity(Rarity.EPIC).component(DataComponents.LORE, lore("item.mlinnotas.astral_fragment")));
	/** Escama de Dragao (dragao do End, 2 a 4 por morte). */
	public static final Item DRAGON_SCALE = ModItems.register("dragon_scale", Item::new, new Item.Properties().rarity(Rarity.EPIC).fireResistant().component(DataComponents.LORE, lore("item.mlinnotas.dragon_scale")));

	public static final Item COBALT_GOLEM_SPAWN_EGG = ModItems.register("cobalt_golem_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(COBALT_GOLEM));
	public static final Item ABYSSAL_GUARDIAN_SPAWN_EGG = ModItems.register("abyssal_guardian_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ABYSSAL_GUARDIAN));
	public static final Item IGNEOUS_COLOSSUS_SPAWN_EGG = ModItems.register("igneous_colossus_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(IGNEOUS_COLOSSUS));
	public static final Item ASTRAL_HERALD_SPAWN_EGG = ModItems.register("astral_herald_spawn_egg", SpawnEggItem::new,
			new Item.Properties().spawnEgg(ASTRAL_HERALD));

	// --- covis ---
	public static final StructureType<LairStructure> LAIR = Registry.register(BuiltInRegistries.STRUCTURE_TYPE, MlinNotas.id("lair"), () -> LairStructure.CODEC);
	public static final StructurePieceType LAIR_PIECE = Registry.register(BuiltInRegistries.STRUCTURE_PIECE, MlinNotas.id("lair"),
			(StructurePieceType.ContextlessType) LairPiece::new);

	private BossRegistry() {
	}

	/** Linha cinza no tooltip com a chave {@code <prefix>.tooltip}. */
	private static ItemLore lore(String prefix) {
		return new ItemLore(List.of(Component.translatable(prefix + ".tooltip").withStyle(ChatFormatting.GRAY)));
	}

	/** Estrutura do covil ({@code /locate structure mlinnotas:<id>}). */
	public static ResourceKey<Structure> lairKey(LairKind kind) {
		return ResourceKey.create(Registries.STRUCTURE, MlinNotas.id(kind.id()));
	}

	private static Block altar(String name, BossKind kind, MapColor color, int light) {
		return Blocks.register(ModBlocks.key(name), p -> new BossAltarBlock(kind, p), BlockBehaviour.Properties.of().mapColor(color)
				.strength(-1.0F, 3600000.0F).noLootTable().sound(com.mlin.notas.registry.ModSounds.BOSS_ALTAR).lightLevel(state -> light).pushReaction(PushReaction.IMMOVEABLE));
	}

	private static <T extends Entity> EntityType<T> entity(String name, EntityType.Builder<T> builder) {
		ResourceKey<EntityType<?>> key = ResourceKey.create(Registries.ENTITY_TYPE, MlinNotas.id(name));
		return Registry.register(BuiltInRegistries.ENTITY_TYPE, key, builder.build(key));
	}

	@SuppressWarnings("unchecked")
	public static EntityType<? extends BossMob> type(BossKind kind) {
		return switch (kind) {
			case COBALT_GOLEM -> COBALT_GOLEM;
			case ABYSSAL_GUARDIAN -> ABYSSAL_GUARDIAN;
			case IGNEOUS_COLOSSUS -> IGNEOUS_COLOSSUS;
			case ASTRAL_HERALD -> ASTRAL_HERALD;
		};
	}

	/** Item consumido no altar do chefe (o mesmo id de {@link BossKind#offering()}). */
	public static Item offeringItem(BossKind kind) {
		return switch (kind) {
			case COBALT_GOLEM -> Items.IRON_BLOCK;
			case ABYSSAL_GUARDIAN -> Items.ECHO_SHARD;
			case ASTRAL_HERALD -> ModItems.SENTINEL_CORE;
			case IGNEOUS_COLOSSUS -> Items.AIR;
		};
	}

	public static Item dropItem(BossKind kind) {
		return switch (kind) {
			case COBALT_GOLEM -> COBALT_HEART;
			case ABYSSAL_GUARDIAN -> ABYSSAL_CORE;
			case IGNEOUS_COLOSSUS -> COLOSSUS_EMBER;
			case ASTRAL_HERALD -> ASTRAL_FRAGMENT;
		};
	}

	public static void init() {
		FabricDefaultAttributeRegistry.register(COBALT_GOLEM, CobaltGolem.createAttributes());
		FabricDefaultAttributeRegistry.register(ABYSSAL_GUARDIAN, AbyssalGuardian.createAttributes());
		FabricDefaultAttributeRegistry.register(IGNEOUS_COLOSSUS, IgneousColossus.createAttributes());
		FabricDefaultAttributeRegistry.register(ASTRAL_HERALD, AstralHerald.createAttributes());
		DragonScales.init();
	}
}
