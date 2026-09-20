package fr.tropimon.events;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class DeferredUpdaterTest {
  @TempDir Path fixture;

  @Test
  void managedCopiesAndTrackingArePreserved() throws Exception {
    Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
    var field = TropimonSelfUpdater.class.getDeclaredField("WINDOWS_INSTALLER");
    field.setAccessible(true);
    Path script = fixture.resolve("managed.ps1");
    Files.writeString(script, (String) field.get(null));
    Path mods = Files.createDirectories(fixture.resolve("profile/instance/mods"));
    Path managed = Files.createDirectories(fixture.resolve("profile/instance/mods-user"));
    Path target = mods.resolve("test.jar"), imported = managed.resolve("test.jar");
    Path tracker = fixture.resolve("profile/user-mods-tracked.json");
    Files.writeString(tracker, "[\"test.jar\",\"other-disabled.jar\"]");
    jar(target, "1.0.0");
    Files.copy(target, imported);
    Path incoming = fixture.resolve("incoming.jar");
    jar(incoming, "1.1.0");
    String old = hash(target), next = hash(incoming), tracked = Files.readString(tracker);
    // Inconsistent persistent imports must never be silently overwritten.
    Files.writeString(imported, "changed");
    assertEquals(2, run(script, incoming, target, old, next));
    assertEquals(old, hash(target));
    Files.copy(target, imported, StandardCopyOption.REPLACE_EXISTING);
    assertEquals(
        0,
        run(script, incoming, target, old, next),
        () -> {
          try {
            return Files.readString(fixture.resolve("test-output.txt"));
          } catch (Exception e) {
            return "No output";
          }
        });
    assertEquals(next, hash(target));
    assertEquals(next, hash(imported));
    assertEquals(tracked, Files.readString(tracker));
    assertEquals(2, run(script, incoming, target, old, next));
    assertEquals(next, hash(imported));
  }

  private void jar(Path path, String version) throws Exception {
    try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(path))) {
      zip.putNextEntry(new java.util.zip.ZipEntry("fabric.mod.json"));
      zip.write(
          ("{\"id\":\"test_fixture\",\"version\":\"" + version + "\"}")
              .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      zip.closeEntry();
    }
  }

  @Test
  void generatedInstallerPreservesChangedTarget() throws Exception {
    Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
    var field = TropimonSelfUpdater.class.getDeclaredField("WINDOWS_INSTALLER");
    field.setAccessible(true);
    Path script = fixture.resolve("install.ps1");
    Files.writeString(script, (String) field.get(null));
    Path mods = Files.createDirectories(fixture.resolve("instance/mods")),
        stage = Files.createDirectories(fixture.resolve("stage"));
    Path target = mods.resolve("test.jar"), incoming = stage.resolve("incoming.jar");
    Files.writeString(target, "old fixture");
    Files.writeString(incoming, "new fixture");
    String old = hash(target), next = hash(incoming);
    assertEquals(0, run(script, incoming, target, old, next));
    assertEquals(next, hash(target));
    assertTrue(Files.isDirectory(fixture.resolve("instance/mod-archive")));
    assertEquals(2, run(script, incoming, target, old, next));
    assertEquals(next, hash(target));
  }

  private int run(Path script, Path stage, Path target, String old, String next) throws Exception {
    var process =
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
                "2147483647",
                "-Staged",
                stage.toString(),
                "-Target",
                target.toString(),
                "-ExpectedOldHash",
                old,
                "-NewHash",
                next,
                "-ModId",
                "test_fixture")
            .redirectErrorStream(true)
            .redirectOutput(fixture.resolve("test-output.txt").toFile())
            .start();
    assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Synthetic deferred installer timeout");
    return process.exitValue();
  }

  private String hash(Path file) throws Exception {
    return HexFormat.of()
        .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
  }
}
