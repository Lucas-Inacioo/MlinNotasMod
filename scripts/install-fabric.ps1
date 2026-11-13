# Instala o Fabric Loader no launcher oficial do Minecraft e coloca a Fabric API na pasta mods.
# Uso: powershell -ExecutionPolicy Bypass -File scripts\install-fabric.ps1
# Depois: .\gradlew.bat installToMinecraft  e abra o perfil "fabric-loader-26.3" no launcher.

$ErrorActionPreference = "Stop"

$props = Get-Content "$PSScriptRoot\..\gradle.properties" | Where-Object { $_ -match "=" } | ForEach-Object {
    $k, $v = $_ -split "=", 2; @{ $k.Trim() = $v.Trim() }
}
$mc = ($props | Where-Object { $_.ContainsKey("minecraft_version") }).minecraft_version
$loader = ($props | Where-Object { $_.ContainsKey("loader_version") }).loader_version
$api = ($props | Where-Object { $_.ContainsKey("fabric_api_version") }).fabric_api_version

$javaHome = $env:JAVA_HOME
if (-not $javaHome) { $javaHome = "C:\Program Files\Eclipse Adoptium\jdk-25.0.5.8-hotspot" }
$java = Join-Path $javaHome "bin\java.exe"
$mcDir = Join-Path $env:APPDATA ".minecraft"
$tmp = Join-Path $env:TEMP "mlinnotas-fabric"
New-Item -ItemType Directory -Force $tmp | Out-Null

Write-Host "Minecraft $mc / Fabric Loader $loader / Fabric API $api"

# 1) Fabric installer (versao estavel mais recente)
$installerVersion = (Invoke-RestMethod "https://meta.fabricmc.net/v2/versions/installer" | Where-Object { $_.stable } | Select-Object -First 1).version
$installerJar = Join-Path $tmp "fabric-installer-$installerVersion.jar"
if (-not (Test-Path $installerJar)) {
    Invoke-WebRequest "https://maven.fabricmc.net/net/fabricmc/fabric-installer/$installerVersion/fabric-installer-$installerVersion.jar" -OutFile $installerJar
}
& $java -jar $installerJar client -mcversion $mc -loader $loader -dir $mcDir -noprofile:$false
if ($LASTEXITCODE -ne 0) { throw "fabric-installer falhou" }

# 2) Fabric API na pasta mods
$mods = Join-Path $mcDir "mods"
New-Item -ItemType Directory -Force $mods | Out-Null
Get-ChildItem $mods -Filter "fabric-api-*.jar" | Remove-Item -Force
$apiJar = Join-Path $mods "fabric-api-$api.jar"
Invoke-WebRequest "https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/$api/fabric-api-$api.jar" -OutFile $apiJar

Write-Host "OK. Perfil 'fabric-loader-$loader-$mc' criado no launcher; Fabric API em $apiJar"
Write-Host "Agora: .\gradlew.bat installToMinecraft"
