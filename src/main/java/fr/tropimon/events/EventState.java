package fr.tropimon.events;

import java.util.*;
import java.util.regex.*;

/** Session-local observations, never predictions from a raid schedule. */
public final class EventState {
  public enum Kind {
    RAID,
    MEGA,
    SHINY,
    XP,
    IV,
    ABILITY,
    CLEAR,
    SEASON
  }

  public record Notice(Kind kind, String title, String detail, long observed, long end) {
    public String status(long now) {
      if (end == 0) return "Annonce reçue · état actuel inconnu";
      long seconds = Math.max(0, (end - now + 999) / 1000);
      if (seconds == 0) return "Terminé";
      return seconds >= 3600
          ? seconds / 3600 + " h " + seconds % 3600 / 60 + " min"
          : seconds >= 60 ? seconds / 60 + " min " + seconds % 60 + " s" : seconds + " s";
    }
  }

  private final EnumMap<Kind, Notice> notices = new EnumMap<>(Kind.class);
  private final LinkedHashMap<String, Long> recent = new LinkedHashMap<>();
  public String name = "", description = "";
  public Map<String, Integer> objectives = Map.of();
  public Integer points, currency, rank;
  public boolean serverRecognized;

  public void reset() {
    notices.clear();
    recent.clear();
    name = "";
    description = "";
    objectives = Map.of();
    points = null;
    currency = null;
    rank = null;
    serverRecognized = false;
  }

  public List<Notice> visible(long now) {
    return notices.values().stream()
        .filter(n -> n.end() == 0 ? now - n.observed() < 900_000 : n.end() > now)
        .toList();
  }

  public void definition(
      String name, String description, long end, Map<String, Integer> objectives, long now) {
    if (!this.name.equals(name)) {
      points = null;
      currency = null;
      rank = null;
    }
    this.name = name;
    this.description = description;
    this.objectives = Map.copyOf(objectives);
    serverRecognized = true;
    notices.put(Kind.SEASON, new Notice(Kind.SEASON, name, description, now, end));
  }

  public void progress(int points, int currency, int rank) {
    this.points = points;
    this.currency = currency;
    this.rank = rank;
    serverRecognized = true;
  }

  public boolean accept(String input, boolean officialSystem, long now) {
    if (!officialSystem || input == null || input.length() > 2048) return false;
    String s = input.replaceAll("§[0-9a-fk-orA-FK-OR]", "").strip();
    // Official server lines are timestamped. Rank/chat separators are never discarded.
    if (!s.matches("^\\[\\d{2}:\\d{2}]\\s+.*")) return false;
    s = s.replaceFirst("^\\[\\d{2}:\\d{2}]\\s+", "").replaceFirst("^[ꌈ]\\s*", "").strip();
    if (s.contains("") || s.contains("ꌂ") || s.contains("ꌃ")) return false;
    recent.entrySet().removeIf(e -> now - e.getValue() > 2500);
    if (recent.containsKey(s)) return false;
    Matcher raid =
        Pattern.compile(
                "^([A-Za-z0-9_]{1,16}) a déclenché un (Mega |Méga )?Raid ! \\(Clique pour te"
                    + " téléporter\\)$",
                Pattern.CASE_INSENSITIVE)
            .matcher(s);
    Kind kind;
    String title;
    long end;
    if (s.equals("No boosts are currently active.")
        || s.equals("Aucun boost n'est actuellement actif.")) {
      for (Kind k : List.of(Kind.SHINY, Kind.XP, Kind.IV, Kind.ABILITY)) notices.remove(k);
      remember(s, now);
      return true;
    } else if (raid.matches()) {
      kind = raid.group(2) == null ? Kind.RAID : Kind.MEGA;
      title = kind == Kind.RAID ? "Raid annoncé" : "Méga Raid annoncé";
      end = 0;
    } else {
      Matcher clear =
          Pattern.compile(
                  "^Suppression des objets au sol dans (\\d{1,3})"
                      + " (minute|minutes|seconde|secondes)[.!]?$")
              .matcher(s);
      Matcher boost =
          Pattern.compile(
                  "^[A-Za-z0-9_]{1,16} (?:triggered a |a déclenché un )(Boost Shiny x2|Boost XP"
                      + " x2|Boost IVs \\+10|Boost Talent Caché 10%|Boost Hidden Ability 10%)(?:"
                      + " for an hour| for one hour| pendant une heure) ?!$")
              .matcher(s);
      Matcher listing =
          Pattern.compile(
                  "^- (Boost Shiny x2|Boost XP x2|Boost IVs \\+10|Boost Talent Caché 10%|Boost"
                      + " Hidden Ability 10%) \\((?:fin dans|end in) (.+)\\)$")
              .matcher(s);
      Matcher extension =
          Pattern.compile(
                  "^[A-Za-z0-9_]{1,16} (?:extended the duration of |has increased the duration of"
                      + " |a augmenté la durée du |a prolongé la durée du )(Boost Shiny x2|Boost XP"
                      + " x2|Boost IVs \\+10|Boost Talent Caché 10%|Boost Hidden Ability"
                      + " 10%).*\\((?:fin dans|end in) (.+)\\)[.!]?$")
              .matcher(s);
      if (clear.matches()) {
        kind = Kind.CLEAR;
        title = "Nettoyage au sol";
        end =
            now
                + Integer.parseInt(clear.group(1))
                    * 1000L
                    * (clear.group(2).startsWith("minute") ? 60 : 1);
      } else if (boost.matches()) {
        title = boost.group(1);
        kind = boostKind(title);
        Notice old = notices.get(kind);
        end = Math.max(now, old == null ? 0 : old.end()) + 3_600_000;
      } else if (listing.matches() || extension.matches()) {
        Matcher match = listing.matches() ? listing : extension;
        title = match.group(1);
        kind = boostKind(title);
        long duration = parseDuration(match.group(2));
        if (duration <= 0) return false;
        end = now + duration;
      } else return false;
    }
    remember(s, now);
    notices.put(kind, new Notice(kind, title, s, now, end));
    return true;
  }

  private void remember(String s, long now) {
    recent.put(s, now);
    if (recent.size() > 64) recent.remove(recent.keySet().iterator().next());
  }

  private static Kind boostKind(String s) {
    return s.contains("Shiny")
        ? Kind.SHINY
        : s.contains("XP") ? Kind.XP : s.contains("IVs") ? Kind.IV : Kind.ABILITY;
  }

  static long parseDuration(String s) {
    Matcher m =
        Pattern.compile("(\\d{1,4})\\s*(h|min|m|s)", Pattern.CASE_INSENSITIVE).matcher(s.strip());
    long seconds = 0;
    int pos = 0;
    while (m.find()) {
      if (!s.substring(pos, m.start()).isBlank()) return -1;
      seconds +=
          Long.parseLong(m.group(1))
              * (m.group(2).equalsIgnoreCase("h")
                  ? 3600
                  : m.group(2).equalsIgnoreCase("s") ? 1 : 60);
      pos = m.end();
    }
    return s.substring(pos).isBlank() && seconds <= 604800 ? seconds * 1000 : -1;
  }
}
