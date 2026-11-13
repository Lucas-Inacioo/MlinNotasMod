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
