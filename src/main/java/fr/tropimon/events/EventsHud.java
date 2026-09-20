package fr.tropimon.events;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Automatic HUD; the existing chat screen provides the pointer for gym shortcuts. */
final class EventsHud {
  private static int tooltipScroll;
  private static String hovered = "";

  private record Layout(int columns, int size) {
    int x(int index) {
      return 8 + index % columns * (size + 4);
    }

    int y(int index) {
      return 24 + index / columns * (size + 4);
    }
  }

  private static Layout layout(int count, int barons) {
    var window = MinecraftClient.getInstance().getWindow();
    int columns = Math.max(1, Math.min(4, (window.getScaledWidth() - 16) / 28));
    int rows = Math.max(1, (count + columns - 1) / columns);
    return new Layout(
        columns,
        Math.max(12, Math.min(24, (window.getScaledHeight() - 32 - barons * 28) / rows - 4)));
  }

  static GymObservation gymAt(double mouseX, double mouseY) {
    var state = EventsClient.STATE;
    var gyms = state.gyms.values().stream().filter(GymObservation::open).toList();
    int index = state.visible(System.currentTimeMillis()).size();
    var layout = layout(index + gyms.size(), EventsClient.BARONS.visible().size());
    for (var gym : gyms) {
      int x = layout.x(index), y = layout.y(index++);
      if (mouseX >= x && mouseX < x + layout.size && mouseY >= y && mouseY < y + layout.size)
        return gym;
    }
    return null;
  }

  static boolean click(double x, double y, int button) {
    var client = MinecraftClient.getInstance();
    if (button != 0
        || client.player == null
        || client.options.hudHidden
        || !(client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen))
      return false;
    var gym = gymAt(x, y);
    if (gym == null) return false;
    GymTeleport.visit(gym);
    return true;
  }

  static void tooltip(DrawContext context, int x, int y) {
    var client = MinecraftClient.getInstance();
    var state = EventsClient.STATE;
    long now = System.currentTimeMillis();
    var lines = new java.util.ArrayList<String>();
    String key = "";
    var gym = gymAt(x, y);
    if (gym != null) {
      key = gym.type();
      lines.add("Arène " + gym.label() + " · Clic pour se téléporter");
      lines.add("Champion : " + (gym.leader().isBlank() ? "non renseigné" : gym.leader()));
      lines.add(gym.battle());
      lines.add("Instantané reçu il y a " + Math.max(0, (now - gym.observed()) / 1000) + " s");
    } else {
      var notices = state.visible(now);
      int count =
          notices.size() + (int) state.gyms.values().stream().filter(GymObservation::open).count();
      var layout = layout(count, EventsClient.BARONS.visible().size());
      for (int i = 0; i < notices.size(); i++) {
        if (x < layout.x(i)
            || x >= layout.x(i) + layout.size
            || y < layout.y(i)
            || y >= layout.y(i) + layout.size) continue;
        var notice = notices.get(i);
        key = notice.kind().name();
        lines.add(notice.title());
        lines.add(notice.status(now));
        lines.add(notice.detail());
        if (notice.kind() == EventState.Kind.RAID || notice.kind() == EventState.Kind.MEGA) {
          var boss = state.raidBoss(notice.kind());
          lines.add(boss == null ? "Pokémon non identifié" : "Pokémon annoncé : " + boss.pokemon());
          lines.add("La disparition du signal ne prouve pas la fin du raid.");
        }
        if (notice.kind() == EventState.Kind.SEASON) {
          lines.add(
              "Points : "
                  + value(state.points)
                  + " · Monnaie : "
                  + value(state.currency)
                  + " · Rang : "
                  + value(state.rank));
          state.objectives.forEach((goal, amount) -> lines.add(objective(goal) + " : " + amount));
        }
        break;
      }
    }
    if (!key.equals(hovered)) tooltipScroll = 0;
    hovered = key;
    if (lines.isEmpty()) return;
    var wrapped = new java.util.ArrayList<net.minecraft.text.Text>();
    int width = Math.max(100, Math.min(280, client.getWindow().getScaledWidth() - 28));
    for (String line : lines)
      for (var part :
          client
              .textRenderer
              .getTextHandler()
              .wrapLines(
                  net.minecraft.text.Text.literal(line), width, net.minecraft.text.Style.EMPTY))
        wrapped.add(net.minecraft.text.Text.literal(part.getString()));
    int limit = Math.max(3, (client.getWindow().getScaledHeight() - 30) / 10);
    if (wrapped.size() > limit) {
      tooltipScroll = Math.min(tooltipScroll, wrapped.size() - limit + 1);
      wrapped =
          new java.util.ArrayList<>(
              wrapped.subList(tooltipScroll, Math.min(wrapped.size(), tooltipScroll + limit - 1)));
      wrapped.add(net.minecraft.text.Text.literal("Molette : autres informations"));
    }
    context.drawTooltip(client.textRenderer, wrapped, x, y);
  }

  private static String value(Integer value) {
    return value == null ? "non reçu" : value.toString();
  }

  static boolean scroll(double x, double y, double amount) {
    if (hovered.isEmpty() || amount == 0 || MinecraftClient.getInstance().options.hudHidden)
      return false;
    var state = EventsClient.STATE;
    int count =
        state.visible(System.currentTimeMillis()).size()
            + (int) state.gyms.values().stream().filter(GymObservation::open).count();
    var layout = layout(count, EventsClient.BARONS.visible().size());
    boolean hit = false;
    for (int i = 0; i < count; i++)
      hit |=
          x >= layout.x(i)
              && x < layout.x(i) + layout.size
              && y >= layout.y(i)
              && y < layout.y(i) + layout.size;
    if (!hit) return false;
    tooltipScroll = Math.clamp(tooltipScroll + (amount > 0 ? -1 : 1), 0, 100);
    return true;
  }

  static void draw(DrawContext context) {
    var client = MinecraftClient.getInstance();
    var state = EventsClient.STATE;
    long now = System.currentTimeMillis();
    var notices = state.visible(now);
    var gyms = state.gyms.values().stream().filter(GymObservation::open).toList();
    var barons = EventsClient.BARONS.visible();
    var layout = layout(notices.size() + gyms.size(), barons.size());
    int columns = layout.columns, size = layout.size;
    int step = size + 4;
    int index = 0;
    for (var notice : notices) {
      int x = layout.x(index), y = layout.y(index);
      index++;
      EventIcons.draw(context, notice.kind(), x, y, size);
      if (notice.end() > now) {
        long seconds = (notice.end() - now + 999) / 1000;
        String remaining = seconds >= 60 ? seconds / 60 + "m" : seconds + "s";
        context.drawTextWithShadow(client.textRenderer, remaining, x + 2, y + size - 7, 0xFFFFFFFF);
      }
    }
    for (var gym : gyms) {
      int x = layout.x(index), y = layout.y(index);
      index++;
      EventIcons.gym(context, gym, x, y, size);
      if (gym.battle().startsWith("Prise d'arène :"))
        EventIcons.tile(context, 10, x + size - 10, y - 2, 12);
    }
    int y = 24 + (index + columns - 1) / columns * step;
    int textWidth = Math.max(60, Math.min(210, client.getWindow().getScaledWidth() - 48));
    for (var baron : barons) {
      EventIcons.baron(context, baron.portrait(), 8, y, 24);
      String name =
          "Baron · " + baron.entity().getPokemon().getSpecies().getTranslatedName().getString();
      int distance = (int) Math.round(baron.entity().distanceTo(client.player));
      String detail = distance + " blocs · Niv. " + baron.entity().getPokemon().getLevel();
      if (baron.entity().isBattling()) detail += " · Combat";
      context.drawTextWithShadow(
          client.textRenderer,
          client.textRenderer.trimToWidth(name, textWidth),
          36,
          y + 2,
          0xFFFF7575);
      context.drawTextWithShadow(
          client.textRenderer,
          client.textRenderer.trimToWidth(detail, textWidth),
          36,
          y + 13,
          0xFFFFFFFF);
      y += 28;
    }
    if (EventsClient.BARONS.count() > 4)
      context.drawTextWithShadow(
          client.textRenderer,
          "+ " + (EventsClient.BARONS.count() - 4) + " autres Barons",
          8,
          y,
          0xFFFF7575);
  }

  private static String objective(String key) {
    return switch (key) {
      case "HUNTS" -> "Chasses";
      case "CHROMATIQUE" -> "Chromatiques";
      case "PLAYTIME" -> "Temps de jeu";
      case "RAIDS" -> "Raids";
      case "RAIDS_MEGA" -> "Méga Raids";
      case "SAFARI_EXCAVATION" -> "Fouilles safari";
      case "SAFARI_LOOT" -> "Butin safari";
      case "RANKED_MATCH" -> "Matchs classés";
      case "POKESTOP" -> "Pokéstops";
      case "LOOTR_CHEST" -> "Coffres";
      default -> key;
    };
  }
}
