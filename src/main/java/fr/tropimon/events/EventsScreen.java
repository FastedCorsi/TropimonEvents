package fr.tropimon.events;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** F6 frees the pointer above the HUD; no dashboard or combat interface. */
public final class EventsScreen extends Screen {
  public EventsScreen() {
    super(Text.literal("Tropimon Events"));
  }

  public void render(DrawContext c, int mouseX, int mouseY, float delta) {
    EventsHud.draw(c, mouseX, mouseY, true);
    c.drawCenteredTextWithShadow(
        textRenderer,
        "Survole une icône · F6 / Échap : retour au jeu",
        width / 2,
        height - 20,
        0xFFFFFFFF);
  }

  public boolean mouseScrolled(double x, double y, double h, double v) {
    EventsHud.scroll(v);
    return true;
  }

  public boolean keyPressed(int key, int scan, int modifiers) {
    if (EventsClient.openKey.matchesKey(key, scan)) {
      close();
      return true;
    }
    return super.keyPressed(key, scan, modifiers);
  }

  public boolean shouldPause() {
    return false;
  }
}
