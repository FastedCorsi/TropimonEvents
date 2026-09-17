package fr.tropimon.events;

import java.util.*;
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

  @Override
  public void onInitializeClient() {
    var key =
        KeyBindingHelper.registerKeyBinding(
            new KeyBinding(
                "key.tropimon_events.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_F6,
                "category.tropimon_events"));
    ClientTickEvents.END_CLIENT_TICK.register(
        c -> {
          while (key.wasPressed()) c.setScreen(new EventsScreen());
        });
    ClientPlayConnectionEvents.JOIN.register((h, s, c) -> STATE.reset());
    ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> STATE.reset());
    HudRenderCallback.EVENT.register(
        (c, t) -> {
          var mc = MinecraftClient.getInstance();
          if (mc.player == null || mc.options.hudHidden || mc.currentScreen instanceof EventsScreen)
            return;
          var visible = STATE.visible(System.currentTimeMillis());
          int x = 8, y = 8;
          for (var notice : visible) {
            c.fill(x, y, x + 25, y + 24, 0xDE0C1A25);
            EventIcons.draw(c, notice.kind(), x + 6, y + 4, 1);
            if (notice.end() > 0) {
              long s = Math.max(0, (notice.end() - System.currentTimeMillis()) / 1000);
              String count = s >= 60 ? s / 60 + "m" : s + "s";
              c.drawText(mc.textRenderer, count, x + 3, y + 17, 0xFFEAF8F1, false);
            }
            if (mc.currentScreen != null) {
              double mx =
                  mc.mouse.getX() * mc.getWindow().getScaledWidth() / mc.getWindow().getWidth();
              double my =
                  mc.mouse.getY() * mc.getWindow().getScaledHeight() / mc.getWindow().getHeight();
              if (mx >= x && mx < x + 25 && my >= y && my < y + 24)
                c.drawTooltip(
                    mc.textRenderer,
                    List.of(
                        net.minecraft.text.Text.literal(notice.title()),
                        net.minecraft.text.Text.literal(notice.status(System.currentTimeMillis())),
                        net.minecraft.text.Text.literal("F6 : détails · annonce serveur")),
                    (int) mx,
                    (int) my);
            }
            x += 29;
          }
        });
    TropimonSelfUpdater.start(LoggerFactory.getLogger("tropimon_events"));
  }

  public static void officialRegion() {
    STATE.reset();
    STATE.serverRecognized = true;
  }

  public static void systemMessage(String text) {
    if (STATE.serverRecognized) STATE.accept(text, true, System.currentTimeMillis());
  }
}
