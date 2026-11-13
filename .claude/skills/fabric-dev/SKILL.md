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
