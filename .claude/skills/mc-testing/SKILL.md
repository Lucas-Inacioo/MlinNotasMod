---
name: mc-testing
description: Como escrever, rodar e depurar os testes automaticos deste mod Fabric - testes unitarios JUnit 5 (src/test), game tests de servidor (src/gametest, rodam no build) e game tests de cliente (runClientGameTest). Use ao adicionar testes ou quando o build falhar em testes.
---

# Testes neste projeto

Tres camadas, do mais rapido ao mais lento:

| Camada | Onde | Como rodar | Quando usar |
|---|---|---|---|
| Unitario (JUnit 5) | `src/test/java` | `./gradlew test` | logica pura em `notes/`, helpers, codecs |
| Game test de servidor | `src/gametest/java`, entrypoint `fabric-gametest` | `./gradlew runGameTest` (tambem roda em `build`) | comandos, eventos, blocos, entidades, persistencia |
| Game test de cliente | `src/gametest/java`, entrypoint `fabric-client-gametest` | `./gradlew runClientGameTest` | telas, HUD, keybinds, screenshots. Nao roda em CI (precisa de janela) |

Relatorios: `build/reports/tests/test/index.html`, `build/test-results/`, logs do game test em `build/run/gametest/logs/`.

## Testes unitarios

- Pacote espelhado do codigo testado (`com.mlin.notas.notes.NotebookTest`).
- Nomes descritivos: `addAssignsSequentialIds`, `rejectsBlankText`.
- Logica pura nao precisa de nada do Minecraft. Classes simples do Minecraft (`Identifier`, `Component.literal`) funcionam direto.
- Classes que dependem de registries (`ItemStack`, `Blocks`, `Items`) precisam de bootstrap:

```java
@BeforeAll
static void bootstrap() {
    SharedConstants.tryDetectVersion();
    Bootstrap.bootStrap();
}
```

- O classloader e o do Fabric (`fabric-loader-junit`), entao mixins do mod tambem aplicam nos testes.

## Game tests de servidor

- Classe publica com metodos `public void nome(GameTestHelper helper)` anotados com `net.fabricmc.fabric.api.gametest.v1.GameTest`.
- **Registre a classe** em `src/gametest/resources/fabric.mod.json` -> `entrypoints."fabric-gametest"`. Esquecer isso = teste silenciosamente nao roda.
- Cada teste recebe uma estrutura vazia (padrao). Coordenadas do helper sao relativas a ela: `helper.setBlock(0, 1, 0, Blocks.STONE)`.
- Termine com `helper.succeed()`. Para falhar, lance excecao (`IllegalStateException`) ou use `helper.fail(...)`.
- Assincrono: `helper.runAfterDelay(ticks, () -> ...)`, `helper.succeedWhen(() -> helper.assertBlockPresent(...))`, `helper.runAtTickTime(...)`. Timeout padrao 20 ticks; ajuste com `@GameTest(maxTicks = 400)` (atributos: `maxTicks`, `setupTicks`, `structure`, `required`, `skyAccess`...).
- Estrutura customizada: salve um `.nbt` em `src/gametest/resources/data/mlinnotas-gametest/structure/` e use `@GameTest(structure = "mlinnotas-gametest:nome")`.
- Jogador fake: `(ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL)` para inventario/itens. Ele **nao tem conexao** (`connection == null`): qualquer coisa que mande pacote (`openMenu`, `sendOverlayMessage`, `displayClientMessage`) da NPE. Para isso use `helper.makeMockServerPlayerInLevel()` (deprecated, jogador CRIATIVO com conexao fake; `@SuppressWarnings("removal")`) e remova no fim com `helper.getLevel().getServer().getPlayerList().remove(player)`.
- A estrutura padrao (`fabric-gametest-api-v1:empty`) tem 8x8x8 blocos; coordenadas relativas 0..7.
- Acesso ao servidor: `helper.getLevel().getServer()`.
- Exemplos em `src/gametest/java/com/mlin/notas/gametest/MlinNotasGameTest.java`.

## Game tests de cliente

- Classe implementa `FabricClientGameTest`, metodo `runTest(ClientGameTestContext ctx)`. Registre em `fabric-client-gametest`.
- Mundo: `try (TestSingleplayerContext sp = ctx.worldBuilder().create()) { ... }`. Nivel/jogador via `sp.getConnection().getClientLevel()` / `.getClientPlayer()`; esperar chunks: `sp.getConnection().waitForChunksRender()`.
- Input: `ctx.getInput().pressKey(...)`, `ctx.getInput().typeChars("...")`.
- Telas: `ctx.waitForScreen(MinhaScreen.class)`, `ctx.clickScreenButton("gui.done")`.
- `ctx.takeScreenshot("nome")` -> `build/run/clientGameTest/screenshots/`.
- Um unico processo roda todos os client tests em sequencia; feche o que abrir.

## Depurando falhas

1. Compilacao falha com "cannot find symbol" em classe do Minecraft: nome Yarn ou API mudou. Veja a skill `fabric-dev` e `genSources`.
2. `build` falha em `runGameTest`: abra `build/run/gametest/logs/latest.log`, procure `[GameTest]` e o stack trace. Teste que passou o timeout = faltou `succeed()` ou condicao nunca atingida.
3. Teste nao aparece: classe nao esta no `fabric.mod.json` do gametest, ou metodo nao e `public`.
4. Unit test "Not bootstrapped": falta `Bootstrap.bootStrap()` no `@BeforeAll`.
5. Game test de cliente em CI: nao rode. Se precisar, use `ClientProductionRunTask` com `useXVFB = true` (ver docs Fabric "Automated Testing").
6. Erro de `ConcurrentModification`/`Accessed from wrong thread` em game test: execute no thread do servidor (`helper.getLevel().getServer().execute(...)`).

## Ao criar uma feature

1. Escreva o teste unitario da logica pura primeiro.
2. Escreva um game test que exercita o caminho real (comando, evento).
3. `./gradlew build` ate verde.
