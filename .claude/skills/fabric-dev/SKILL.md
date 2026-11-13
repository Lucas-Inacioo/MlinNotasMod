---
name: fabric-dev
description: Padroes de desenvolvimento Fabric (Minecraft 26.x, Mojang mappings, Java 25) para este mod - registrar conteudo, eventos, comandos, mixins, client vs server, onde achar a API certa. Use ao implementar qualquer feature do mod.
---

# Desenvolvimento Fabric neste projeto

## Antes de escrever codigo

1. Leia `CLAUDE.md` (layout e regras).
2. O mapeamento e **Mojang** (oficial). Nomes Yarn nao compilam. Tabela rapida:

| Yarn (NAO usar) | Mojang (usar) |
|---|---|
| `MinecraftClient` | `net.minecraft.client.Minecraft` |
| `Text` | `net.minecraft.network.chat.Component` |
| `Identifier.of(ns, path)` | `Identifier.fromNamespaceAndPath(ns, path)` |
| `ServerCommandSource` | `net.minecraft.commands.CommandSourceStack` |
| `CommandManager.literal` | `net.minecraft.commands.Commands.literal` |
| `ServerPlayerEntity` | `net.minecraft.server.level.ServerPlayer` |
| `ServerWorld` | `net.minecraft.server.level.ServerLevel` |
| `TestContext` | `net.minecraft.gametest.framework.GameTestHelper` |
| `Registries.ITEM` | `net.minecraft.core.registries.BuiltInRegistries.ITEM` |
| `RegistryKeys` | `net.minecraft.core.registries.Registries` |

3. Se nao tiver certeza de um nome: `./gradlew genSources`, depois procure no jar `*-sources.jar` em `~/.gradle/caches/fabric-loom/` (ou use o IDE). Javadoc da Fabric API: https://maven.fabricmc.net/docs/ (escolha a versao de `gradle.properties`).

## Eventos (prefira a mixin)

Fabric API expoe hooks em `net.fabricmc.fabric.api.*`:

- Ciclo de vida: `ServerLifecycleEvents.SERVER_STARTED/SERVER_STOPPING`, `ServerTickEvents.END_SERVER_TICK`
- Jogador: `ServerPlayConnectionEvents.JOIN/DISCONNECT`, `ServerPlayerEvents`
- Comandos: `CommandRegistrationCallback.EVENT` (ver `command/NotaCommand.java`)
- Cliente: `ClientTickEvents`, `ClientLifecycleEvents`, `HudRenderCallback`/`HudLayerRegistrationCallback`, `KeyBindingHelper`
- Interacao: `UseBlockCallback`, `UseItemCallback`, `AttackEntityCallback`

Registre tudo no entrypoint (`MlinNotas.onInitialize` ou `MlinNotasClient.onInitializeClient`).

## Registrar conteudo

```java
// Item (26.x exige ResourceKey nas propriedades)
public static final ResourceKey<Item> CADERNO_KEY =
    ResourceKey.create(Registries.ITEM, MlinNotas.id("caderno"));
public static final Item CADERNO = Registry.register(BuiltInRegistries.ITEM, CADERNO_KEY,
    new Item(new Item.Properties().setId(CADERNO_KEY)));
```

Sempre acompanhado de: `assets/mlinnotas/lang/en_us.json` + `pt_br.json`, `assets/mlinnotas/items/<id>.json`, `assets/mlinnotas/models/item/<id>.json`, textura em `assets/mlinnotas/textures/item/<id>.png`. Grupo criativo via `ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.X)`.

## Persistencia

- Por mundo: `SavedData` + `SavedDataType` com `Codec` (`net.minecraft.world.level.saveddata`), obtido de `server.overworld().getDataStorage()`.
- Por jogador: `AttachmentType` (Fabric API `fabric-data-attachment-api-v1`) ou `SavedData` indexado por UUID.
- Serialize com `Codec`/`MapCodec` (`com.mojang.serialization`), nunca NBT a mao.

## Rede

Pacotes customizados: `CustomPacketPayload` + `StreamCodec`, registre com `PayloadTypeRegistry.playS2C()/playC2S()` e trate com `ServerPlayNetworking.registerGlobalReceiver` / `ClientPlayNetworking.registerGlobalReceiver`.

## Cliente vs servidor

- `src/main` nunca importa `net.minecraft.client.*`. Se precisar, mova para `src/client` e exponha via entrypoint `client`.
- Para telas: `Screen` (`net.minecraft.client.gui.screens`), widgets em `net.minecraft.client.gui.components` (`EditBox`, `Button`, `MultiLineEditBox`).
- Keybind: `KeyBindingHelper.registerKeyBinding(new KeyMapping("key.mlinnotas.abrir", GLFW.GLFW_KEY_N, "category.mlinnotas"))`, cheque em `ClientTickEvents.END_CLIENT_TICK`.

## Mixins

So quando nao houver evento. Checklist:
- Classe em `mixin/` (ou `client/mixin/`), adicionada ao json correspondente.
- Prefixo `mlinnotas$` nos metodos; `@Inject` com `at = @At("HEAD"|"TAIL"|"RETURN")`.
- Metodo alvo: confirme a assinatura nas fontes geradas. Se o metodo for sobrecarregado, use o descriptor completo em `method`.
- Prefira `@Inject` + `cancellable` a `@Overwrite`/`@Redirect`.
- Verifique no log de `runClient`/`runGameTest` se o mixin aplicou (`Mixin apply` ou erro `InvalidInjectionException`).

## Checklist antes de entregar

- [ ] `./gradlew build` passa (compila, unit tests, game tests).
- [ ] Nova logica pura esta em `notes/` com teste unitario.
- [ ] Nada de `net.minecraft.client` em `src/main`.
- [ ] Strings visiveis tem chave de lang (pt_br + en_us).
- [ ] `CLAUDE.md` > "Estado atual" atualizado se a feature mudou o que existe.

## Nomes confirmados no 26.3 (ja verificados nas fontes)

Fontes descompiladas: `./gradlew genSources` gera `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-*-sources.jar` (`common` e `clientOnly`); descompacte num diretorio temporario e use `grep`. Fontes da Fabric API (Mojang mappings) em `https://maven.fabricmc.net/net/fabricmc/fabric-api/<modulo>/<versao>/<modulo>-<versao>-sources.jar` (versoes em `~/.gradle/caches/modules-2/files-2.1/net.fabricmc.fabric-api/`).

- Teclas: nao existe `org.lwjgl.glfw.GLFW` no classpath. Use `com.mojang.blaze3d.platform.InputConstants.KEY_G` etc. Categoria propria: `KeyMapping.Category.register(MlinNotas.id("main"))` (lang `key.category.mlinnotas.main`). Registrar com `KeyMappingHelper.registerKeyMapping` (`fabric-key-mapping-api-v1`).
- Shift/Ctrl em eventos: `event.hasShiftDown()` (`InputWithModifiers`); `Screen.hasShiftDown()` nao existe mais.
- GUI: a classe de desenho e `net.minecraft.client.gui.GuiGraphicsExtractor` (nao `GuiGraphics`). Metodos: `fill`, `blit(RenderPipelines.GUI_TEXTURED, Identifier, x, y, u, v, w, h, texW, texH)`, `text(font, ...)`, `centeredText`, `item(stack, x, y)`, `itemDecorations`, `setTooltipForNextFrame`, `enableScissor/disableScissor`, `pose()`. Telas sobrescrevem `extractBackground(graphics, mouseX, mouseY, a)` em vez de `renderBackground`; `AbstractContainerScreen` tem `extractBackground`, `extractLabels`, `extractSlot`, `extractTooltip`.
- HUD: `HudElementRegistry.addLast(id, (graphics, deltaTracker) -> ...)` (`fabric-rendering-v1`, pacote `client.rendering.v1.hud`).
- Telas de container: `leftPos/topPos/imageWidth/imageHeight` sao protected; use o accessor `client/mixin/AbstractContainerScreenAccessor`. Widgets de outro mod: `Screens.getWidgets(screen).add(button)` em `ScreenEvents.AFTER_INIT`. Teclas dentro de telas: `ScreenKeyboardEvents.allowKeyPress(screen)` com `keyMapping.matches(event)`.
- Menus com dados extras: `net.fabricmc.fabric.api.menu.v1.ExtendedMenuType<T, D>(factory, streamCodec)` + `ExtendedMenuProvider` (`fabric-menu-api-v1`). `MenuProvider` simples: `new SimpleMenuProvider((id, inv, player) -> menu, titulo)`; `player.openMenu(provider)`.
- `CraftingMenu(id, inventory)` usa `ContainerLevelAccess.NULL` e `stillValid` devolve true (mesa portatil sem mixin).
- Itens: `Item.Properties().setId(key)` obrigatorio; `BlockItem` com `.useBlockDescriptionPrefix()`. `appendHoverText(stack, TooltipContext, TooltipDisplay, Consumer<Component>, TooltipFlag)` esta `@Deprecated` mas funciona.
- Eventos de uso: `Item.use(Level, Player, InteractionHand)` e `useOn(UseOnContext)` devolvem `InteractionResult` (sem `InteractionResultHolder`).
- Blocos: `Blocks.register(ResourceKey<Block>, Function<Properties, Block>, BlockBehaviour.Properties)` e publico. `BlockBehaviour.Properties.of().setId(key)`. Hooks: `useWithoutItem`, `useItemOn`, `affectNeighborsAfterRemoval` (substitui `onRemove`).
- Block entity: `BlockEntityType(BlockEntitySupplier, Set<Block>)` (construtor publico). `loadAdditional(ValueInput)` / `saveAdditional(ValueOutput)` (sem CompoundTag). Itens: `ContainerHelper.saveAllItems(output, items)` / `loadAllItems(input, items)`.
- Attachments (dados extras em entidades/block entities/chunks): `AttachmentRegistry.create(id, b -> b.persistent(Codec.INT))`; `((AttachmentTarget) blockEntity).getAttached(TYPE)/setAttached/removeAttached`.
- Fornalha: `AbstractFurnaceBlockEntity.getSpeedMultiplier(ServerLevel, ItemStack)` (protected) ja existe para combustiveis; o mod injeta ali.
- Entidades: `EntityTypes.VILLAGER` (classe `EntityTypes`, nao `EntityType`). `Mob.canBeLeashed()`; `AbstractVillager` devolve false (mixin do mod devolve true).
- Rede: `CustomPacketPayload` record + `Type<>(id)` + `StreamCodec`; `PayloadTypeRegistry.serverboundPlay().register(TYPE, CODEC)`; `ServerPlayNetworking.registerGlobalReceiver(TYPE, (payload, ctx) -> ctx.player())`; cliente `ClientPlayNetworking.send(payload)` / `canSend(TYPE)`. Codecs: `ByteBufCodecs.BOOL/VAR_INT/STRING_UTF8`, `StreamCodec.unit(...)`, `StreamCodec.composite(...)`.
- Receitas: `server.getRecipeManager().getRecipes()` -> `RecipeHolder<?>`; `ShapedRecipe.getIngredients()` (`List<Optional<Ingredient>>`), `getWidth/getHeight`; `ShapelessRecipe`; `Ingredient.items()` (`Stream<Holder<Item>>`); `AbstractCookingRecipe.cookingTime()`. O cliente **nao** recebe a lista de receitas; sincronize por pacote proprio.
- Mapa/chunks: `BlockState.getMapColor(level, pos)` -> `MapColor` (`.col`, `calculateARGBColor(Brightness)`); `Level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z)`; `Level.getChunk(cx, cz)`.
- **Altura**: `LevelChunk.getHeight(Heightmap.Types, x, z)` ja devolve o Y do bloco do topo; `Level.getHeight(...)` devolve o primeiro ar (topo + 1). Nao subtraia 1 do valor do chunk.
- Baus animados: block entity implementa `LidBlockEntity` com `ChestLidController` + `ContainerOpenersCounter` (sincroniza por `level.blockEvent(pos, block, 1, count)` e `triggerEvent`), ticker so no cliente, `tick` agendado chama `recheckOpeners`. Renderer: `ChestModel(context.bakeLayer(ModelLayers.CHEST))` + `Sheets.CHEST_MAPPER.apply(id)` (textura em `textures/entity/chest/`, atlas de baus aceita qualquer namespace) e `ChestRenderer.modelTransformation(facing)`; registrar com `BlockEntityRenderers.register`. Item: definicao `minecraft:special` com `{"type": "minecraft:chest", "texture": "mlinnotas:iron"}`; modelo de bloco so com `particle`.
- `AbstractContainerScreen.mouseScrolled` nao repassa a roda para widgets adicionados: use `ScreenMouseEvents.allowMouseScroll(screen)`.
- Icones: `PlayerFaceExtractor.extractRenderState(graphics, player.getSkin(), x, y, size)`; ovo de spawn: `SpawnEggItem.byId(entityType)`; item em escala via `graphics.pose().pushMatrix()/translate/scale` + `graphics.item(...)`.
- Texturas dinamicas: `DynamicTexture(String label, w, h, boolean zero)`, `getPixels()` (`NativeImage.setPixel(x, y, argb)`), `upload()`, `Minecraft.getInstance().getTextureManager().register(id, texture)`.
- Jogador: `player.sendOverlayMessage(Component)` (barra de acao), `player.sendSystemMessage(Component)`. Inventario: `Inventory.getNonEquipmentItems()` (36 slots: 0..8 hotbar, 9..35 principal), `getItem/setItem`.
- Mundo: `Level.destroyBlock(pos, drop, breaker)`, `Block.popResource(level, pos, stack)`, `Block.UPDATE_ALL`, `state.canBeReplaced()`, `state.getSoundType().getPlaceSound()`.
- Aba criativa: `FabricCreativeModeTab.builder().title(...).icon(...).displayItems((params, out) -> ...).build()` + `Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, tab)`.
- Formato de receita JSON: `"key": {"#": "#minecraft:planks"}` (string ou tag com `#`), `"result": {"id": "...", "count": n}`. Loot table de bloco: copie `data/minecraft/loot_table/blocks/furnace.json` do jar (usa `"condition"` singular e `"modifier"`).
- Permissoes: `player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)` (`net.minecraft.server.permissions`). No servidor de game test todo mundo e nivel 0; para testar algo que exige operador use `getPlayerList().op(player.nameAndId(), Optional.of(LevelBasedPermissionSet.OWNER), Optional.empty())`.
- Teleporte de `ServerPlayer`: `player.teleportTo(x, y, z)` (manda pacote pela conexao; funciona com `makeMockServerPlayerInLevel`).
- Cliente: `Minecraft.getInstance().gui.setScreen(screen)` (nao existe `Minecraft.setScreen`); `gui.screen()` da a tela atual. `IntegratedServer` fica em `net.minecraft.client.server`. `minecraft.getSingleplayerServer().getWorldData().getLevelName()` / `minecraft.getCurrentServer().ip`.
- Mouse em telas: `mouseClicked(MouseButtonEvent, boolean)`, `mouseDragged(MouseButtonEvent, dx, dy)`, `mouseReleased(MouseButtonEvent)`, `mouseScrolled(x, y, scrollX, scrollY)`; botoes `InputConstants.MOUSE_BUTTON_LEFT` (=1) e `MOUSE_BUTTON_RIGHT` (=3), nao os valores do GLFW.
- Widgets: `CycleButton.onOffBuilder(bool).create(x, y, w, h, Component, (btn, v) -> ...)`, `CycleButton.<T>builder(stringifier, valorInicial).withValues(...)`, `EditBox(font, x, y, w, h, Component)` com `setResponder`, `Tooltip.create(Component)`, `Button.builder(...).bounds(...).tooltip(...).build()`.
- `ChunkPos` e record: `ChunkPos.pack(x, z)`, `ChunkPos.getX(long)`, `getZ(long)`, `getMinBlockX()`; chunk carregado no cliente: `level.getChunkSource().getChunkNow(x, z)`, `level.hasChunk(x, z)`.
- Render 2D: o `pose()` (`Matrix3x2fStack` do JOML: `pushMatrix/translate/rotate/popMatrix`) e respeitado por `fill`, `blit` e `text`. `blit(pipeline, id, x, y, u, v, w, h, srcW, srcH, texW, texH[, cor])` escala a regiao `srcW x srcH` da textura para `w x h`.
- HUD: a tela de debug (F3) pode ser checada com `minecraft.getDebugOverlay().showDebugScreen()`.
- Baus/containers: `RandomizableContainerBlockEntity` (como `BarrelBlockEntity`): `getItems/setItems/getContainerSize/getDefaultName/createMenu(id, inv)`, salve com `trySaveLootTable`/`ContainerHelper.saveAllItems`. `BlockEntity.preRemoveSideEffects` ja dropa o conteudo de qualquer `Container` ao quebrar o bloco (nao chame `Containers.dropContents` de novo; ao substituir um bau por outro, `clearContent()` antes do `setBlock`). O nome custom (`name`) e privado em `BaseContainerBlockEntity`: guarde o seu proprio campo e sobrescreva `getCustomName`.
- `BaseEntityBlock` nao exige `codec()` no 26.3. `MenuType` simples: `new MenuType<>((id, inv) -> menu, FeatureFlags.VANILLA_SET)` + `Registry.register(BuiltInRegistries.MENU, id, type)`. `MenuScreens.register(type, Screen::new)` e publico.
- Conteudo em item: `DataComponents.CONTAINER` (`ItemContainerContents.fromItems(list)` / `.copyInto(NonNullList)`, max 256 slots). Data component proprio: `Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id, DataComponentType.<T>builder().persistent(codec).networkSynchronized(ByteBufCodecs.fromCodec(codec)).build())`.
- Clique de item sobre item no inventario: `Item.overrideOtherStackedOnMe(self, other, slot, ClickAction, player, SlotAccess)` (chamado no item que recebe o clique; `ClickAction.SECONDARY` = botao direito).
- Combustivel: `DataComponents.COOKING_FUEL` guarda `ResolvableInt` (referencia a `ContextIntProvider`); resolva com `ResolvableInt.getFromItem(stack, COOKING_FUEL, CookingFuel::burnTime, lootContext, 0)` e um `LootContext` de `LootContextParamSets.EMPTY`. Carvao = 1600.
- Receita de fundicao: `level.recipeAccess().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level)`; crafting: `getRecipeFor(RecipeType.CRAFTING, craftingContainer.asCraftInput(), level)` e `holder.value().assemble(input)`. Resultado de crafting em menu proprio: `ResultContainer` + `ResultSlot(player, craftSlots, resultSlots, 0, x, y)`, atualize em `slotsChanged` e mande `ClientboundContainerSetSlotPacket(containerId, incrementStateId(), slotIndex, result)`.
- Baldes: `((BucketItem) Items.WATER_BUCKET).emptyContents(player, level, pos, hitResult)` e publico; coletar fonte: `BucketPickup.pickupBlock(player, level, pos, state)`. Raycast de fluido: `Item.getPlayerPOVHitResult(level, player, ClipContext.Fluid.SOURCE_ONLY)` (protected static). `FluidState.getType().is(FluidTags.LAVA)`.
- Git nesta maquina usa `core.autocrlf=true`: arquivos no worktree ficam com CRLF; `perl -0pi` com `\n` nao casa. Normalize com `sed -i 's/\r$//'` antes de editar por script.
- `InventorySortServer.isSortable` aceita `Slot` exato ou subclasses que implementem `sorting/SortableSlot`.
- Materiais: `ToolMaterial(incorrectTag, dur, speed, bonus, ench, repairTag)` e `ArmorMaterial(durMult, ArmorMaterials.makeDefense(botas, calca, peito, capacete, corpo), ench, som, tenacidade, kb, repairTag, ResourceKey<EquipmentAsset>)` sao records publicos. Itens: `Item.Properties().sword/shovel/pickaxe/axe/hoe(material, dano, vel)`, `.spear(material, 9 floats)`, `.humanoidArmor(material, ArmorType.X)`. Asset de armadura: `assets/<ns>/equipment/<nome>.json` (`layers.humanoid` e `humanoid_leggings`) + `textures/entity/equipment/humanoid[_leggings]/<nome>.png`; chave `ResourceKey.create(EquipmentAssets.ROOT_ID, id)`.
- Mudar componentes de itens vanilla sem mixin: `DefaultItemComponentEvents.MODIFY.register(ctx -> ctx.modify(item, builder -> builder.set(...)))`; `builder.get(type)` le o valor atual (`FabricComponentMapBuilder`). `Tool` e `Tool.Rule(blocks, Optional<Float> speed, Optional<Boolean> correctForDrops)` sao records.
- Worldgen no 26.3: "configured feature" virou `data/<ns>/worldgen/feature/<id>.json` (`{"type":"minecraft:ore","size":..,"discard_chance_on_air_exposure":..,"targets":[{"state":"ns:bloco","target":{...}}]}`); `placed_feature` aponta para ele. Adicionar a biomas: `BiomeModifications.addFeature(BiomeSelectors.tag(BiomeTags.IS_OVERWORLD), GenerationStep.Decoration.UNDERGROUND_ORES, key)`. Use as tags `is_overworld/is_nether/is_end`: `BiomeSelectors.foundInOverworld()` depende do preset e nao pega os biomas no servidor de game test (mundo plano).
- Ferramenta certa: `stack.isCorrectToolForDrops(state)`; drops de loot table: `Block.getDrops(state, serverLevel, pos, blockEntity, entity, tool)`. Receita por id: `server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id))`. Biomas de qualquer dimensao: `registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.X).value().getGenerationSettings().features()`.
- Menu com janela rolavel: subclasse de `Slot` sobrescrevendo `getItem/set/remove/getContainerSlot` com indice deslocado; mude a rolagem por `clickMenuButton` (cliente: `minecraft.gameMode.handleInventoryButtonClick(containerId, id)`) e no servidor chame `sendAllDataToRemote()` (o diff normal nao reenvia slot cujo item "nao mudou").
- Estrutura procedural (sem NBT): `Structure` com `MapCodec` (`simpleCodec(Ctor::new)`), `findGenerationPoint(GenerationContext)` devolve `GenerationStub(pos, builder -> builder.addPiece(...))`; registrar `Registry.register(BuiltInRegistries.STRUCTURE_TYPE, id, () -> CODEC)` e a peca `Registry.register(BuiltInRegistries.STRUCTURE_PIECE, id, (StructurePieceType.ContextlessType) Piece::new)` (construtor `(CompoundTag)` + `addAdditionalSaveData`, `tag.getLongOr/getIntOr`). `postProcess(WorldGenLevel, ..., BoundingBox chunkBB, ...)` e chamado por chunk: so coloque o que `chunkBB.isInside(pos)`. Baus com loot: `RandomizableContainer.setBlockEntityLootTable(level, random, pos, BuiltInLootTables.END_CITY_TREASURE)`; entidades: `EntityTypes.SHULKER.create(level.getLevel(), EntitySpawnReason.STRUCTURE)` + `level.addFreshEntity`. JSON: `worldgen/structure/<id>.json` (`type`, `biomes`, `step`, `spawn_overrides`) e `worldgen/structure_set`.
- Biomas no 26.3: JSON com `attributes` (`minecraft:gameplay/natural_mob_spawns`, `minecraft:visual/fog_color`, `minecraft:visual/ambient_particles`), `effects.water_color`, `features` (11 listas por etapa: 4 = estruturas de superficie, 6 = minerios, 9 = vegetacao). Biomas do End: `TheEndBiomes.addHighlandsBiome/addMidlandsBiome/addBarrensBiome` (fabric-biome-api). Superficie do End: `data/minecraft/worldgen/material_rule/end.json` (`minecraft:sequence`, `"if_true": "minecraft:on_floor"`, condicao `{"type":"minecraft:biome","biome_is":...}`). Patches de planta: `feature` `minecraft:simple_block` + placed com `count`, `offset` (trapezoid) e `block_predicate_filter`.
- O servidor de game test usa **End/Nether planos** (`FlatLevelSource` + `FixedBiomeSource`): `findNearestMapStructure` no End nao acha nada la. Para testar geracao real use o client game test com `context.worldBuilder().setUseConsistentSettings(false).create()` (mundo normal) e `computeOnServer`.
- Entidades: `EntityType.Builder.of(Ctor::new, MobCategory).sized(w, h)` + `builder.build(ResourceKey)`; atributos `FabricDefaultAttributeRegistry.register(type, builder)`; `SpawnPlacements.register(type, SpawnPlacementTypes.X, Heightmap.Types, predicado)` (publico via AW). `Mob.doHurtTarget(ServerLevel, Entity)`, `customServerAiStep(ServerLevel)`. Renderers: `EntityRenderers.register(type, Renderer::new)`; reaproveite modelos vanilla (`IronGolemModel`, `SquidModel`, `EndermiteModel` com `context.bakeLayer(ModelLayers.X)`) num `MobRenderer<E, State, Model>` com textura propria. Ovo: `new Item.Properties().spawnEgg(type)` + `SpawnEggItem::new` (textura propria em item/). Comida com efeito: `.food(FoodProperties, Consumables.defaultFood().onConsume(new ApplyStatusEffectsConsumeEffect(effect)).build())`.
- Blocos em cruz (planta, aglomerado) nao precisam de render layer: a camada de recorte vem da transparencia da textura. `PushReaction.POPPED` (nao existe `DESTROY`); vidro colorido: `Blocks.STAINED_GLASS.magenta()` (`ColorCollection`).
- Features no 26.3: `net.minecraft.world.level.levelgen.feature.Feature` e uma interface; a "configured feature" e um record que implementa `Feature` (`codec()` + `place(WorldGenLevel, ChunkGenerator, RandomSource, BlockPos)`). Tipo proprio: `Registry.register(BuiltInRegistries.FEATURE_TYPE, id, MapCodec)` e JSON `{"type":"mlinnotas:x", ...}` (ver `end/EndFeatures`). `BlockState` em JSON de feature e string (`"minecraft:calcite"`). A ordem das features numa etapa tem que ser a mesma em todos os biomas (senao "feature order cycle").
- Modelos de entidade proprios: `EntityModel<State>` com `static LayerDefinition createBodyLayer()` (`MeshDefinition`/`CubeListBuilder.texOffs(u,v).addBox(...)`/`PartPose`), registrar com `ModelLayerRegistry.registerModelLayer(new ModelLayerLocation(id, "main"), Model::createBodyLayer)` (fabric-rendering-v1) e `context.bakeLayer(layer)` no renderer. Translucido/brilhante: `super(root, RenderTypes::entityTranslucentEmissive)`. `setupAnim` chama `super.setupAnim` (reseta a pose) e depois soma deltas; `ModelPart` tem `xScale/yScale/zScale`.
- Biomas do End: o `TheEndBiomeSource` so e criado por `create(HolderGetter)`; o mixin do mod embrulha o retorno de `createResolver` (roda depois da Fabric). `Climate.Sampler.erosion()` no End e o ruido de ilhas.
