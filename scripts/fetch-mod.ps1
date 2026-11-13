# Baixa um mod conhecido do Modrinth (ou clona o repositorio de fontes dele) para usar como referencia.
# Uso: powershell -ExecutionPolicy Bypass -File scripts\fetch-mod.ps1 <slug-no-modrinth> [-Decompile]
#   ex.: scripts\fetch-mod.ps1 xaeros-minimap
#        scripts\fetch-mod.ps1 iron-chests -Decompile
# Resultado em reference\<slug>\ (pasta ignorada pelo git):
#   - src\        clone raso do repositorio de fontes, se o mod informar um (nomes legiveis, melhor opcao)
#   - jar\        o .jar publicado para a versao de Minecraft do gradle.properties (ou a mais proxima)
#   - decompiled\ fontes descompiladas com Vineflower (so com -Decompile). Classes do Minecraft aparecem
#                 com nomes intermediary (class_1234/method_5678) porque mods Fabric sao publicados assim.
#   - LICENSE.txt licenca informada pelo Modrinth. Respeite-a: referencia e aprendizado, nao copia.

param(
    [Parameter(Mandatory = $true)][string]$Slug,
    [switch]$Decompile
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$headers = @{ "User-Agent" = "mlinnotas-reference-fetcher" }

$root = Resolve-Path "$PSScriptRoot\.."
$props = @{}
Get-Content "$root\gradle.properties" | Where-Object { $_ -match "^\s*[^#].*=" } | ForEach-Object {
    $k, $v = $_ -split "=", 2
    $props[$k.Trim()] = $v.Trim()
}
$mc = $props["minecraft_version"]

$out = Join-Path $root "reference\$Slug"
New-Item -ItemType Directory -Force $out | Out-Null

# 1) Metadados do projeto
$project = Invoke-RestMethod "https://api.modrinth.com/v2/project/$Slug" -Headers $headers -UseBasicParsing
Write-Host "Mod: $($project.title) - licenca: $($project.license.id)"
"$($project.license.id)`n$($project.license.url)`nFonte: $($project.source_url)" | Set-Content -Encoding utf8 (Join-Path $out "LICENSE.txt")

# 2) Repositorio de fontes (clone raso), quando houver
if ($project.source_url -and (Get-Command git -ErrorAction SilentlyContinue)) {
    $srcDir = Join-Path $out "src"
    if (-not (Test-Path $srcDir)) {
        Write-Host "Clonando fontes de $($project.source_url) ..."
        try { git clone --depth 1 $project.source_url $srcDir } catch { Write-Warning "clone falhou: $($_.Exception.Message)" }
    }
}

# 3) Jar publicado para Fabric na versao atual (ou a ultima disponivel)
$versions = Invoke-RestMethod "https://api.modrinth.com/v2/project/$Slug/version?loaders=%5B%22fabric%22%5D" -Headers $headers -UseBasicParsing
$pick = @($versions | Where-Object { $_.game_versions -contains $mc }) | Select-Object -First 1
if (-not $pick) {
    $pick = @($versions) | Select-Object -First 1
    if ($pick) { Write-Warning "Sem versao para MC $mc; usando $($pick.version_number) ($($pick.game_versions -join ','))" }
}
if (-not $pick) { throw "Nenhuma versao Fabric encontrada para '$Slug'" }
$file = @($pick.files | Where-Object { $_.primary }) | Select-Object -First 1
if (-not $file) { $file = $pick.files[0] }
$jarDir = Join-Path $out "jar"
New-Item -ItemType Directory -Force $jarDir | Out-Null
$jarPath = Join-Path $jarDir $file.filename
if (-not (Test-Path $jarPath)) {
    Write-Host "Baixando $($file.filename) ..."
    Invoke-WebRequest $file.url -OutFile $jarPath -Headers $headers -UseBasicParsing
}
$sha = (Get-FileHash $jarPath -Algorithm SHA512).Hash.ToLower()
if ($sha -ne $file.hashes.sha512) { Remove-Item $jarPath; throw "SHA-512 do jar nao confere com o Modrinth" }

# 4) Descompilar com Vineflower (opcional)
if ($Decompile) {
    $javaHome = $env:JAVA_HOME
    if (-not $javaHome) { $javaHome = "C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot" }
    $java = Join-Path $javaHome "bin\java.exe"
    if (-not (Test-Path $java)) { throw "java.exe nao encontrado em $java (defina JAVA_HOME)" }
    $vfVersion = "1.11.1"
    $vf = Join-Path $env:TEMP "vineflower-$vfVersion.jar"
    if (-not (Test-Path $vf)) {
        Invoke-WebRequest "https://repo1.maven.org/maven2/org/vineflower/vineflower/$vfVersion/vineflower-$vfVersion.jar" -OutFile $vf -UseBasicParsing
    }
    $dec = Join-Path $out "decompiled"
    New-Item -ItemType Directory -Force $dec | Out-Null
    & $java -jar $vf --silent $jarPath $dec
    Write-Host "Descompilado em $dec"
}

Write-Host "Pronto: $out"
