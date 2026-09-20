package fr.tropimon.events;

import com.google.gson.*;
import java.util.*;

/** A server snapshot, never a promise that the arena is still open. */
public record GymObservation(
    String type, String leader, boolean open, String battle, long observed) {
  static final List<String> TYPES =
      List.of(
          "NORMAL",
          "FIRE",
          "WATER",
          "ELECTRIC",
          "GRASS",
          "ICE",
          "FIGHTING",
          "POISON",
          "GROUND",
          "FLYING",
          "PSYCHIC",
          "BUG",
          "ROCK",
          "GHOST",
          "DRAGON",
          "DARK",
          "STEEL",
          "FAIRY");
  private static final List<String> LABELS =
      List.of(
          "Normal",
          "Feu",
          "Eau",
          "Électrik",
          "Plante",
          "Glace",
          "Combat",
          "Poison",
          "Sol",
          "Vol",
          "Psy",
          "Insecte",
          "Roche",
          "Spectre",
          "Dragon",
          "Ténèbres",
          "Acier",
          "Fée");

  public String label() {
    return LABELS.get(TYPES.indexOf(type));
  }

  public String status() {
    return open ? "Ouverte lors de l'observation" : "Fermée lors de l'observation";
  }

  static Map<String, GymObservation> selector(JsonObject json, long now) {
    JsonObject gyms = json.getAsJsonObject("gyms");
    if (gyms == null || gyms.size() > 18) throw new IllegalArgumentException();
    Map<String, GymObservation> result = new LinkedHashMap<>();
    for (var entry : gyms.entrySet()) {
      String type = type(entry.getKey());
      JsonObject gym = entry.getValue().getAsJsonObject();
      if (!type.equals(type(text(gym, "type", 16)))) throw new IllegalArgumentException();
      boolean open = bool(gym, "open");
      if (gym.has("vacationActive") && bool(gym, "vacationActive")) open = false;
      result.put(
          type,
          new GymObservation(
              type, optionalText(gym, "leaderName", 64), open, "État du match non reçu", now));
    }
    return result;
  }

  static GymObservation terminal(JsonObject json, long now) {
    JsonObject gym = json.getAsJsonObject("first");
    String type = type(text(gym, "type", 16));
    String leader = "";
    if (gym.has("champion") && !gym.get("champion").isJsonNull()) {
      JsonObject champion = gym.getAsJsonObject("champion");
      if (champion.has("leaderData") && !champion.get("leaderData").isJsonNull())
        leader = optionalText(champion.getAsJsonObject("leaderData"), "name", 64);
    }
    String battle = "Aucun match transmis";
    if (json.has("second") && !json.get("second").isJsonNull()) {
      JsonObject match = json.getAsJsonObject("second");
      if (!type.equals(type(text(match, "gymType", 16)))) throw new IllegalArgumentException();
      String challenge = text(match, "gymChallengeType", 16);
      if (!Set.of("TITLE", "BADGE", "MASTERY").contains(challenge))
        throw new IllegalArgumentException();
      String status =
          switch (text(match, "status", 32)) {
            case "WAITING_CONFIRMATION" -> "confirmation attendue";
            case "SELECTING_LEAD" -> "préparation de la manche";
            case "IN_PROGRESS" -> "match observé en cours";
            case "COMPLETED" -> "terminé";
            case "CANCELLED" -> "annulé";
            default -> throw new IllegalArgumentException();
          };
      battle =
          (challenge.equals("TITLE")
                  ? "Prise d'arène : "
                  : "Défi " + (challenge.equals("BADGE") ? "badge" : "maîtrise") + " : ")
              + status;
    }
    return new GymObservation(type, leader, bool(gym, "open"), battle, now);
  }

  private static String type(String value) {
    if (!TYPES.contains(value)) throw new IllegalArgumentException();
    return value;
  }

  private static boolean bool(JsonObject object, String key) {
    var value = object.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean())
      throw new IllegalArgumentException();
    return value.getAsBoolean();
  }

  private static String optionalText(JsonObject object, String key, int max) {
    return !object.has(key) || object.get(key).isJsonNull() ? "" : text(object, key, max);
  }

  private static String text(JsonObject object, String key, int max) {
    var value = object.get(key);
    if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
      throw new IllegalArgumentException();
    String s = value.getAsString();
    if (s.length() > max || s.codePoints().anyMatch(c -> Character.isISOControl(c) || c == 167))
      throw new IllegalArgumentException();
    return s;
  }
}
