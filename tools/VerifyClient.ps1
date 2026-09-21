param(
    [ValidateSet('standalone', 'integrations', 'updater', 'barons', 'barons-no-xaero')][string]$Mode = 'standalone',
    [string]$LauncherDirectory = $env:TROPIMON_HOME,
    [string]$CobblemonJar,
    [string]$OfficialResourcesJar,
    [string]$MinimapResourcesJar,
    [ValidateRange(960, 3840)][int]$Width = 1400,
    [ValidateRange(600, 2160)][int]$Height = 900,
    [ValidateSet('fr_fr', 'en_us')][string]$Language = 'fr_fr',
    [ValidatePattern('^[a-z0-9-]+$')][string]$RunName = 'icons-current'
)
# Import the engine's built-in modules explicitly, including when launched by a build daemon.
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Utility/Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop
Import-Module (Join-Path $PSHOME 'Modules/CimCmdlets/CimCmdlets.psd1') -ErrorAction Stop
$ErrorActionPreference = 'Stop'
$project = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
if (!$LauncherDirectory) { $LauncherDirectory = Join-Path $env:APPDATA '.tropimon' }
$launcher = $LauncherDirectory
$run = Join-Path $project "build/verify-$RunName"
if (Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'" |
        Where-Object { $_.CommandLine -and $_.CommandLine.Contains($run) }) {
    throw 'Close this isolated test instance before replacing its test JAR.'
}
$mods = Join-Path $run 'mods'
New-Item -ItemType Directory -Force $mods | Out-Null
$modVersion = ((Get-Content (Join-Path $project 'gradle.properties') | Select-String '^mod_version=').Line -split '=', 2)[1]
$artifact = Join-Path $project "build/libs/tropimon-events-$modVersion.jar"
if (!(Test-Path -LiteralPath $artifact)) { throw 'Build the release JAR first.' }
Get-ChildItem -LiteralPath $mods -Filter 'tropimon-events-*.jar' -File |
    ForEach-Object { Remove-Item -LiteralPath $_.FullName }
Copy-Item -LiteralPath $artifact -Destination $mods
Copy-Item -LiteralPath (Join-Path $project "build/smoke-helper/tropimon-events-$modVersion-smoke.jar") -Destination $mods
$activeCobblemon = if ($CobblemonJar) { @(Get-Item -LiteralPath $CobblemonJar) } else { @(Get-ChildItem (Join-Path $launcher 'mods') -Filter 'Cobblemon-fabric-*.jar' -File) }
if ($activeCobblemon.Count -ne 1) {
    throw "La vérification exige exactement un JAR Cobblemon actif dans l'instance."
}
Get-ChildItem -LiteralPath $mods -Filter 'Cobblemon-fabric-*.jar' -File |
    ForEach-Object { Remove-Item -LiteralPath $_.FullName }
Copy-Item -LiteralPath $activeCobblemon[0].FullName -Destination (Join-Path $mods 'Cobblemon-under-test.jar')
$patterns = @('fabric-api-0.116.6+1.21.1.jar', 'fabric-language-kotlin-*.jar')
if ($Mode -eq 'integrations') { $patterns += @('TropimodClient-*.jar', 'TropimonBuild-*.jar', '*xaero*.jar', 'mega_showdown-*.jar', 'architectury-*.jar') }
foreach ($pattern in $patterns) {
    Get-ChildItem (Join-Path $launcher 'mods') -Filter $pattern |
        Where-Object { $_.Name -notlike '*BetterPC*' } |
        ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $mods }
}
if ($Mode -eq 'barons') {
    Get-ChildItem (Join-Path $launcher 'profiles/stable/instance/mods') -Filter '*xaero*.jar' |
        ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $mods }
}
$version = Get-Content (Join-Path $launcher '1.21.1.json') -Raw | ConvertFrom-Json
$loader = Get-Content (Join-Path $launcher 'fabric-loader-0.17.3-1.21.1.json') -Raw | ConvertFrom-Json
$classpath = [Collections.Generic.List[string]]::new()
foreach ($library in @($loader.libraries) + @($version.libraries)) {
    $allowed = !$library.rules
    foreach ($rule in $library.rules) {
        if (!$rule.os -or (!$rule.os.name -or $rule.os.name -eq 'windows')) { $allowed = $rule.action -eq 'allow' }
    }
    if (!$allowed) { continue }
    $relative = $library.downloads.artifact.path
    if (!$relative) {
        $parts = $library.name.Split(':')
        $relative = $parts[0].Replace('.', '/') + '/' + $parts[1] + '/' + $parts[2] + '/' + $parts[1] + '-' + $parts[2] + '.jar'
    }
    $path = Join-Path (Join-Path $launcher 'libraries') $relative
    if (!(Test-Path -LiteralPath $path)) { throw "Missing library: $relative" }
    $classpath.Add($path)
}
$classpath.Add((Join-Path $launcher 'client.jar'))
$java = Join-Path $launcher 'runtime/x64/jdk-21.0.6+7/bin/java.exe'
$optionsPath = Join-Path $run 'options.txt'
$optionsLines = if (Test-Path -LiteralPath $optionsPath) { @(Get-Content -LiteralPath $optionsPath | Where-Object { $_ -notmatch '^lang:|^resourcePacks:' }) } else { @() }
if ($OfficialResourcesJar) {
    # Local visual fixture only: read the installed artwork, never include it in a delivery.
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $pack = Join-Path $run 'resourcepacks/official-gym-fixture'
    $archive = [IO.Compression.ZipFile]::OpenRead($OfficialResourcesJar)
    try {
        foreach ($entry in $archive.Entries | Where-Object FullName -match '^assets/tropimodclient/guis/gyms/badge/badge_[a-z]+\.png$') {
            $target = Join-Path $pack $entry.FullName
            New-Item -ItemType Directory -Force (Split-Path -Parent $target) | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
        }
        $nested = Join-Path $run 'official-furnitures-fixture.jar'
        [IO.Compression.ZipFileExtensions]::ExtractToFile($archive.GetEntry('META-INF/jars/Tropifurnitures-1.0.0+1.21.1.jar'), $nested, $true)
        $furnitures = [IO.Compression.ZipFile]::OpenRead($nested)
        try {
            $asset = 'assets/tropifurnitures/textures/item/great_enigma_berry.png'
            $target = Join-Path $pack $asset
            New-Item -ItemType Directory -Force (Split-Path -Parent $target) | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($furnitures.GetEntry($asset), $target, $true)
        } finally { $furnitures.Dispose() }
    } finally { $archive.Dispose() }
    [IO.File]::WriteAllText((Join-Path $pack 'pack.mcmeta'), '{"pack":{"pack_format":34,"description":"Local installed gym artwork fixture"}}')
    $optionsLines += 'resourcePacks:["vanilla","file/official-gym-fixture"]'
}
if ($Mode -eq 'barons') {
    if (!$MinimapResourcesJar) { throw 'Select the installed minimap artwork JAR for the isolated fixture.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    New-Item -ItemType Directory -Force (Join-Path $run 'resourcepacks') | Out-Null
    $iconPack = Join-Path $run 'resourcepacks/installed-minimap-fixture'
    $archive = [IO.Compression.ZipFile]::OpenRead($MinimapResourcesJar)
    try {
        foreach ($entry in $archive.Entries | Where-Object { $_.FullName.StartsWith('assets/xaerominimap/entity/icon/') -and $_.Name -and $_.FullName -notmatch '/\.idea/' }) {
            $target = Join-Path $iconPack $entry.FullName
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
            [IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
        }
    } finally { $archive.Dispose() }
    [IO.File]::WriteAllText((Join-Path $iconPack 'pack.mcmeta'), '{"pack":{"pack_format":34,"description":"Local installed minimap artwork fixture"}}')
    $optionsLines = @($optionsLines | Where-Object { $_ -notmatch '^resourcePacks:' })
    $optionsLines += 'resourcePacks:["vanilla","file/installed-minimap-fixture"]'
    $xaeroConfig = Join-Path $run 'config'
    New-Item -ItemType Directory -Force $xaeroConfig | Out-Null
    # Explicit icons in the isolated fixture; never change the player's settings.
    [IO.File]::WriteAllText((Join-Path $xaeroConfig 'xaerominimap_entities.json'), '{"hardInclude":"anything","includeList":[],"includeListInSuperCategory":true,"excludeMode":"ONLY","excludeList":[],"name":"gui.xaero_entity_category_root","protection":true,"settingOverrides":{"displayed":true,"icons":2.0,"iconScale":2.0,"color":14.0},"subCategories":[]}', [Text.UTF8Encoding]::new($false))
    [IO.File]::WriteAllText((Join-Path $xaeroConfig 'xaerominimap.txt'), "allowInternetAccess:false`nupdateNotification:false`nmodule;id=xaerominimap:minimap;active=true;x=0;y=0;centered=false;fromRight=true;fromBottom=false;flippedVer=false;flippedHor=false;`n", [Text.UTF8Encoding]::new($false))
}
[IO.File]::WriteAllLines($optionsPath, [string[]]($optionsLines + "lang:$Language"), [Text.UTF8Encoding]::new($false))
$smokeFlag = if ($Mode -eq 'updater') { '-Dupdater.consent.smoke=true' } else { '-Dtropimon.smoke=true' }
if ($Mode -eq 'updater') {
    $configDirectory = Join-Path $run 'config'
    New-Item -ItemType Directory -Force -Path $configDirectory | Out-Null
    [IO.File]::WriteAllText((Join-Path $configDirectory 'tropimon_events-updater.json'), '{"enabled":true}', [Text.UTF8Encoding]::new($false))
}
$arguments = @('-Xmx3G', $smokeFlag, '-Dfabric.debug.disableErrorGui=true', "-Dtropimon.smoke.language=$Language", '-Dfabric.log.disableAnsi=true',
    "-Dtropimon.smoke.barons=$($Mode.StartsWith('barons').ToString().ToLowerInvariant())",
    "-Djava.library.path=$(Join-Path $launcher 'natives')",
    '-cp', ($classpath -join ';'), $loader.mainClass,
    '--username', 'InstrumentTest', '--uuid', '00000000000000000000000000000001',
    '--accessToken', '0', '--version', '1.21.1', '--userType', 'legacy',
    '--gameDir', $run, '--assetsDir', (Join-Path $launcher 'assets'),
    '--assetIndex', $version.assetIndex.id, '--width', $Width.ToString(), '--height', $Height.ToString())
Push-Location $run
try { & $java @arguments } finally { Pop-Location }
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
$successMarker = if ($Mode -eq 'updater') { 'UPDATER_CONSENT_SMOKE_OK' } else { 'TROPIMON_SMOKE_OK' }
if (-not (Select-String -LiteralPath (Join-Path $run "logs/latest.log") -SimpleMatch $successMarker -Quiet)) { throw "Verification incompletement validee : consulter le journal local." }
