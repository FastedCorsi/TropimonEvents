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
  int ticks, stage = -1, scale = 1;
  long start;
  boolean joined;
  net.minecraft.client.gui.screen.Screen screen;
  BlockPos habitatPos;

  record GymFixture(String json) implements net.minecraft.network.packet.CustomPayload {
    static final Id<GymFixture> ID = new Id<>(net.minecraft.util.Identifier.of(EventWire.GYMS));
    static final net.minecraft.network.codec.PacketCodec<
            net.minecraft.network.RegistryByteBuf, GymFixture>
        CODEC =
            net.minecraft.network.codec.PacketCodec.of(
                (v, b) -> b.writeString(v.json()), b -> new GymFixture(b.readString()));

    public Id<? extends net.minecraft.network.packet.CustomPayload> getId() {
      return ID;
    }
  }

  record TerminalFixture(byte[] data) implements net.minecraft.network.packet.CustomPayload {
    static final Id<TerminalFixture> ID =
        new Id<>(net.minecraft.util.Identifier.of(EventWire.CHALLENGER));
    static final net.minecraft.network.codec.PacketCodec<
            net.minecraft.network.RegistryByteBuf, TerminalFixture>
        CODEC =
            net.minecraft.network.codec.PacketCodec.of(
                (v, b) -> b.writeByteArray(v.data()), b -> new TerminalFixture(b.readByteArray()));

    public Id<? extends net.minecraft.network.packet.CustomPayload> getId() {
      return ID;
    }

    static TerminalFixture create(String json) {
      byte[] raw = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      var compressor = new io.airlift.compress.zstd.ZstdCompressor();
      byte[] compressed = new byte[compressor.maxCompressedLength(raw.length)];
      int n = compressor.compress(raw, 0, raw.length, compressed, 0, compressed.length);
      return new TerminalFixture(
          java.nio.ByteBuffer.allocate(n + 4).putInt(raw.length).put(compressed, 0, n).array());
    }
  }

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
    net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
        (handler, sender, client) -> {
          joined = true;
          ticks = 0;
        });
    net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register(
        (client, opened, width, height) -> {
          if (opened instanceof EventsScreen)
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(opened).register(
                (rendered, context, mouseX, mouseY, delta) -> EventsHud.draw(context, 102, 62));
        });
    net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
        .register(EventFixture.ID, EventFixture.CODEC);
    net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
        .register(GymFixture.ID, GymFixture.CODEC);
    net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
        GymFixture.ID,
        (payload, context) ->
            context
                .client()
                .setScreen(
                    new fr.erusel.tropimodclient.client.gui.gym.navigator.GymNavigatorScreen()));
    net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
        .register(TerminalFixture.ID, TerminalFixture.CODEC);
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
                if (!joined || client.player == null) return;
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
                client.setScreen(null);
                stage = 1;
                ticks = 0;
              }
              case 1 -> {
                require(
                    EventsClient.STATE.name.equals("Festival de vérification"),
                    "official event wire observed through real decoder");
                EventsClient.GYMS.request(
                    System.currentTimeMillis(),
                    () -> client.getNetworkHandler().sendChatCommand("gym open"));
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
                                  "- Shiny x2 (end in 1 hour, 17 minutes and 53 seconds)",
                                  "- XP x2 (end in 20 minutes and 31 seconds)",
                                  "- Talent Caché 10% (end in 18 minutes and 9 seconds)",
                                  "- IVs +10 (end in 18 minutes and 10 seconds)",
                                  " TestPlayer a déclenché un Mega Raid ! (Clique pour te"
                                      + " téléporter)"))
                            player.networkHandler.sendPacket(
                                new net.minecraft.network.packet.s2c.play.GameMessageS2CPacket(
                                    net.minecraft.text.Text.literal(text), false));
                          player.networkHandler.sendPacket(
                              new net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket(
                                  new GymFixture(
                                      "{\"gyms\":{\"FIRE\":{\"type\":\"FIRE\",\"open\":true,\"leaderName\":\"TestLeader\"},\"WATER\":{\"type\":\"WATER\",\"open\":false}}}")));
                          player.networkHandler.sendPacket(
                              new net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket(
                                  TerminalFixture.create(
                                      "{\"first\":{\"type\":\"FIRE\",\"open\":true,\"champion\":{\"leaderData\":{\"name\":\"TestLeader\"}}},\"second\":{\"gymType\":\"FIRE\",\"gymChallengeType\":\"TITLE\",\"status\":\"IN_PROGRESS\"}}")));
                          for (String boss : List.of("Raid : Charizard", "Mega Raid : Gengar")) {
                            var bar =
                                new net.minecraft.entity.boss.ServerBossBar(
                                    net.minecraft.text.Text.literal(boss),
                                    net.minecraft.entity.boss.BossBar.Color.RED,
                                    net.minecraft.entity.boss.BossBar.Style.PROGRESS);
                            player.networkHandler.sendPacket(
                                net.minecraft.network.packet.s2c.play.BossBarS2CPacket.add(bar));
                          }
                        })
                    .get();
                stage = 2;
                ticks = 0;
              }
              case 2 -> {
                require(
                    EventsClient.STATE.visible(System.currentTimeMillis()).size() == 7,
                    "actual system message packets detected");
                require(client.currentScreen == null, "automatic gym response never opens menu");
                EventsClient.GYMS.request(System.currentTimeMillis(), () -> {});
                client.getNetworkHandler().sendChatCommand("gym open");
                client.setScreen(
                    new fr.erusel.tropimodclient.client.gui.gym.navigator.GymNavigatorScreen());
                require(client.currentScreen != null, "manual gym menu preserved");
                client.setScreen(null);
                shot(client, "events");
                require(
                    EventsClient.STATE.gyms.size() == 2
                        && EventsClient.STATE.gyms.get("FIRE").battle().contains("en cours"),
                    "gym wire and compressed terminal decoded");
                require(
                    EventsClient.STATE.raidBoss(EventState.Kind.RAID).pokemon().equals("Charizard"),
                    "explicit raid boss network label decoded");
                // F6 points at the first arena; do not operate a real server.
                screen = new EventsScreen();
                client.setScreen(screen);
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(client.getWindow().getHandle(), 36, 124);
                stage = 3;
                ticks = 0;
              }
              case 3 -> {
                shot(client, "arena-tooltip");
                client.options.getGuiScale().setValue(scale);
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 1400, 1000);
                client.onResolutionChanged();
                stage = 5;
                ticks = 0;
              }
              case 5 -> {
                require(
                    client.getWindow().getScaleFactor() == scale, "effective GUI scale " + scale);
                shot(client, "gui-" + scale);
                if (++scale <= 4) {
                  client.options.getGuiScale().setValue(scale);
                  client.onResolutionChanged();
                } else {
                  org.lwjgl.glfw.GLFW.glfwSetWindowSize(client.getWindow().getHandle(), 640, 480);
                  stage = 6;
                }
                ticks = 0;
              }
              case 6 -> {
                require(
                    client.getWindow().getScaleFactor() == 2,
                    "small window clamps requested GUI 4 to effective 2");
                shot(client, "small-window");
                require(screen.mouseScrolled(18, 90, 0, -1), "tooltip scroll handled");
                require(
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_F6, 0, 0),
                    "F6 closes inspection");
                require(client.currentScreen == null, "F6 returns to automatic HUD");
                EventsClient.STATE.reset();
                require(
                    EventsClient.STATE.visible(System.currentTimeMillis()).isEmpty(),
                    "session reset");
                stage = 4;
                ticks = 0;
              }
              case 4 -> {
                shot(client, "empty-inspect");
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
