package fr.tropimon.events;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/** Isolated synthetic world only; excluded from all delivery JARs. */
public final class SmokeClient implements ClientModInitializer {
  int ticks, stage = -1;
  long start;
  net.minecraft.client.gui.screen.Screen screen;
  BlockPos habitatPos;

  record EventFixture(String json) implements net.minecraft.network.packet.CustomPayload {
    static final Id<EventFixture> ID =
        new Id<>(net.minecraft.util.Identifier.of("tropimon", "open_event_packet"));
    static final net.minecraft.network.codec.PacketCodec<
            net.minecraft.network.RegistryByteBuf, EventFixture>
        CODEC =
            net.minecraft.network.codec.PacketCodec.of(
                (value, buffer) -> buffer.writeString(value.json()),
                buffer -> new EventFixture(buffer.readString()));

    public Id<? extends net.minecraft.network.packet.CustomPayload> getId() {
      return ID;
    }
  }

  @Override
  public void onInitializeClient() {
    if (!Boolean.getBoolean("tropimon.smoke")) return;
    net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
        .register(EventFixture.ID, EventFixture.CODEC);
    start = System.nanoTime();
    ClientTickEvents.END_CLIENT_TICK.register(
        client -> {
          if (stage == 99) return;
          try {
            if (System.nanoTime() - start > 240_000_000_000L)
              throw new AssertionError("Smoke timeout: " + stage);
            if (client.getOverlay() != null || ++ticks < 40) return;
            switch (stage) {
              case -1 -> {
                stage = 0;
                ticks = 0;
                client.options.getViewDistance().setValue(2);
                client.options.getGuiScale().setValue(2);
                client.options.pauseOnLostFocus = false;
                client
                    .getTutorialManager()
                    .setStep(net.minecraft.client.tutorial.TutorialStep.NONE);
                client
                    .createIntegratedServerLoader()
                    .createAndStart(
                        "instrument-smoke-" + System.currentTimeMillis(),
                        new LevelInfo(
                            "Instrument verification",
                            GameMode.CREATIVE,
                            false,
                            Difficulty.PEACEFUL,
                            true,
                            new GameRules(),
                            DataConfiguration.SAFE_MODE),
                        new GeneratorOptions(1L, false, false),
                        registries ->
                            registries
                                .get(RegistryKeys.WORLD_PRESET)
                                .get(WorldPresets.FLAT)
                                .createDimensionsRegistryHolder(),
                        client.currentScreen);
              }
              case 0 -> {
                if (client.player == null) return;
                long end = System.currentTimeMillis() / 1000 + 7200;
                client
                    .getServer()
                    .submit(
                        () -> {
                          var player =
                              client
                                  .getServer()
                                  .getPlayerManager()
                                  .getPlayer(client.player.getUuid());
                          String json =
                              "{\"name\":\"Festival de vérification\",\"description\":\"Événement"
                                  + " synthétique du test hors ligne\",\"endTimestamp\":"
                                  + end
                                  + ",\"eventObjectives\":{\"HUNTS\":25,\"RAIDS\":10}}";
                          player.networkHandler.sendPacket(
                              new net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket(
                                  new EventFixture(json)));
                        })
                    .get();
                screen = new EventsScreen();
                client.setScreen(screen);
                stage = 1;
                ticks = 0;
              }
              case 1 -> {
                require(
                    EventsClient.STATE.name.equals("Festival de vérification"),
                    "official event wire observed through real decoder");
                client
                    .getServer()
                    .submit(
                        () -> {
                          var player =
                              client
                                  .getServer()
                                  .getPlayerManager()
                                  .getPlayer(client.player.getUuid());
                          for (String text :
                              List.of(
                                  "[12:30]  TestPlayer triggered a Boost Shiny x2 for an hour !",
                                  "[12:30] TestPlayer a déclenché un Mega Raid ! (Clique pour te"
                                      + " téléporter)"))
                            player.networkHandler.sendPacket(
                                new net.minecraft.network.packet.s2c.play.GameMessageS2CPacket(
                                    net.minecraft.text.Text.literal(text), false));
                        })
                    .get();
                stage = 2;
                ticks = 0;
              }
              case 2 -> {
                require(
                    EventsClient.STATE.visible(System.currentTimeMillis()).size() == 3,
                    "actual system message packets detected");
                shot(client, "events");
                EventsClient.STATE.reset();
                require(
                    EventsClient.STATE.visible(System.currentTimeMillis()).isEmpty(),
                    "session reset");
                done(client);
              }
            }
          } catch (Throwable ex) {
            ex.printStackTrace();
            System.err.println("TROPIMON_SMOKE_FAILED");
            client.scheduleStop();
            stage = 99;
          }
        });
  }

  static Object field(Object instance, String name) throws Exception {
    var f = instance.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return f.get(instance);
  }

  static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
    System.out.println("TROPIMON_CHECK: " + message);
  }

  static void shot(MinecraftClient client, String name) throws Exception {
    Path dir = client.runDirectory.toPath().resolve("verification");
    Files.createDirectories(dir);
    try (var image = ScreenshotRecorder.takeScreenshot(client.getFramebuffer())) {
      image.writeTo(dir.resolve(name + ".png"));
    }
  }

  void done(MinecraftClient client) {
    System.out.println("TROPIMON_SMOKE_OK");
    client.scheduleStop();
    stage = 99;
  }
}
