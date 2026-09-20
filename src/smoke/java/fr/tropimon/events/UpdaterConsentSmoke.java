package fr.tropimon.events;

import java.nio.file.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.ScreenshotRecorder;

/** Offline visual test: legacy configuration, refusal and exact-version disclosure. */
public final class UpdaterConsentSmoke implements ClientModInitializer {
    private int tick, stage;
    private long started;
    @Override public void onInitializeClient() {
        if (!Boolean.getBoolean("updater.consent.smoke")) return;
        started = System.nanoTime();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            try {
                if (stage == 4) return;
                if (System.nanoTime() - started > 120_000_000_000L) throw new AssertionError("Consent smoke timeout");
                if (client.getOverlay() != null || ++tick < 40) return;
                tick = 0;
                if (stage == 0) {
                    require(client.currentScreen != null && client.currentScreen.getClass().getSimpleName().equals("UpdateScreen"), "Legacy enabled flag must show a consent screen");
                    screenshot(client, "check-consent.png");
                    require(noHttp(), "No network client before consent");
                    var buttons = client.currentScreen.children().stream().filter(ButtonWidget.class::isInstance).map(ButtonWidget.class::cast).toList();
                    buttons.getLast().onPress();
                    require(noHttp(), "Declining must not initialize networking");
                    var prefs = com.google.gson.JsonParser.parseString(Files.readString(FabricLoader.getInstance().getConfigDir().resolve("tropimon_events-updater.json"))).getAsJsonObject();
                    require(!TropimonSelfUpdater.checksConsented(prefs), "Refusal persists");
                    stage = 1;
                } else if (stage == 1) {
                    Class<?> asset = Class.forName(TropimonSelfUpdater.class.getName() + "$ReleaseAsset");
                    var assetConstructor = asset.getDeclaredConstructor(String.class, String.class); assetConstructor.setAccessible(true);
                    Object jar = assetConstructor.newInstance("example-mod-99.0.0.jar", "https://example.invalid/not-requested.jar");
                    var offerConstructor = TropimonSelfUpdater.ReleaseOffer.class.getDeclaredConstructors()[0]; offerConstructor.setAccessible(true);
                    Object offer = offerConstructor.newInstance("99.0.0", jar, null, null);
                    Class<?> screen = Class.forName(TropimonSelfUpdater.class.getName() + "$UpdateScreen");
                    var constructor = screen.getDeclaredConstructor(Screen.class, TropimonSelfUpdater.ReleaseOffer.class); constructor.setAccessible(true);
                    client.setScreen((Screen) constructor.newInstance(new TitleScreen(), offer));
                    stage = 2;
                } else if (stage == 2) {
                    screenshot(client, "download-consent.png");
                    client.currentScreen.mouseScrolled(100, 100, 0, -20);
                    stage = 3;
                } else {
                    screenshot(client, "download-consent-scrolled.png");
                    client.currentScreen.close();
                    require(noHttp(), "Closing the download screen does not download");
                    require(!Files.exists(FabricLoader.getInstance().getConfigDir().resolve(".tropimon-updates")), "No update files without approval");
                    System.out.println("UPDATER_CONSENT_SMOKE_OK");
                    stage = 4; client.scheduleStop();
                }
            } catch (Throwable failure) {
                stage = 4; failure.printStackTrace(); client.scheduleStop();
            }
        });
    }
    private static boolean noHttp() throws Exception {
        var field = TropimonSelfUpdater.class.getDeclaredField("http"); field.setAccessible(true); return field.get(null) == null;
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    private static void screenshot(MinecraftClient client, String name) throws Exception {
        Path directory = client.runDirectory.toPath().resolve("verification"); Files.createDirectories(directory);
        try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) { image.writeTo(directory.resolve(name)); }
    }
}
