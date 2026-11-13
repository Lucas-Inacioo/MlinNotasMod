# Instala o Fabric Loader no launcher oficial do Minecraft e coloca a Fabric API na pasta mods.
# Uso: powershell -ExecutionPolicy Bypass -File scripts\install-fabric.ps1
# Depois: .\gradlew.bat installToMinecraft  e abra o perfil "fabric-loader-<loader>-<mc>" no launcher.

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$props = @{}
Get-Content "$PSScriptRoot\..\gradle.properties" | Where-Object { $_ -match "^\s*[^#].*=" } | ForEach-Object {
    $k, $v = $_ -split "=", 2
    $props[$k.Trim()] = $v.Trim()
}
$mc = $props["minecraft_version"]
$loader = $props["loader_version"]
$api = $props["fabric_api_version"]
if (-not $mc -or -not $loader -or -not $api) { throw "Nao consegui ler minecraft_version/loader_version/fabric_api_version de gradle.properties" }

$javaHome = $env:JAVA_HOME
if (-not $javaHome) { $javaHome = "C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot" }
$java = Join-Path $javaHome "bin\java.exe"
if (-not (Test-Path $java)) { throw "java.exe nao encontrado em $java (defina JAVA_HOME)" }
$mcDir = Join-Path $env:APPDATA ".minecraft"
$tmp = Join-Path $env:TEMP "mlinnotas-fabric"
New-Item -ItemType Directory -Force $tmp | Out-Null

Write-Host "Minecraft $mc / Fabric Loader $loader / Fabric API $api"

# 1) Fabric installer: usa a URL que o meta API devolve; se falhar, cai num release conhecido
$installerUrl = $null
try {
    $meta = Invoke-RestMethod "https://meta.fabricmc.net/v2/versions/installer" -UseBasicParsing
    $stable = @($meta | Where-Object { $_.stable }) | Select-Object -First 1
    if ($stable -and $stable.url) { $installerUrl = $stable.url }
} catch {
    Write-Warning "meta.fabricmc.net indisponivel: $($_.Exception.Message)"
}
if (-not $installerUrl) {
    $installerUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-installer/1.1.2/fabric-installer-1.1.2.jar"
}
$installerJar = Join-Path $tmp ([IO.Path]::GetFileName($installerUrl))
if (-not (Test-Path $installerJar)) {
    Write-Host "Baixando $installerUrl"
    Invoke-WebRequest $installerUrl -OutFile $installerJar -UseBasicParsing
}

& $java -jar $installerJar client -mcversion $mc -loader $loader -dir $mcDir
if ($LASTEXITCODE -ne 0) { throw "fabric-installer falhou (exit $LASTEXITCODE)" }

# 2) Fabric API na pasta mods
$mods = Join-Path $mcDir "mods"
New-Item -ItemType Directory -Force $mods | Out-Null
Get-ChildItem $mods -Filter "fabric-api-*.jar" -ErrorAction SilentlyContinue | Remove-Item -Force
$apiUrl = "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$api/fabric-api-$api.jar"
$apiJar = Join-Path $mods "fabric-api-$api.jar"
Write-Host "Baixando $apiUrl"
Invoke-WebRequest $apiUrl -OutFile $apiJar -UseBasicParsing

Write-Host ""
Write-Host "OK. Perfil 'fabric-loader-$loader-$mc' criado no launcher; Fabric API em $apiJar"
Write-Host "Agora: .\gradlew.bat installToMinecraft"
