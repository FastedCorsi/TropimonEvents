package fr.tropimon.events;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/** Automatic HUD; the existing chat screen provides the pointer for gym shortcuts. */
final class EventsHud {
  private static int tooltipScroll;
  private static String hovered = "";

  private record Layout(int size, int[] xs, int[] ys) {
    int x(int index) {
      return xs[index];
    }

    int y(int index) {
      return ys[index];
    }
  }

  private static Layout layout(int count) {
    var window = MinecraftClient.getInstance().getWindow();
    var renderer = MinecraftClient.getInstance().textRenderer;
    int size = 16, gap = 4;
    int width = Math.max(size, Math.min(160, window.getScaledWidth() - 16));
    long now = System.currentTimeMillis();
    var notices = EventsClient.STATE.visible(now);
    int top = 24;
    var bossHud = MinecraftClient.getInstance().inGameHud.getBossBarHud();
    if (8 + width > window.getScaledWidth() / 2 - 91
        && bossHud instanceof fr.tropimon.events.mixin.BossBarHudAccessor accessor) {
      int visibleBars =
          Math.min(
              accessor.events$bossBars().size(),
              Math.max(0, (window.getScaledHeight() / 3 - 12) / 19 + 1));
      if (visibleBars > 0) top = Math.max(top, 12 + visibleBars * 19);
    }
    int[] xs = new int[count], ys = new int[count];
    int x = 8, y = top, rowHeight = 0;
    for (int i = 0; i < count; i++) {
      var lines = i < notices.size() ? timerLines(notices.get(i), now) : java.util.List.<String>of();
      int itemWidth = size;
      for (String line : lines) itemWidth = Math.max(itemWidth, renderer.getWidth(line));
      if (x > 8 && x + itemWidth > 8 + width) {
        x = 8;
        y += rowHeight + gap;
        rowHeight = 0;
      }
      xs[i] = x + (itemWidth - size) / 2;
      ys[i] = y;
      rowHeight = Math.max(rowHeight, size + (lines.isEmpty() ? 0 : 2 + lines.size() * 9));
      x += itemWidth + gap;
    }
    return new Layout(size, xs, ys);
  }

  static GymObservation gymAt(double mouseX, double mouseY) {
    var state = EventsClient.STATE;
    var gyms = state.gyms.values().stream().filter(GymObservation::open).toList();
    int index = state.visible(System.currentTimeMillis()).size();
    var layout = layout(index + gyms.size());
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
        || !EventsClient.STATE.serverRecognized
        || client.options.hudHidden
        || !(client.currentScreen instanceof net.minecraft.client.gui.screen.ChatScreen))
      return false;
    if (raidAt(x, y) != null && client.getNetworkHandler() != null) {
      // CapitalScreen's Ancient Ruins entry has id "raid" and sends this same command.
      client.getNetworkHandler().sendChatCommand("warp raid");
      client.setScreen(null);
      return true;
    }
    var gym = gymAt(x, y);
    if (gym == null) return false;
    GymTeleport.visit(gym);
    return true;
  }

  static EventState.Notice raidAt(double mouseX, double mouseY) {
    var state = EventsClient.STATE;
    var notices = state.visible(System.currentTimeMillis());
    var layout = layout(notices.size() + (int) state.gyms.values().stream().filter(GymObservation::open).count());
    for (int i = 0; i < notices.size(); i++) {
      var notice = notices.get(i);
      if (notice.kind() != EventState.Kind.RAID && notice.kind() != EventState.Kind.MEGA
          && notice.kind() != EventState.Kind.NEXT_RAID) continue;
      if (mouseX >= layout.x(i) && mouseX < layout.x(i) + layout.size
          && mouseY >= layout.y(i) && mouseY < layout.y(i) + layout.size) return notice;
    }
    return null;
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
      var layout = layout(count);
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
        if (notice.kind() == EventState.Kind.RAID || notice.kind() == EventState.Kind.MEGA
            || notice.kind() == EventState.Kind.NEXT_RAID)
          lines.add("Clic : se téléporter aux Anciennes Ruines");
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
    var layout = layout(count);
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
    var layout = layout(notices.size() + gyms.size());
    int size = layout.size;
    int index = 0;
    for (var notice : notices) {
      int x = layout.x(index), y = layout.y(index);
      index++;
      EventIcons.draw(context, notice.kind(), x, y, size);
      int textY = y + size + 2;
      for (String line : timerLines(notice, now)) {
        context.drawCenteredTextWithShadow(
            client.textRenderer, line, x + size / 2, textY,
            notice.kind() == EventState.Kind.NEXT_RAID ? 0xFFFFD16A : 0xFFFFFFFF);
        textY += 9;
      }
    }
    for (var gym : gyms) {
      int x = layout.x(index), y = layout.y(index);
      index++;
      EventIcons.gym(context, gym, x, y, size);
    }
  }

  private static java.util.List<String> timerLines(EventState.Notice notice, long now) {
    String text = timer(notice, now);
    if (text.isEmpty()) return java.util.List.of();
    var renderer = MinecraftClient.getInstance().textRenderer;
    var lines = new java.util.ArrayList<String>();
    String line = "";
    for (String word : text.split(" ")) {
      String next = line.isEmpty() ? word : line + " " + word;
      if (!line.isEmpty() && renderer.getWidth(next) > 36) {
        lines.add(line);
        line = word;
      } else line = next;
    }
    lines.add(line);
    return lines;
  }

  private static String timer(EventState.Notice notice, long now) {
    if (notice.kind() == EventState.Kind.NEXT_RAID)
      return "Dans " + EventState.duration(notice.end() - now);
    if (notice.end() > now) return EventState.duration(notice.end() - now);
    return notice.kind() == EventState.Kind.RAID || notice.kind() == EventState.Kind.MEGA
        ? "En cours"
        : "";
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
