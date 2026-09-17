package fr.tropimon.events;

import java.util.*;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public final class EventsScreen extends InstrumentScreen {
  private int selected = 0;
  private int objectiveOffset;

  public EventsScreen() {
    super("Tropimon Events");
  }

  @Override
  public void render(DrawContext c, int mouseX, int mouseY, float delta) {
    long now = System.currentTimeMillis();
    var state = EventsClient.STATE;
    var all = state.visible(now);
    int mx = localX(mouseX), my = localY(mouseY);
    begin(c, 0xFF80DFCA, "TROPIMON / EVENTS", "Le journal des signaux confirmés");
    c.fill(16, 56, 544, 107, PANEL);
    for (var kind : EventState.Kind.values()) {
      int x = 26 + kind.ordinal() * 64;
      var notice = all.stream().filter(n -> n.kind() == kind).findFirst().orElse(null);
      c.fill(x, 62, x + 46, 99, notice == null ? 0xFF172E3B : 0xFF294856);
      EventIcons.draw(c, kind, x + 11, 66, 2);
      if (hit(mx, my, x, 62, 46, 37)) selected = kind.ordinal();
    }
    var kind = EventState.Kind.values()[selected];
    var notice = all.stream().filter(n -> n.kind() == kind).findFirst().orElse(null);
    EventIcons.draw(c, kind, 28, 128, 2);
    label(c, notice == null ? kindName(kind) : notice.title(), 64, 126, EventIcons.color(kind));
    label(
        c,
        notice == null ? "Aucune annonce confirmée dans cette session" : notice.status(now),
        64,
        143,
        MUTED);
    String detail =
        notice == null
            ? "Les données ne sont jamais déduites d'un horaire fixe. Les icônes apparaissent dans"
                  + " le HUD à réception d'une annonce reconnue."
            : notice.detail();
    int line = 0;
    for (var part : textRenderer.wrapLines(Text.literal(detail), 285)) {
      if (line >= 8) break;
      c.drawText(textRenderer, part, 28, 177 + line++ * 12, WHITE, false);
    }
    c.fill(331, 117, 532, 301, PANEL);
    label(c, "ÉVÉNEMENT DU SERVEUR", 344, 131, 0xFF80DFCA);
    if (state.name.isBlank())
      text(
          c,
          "Ouvre une fois le menu d'événement officiel : son nom, sa fin et ses objectifs seront"
              + " repris ici.",
          344,
          153,
          173,
          MUTED);
    else {
      text(c, state.name, 344, 153, 173, WHITE);
      label(c, "Points : " + (state.points == null ? "non reçus" : state.points), 344, 180, WHITE);
      label(
          c,
          "Monnaie : " + (state.currency == null ? "non reçue" : state.currency),
          344,
          194,
          WHITE);
      label(c, "Rang : " + (state.rank == null ? "non reçu" : state.rank), 344, 208, WHITE);
      int y = 228;
      for (var goal : state.objectives.entrySet().stream().skip(objectiveOffset).toList()) {
        if (y > 278) break;
        label(
            c,
            textRenderer.trimToWidth(objective(goal.getKey()) + " : " + goal.getValue(), 172),
            344,
            y,
            MUTED);
        y += 12;
      }
      if (state.objectives.size() > 5) label(c, "Molette : autres objectifs", 344, 287, MUTED);
    }
    label(
        c,
        state.serverRecognized
            ? "Source : client officiel / session en cours"
            : "En attente d'un signal du client Tropimon officiel",
        28,
        291,
        MUTED);
    end(c);
  }

  static String kindName(EventState.Kind k) {
    return switch (k) {
      case RAID -> "Raids";
      case MEGA -> "Méga Raids";
      case SHINY -> "Boost chromatique";
      case XP -> "Boost expérience";
      case IV -> "Boost IV";
      case ABILITY -> "Talent caché";
      case CLEAR -> "Nettoyage";
      case SEASON -> "Événement saisonnier";
    };
  }

  @Override
  public boolean mouseScrolled(double x, double y, double h, double v) {
    if (localX(x) >= 331)
      objectiveOffset =
          Math.clamp(
              objectiveOffset + (v > 0 ? -1 : 1),
              0,
              Math.max(0, EventsClient.STATE.objectives.size() - 5));
    return true;
  }

  static String objective(String s) {
    return switch (s) {
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
      default -> s;
    };
  }
}
