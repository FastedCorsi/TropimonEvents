package fr.tropimon.events;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.LoggerFactory;

public final class EventsClient implements ClientModInitializer {
  public static final EventState STATE = new EventState();
  static KeyBinding openKey;
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
    openKey =
        KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "key.tropimon_events.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "category.tropimon_events"));
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          while (openKey.wasPressed()) {
            EventsHud.scroll = 0;
            c.setScreen(c.currentScreen instanceof EventsScreen ? null : new EventsScreen());
          }
        });
    ClientPlayConnectionEvents.JOIN.register((h, s, c) -> reset());
    ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> reset());
    HudRenderCallback.EVENT.register(
        (c, t) -> {
          var mc = MinecraftClient.getInstance();
          if (mc.player == null || mc.options.hudHidden || mc.currentScreen instanceof EventsScreen)
            return;
          double mx =
              mc.currentScreen == null
                  ? -1
                  : mc.mouse.getX() * mc.getWindow().getScaledWidth() / mc.getWindow().getWidth();
          double my =
              mc.currentScreen == null
                  ? -1
                  : mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight();
          EventsHud.draw(c, mx, my, false);
        });
    TropimonSelfUpdater.start(LoggerFactory.getLogger("tropimon_events"));
  }

  private static void reset() {
    STATE.reset();
    EventIcons.reset();
    EventsHud.scroll = 0;
  }

  public static void officialRegion() {
    reset();
    STATE.serverRecognized = true;
  }

  public static void systemMessage(String text) {
    if (STATE.serverRecognized) STATE.accept(text, true, System.currentTimeMillis());
  }
}
