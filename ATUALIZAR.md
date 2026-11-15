# Atualizar o mod

Passo a passo para quem já instalou o mod uma vez (seção "Instalar em outro computador" do [README](README.md)) e só quer a versão nova.

1. **Feche o Minecraft** (o jogo e o launcher).
2. Abra o PowerShell na pasta do projeto e baixe a versão nova:
   ```powershell
   cd $HOME\Documents\MlinNotasMod
   git pull
   ```
   Baixou o projeto como ZIP? Baixe o ZIP de novo em https://github.com/Lucas-Inacioo/MlinNotasMod (**Code > Download ZIP**) e extraia por cima da pasta antiga.
3. Compile e copie o mod para a pasta de mods:
   ```powershell
   $env:JAVA_HOME = (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory | Where-Object Name -like "jdk-25*" | Select-Object -First 1).FullName
   .\gradlew.bat installToMinecraft
   ```
   No fim deve aparecer `Instalado em ...\.minecraft\mods`. O arquivo `mlinnotas-0.1.0.jar` antigo é substituído.
4. Abra o launcher com o perfil `fabric-loader-0.20.1-26.3` e jogue.

## Jogando junto

Os dois computadores precisam da **mesma versão**. Para conferir, rode nos dois:

```powershell
git log -1 --format="%h %s"
```

O código no começo da linha tem que ser igual. Se for diferente, quem está atrás faz os passos acima.

## Se algo der errado

- **`git pull` reclama de alterações locais:** rode `git stash` e depois `git pull` de novo.
- **O jogo fecha ao abrir, pedindo outra versão do Fabric:** rode uma vez `powershell -ExecutionPolicy Bypass -File scripts\install-fabric.ps1` e repita o passo 3.
- **Erro de Java no passo 3:** confira se o Java 25 está instalado (`winget install EclipseAdoptium.Temurin.25.JDK`) e abra um PowerShell novo.
- **Mais de um jar do mod na pasta `%APPDATA%\.minecraft\mods`:** deixe só o `mlinnotas-0.1.0.jar`.
