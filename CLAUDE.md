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
| `src/main/java/com/mlin/notas/notes/` | **logica pura** das anotacoes (sem classes do Minecraft) — testavel por JUnit |
| `src/main/java/com/mlin/notas/<feature>/` | um pacote por feature (`tree`, `sorting`, `wand`, `tablet`, `furnace`, `chest`, `backpack`, `recipes`...). Logica pura em classes sem `net.minecraft` (`TreeFeller`, `InventorySorter`, `WandPlanner`), ponte com o jogo em classes separadas no mesmo pacote |
| `src/main/java/com/mlin/notas/registry/` | registro de itens, blocos, menus, pacotes, aba criativa (`ModItems`, `ModPayloads`, `ModCreativeTab`...) |
| `src/main/java/com/mlin/notas/util/` | utilitarios puros (`Pos`) |
| `src/main/java/com/mlin/notas/command/` | comandos Brigadier |
| `src/main/java/com/mlin/notas/mixin/` | mixins comuns (listados em `mlinnotas.mixins.json`) |
| `src/client/java/com/mlin/notas/client/` | codigo so de cliente (render, telas, keybinds). Entrypoint: `MlinNotasClient` |
| `src/client/.../client/mixin/` | mixins de cliente (`mlinnotas.client.mixins.json`) |
| `src/main/resources/` | `fabric.mod.json`, mixins json, `assets/mlinnotas/` (lang, textures, models), `data/mlinnotas/` |
| `src/test/java/` | testes unitarios JUnit 5 |
| `src/gametest/java/` | game tests (servidor e cliente). Registrados em `src/gametest/resources/fabric.mod.json` |

## Regras do projeto

- **Toda logica de dominio fica em classes sem importar `net.minecraft`** (ex.: `notes/`, `tree/TreeFeller`, `sorting/InventorySorter`). Classes do Minecraft so nas bordas (comandos, eventos, itens, telas). Isso mantem os testes unitarios rapidos e sem Bootstrap.
- Toda feature nova vem com teste: unitario se for logica pura, game test se tocar o mundo/servidor.
- Codigo de cliente **nunca** e referenciado de `src/main` (crasha no servidor dedicado). Use o entrypoint `client` ou `EnvType` checks.
- Mixin e ultimo recurso. Antes, procure um evento/hook da Fabric API (`ServerLifecycleEvents`, `ServerPlayerEvents`, `CommandRegistrationCallback`, `ServerTickEvents`, etc.).
- Metodos de mixin usam prefixo `mlinnotas$`.
- Identifiers via `MlinNotas.id("...")`. Nunca `Identifier.parse("mlinnotas:...")` espalhado.
- Textos para o jogador: `Component.translatable("...")` com chave em `assets/mlinnotas/lang/pt_br.json` e `en_us.json` (literal so em prototipo).
- Se um nome de classe/metodo do Minecraft nao compilar, **nao chute**: rode `./gradlew genSources` e procure no jar de fontes, ou consulte https://docs.fabricmc.net/develop/ e https://maven.fabricmc.net/docs/.
- `build/`, `run/`, `.gradle/`, `logs/` nao sao versionados. Mantenha o projeto em disco local (`C:/Users/lucas/Documents/MlinNotasMod`), nao em pasta sincronizada com nuvem.
- Commits em portugues ou ingles, mensagem curta no imperativo. Nao commite `.claude/settings.local.json`.

## Mods de referencia

Quando o usuario citar um mod existente (ex.: "como o Xaero's Minimap", "igual ao Iron Chests"), e **permitido e esperado** baixar esse mod de uma fonte confiavel para estudar e usar como base da implementacao. Fontes aceitas: **Modrinth** (API `https://api.modrinth.com/v2`), **CurseForge** e o **repositorio oficial de fontes** do mod (GitHub/GitLab linkado na pagina do mod). Nada de sites de redistribuicao.

- Script: `scripts/fetch-mod.ps1 <slug-do-modrinth> [-Decompile]` baixa para `reference/<slug>/` (ignorado pelo git): clone raso das fontes (quando o mod publica), o jar Fabric da versao atual de Minecraft e, com `-Decompile`, as fontes descompiladas pelo Vineflower.
- Sem PowerShell, o mesmo fluxo a mao: `curl -A mlinnotas https://api.modrinth.com/v2/project/<slug>` (campo `source_url`), `.../project/<slug>/version?loaders=["fabric"]` (campo `files[].url`), confira o `sha512`, e descompile com `java -jar vineflower.jar <jar> <pasta>`.
- Prefira sempre as **fontes do repositorio**: jars Fabric publicados usam nomes intermediary (`class_1234`, `method_5678`) para classes do Minecraft, entao o descompilado serve para entender a estrutura, nao para copiar.
- Respeite a licenca (`reference/<slug>/LICENSE.txt`). MIT/LGPL/Apache: pode se inspirar e adaptar com credito no commit. "All Rights Reserved": so leitura para entender a abordagem; reescreva do zero.
- Codigo adaptado entra nas convencoes deste projeto (Mojang mappings, pacotes `com.mlin.notas.*`, logica pura sem `net.minecraft` onde possivel, com testes). Nunca copie o jar para `mods/` nem adicione como dependencia sem o usuario pedir.

## Skills disponiveis

- `.claude/skills/fabric-dev/` — como registrar itens/blocos/comandos/eventos, mixins, client vs server, onde achar APIs.
- `.claude/skills/mc-testing/` — como escrever e rodar testes unitarios e game tests, e como depurar falhas.

## Estado atual

- Anotacoes: comando `/nota add|list|remove` com cadernos em memoria por jogador (`NotebookManager`). Persistencia, tela e keybind ainda nao existem.
- Gammabright: tecla G (cliente, mixin em `LightmapRenderStateExtractor`).
- Quebrar arvore inteira: machado + tronco com folhas, sem agachar (`tree/`).
- Laco em aldeao: mixin `AbstractVillagerMixin`.
- Organizador de inventario: tecla R (mundo ou tela), Shift+R inventario do jogador numa tela de bau, botao "Ord" nas telas de container (`sorting/`, pacote `SortRequestPayload`).
- Varinhas de construcao (madeira 9, pedra 27, ferro 81, diamante 256 blocos) (`wand/`).
- Tablet de crafting: item ou tecla C com o tablet no inventario (`tablet/`).
- Upgrades de fornalha (ferro 2x, ouro 4x, diamante 8x) via attachment + mixin (`furnace/`).
- Texturas geradas por programa (nao ha arte manual): `java scripts/TexGen.java src/main/resources/assets/mlinnotas` regenera todas (edite o TexGen para novos icones).
- Baus grandes: `iron_chest` (9x6=54), `gold_chest` (9x9=81), `diamond_chest` (12x9=108) (`chest/`, `BigChestBlock/BlockEntity/Menu`, tela `client/screen/BigChestScreen`). Upgrades `chest_upgrade_iron|gold|diamond` convertem bau vanilla -> ferro -> ouro -> diamante com botao direito, mantendo conteudo, nome e orientacao (`ChestUpgrades`). Botao "Ord" ordena.
- Mochila: item `backpack` (27 slots; botao direito ou tecla B abre). Upgrades aplicados arrastando o item de upgrade sobre a mochila no inventario + botao direito: `backpack_upgrade_size` (+27, ate 81), `_crafting` (grade 3x3 na tela), `_furnace` (funde o conteudo com combustivel da propria mochila, em `inventoryTick`), `_water`/`_lava` (tanques: Shift+clique numa fonte enche / soma carga; Shift+clique num bloco coloca; Shift+clique no ar alterna agua/lava). Estado no data component `mlinnotas:backpack_upgrades` (`backpack/`, `BackpackMenu`, tela `client/screen/BackpackScreen`).
