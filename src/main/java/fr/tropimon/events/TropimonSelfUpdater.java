package fr.tropimon.events;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.slf4j.Logger;

/** Updater autonome de ce mod. Aucune classe d'un autre mod Tropimon n'est requise. */
final class TropimonSelfUpdater {
  private static final String MOD_ID = "tropimon_events";
  private static final String REPOSITORY = "TropimonEvents";
  private static final String RELEASE_API =
      "https://api.github.com/repos/FastedCorsi/" + REPOSITORY + "/releases/latest";
  private static final String RELEASE_DOWNLOAD_PREFIX =
      "https://github.com/FastedCorsi/" + REPOSITORY + "/releases/download/";
  private static final Duration CHECK_INTERVAL = Duration.ofHours(6);
  private static final long MAX_JAR_SIZE = 64L * 1024L * 1024L;
  private static final HttpClient HTTP =
      HttpClient.newBuilder()
          .connectTimeout(Duration.ofSeconds(10))
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  private TropimonSelfUpdater() {}

  static void start(Logger logger) {
    if (Boolean.getBoolean("tropimon.smoke")
        || FabricLoader.getInstance().isDevelopmentEnvironment()
        || !enabled(logger)) return;
    CompletableFuture.runAsync(() -> check(logger))
        .exceptionally(
            failure -> {
              logger.warn(
                  "Mise a jour automatique {} indisponible pour cette session ({}).",
                  MOD_ID,
                  rootCause(failure).getClass().getSimpleName());
              return null;
            });
  }

  private static boolean enabled(Logger logger) {
    Path config = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID + "-updater.json");
    try {
      if (Files.notExists(config)) {
        Files.createDirectories(config.getParent());
        Files.writeString(config, "{\n  \"enabled\": true\n}\n", StandardCharsets.UTF_8);
        return true;
      }
      JsonObject json = JsonParser.parseString(Files.readString(config)).getAsJsonObject();
      return !json.has("enabled") || json.get("enabled").getAsBoolean();
    } catch (Exception failure) {
      logger.warn("Configuration de mise a jour {} illisible; mise a jour desactivee.", MOD_ID);
      return false;
    }
  }

  private static void check(Logger logger) {
    if (!isWindows()) {
      logger.info(
          "Mise a jour automatique {}: installation differee disponible sous Windows uniquement.",
          MOD_ID);
      return;
    }
    ModContainer container = FabricLoader.getInstance().getModContainer(MOD_ID).orElse(null);
    if (container == null) return;
    Path installedJar = installedJar(container);
    if (installedJar != null
        && !installedJar
            .getParent()
            .equals(
                FabricLoader.getInstance()
                    .getGameDir()
                    .toAbsolutePath()
                    .normalize()
                    .resolve("mods"))) return;
    if (installedJar == null || recentlyChecked()) return;

    try {
      markChecked();
      JsonObject release = requestJson(RELEASE_API);
      if (release.get("draft").getAsBoolean() || release.get("prerelease").getAsBoolean()) return;
      String currentVersion = container.getMetadata().getVersion().getFriendlyString();
      String releaseVersion = release.get("tag_name").getAsString().replaceFirst("^[vV]", "");
      if (compareVersions(releaseVersion, currentVersion) <= 0) {
        markChecked();
        return;
      }
      ReleaseAsset jarAsset = selectJar(release.getAsJsonArray("assets"));
      if (jarAsset == null) {
        throw new IOException("Release assets incomplete");
      }
      ReleaseAsset checksumAsset =
          selectChecksum(release.getAsJsonArray("assets"), jarAsset.name());
      if (checksumAsset == null) throw new IOException("Release assets incomplete");

      Path updateDir =
          FabricLoader.getInstance().getConfigDir().resolve(".tropimon-updates").resolve(MOD_ID);
      Files.createDirectories(updateDir);
      String expectedHash = requestText(checksumAsset.url(), 512).trim().split("\\s+", 2)[0];
      if (!expectedHash.matches("(?i)[0-9a-f]{64}")) {
        throw new IOException("Invalid SHA-256 sidecar");
      }

      Path staged = updateDir.resolve(jarAsset.name());
      download(jarAsset.url(), staged, MAX_JAR_SIZE);
      String downloadedHash = sha256(staged);
      if (!downloadedHash.equalsIgnoreCase(expectedHash)) {
        Files.deleteIfExists(staged);
        throw new IOException("SHA-256 mismatch");
      }

      JarMetadata metadata = inspectJar(staged);
      if (!MOD_ID.equals(metadata.id()) || !metadata.version().equals(releaseVersion)) {
        Files.deleteIfExists(staged);
        throw new IOException("Unexpected mod id");
      }
      if (compareVersions(metadata.version(), currentVersion) <= 0) {
        Files.deleteIfExists(staged);
        markChecked();
        return;
      }

      Path installer = updateDir.resolve("install-after-minecraft.ps1");
      Files.writeString(installer, WINDOWS_INSTALLER, StandardCharsets.UTF_8);
      armInstaller(installer, staged, installedJar, sha256(installedJar), downloadedHash);
      logger.info(
          "Mise a jour {} {} preparee; installation automatique apres l'arret de Minecraft.",
          MOD_ID,
          metadata.version());
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    } catch (Exception failure) {
      logger.warn(
          "Verification de mise a jour {} echouee ({}).",
          MOD_ID,
          failure.getClass().getSimpleName());
    }
  }

  private static Path installedJar(ModContainer container) {
    return container.getOrigin().getPaths().stream()
        .filter(Files::isRegularFile)
        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))
        .findFirst()
        .map(Path::toAbsolutePath)
        .map(Path::normalize)
        .orElse(null);
  }

  private static boolean recentlyChecked() {
    Path marker = marker();
    try {
      if (Files.notExists(marker)) return false;
      Instant checkedAt = Instant.parse(Files.readString(marker).trim());
      return checkedAt.plus(CHECK_INTERVAL).isAfter(Instant.now());
    } catch (Exception ignored) {
      return false;
    }
  }

  private static void markChecked() throws IOException {
    Path marker = marker();
    Files.createDirectories(marker.getParent());
    Files.writeString(marker, Instant.now().toString(), StandardCharsets.UTF_8);
  }

  private static Path marker() {
    return FabricLoader.getInstance()
        .getConfigDir()
        .resolve(".tropimon-updates")
        .resolve(MOD_ID)
        .resolve("last-check.txt");
  }

  private static JsonObject requestJson(String url) throws IOException, InterruptedException {
    String body = requestText(url, 2 * 1024 * 1024);
    return JsonParser.parseString(body).getAsJsonObject();
  }

  private static String requestText(String url, long maxBytes)
      throws IOException, InterruptedException {
    HttpRequest request = request(url);
    HttpResponse<InputStream> response =
        HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
    if (response.statusCode() != 200) {
      response.body().close();
      throw new IOException("HTTP " + response.statusCode());
    }
    try (InputStream input = response.body()) {
      return new String(readLimited(input, maxBytes), StandardCharsets.UTF_8);
    }
  }

  private static void download(String url, Path destination, long maxBytes)
      throws IOException, InterruptedException {
    HttpResponse<InputStream> response =
        HTTP.send(request(url), HttpResponse.BodyHandlers.ofInputStream());
    if (response.statusCode() != 200) {
      response.body().close();
      throw new IOException("HTTP " + response.statusCode());
    }
    Path temporary = destination.resolveSibling(destination.getFileName() + ".part");
    try (InputStream input = response.body();
        var output = Files.newOutputStream(temporary)) {
      byte[] buffer = new byte[16 * 1024];
      long total = 0;
      int read;
      while ((read = input.read(buffer)) >= 0) {
        total += read;
        if (total > maxBytes) throw new IOException("Download too large");
        output.write(buffer, 0, read);
      }
    } catch (IOException | RuntimeException failure) {
      Files.deleteIfExists(temporary);
      throw failure;
    }
    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
  }

  private static HttpRequest request(String url) throws IOException {
    if (!(url.equals(RELEASE_API) || url.startsWith(RELEASE_DOWNLOAD_PREFIX))) {
      throw new IOException("Untrusted update URL");
    }
    return HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .header("Accept", "application/vnd.github+json")
        .header("User-Agent", "Tropimon-" + MOD_ID + "-Updater")
        .GET()
        .build();
  }

  private static byte[] readLimited(InputStream input, long maxBytes) throws IOException {
    byte[] buffer = new byte[8192];
    try (var output = new java.io.ByteArrayOutputStream()) {
      long total = 0;
      int read;
      while ((read = input.read(buffer)) >= 0) {
        total += read;
        if (total > maxBytes) throw new IOException("Response too large");
        output.write(buffer, 0, read);
      }
      return output.toByteArray();
    }
  }

  private static ReleaseAsset selectJar(JsonArray assets) throws IOException {
    if (assets == null) throw new IOException("Missing assets");
    ReleaseAsset selected = null;
    for (var element : assets) {
      JsonObject asset = element.getAsJsonObject();
      String name = asset.get("name").getAsString();
      String lower = name.toLowerCase(Locale.ROOT);
      if (lower.endsWith(".jar")
          && !lower.contains("sources")
          && !lower.contains("dev")
          && !lower.contains("local")) {
        if (selected != null) throw new IOException("Ambiguous release JARs");
        selected = checkedAsset(name, asset.get("browser_download_url").getAsString());
      }
    }
    return selected;
  }

  private static ReleaseAsset selectChecksum(JsonArray assets, String jarName) throws IOException {
    if (assets == null || jarName == null) return null;
    for (var element : assets) {
      JsonObject asset = element.getAsJsonObject();
      String name = asset.get("name").getAsString();
      if (name.equalsIgnoreCase(jarName + ".sha256")) {
        return checkedAsset(name, asset.get("browser_download_url").getAsString());
      }
    }
    return null;
  }

  private static ReleaseAsset checkedAsset(String name, String url) throws IOException {
    if (!name.matches("[A-Za-z0-9][A-Za-z0-9._+\\-]*")
        || !url.startsWith(RELEASE_DOWNLOAD_PREFIX)) {
      throw new IOException("Unsafe release asset");
    }
    return new ReleaseAsset(name, url);
  }

  private static JarMetadata inspectJar(Path jar) throws IOException {
    try (ZipFile zip = new ZipFile(jar.toFile())) {
      ZipEntry entry = zip.getEntry("fabric.mod.json");
      if (entry == null || entry.getSize() > 1024 * 1024) throw new IOException("Missing metadata");
      try (InputStream input = zip.getInputStream(entry)) {
        JsonObject json =
            JsonParser.parseString(
                    new String(readLimited(input, 1024 * 1024), StandardCharsets.UTF_8))
                .getAsJsonObject();
        return new JarMetadata(json.get("id").getAsString(), json.get("version").getAsString());
      }
    }
  }

  private static int compareVersions(String left, String right) {
    int[] a = numericParts(left);
    int[] b = numericParts(right);
    for (int index = 0; index < Math.max(a.length, b.length); index++) {
      int av = index < a.length ? a[index] : 0;
      int bv = index < b.length ? b[index] : 0;
      if (av != bv) return Integer.compare(av, bv);
    }
    return 0;
  }

  private static int[] numericParts(String version) {
    String core = version.split("[+-]", 2)[0];
    String[] parts = core.replaceFirst("^[vV]", "").split("\\.");
    int[] values = new int[parts.length];
    for (int index = 0; index < parts.length; index++) {
      try {
        values[index] = Integer.parseInt(parts[index].replaceAll("[^0-9].*$", ""));
      } catch (NumberFormatException ignored) {
        values[index] = 0;
      }
    }
    return values;
  }

  private static String sha256(Path file) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream input = Files.newInputStream(file)) {
      byte[] buffer = new byte[16 * 1024];
      int read;
      while ((read = input.read(buffer)) >= 0) digest.update(buffer, 0, read);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static void armInstaller(
      Path script, Path staged, Path target, String oldHash, String newHash) throws IOException {
    new ProcessBuilder(
            "powershell.exe",
            "-NoProfile",
            "-NonInteractive",
            "-WindowStyle",
            "Hidden",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            script.toString(),
            "-ParentPid",
            Long.toString(ProcessHandle.current().pid()),
            "-Staged",
            staged.toString(),
            "-Target",
            target.toString(),
            "-ExpectedOldHash",
            oldHash,
            "-NewHash",
            newHash,
            "-ModId",
            MOD_ID)
        .redirectErrorStream(true)
        .redirectOutput(script.resolveSibling("install.log").toFile())
        .start();
  }

  private static boolean isWindows() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
  }

  private static Throwable rootCause(Throwable failure) {
    Throwable current = failure;
    while (current.getCause() != null) current = current.getCause();
    return current;
  }

  private record ReleaseAsset(String name, String url) {}

  private record JarMetadata(String id, String version) {}

  private static final String WINDOWS_INSTALLER =
      """
param(
 [Parameter(Mandatory=$true)][long]$ParentPid,
 [Parameter(Mandatory=$true)][string]$Staged,
 [Parameter(Mandatory=$true)][string]$Target,
 [Parameter(Mandatory=$true)][string]$ExpectedOldHash,
 [Parameter(Mandatory=$true)][string]$NewHash,
 [Parameter(Mandatory=$true)][string]$ModId
)
$ErrorActionPreference='Stop'
Import-Module (Join-Path $PSHOME 'Modules/Microsoft.PowerShell.Utility/Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop
Import-Module (Join-Path $PSHOME 'Modules/CimCmdlets/CimCmdlets.psd1') -ErrorAction Stop
$locked=$null
$managedLock=$null
$trackerLock=$null
$status=Join-Path (Split-Path -Path $Staged -Parent) 'update-status.json'
function Status([string]$state) { @{state=$state;updatedAt=[DateTimeOffset]::UtcNow.ToString('o')} | ConvertTo-Json | Set-Content -LiteralPath $status -Encoding UTF8 }
function Running([string]$instance) {
 foreach($process in (Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'")){
  $line=$process.CommandLine
  if([string]::IsNullOrWhiteSpace($line)){throw 'Java process cannot be verified'}
  if($line -notmatch 'KnotClient|net\\.minecraft\\.client|--gameDir|--launchTarget'){continue}
  $match=[regex]::Match($line,'--gameDir(?:\\s+|=)(?:"([^"]+)"|([^\\s"]+))')
  if(!$match.Success){throw 'Minecraft instance cannot be verified'}
  $dir=if($match.Groups[1].Success){$match.Groups[1].Value}else{$match.Groups[2].Value}
  if([IO.Path]::GetFullPath($dir).TrimEnd('\\','/') -ieq $instance.TrimEnd('\\','/')){return $true}
 }
 return $false
}
try {
 $Target=[IO.Path]::GetFullPath($Target)
 $mods=Split-Path -Path $Target -Parent
 $instance=Split-Path -Path $mods -Parent
 if((Split-Path -Path $mods -Leaf) -cne 'mods'){throw 'Invalid target'}
 foreach($path in @($mods,$instance,$Target,$Staged)){if((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Redirected path'}}
 $managed=Join-Path $instance 'mods-user'
 $tracker=Join-Path (Split-Path -Parent $instance) 'user-mods-tracked.json'
 $isManaged=(Test-Path -LiteralPath $managed) -or (Test-Path -LiteralPath $tracker)
 if($isManaged){
  Add-Type -AssemblyName System.IO.Compression.FileSystem
  function Meta([string]$p){
   $zip=[IO.Compression.ZipFile]::OpenRead($p)
   try{$entry=$zip.GetEntry('fabric.mod.json');if(!$entry -or $entry.Length -gt 1048576){throw 'Invalid metadata'};$reader=[IO.StreamReader]::new($entry.Open());try{$reader.ReadToEnd()|ConvertFrom-Json}finally{$reader.Dispose()}}finally{$zip.Dispose()}
  }
  $leaf=Split-Path -Leaf $Target
  $managedTarget=Join-Path $managed $leaf
  foreach($path in @((Split-Path -Parent $instance),$managed,$tracker,$managedTarget)){
   if(!(Test-Path -LiteralPath $path) -or ((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'Unknown or redirected managed layout'}
  }
  $tracking=Get-Content -LiteralPath $tracker -Raw
  if(!$tracking.TrimStart().StartsWith('[')){throw 'Unknown tracking format'}
  $parsedTracking=ConvertFrom-Json -InputObject $tracking
  $tracked=@($parsedTracking)
  foreach($name in $tracked){if($name -isnot [string] -or [IO.Path]::GetFileName($name) -cne $name){throw 'Unknown tracking entry'}}
  if(@($tracked|Where-Object{$_ -ceq $leaf}).Count -ne 1){throw 'Mod not uniquely tracked'}
  $trackerHash=(Get-FileHash -LiteralPath $tracker -Algorithm SHA256).Hash
  if((Get-FileHash -LiteralPath $managedTarget -Algorithm SHA256).Hash -ine $ExpectedOldHash){throw 'Managed copy differs'}
  foreach($dir in @($mods,$managed)){
   $same=@(Get-ChildItem -LiteralPath $dir -Filter '*.jar' -File|Where-Object{(Meta $_.FullName).id -ceq $ModId})
   if($same.Count -ne 1 -or $same[0].Name -cne $leaf){throw 'Duplicate or ambiguous mod'}
  }
  $oldMeta=Meta $Target; $newMeta=Meta $Staged
  if($oldMeta.id -cne $ModId -or $newMeta.id -cne $ModId -or [version]$newMeta.version -le [version]$oldMeta.version){throw 'Unexpected update identity or version'}
 }
 Status 'waiting'
 while((Get-Process -Id $ParentPid -ErrorAction SilentlyContinue) -or (Running $instance)){Start-Sleep -Seconds 3}
 foreach($path in @($mods,$instance,$Target,$Staged)){if((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Redirected path after waiting'}}
 $archiveParent=Join-Path $instance 'mod-archive'
 if((Test-Path -LiteralPath $archiveParent) -and ((Get-Item -LiteralPath $archiveParent).Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'Redirected archive'}
 if((Get-FileHash -LiteralPath $Staged -Algorithm SHA256).Hash -ine $NewHash){throw 'Staged hash changed'}
 $archive=Join-Path $instance ('mod-archive/'+$ModId+'-'+[guid]::NewGuid().ToString('N'))
 New-Item -ItemType Directory -Path $archive | Out-Null
 $incoming=Join-Path $archive 'incoming.jar'
 Copy-Item -LiteralPath $Staged -Destination $incoming
 if((Get-FileHash -LiteralPath $incoming -Algorithm SHA256).Hash -ine $NewHash){throw 'Copy hash mismatch'}
 if(Running $instance){throw 'Minecraft restarted'}
 if($isManaged){
  foreach($path in @((Split-Path -Parent $instance),$managed,$tracker,$managedTarget)){if((Get-Item -LiteralPath $path).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'Redirected managed path after waiting'}}
  foreach($dir in @($mods,$managed)){
   $same=@(Get-ChildItem -LiteralPath $dir -Filter '*.jar' -File|Where-Object{(Meta $_.FullName).id -ceq $ModId})
   if($same.Count -ne 1 -or $same[0].Name -cne $leaf){throw 'Mod set changed while waiting'}
  }
  if((Get-FileHash -LiteralPath $tracker -Algorithm SHA256).Hash -ine $trackerHash){throw 'Tracking changed since preparation'}
  $managedLock=[IO.File]::Open($managedTarget,[IO.FileMode]::Open,[IO.FileAccess]::Read,([IO.FileShare]::Read -bor [IO.FileShare]::Delete))
  $trackerLock=[IO.File]::Open($tracker,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Read)
  $locked=[IO.File]::Open($Target,[IO.FileMode]::Open,[IO.FileAccess]::Read,([IO.FileShare]::Read -bor [IO.FileShare]::Delete))
  if((Get-FileHash -LiteralPath $managedTarget -Algorithm SHA256).Hash -ine $ExpectedOldHash -or (Get-FileHash -LiteralPath $Target -Algorithm SHA256).Hash -ine $ExpectedOldHash -or (Get-FileHash -LiteralPath $tracker -Algorithm SHA256).Hash -ine $trackerHash){throw 'Managed target changed'}
  $managedIncoming=Join-Path $archive 'managed-incoming.jar'
  Copy-Item -LiteralPath $incoming -Destination $managedIncoming
  if((Get-FileHash -LiteralPath $managedIncoming -Algorithm SHA256).Hash -ine $NewHash){throw 'Managed staged copy differs'}
  $backup=Join-Path $archive 'runtime-before.jar';$managedBackup=Join-Path $archive 'managed-before.jar'
  $movedRuntime=$false;$movedManaged=$false
  try{
   if(Running $instance){throw 'Minecraft restarted'}
   Move-Item -LiteralPath $Target -Destination $backup;$movedRuntime=$true
   Move-Item -LiteralPath $managedTarget -Destination $managedBackup;$movedManaged=$true
   Move-Item -LiteralPath $incoming -Destination $Target
   Move-Item -LiteralPath $managedIncoming -Destination $managedTarget
   foreach($p in @($Target,$managedTarget)){if((Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash -ine $NewHash -or (Meta $p).version -cne $newMeta.version){throw 'Installed copy differs'}}
   foreach($p in @($backup,$managedBackup)){if((Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash -ine $ExpectedOldHash){throw 'Backup differs'}}
   if((Get-FileHash -LiteralPath $tracker -Algorithm SHA256).Hash -ine $trackerHash){throw 'Tracking changed'}
  }catch{
   if($movedRuntime){if(Test-Path -LiteralPath $Target){if((Get-FileHash -LiteralPath $Target -Algorithm SHA256).Hash -ine $NewHash){throw 'Concurrent modification preserved'};Move-Item -LiteralPath $Target -Destination (Join-Path $archive 'failed-runtime.jar')};Move-Item -LiteralPath $backup -Destination $Target}
   if($movedManaged){if(Test-Path -LiteralPath $managedTarget){if((Get-FileHash -LiteralPath $managedTarget -Algorithm SHA256).Hash -ine $NewHash){throw 'Concurrent modification preserved'};Move-Item -LiteralPath $managedTarget -Destination (Join-Path $archive 'failed-managed.jar')};Move-Item -LiteralPath $managedBackup -Destination $managedTarget}
   throw
  }
  Status 'installed';exit 0
 }
 $locked=[IO.File]::Open($Target,[IO.FileMode]::Open,[IO.FileAccess]::Read,[IO.FileShare]::Delete)
 if((Get-FileHash -InputStream $locked -Algorithm SHA256).Hash -ine $ExpectedOldHash){throw 'Target changed since preparation'}
 $backup=Join-Path $archive (Split-Path -Path $Target -Leaf)
 Move-Item -LiteralPath $Target -Destination $backup
 try {
  Move-Item -LiteralPath $incoming -Destination $Target
  if((Get-FileHash -LiteralPath $Target -Algorithm SHA256).Hash -ine $NewHash){throw 'Final hash mismatch'}
 } catch {
  if(Test-Path -LiteralPath $Target){Move-Item -LiteralPath $Target -Destination (Join-Path $archive 'failed.jar')}
  if(!(Test-Path -LiteralPath $Target)){Move-Item -LiteralPath $backup -Destination $Target}
  throw
 }
 $locked.Dispose();$locked=$null
 if((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ine $ExpectedOldHash){throw 'Backup hash mismatch'}
 Status 'installed'
} catch { Write-Output $_.FullyQualifiedErrorId; Status 'blocked'; exit 2 } finally {if($locked){$locked.Dispose()};if($managedLock){$managedLock.Dispose()};if($trackerLock){$trackerLock.Dispose()}}

""";
}
