# Mlin Notas

Mod de anotacoes para Minecraft (Fabric, Minecraft 26.3, Java 25).

## Requisitos

- JDK 25 (Temurin): `winget install EclipseAdoptium.Temurin.25.JDK`
- Minecraft Java Edition instalado (launcher oficial) para testar no jogo real

## Desenvolvimento

Mantenha o projeto em disco local (nao em pasta sincronizada com Google Drive/OneDrive): o Gradle grava muitos arquivos em `.gradle/` e `build/`.

```bash
./gradlew build              # compila + testes unitarios + game tests de servidor
./gradlew runClient          # abre o cliente de desenvolvimento com o mod
./gradlew runClientGameTest  # game tests de cliente (abre o jogo, tira screenshots)
```

Estrutura, regras e comandos completos em [CLAUDE.md](CLAUDE.md). Guias para desenvolvimento e testes em `.claude/skills/`.

## Jogar com o mod no launcher oficial

```powershell
powershell -ExecutionPolicy Bypass -File scripts\install-fabric.ps1   # instala Fabric Loader + Fabric API (uma vez)
.\gradlew.bat installToMinecraft                                       # copia o jar para %APPDATA%\.minecraft\mods
```

Depois selecione o perfil `fabric-loader-...-26.3` no launcher.

## Instalar em outro computador (para jogar junto)

Faca uma vez, no PowerShell do outro computador (Windows):

1. Instale o Minecraft Java Edition (launcher oficial) e abra o Minecraft 26.3 pelo menos uma vez, depois feche.
2. Instale o Java 25 e o Git:
   ```powershell
   winget install EclipseAdoptium.Temurin.25.JDK
   winget install Git.Git
   ```
   Feche e abra o PowerShell de novo para o `java` e o `git` aparecerem.
3. Baixe o projeto numa pasta local (nao use OneDrive/Google Drive). Sem Git, tambem da para baixar o ZIP em https://github.com/Lucas-Inacioo/MlinNotasMod (botao **Code > Download ZIP**) e extrair em `Documentos\MlinNotasMod`.
   ```powershell
   cd $HOME\Documents
   git clone https://github.com/Lucas-Inacioo/MlinNotasMod.git
   cd MlinNotasMod
   ```
4. Instale o Fabric e a Fabric API, compile o mod e copie para a pasta de mods:
   ```powershell
   $env:JAVA_HOME = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory | Where-Object Name -like "jdk-25*" | Select-Object -First 1).FullName
   powershell -ExecutionPolicy Bypass -File scripts\install-fabric.ps1
   .\gradlew.bat installToMinecraft
   ```
   A primeira compilacao baixa o Minecraft e as dependencias (alguns minutos).
5. Abra o launcher, escolha o perfil `fabric-loader-0.20.1-26.3` e clique em Jogar. Ao abrir, aperte Y num mundo: o Livro de Quests do mod deve aparecer.

**Atualizar depois** (quando houver versao nova): siga [ATUALIZAR.md](ATUALIZAR.md).

**Jogar junto:** os dois precisam do mesmo jar (mesma versao do repositorio). Na mesma rede, quem cria o mundo abre **Esc > Abrir para LAN** e o outro entra por **Multijogador**. O progresso de habilidades, quests e vilas e do mundo, entao fica salvo no computador de quem hospeda.

**Teclas do mod:** Y livro de quests, K arvore de habilidades, M mapa grande, B waypoint, N HUD, R/U receitas/usos, O organizar inventario, V mochila, C tablet, ` (crase) mineracao em veio, Alt esquerdo esquiva, G brilho. Todas podem ser trocadas em **Opcoes > Controles**.

## Comandos no jogo

- `/nota add <texto>`
- `/nota list`
- `/nota remove <id>`

## CI

GitHub Actions roda `./gradlew build` em todo push/PR (`.github/workflows/build.yml`). Relatorios de teste sao anexados ao run quando falha.
