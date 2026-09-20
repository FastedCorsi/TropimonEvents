package fr.tropimon.events;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import org.slf4j.LoggerFactory;

public final class EventsClient implements ClientModInitializer {
  public static final EventState STATE = new EventState();
  public static final BaronTracker BARONS = new BaronTracker();
  public static final GymRefresh GYMS = new GymRefresh();
  private static final net.minecraft.network.packet.s2c.play.BossBarS2CPacket.Consumer RAID_BARS =
      new net.minecraft.network.packet.s2c.play.BossBarS2CPacket.Consumer() {
        public void add(
            java.util.UUID id,
            net.minecraft.text.Text name,
            float percent,
            net.minecraft.entity.boss.BossBar.Color color,
            net.minecraft.entity.boss.BossBar.Style style,
            boolean darken,
            boolean music,
            boolean fog) {
          updateName(id, name);
        }

        public void updateName(java.util.UUID id, net.minecraft.text.Text name) {
          STATE.raidBar(id, name.getString(), System.currentTimeMillis());
        }

        public void remove(java.util.UUID id) {
          STATE.removeRaidBar(id);
        }
      };

  public static void observeBossBar(net.minecraft.network.packet.s2c.play.BossBarS2CPacket packet) {
    packet.accept(RAID_BARS);
  }

  public void onInitializeClient() {
    BARONS.register();
    BaronOutline.register();
    net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback.EVENT.register(
        (dispatcher, registry) ->
            dispatcher.register(
                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal(
                        "tropimonevents")
                    .then(
                        net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal(
                                "sound")
                            .then(
                                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
                                    .literal("on")
                                    .executes(context -> sound(context.getSource(), true)))
                            .then(
                                net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
                                    .literal("off")
                                    .executes(context -> sound(context.getSource(), false))))));
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          BARONS.tick(c);
          if (c.player != null
              && c.getNetworkHandler() != null
              && STATE.serverRecognized
              && c.currentScreen == null
              && net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("tropimodclient")
              && GYMS.due(System.currentTimeMillis())) {
            GYMS.request(
                System.currentTimeMillis(),
                () -> c.getNetworkHandler().sendChatCommand("gym open"));
          }
        });
    ClientPlayConnectionEvents.JOIN.register((h, s, c) -> reset());
    ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> reset());
    HudRenderCallback.EVENT.register(
        (c, t) -> {
          var mc = MinecraftClient.getInstance();
          if (mc.player == null || mc.options.hudHidden || mc.currentScreen != null) return;
          EventsHud.draw(c);
        });
    net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register(
        (client, screen, width, height) -> {
          if (!(screen instanceof net.minecraft.client.gui.screen.ChatScreen)) return;
          net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen)
              .register(
                  (s, context, mouseX, mouseY, delta) -> {
                    if (client.player != null && !client.options.hudHidden) {
                      EventsHud.draw(context);
                      EventsHud.tooltip(context, mouseX, mouseY);
                    }
                  });
          net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseClick(screen)
              .register((s, mouseX, mouseY, button) -> !EventsHud.click(mouseX, mouseY, button));
          net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents.allowMouseScroll(screen)
              .register(
                  (s, mouseX, mouseY, horizontal, vertical) ->
                      !EventsHud.scroll(mouseX, mouseY, vertical));
        });
    TropimonSelfUpdater.start(LoggerFactory.getLogger("tropimon_events"));
  }

  private static void reset() {
    BARONS.reset();
    GYMS.reset(System.currentTimeMillis());
    STATE.reset();
    EventIcons.reset();
  }

  private static int sound(
      net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource source, boolean enabled) {
    BARONS.soundEnabled = enabled;
    source.sendFeedback(
        net.minecraft.text.Text.literal(
            "Son des Barons " + (enabled ? "activé" : "coupé") + " pour cette session."));
    return 1;
  }

  public static void officialRegion() {
    GYMS.reset(System.currentTimeMillis());
    EventIcons.reset();
  }

  public static void systemMessage(String text) {
    STATE.systemMessage(text, System.currentTimeMillis());
  }
}
