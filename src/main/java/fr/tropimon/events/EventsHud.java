package fr.tropimon.events;

import java.util.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

final class EventsHud {
  static int scroll;

  static void scroll(double amount) {
    scroll = Math.clamp(scroll + (amount > 0 ? -1 : 1), 0, 100);
  }

  static void draw(DrawContext c, double mouseX, double mouseY, boolean inspect) {
    var mc = MinecraftClient.getInstance();
    var state = EventsClient.STATE;
    long now = System.currentTimeMillis();
    var notices = state.visible(now);
    int columns = Math.max(1, Math.min(4, (mc.getWindow().getScaledWidth() - 16) / 38));
    int count = (inspect ? 8 : notices.size()) + Math.max(inspect ? 1 : 0, state.gyms.size());
    int rows = Math.max(1, (count + columns - 1) / columns);
    int size = Math.max(12, Math.min(32, (mc.getWindow().getScaledHeight() - 40) / rows - 6));
    int step = size + 6;
    int i = 0;
    List<Text> hovered = null;
    for (var kind : EventState.Kind.values()) {
      var notice = notices.stream().filter(n -> n.kind() == kind).findFirst().orElse(null);
      if (notice == null && !inspect) continue;
      int x = 8 + i % columns * step, y = 8 + i / columns * step;
      i++;
      EventIcons.draw(c, kind, x, y, size);
      if (notice != null && notice.end() > now) {
        long seconds = (notice.end() - now + 999) / 1000;
        String remaining = seconds >= 60 ? seconds / 60 + "m" : seconds + "s";
        c.drawTextWithShadow(mc.textRenderer, remaining, x + 2, y + size - 7, 0xFFFFFFFF);
      }
      if (hit(mouseX, mouseY, x, y, size)) {
        hovered = new ArrayList<>();
        add(hovered, name(kind));
        if (notice == null) add(hovered, "Aucune information reçue dans cette session");
        else {
          add(hovered, notice.title());
          add(hovered, notice.status(now));
          add(hovered, notice.detail());
          add(hovered, age(now, notice.observed()));
        }
        if (kind == EventState.Kind.RAID || kind == EventState.Kind.MEGA) {
          var boss = state.raidBoss(kind);
          add(
              hovered,
              boss == null ? "Pokémon non identifié" : "Pokémon annoncé : " + boss.pokemon());
          add(hovered, "La disparition du signal ne prouve pas la fin du raid.");
        }
        if (kind == EventState.Kind.SEASON) {
          if (state.name.isBlank())
            add(hovered, "Ouvre le menu d'événement officiel pour recevoir ses données.");
          else {
            add(
                hovered,
                "Points : "
                    + value(state.points)
                    + " · Monnaie : "
                    + value(state.currency)
                    + " · Rang : "
                    + value(state.rank));
            for (var goal : state.objectives.entrySet())
              add(hovered, objective(goal.getKey()) + " : " + goal.getValue());
          }
        }
      }
    }
    for (var gym : state.gyms.values()) {
      int x = 8 + i % columns * step, y = 8 + i / columns * step;
      i++;
      EventIcons.tile(c, gym.open() ? 8 : 9, x, y, size);
      if (gym.battle().startsWith("Prise d'arène :")) EventIcons.tile(c, 10, x + 18, y - 2, 16);
      if (hit(mouseX, mouseY, x, y, size)) {
        hovered = new ArrayList<>();
        add(hovered, "Arène " + gym.label());
        add(hovered, gym.status());
        add(hovered, "Champion : " + (gym.leader().isBlank() ? "non renseigné" : gym.leader()));
        add(hovered, gym.battle());
        add(hovered, age(now, gym.observed()));
        add(hovered, "Instantané serveur · ouvre le menu officiel pour actualiser.");
      }
    }
    if (inspect && state.gyms.isEmpty()) {
      int x = 8 + i % columns * step, y = 8 + i / columns * step;
      EventIcons.tile(c, 11, x, y, size);
      if (hit(mouseX, mouseY, x, y, size))
        hovered =
            List.of(
                Text.literal("Arènes : état non reçu"),
                Text.literal("Ouvre le navigateur ou un terminal d'arène officiel."));
    }
    if (hovered != null) {
      int maxLines = Math.max(3, (mc.getWindow().getScaledHeight() - 30) / 10);
      if (hovered.size() > maxLines) {
        int offset = Math.min(scroll, hovered.size() - maxLines + 1);
        hovered =
            new ArrayList<>(
                hovered.subList(offset, Math.min(hovered.size(), offset + maxLines - 1)));
        hovered.add(Text.literal("Molette : autres informations"));
      }
      c.drawTooltip(mc.textRenderer, hovered, (int) mouseX, (int) mouseY);
    }
  }

  private static boolean hit(double mx, double my, int x, int y, int size) {
    return mx >= x && mx < x + size && my >= y && my < y + size;
  }

  private static String value(Integer n) {
    return n == null ? "non reçu" : n.toString();
  }

  private static String age(long now, long then) {
    return "Dernière observation : il y a " + Math.max(0, (now - then) / 1000) + " s";
  }

  private static void add(List<Text> lines, String text) {
    var mc = MinecraftClient.getInstance();
    int width = Math.max(100, Math.min(280, mc.getWindow().getScaledWidth() - 28));
    StringBuilder line = new StringBuilder();
    for (String word : text.replace('\n', ' ').split(" ")) {
      if (!line.isEmpty() && mc.textRenderer.getWidth(line + " " + word) > width) {
        lines.add(Text.literal(line.toString()));
        line.setLength(0);
      }
      if (!line.isEmpty()) line.append(' ');
      line.append(word);
    }
    if (!line.isEmpty()) lines.add(Text.literal(line.toString()));
  }

  private static String name(EventState.Kind kind) {
    return switch (kind) {
      case RAID -> "Raid";
      case MEGA -> "Méga Raid";
      case SHINY -> "Boost chromatique";
      case XP -> "Boost expérience";
      case IV -> "Boost IV";
      case ABILITY -> "Talent caché";
      case CLEAR -> "Nettoyage au sol";
      case SEASON -> "Événement saisonnier";
    };
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
