package fr.tropimon.events;

import java.util.*;
import java.util.regex.*;

/** Observations and clearly separate schedule reminders; no inferred raid duration. */
public final class EventState {
  private static final String BOOST_NAME =
      "((?:Boost )?(?:Shiny x2|XP x2|IVs \\+10|Talent Caché 10%|Hidden Ability 10%))";

  public enum Kind {
    RAID,
    MEGA,
    SHINY,
    XP,
    IV,
    ABILITY,
    CLEAR,
    SEASON,
    NEXT_RAID
  }

  public record Notice(Kind kind, String title, String detail, long observed, long end) {
    public String status(long now) {
      if (kind == Kind.NEXT_RAID) return "Début prévu dans " + duration(end - now);
      if (end == 0) return "Annonce reçue · état actuel inconnu";
      long seconds = Math.max(0, (end - now + 999) / 1000);
      if (seconds == 0) return "Terminé";
      return seconds >= 3600
          ? seconds / 3600 + " h " + seconds % 3600 / 60 + " min"
          : seconds >= 60 ? seconds / 60 + " min " + seconds % 60 + " s" : seconds + " s";
    }
  }

  private final EnumMap<Kind, Notice> notices = new EnumMap<>(Kind.class);
  private final EnumMap<Kind, Notice> retainedMiracles = new EnumMap<>(Kind.class);
  private UUID retainedPlayer;
  private long scheduleSecond = Long.MIN_VALUE;
  private Notice scheduledRaid;

  private record EarlyMessage(String text, long received) {}

  private final ArrayDeque<EarlyMessage> earlyMessages = new ArrayDeque<>();
  private final LinkedHashMap<String, Long> recent = new LinkedHashMap<>();
  public final Map<String, GymObservation> gyms = new LinkedHashMap<>();
  private final Map<UUID, RaidBoss> raidBars = new LinkedHashMap<>();

  public record RaidBoss(Kind kind, String pokemon, long observed) {}

  /** Only explicit server boss-bar labels. A generic Pokemon/battle name is insufficient. */
  public void raidBar(UUID id, String title, long now) {
    raidBars.remove(id);
    if (title == null || title.length() > 128) return;
    Matcher m =
        Pattern.compile(
                "^(Mega Raid|Méga Raid|Raid)\\s*[:—-]\\s*([\\p{L}0-9 .:'’♀♂-]{1,64})$",
                Pattern.CASE_INSENSITIVE)
            .matcher(title.strip());
    if (!m.matches() || raidBars.size() >= 32) return;
    Kind kind = m.group(1).equalsIgnoreCase("Raid") ? Kind.RAID : Kind.MEGA;
    raidBars.put(id, new RaidBoss(kind, m.group(2).strip(), now));
    notices.put(
        kind,
        new Notice(kind, kind == Kind.RAID ? "Raid observé" : "Méga Raid observé", title, now, 0));
  }

  public void removeRaidBar(UUID id) {
    raidBars.remove(id);
  }

  public RaidBoss raidBoss(Kind kind) {
    var matches = raidBars.values().stream().filter(b -> b.kind() == kind).toList();
    return matches.size() == 1 ? matches.getFirst() : null;
  }

  public String name = "", description = "";
  public Map<String, Integer> objectives = Map.of();
  public Integer points, currency, rank;
  public boolean serverRecognized;

  public void reset() {
    retainedMiracles.clear();
    retainedPlayer = null;
    clearSession();
  }

  /** Keep only unexpired miracles in memory, scoped to the same player and official recognition. */
  public void connectionChanged(UUID player, long now) {
    if (!Objects.equals(retainedPlayer, player)) retainedMiracles.clear();
    if (serverRecognized && Objects.equals(retainedPlayer, player)) {
      retainedMiracles.clear();
      notices.forEach(
          (kind, notice) -> {
            if (miracle(kind) && notice.end() > now) retainedMiracles.put(kind, notice);
          });
    }
    retainedPlayer = player;
    retainedMiracles.values().removeIf(n -> n.end() <= now);
    clearSession();
  }

  private static boolean miracle(Kind kind) {
    return kind == Kind.SHINY || kind == Kind.XP || kind == Kind.IV || kind == Kind.ABILITY;
  }

  private void clearSession() {
    scheduleSecond = Long.MIN_VALUE;
    scheduledRaid = null;
    earlyMessages.clear();
    notices.clear();
    recent.clear();
    gyms.clear();
    raidBars.clear();
    name = "";
    description = "";
    objectives = Map.of();
    points = null;
    currency = null;
    rank = null;
    serverRecognized = false;
  }

  public void systemMessage(String text, long now) {
    if (serverRecognized) {
      accept(text, true, now);
    } else if (text != null && text.length() <= 2048) {
      if (earlyMessages.size() == 64) earlyMessages.removeFirst();
      earlyMessages.addLast(new EarlyMessage(text, now));
    }
  }

  /** Travel is not a state update; only a new connection resets session observations. */
  public void region(long now) {
    var early = List.copyOf(earlyMessages);
    earlyMessages.clear();
    serverRecognized = true;
    retainedMiracles.forEach(
        (kind, notice) -> {
          if (notice.end() > now) notices.putIfAbsent(kind, notice);
        });
    for (var message : early)
      if (now - message.received() <= 30000) accept(message.text(), true, message.received());
  }

  public List<Notice> visible(long now) {
    var result =
        new ArrayList<>(
            notices.values().stream()
                .filter(n -> n.end() == 0 ? now - n.observed() < 900_000 : n.end() > now)
                .toList());
    if (serverRecognized) {
      if (scheduleSecond != now / 1000) {
        scheduleSecond = now / 1000;
        long start = RaidSchedule.next(now);
        scheduledRaid =
            start - now <= 300_000
                ? new Notice(
                    Kind.NEXT_RAID, "Prochain raid", "Horaire prévu · heure de Paris", now, start)
                : null;
      }
      if (scheduledRaid != null) result.add(scheduledRaid);
    }
    return result;
  }

  public static String duration(long millis) {
    long seconds = Math.max(0, (millis + 999) / 1000);
    return seconds >= 3600
        ? seconds / 3600 + "h " + seconds % 3600 / 60 + "m " + seconds % 60 + "s"
        : seconds >= 60 ? seconds / 60 + "m " + seconds % 60 + "s" : seconds + "s";
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
    // Timestamps are optional client decoration, absent from the original network message.
    // Keep rank/chat separators so a player's quoted announcement is never accepted.
    s = s.replaceFirst("^\\[\\d{2}:\\d{2}]\\s+", "").replaceFirst("^[ꌈ]\\s*", "").strip();
    if (s.contains("") || s.contains("ꌂ") || s.contains("ꌃ")) return false;
    recent.entrySet().removeIf(e -> now - e.getValue() > 2500);
    if (recent.containsKey(s)) return false;
    Matcher raidTime =
        Pattern.compile(
                "^(?:Le |The )?((?:Mega |Méga )?Raid) (?:se termine dans|ends in) (.+?)[.!]?$",
                Pattern.CASE_INSENSITIVE)
            .matcher(s);
    if (raidTime.matches()) {
      long duration = parseDuration(raidTime.group(2));
      if (duration <= 0) return false;
      Kind raidKind = raidTime.group(1).equalsIgnoreCase("Raid") ? Kind.RAID : Kind.MEGA;
      notices.put(raidKind, new Notice(raidKind, raidTime.group(1), s, now, now + duration));
      remember(s, now);
      return true;
    }
    Matcher raidEnd =
        Pattern.compile(
                "^(?:Le |The )?((?:Mega |Méga )?Raid) (?:est terminé|has ended)[.!]?$",
                Pattern.CASE_INSENSITIVE)
            .matcher(s);
    if (raidEnd.matches()) {
      Kind raidKind = raidEnd.group(1).equalsIgnoreCase("Raid") ? Kind.RAID : Kind.MEGA;
      notices.remove(raidKind);
      raidBars.values().removeIf(b -> b.kind() == raidKind);
      remember(s, now);
      return true;
    }
    // Parse arena messages only when their fixed opening/closing words are present.
    if (s.contains("arène") || s.contains(" gym")) {
      var gym = GymObservation.announcement(s, now);
      if (gym != null) {
        var old = gyms.get(gym.type());
        if (gym.leader().isBlank() && old != null)
          gym = new GymObservation(gym.type(), old.leader(), gym.open(), gym.battle(), now);
        gyms.put(gym.type(), gym);
        remember(s, now);
        return true;
      }
    }
    Matcher raid =
        Pattern.compile(
                "^([A-Za-z0-9_]{1,16}) (?:a déclenché un |has started a )(Mega |Méga )?Raid !"
                    + "(?: \\((?:Clique pour te téléporter|Click to teleport)\\))?$",
                Pattern.CASE_INSENSITIVE)
            .matcher(s);
    Kind kind;
    String title;
    long end;
    if (s.equals("No boosts are currently active.")
        || s.equals("Aucun boost n'est actuellement actif.")) {
      for (Kind k : List.of(Kind.SHINY, Kind.XP, Kind.IV, Kind.ABILITY)) notices.remove(k);
      retainedMiracles.clear();
      remember(s, now);
      return true;
    } else if (raid.matches()) {
      kind = raid.group(2) == null ? Kind.RAID : Kind.MEGA;
      title = kind == Kind.RAID ? "Raid annoncé" : "Méga Raid annoncé";
      end = 0;
      // A new announcement cannot reuse the Pokemon from an earlier raid observation.
      raidBars.values().removeIf(b -> b.kind() == kind);
    } else {
      Matcher clear =
          Pattern.compile(
                  "^Suppression des objets au sol dans (\\d{1,3})"
                      + " (minute|minutes|seconde|secondes)[.!]?$")
              .matcher(s);
      Matcher boost =
          Pattern.compile(
                  "^[A-Za-z0-9_]{1,16} (?:triggered a |a déclenché un )"
                      + BOOST_NAME
                      + "(?:"
                      + " for an hour| for one hour| pendant une heure) ?!$")
              .matcher(s);
      Matcher listing =
          Pattern.compile("^- " + BOOST_NAME + " \\((?:fin dans|end in) (.+)\\)$").matcher(s);
      Matcher extension =
          Pattern.compile(
                  "^[A-Za-z0-9_]{1,16} (?:extended the duration of |has increased the duration of"
                      + " |a augmenté la durée du |a prolongé la durée du )"
                      + BOOST_NAME
                      + " \\((?:fin dans|end in) (.+)\\)[.!]?$")
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
    s = s.strip().toLowerCase(Locale.ROOT);
    Matcher m =
        Pattern.compile("(\\d{1,4})\\s*(hours?|heures?|h|minutes?|min|m|secondes?|seconds?|s)")
            .matcher(s);
    long seconds = 0;
    int pos = 0;
    while (m.find()) {
      String separator = s.substring(pos, m.start()).strip();
      if (!(separator.isEmpty() || pos > 0 && separator.matches(",|(?:,\\s*)?(?:and|et)")))
        return -1;
      seconds +=
          Long.parseLong(m.group(1))
              * (m.group(2).startsWith("h") ? 3600 : m.group(2).startsWith("s") ? 1 : 60);
      pos = m.end();
    }
    return s.substring(pos).isBlank() && seconds <= 604800 ? seconds * 1000 : -1;
  }
}
