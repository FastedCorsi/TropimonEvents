package fr.tropimon.events;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import org.slf4j.LoggerFactory;

public final class EventsClient implements ClientModInitializer {
  public static final EventState STATE = new EventState();
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
    BaronOutline.register();
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
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
    // Tropimon can send miracle announcements before JOIN finishes. Clear the previous session
    // when its play handler is initialized, before those first messages can arrive.
    ClientPlayConnectionEvents.INIT.register((h, c) -> connectionChanged(c));
    ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> connectionChanged(c));
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

  private static void connectionChanged(MinecraftClient client) {
    GYMS.reset(System.currentTimeMillis());
    STATE.connectionChanged(client.getSession().getUuidOrNull(), System.currentTimeMillis());
    EventIcons.reset();
  }

  public static void officialRegion() {
    GYMS.reset(System.currentTimeMillis());
  }

  public static void systemMessage(String text) {
    STATE.systemMessage(text, System.currentTimeMillis());
  }
}
