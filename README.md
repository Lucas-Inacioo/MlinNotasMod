# Mlin Notas

Mod de anotacoes para Minecraft (Fabric, Minecraft 26.3, Java 25).

## Requisitos

- JDK 25 (Temurin): `winget install EclipseAdoptium.Temurin.25.JDK`
- Minecraft Java Edition instalado (launcher oficial) para testar no jogo real

## Desenvolvimento

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

## Comandos no jogo

- `/nota add <texto>`
- `/nota list`
- `/nota remove <id>`

## CI

GitHub Actions roda `./gradlew build` em todo push/PR (`.github/workflows/build.yml`). Relatorios de teste sao anexados ao run quando falha.
