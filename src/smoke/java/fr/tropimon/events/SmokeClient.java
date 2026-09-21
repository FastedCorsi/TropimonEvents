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
  volatile int visits;
  volatile int raidVisits;
  volatile boolean visitorRequest;
  BlockPos habitatPos;

  record RegionFixture(String server) implements net.minecraft.network.packet.CustomPayload {
    static final Id<RegionFixture> ID =
        new Id<>(net.minecraft.util.Identifier.of(EventWire.REGION));
    static final net.minecraft.network.codec.PacketCodec<
            net.minecraft.network.RegistryByteBuf, RegionFixture>
        CODEC =
            net.minecraft.network.codec.PacketCodec.of(
                (value, buffer) -> buffer.writeString(value.server()),
                buffer -> new RegionFixture(buffer.readString()));

    public Id<? extends net.minecraft.network.packet.CustomPayload> getId() {
      return ID;
    }
  }

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
    net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register(
        (dispatcher, registry, environment) -> dispatcher.register(
            net.minecraft.server.command.CommandManager.literal("warp").then(
                net.minecraft.server.command.CommandManager.literal("raid").executes(command -> {
                  raidVisits++;
                  return 1;
                }))));
    EventsClient.STATE.accept("- XP x2 (end in 10 minutes)", true, System.currentTimeMillis());
    net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S()
        .register(
            fr.tropimon.tropimodcore.networking.payload.teleport.WarpRequestPayload.ID,
            fr.tropimon.tropimodcore.networking.payload.teleport.WarpRequestPayload.CODEC);
    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
        fr.tropimon.tropimodcore.networking.payload.teleport.WarpRequestPayload.ID,
        (payload, context) -> {
          visits++;
          visitorRequest =
              payload.request().gym().equals("FIRE")
                  && payload.request().warp().equals("VISITOR")
                  && payload.request().player().equals(context.player().getUuid());
        });
    net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.JOIN.register(
        (handler, sender, client) -> {
          joined = true;
          ticks = 0;
        });
    net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
        .register(EventFixture.ID, EventFixture.CODEC);
    net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C()
        .register(RegionFixture.ID, RegionFixture.CODEC);
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
                if (Boolean.getBoolean("tropimon.smoke.barons")) {
                  BaronSmoke.start(client);
                  stage = 99;
                  return;
                }
                require(
                    ((Collection<?>) field(EventsClient.STATE, "earlyMessages"))
                        .stream()
                            .anyMatch(
                                message -> message.toString().contains("1 hour and 13 seconds")),
                    "miracle received before client JOIN is retained");
                require(
                    EventsClient.STATE.visible(System.currentTimeMillis()).stream()
                        .noneMatch(n -> n.kind() == EventState.Kind.XP),
                    "INIT clears previous session before new messages");
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
                                  new RegionFixture("synthetic-region")));
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
                    EventsClient.STATE.visible(System.currentTimeMillis()).stream()
                        .anyMatch(n -> n.kind() == EventState.Kind.SHINY),
                    "Shiny packet received before region recognition survives");
                require(
                    EventsClient.STATE.visible(System.currentTimeMillis()).stream()
                        .map(EventState.Notice::kind)
                        .toList()
                        .containsAll(
                            List.of(
                                EventState.Kind.SHINY,
                                EventState.Kind.ABILITY,
                                EventState.Kind.IV)),
                    "all three miracles received before JOIN survive region recognition");
                require(
                    java.util.Arrays.stream(client.options.allKeys)
                        .noneMatch(k -> k.getTranslationKey().equals("key.tropimon_events.open")),
                    "no Events F6 binding remains");
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
                    EventsClient.STATE.visible(System.currentTimeMillis()).stream()
                            .filter(n -> n.kind() != EventState.Kind.NEXT_RAID)
                            .count()
                        == 7,
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
                client
                    .getServer()
                    .submit(
                        () -> {
                          var player =
                              client
                                  .getServer()
                                  .getPlayerManager()
                                  .getPlayer(client.player.getUuid());
                          player.networkHandler.sendPacket(
                              new net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket(
                                  new RegionFixture("synthetic-arena")));
                        })
                    .get();
                stage = 3;
                ticks = 0;
              }
              case 3 -> {
                require(
                    EventsClient.STATE.gyms.size() == 2
                        && EventsClient.STATE.gyms.get("FIRE").open()
                        && EventsClient.STATE.gyms.get("FIRE").battle().contains("en cours"),
                    "region packet preserves open gym and terminal state");
                require(
                    EventsClient.STATE.visible(System.currentTimeMillis()).stream()
                            .filter(n -> n.kind() != EventState.Kind.NEXT_RAID)
                            .count()
                        == 7,
                    "region packet preserves all HUD event icons");
                require(
                    EventsClient.STATE.raidBoss(EventState.Kind.RAID).pokemon().equals("Charizard"),
                    "region packet preserves unchanged raid identity");
                shot(client, "automatic-hud");
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
                client.setScreen(
                    new net.minecraft.client.gui.screen.ChatScreen("message conservé"));
                net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(
                        client.currentScreen)
                    .register(
                        (screen, context, mouseX, mouseY, delta) ->
                            EventsHud.tooltip(context, 94, 54));
                stage = 7;
                ticks = 0;
              }
              case 7 -> {
                shot(client, "chat-gui-" + scale);
                var chat = client.currentScreen;
                var click =
                    net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(chat)
                        .invoker();
                require(
                    click.allowMouseClick(chat, 1, 1, 0),
                    "outside click remains available to chat");
                require(!EventsHud.scroll(1, 1, -1), "outside wheel remains available to chat");
                boolean found = false;
                for (int y = 24; y < client.getWindow().getScaledHeight() && !found; y += 2)
                  for (int x = 8; x < client.getWindow().getScaledWidth(); x += 2) {
                    var gym = EventsHud.gymAt(x, y);
                    if (gym == null) continue;
                    require(gym.type().equals("FIRE"), "closed gym is never clickable");
                    client.options.hudHidden = true;
                    require(click.allowMouseClick(chat, x, y, 0), "hidden HUD cannot teleport");
                    client.options.hudHidden = false;
                    require(click.allowMouseClick(chat, x, y, 1), "right click does not teleport");
                    require(
                        !click.allowMouseClick(chat, x, y, 0),
                        "left gym click consumed at GUI " + scale);
                    require(client.currentScreen == null, "successful gym request closes chat");
                    found = true;
                    break;
                  }
                require(found, "gym hit area found at GUI " + scale);
                stage = 8;
                ticks = 0;
              }
              case 8 -> {
                require(
                    visits == scale && visitorRequest,
                    "exactly one official visitor request received per click");
                require(raidVisits == scale - 1, "no automatic raid teleport");
                client.setScreen(new net.minecraft.client.gui.screen.ChatScreen("raid"));
                var chat = client.currentScreen;
                var click = net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(chat).invoker();
                boolean found = false;
                var expected = scale % 2 == 0 ? EventState.Kind.MEGA : EventState.Kind.RAID;
                for (int y = 24; y < client.getWindow().getScaledHeight() && !found; y++)
                  for (int x = 8; x < client.getWindow().getScaledWidth(); x++) {
                    var raid = EventsHud.raidAt(x, y);
                    if (raid == null || raid.kind() != expected) continue;
                    client.options.hudHidden = true;
                    require(click.allowMouseClick(chat, x, y, 0), "hidden raid cannot teleport");
                    client.options.hudHidden = false;
                    require(click.allowMouseClick(chat, x, y, 1), "right raid click cannot teleport");
                    EventsClient.STATE.serverRecognized = false;
                    require(click.allowMouseClick(chat, x, y, 0), "foreign server cannot receive raid warp");
                    EventsClient.STATE.serverRecognized = true;
                    require(!click.allowMouseClick(chat, x, y, 0), "raid click consumed at GUI " + scale);
                    require(client.currentScreen == null, "raid warp closes chat");
                    found = true;
                    break;
                  }
                require(found, "raid hit area found at GUI " + scale);
                stage = 9;
                ticks = 0;
              }
              case 9 -> {
                require(raidVisits == scale, "exactly one warp raid reaches server per click");
                if (scale == 5) {
                  EventsClient.STATE.reset();
                  require(
                      EventsClient.STATE.visible(System.currentTimeMillis()).isEmpty(),
                      "session reset");
                  stage = 4;
                } else if (++scale <= 4) {
                  client.options.getGuiScale().setValue(scale);
                  client.onResolutionChanged();
                  stage = 5;
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
                require(client.currentScreen == null, "HUD requires no screen or key binding");
                client.setScreen(
                    new net.minecraft.client.gui.screen.ChatScreen("message conservé"));
                stage = 7;
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
