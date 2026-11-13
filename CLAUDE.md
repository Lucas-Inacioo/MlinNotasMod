# Mlin Notas — mod de Minecraft (Fabric)

Mod de anotacoes dentro do Minecraft. Java 25, Fabric Loader, Fabric API, **Mojang mappings** (nomes oficiais: `net.minecraft.world.level.block.Blocks`, `net.minecraft.client.Minecraft`, `Identifier`, `Component`...). Nao use nomes Yarn (`TestContext`, `MinecraftClient`, `Text`).

Versoes ficam em [gradle.properties](gradle.properties). Confira as atuais em https://fabricmc.net/develop antes de atualizar.

## Comandos

```bash
./gradlew build              # compila + testes unitarios + game tests de servidor (headless)
./gradlew test               # so testes unitarios (JUnit 5, src/test)
./gradlew runGameTest        # so game tests de servidor (src/gametest, entrypoint fabric-gametest)
./gradlew runClientGameTest  # game tests de cliente (abre o jogo, tira screenshots em build/run/clientGameTest/screenshots)
./gradlew runClient          # abre o cliente de desenvolvimento com o mod
./gradlew runServer          # sobe servidor dedicado de desenvolvimento
./gradlew installToMinecraft # copia o jar para %APPDATA%\.minecraft\mods (precisa do Fabric no launcher: scripts/install-fabric.ps1)
./gradlew genSources         # gera fontes do Minecraft decompiladas para consulta (build/ ou ~/.gradle/caches/fabric-loom)
```

No Windows use `gradlew.bat`. Se `java` nao estiver no PATH: `JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot`.

## Layout

| Pasta | O que vai aqui |
|---|---|
| `src/main/java/com/mlin/notas/` | codigo comum (servidor + cliente). Entrypoint: `MlinNotas` |
| `src/main/java/com/mlin/notas/notes/` | **logica pura** (sem classes do Minecraft) — testavel por JUnit |
| `src/main/java/com/mlin/notas/command/` | comandos Brigadier |
| `src/main/java/com/mlin/notas/mixin/` | mixins comuns (listados em `mlinnotas.mixins.json`) |
| `src/client/java/com/mlin/notas/client/` | codigo so de cliente (render, telas, keybinds). Entrypoint: `MlinNotasClient` |
| `src/client/.../client/mixin/` | mixins de cliente (`mlinnotas.client.mixins.json`) |
| `src/main/resources/` | `fabric.mod.json`, mixins json, `assets/mlinnotas/` (lang, textures, models), `data/mlinnotas/` |
| `src/test/java/` | testes unitarios JUnit 5 |
| `src/gametest/java/` | game tests (servidor e cliente). Registrados em `src/gametest/resources/fabric.mod.json` |

## Regras do projeto

- **Toda logica de dominio fica em `notes/` sem importar `net.minecraft`.** Classes do Minecraft so nas bordas (comandos, eventos, telas). Isso mantem os testes unitarios rapidos e sem Bootstrap.
- Toda feature nova vem com teste: unitario se for logica pura, game test se tocar o mundo/servidor.
- Codigo de cliente **nunca** e referenciado de `src/main` (crasha no servidor dedicado). Use o entrypoint `client` ou `EnvType` checks.
- Mixin e ultimo recurso. Antes, procure um evento/hook da Fabric API (`ServerLifecycleEvents`, `ServerPlayerEvents`, `CommandRegistrationCallback`, `ServerTickEvents`, etc.).
- Metodos de mixin usam prefixo `mlinnotas$`.
- Identifiers via `MlinNotas.id("...")`. Nunca `Identifier.parse("mlinnotas:...")` espalhado.
- Textos para o jogador: `Component.translatable("...")` com chave em `assets/mlinnotas/lang/pt_br.json` e `en_us.json` (literal so em prototipo).
- Se um nome de classe/metodo do Minecraft nao compilar, **nao chute**: rode `./gradlew genSources` e procure no jar de fontes, ou consulte https://docs.fabricmc.net/develop/ e https://maven.fabricmc.net/docs/.
- `build/`, `run/`, `.gradle/` nao sao versionados. A pasta do projeto esta num drive sincronizado (Google Drive); evite gravar arquivos enormes fora de `build/`.
- Commits em portugues ou ingles, mensagem curta no imperativo. Nao commite `.claude/settings.local.json`.

## Skills disponiveis

- `.claude/skills/fabric-dev/` — como registrar itens/blocos/comandos/eventos, mixins, client vs server, onde achar APIs.
- `.claude/skills/mc-testing/` — como escrever e rodar testes unitarios e game tests, e como depurar falhas.

## Estado atual

Prototipo: comando `/nota add|list|remove` com cadernos em memoria por jogador (`NotebookManager`). Persistencia (SavedData/NBT), tela de cliente e keybind ainda nao existem.
