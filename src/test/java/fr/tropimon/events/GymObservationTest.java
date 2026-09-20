package fr.tropimon.events;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import org.junit.jupiter.api.Test;

class GymObservationTest {
  @Test
  void explicitOpeningAndClosureUpdateWithoutMenu() {
    for (String[] messages :
        new String[][] {
          {
            "TestLeader a ouvert l'arène Feu. Clique pour te téléporter",
            "L'arène de Feu ferme ses portes."
          },
          {"TestLeader opened the Fire type gym. Teleport", "TestLeader closed the Fire type gym."}
        }) {
      var state = new EventState();
      assertTrue(state.accept("ꌈ " + messages[0], true, 1000));
      assertTrue(state.gyms.get("FIRE").open());
      assertFalse(state.accept("OtherPlayer: " + messages[1], true, 1500));
      assertTrue(state.gyms.get("FIRE").open());
      assertTrue(state.accept(messages[1], true, 2000));
      assertFalse(state.gyms.get("FIRE").open());
      assertEquals("TestLeader", state.gyms.get("FIRE").leader());
    }
    assertNull(
        GymObservation.announcement("TestLeader a ouvert l'arène Inconnue. Téléporter", 1000));
  }

  private static JsonObject json(String s) {
    return JsonParser.parseString(s).getAsJsonObject();
  }

  private static JsonObject terminal(String kind, String status) {
    return json(
        "{\"first\":{\"type\":\"FIRE\",\"open\":true,\"champion\":{\"leaderData\":{\"name\":\"TestLeader\"}}},\"second\":{\"gymType\":\"FIRE\",\"gymChallengeType\":\""
            + kind
            + "\",\"status\":\""
            + status
            + "\"}}");
  }

  @Test
  void distinctStatesAndChallengeTypes() {
    for (String kind : new String[] {"TITLE", "BADGE", "MASTERY"}) {
      for (String status :
          new String[] {
            "WAITING_CONFIRMATION", "SELECTING_LEAD", "IN_PROGRESS", "COMPLETED", "CANCELLED"
          }) {
        var gym = GymObservation.terminal(terminal(kind, status), 1000);
        assertEquals(kind.equals("TITLE"), gym.battle().startsWith("Prise d'arène"));
        assertEquals(status.equals("IN_PROGRESS"), gym.battle().contains("en cours"));
        assertEquals("TestLeader", gym.leader());
      }
    }
  }

  @Test
  void newerSelectorInvalidatesMatchAndResetClearsAll() {
    var state = new EventState();
    new EventWire.Observation(EventWire.LEADER, terminal("TITLE", "IN_PROGRESS"))
        .apply(state, null, 1000);
    assertTrue(state.gyms.get("FIRE").battle().contains("en cours"));
    new EventWire.Observation(
            EventWire.GYMS,
            json(
                "{\"gyms\":{\"FIRE\":{\"type\":\"FIRE\",\"open\":false,\"leaderName\":\"TestLeader\"}}}"))
        .apply(state, null, 2000);
    assertFalse(state.gyms.get("FIRE").open());
    assertFalse(state.gyms.get("FIRE").battle().contains("en cours"));
    state.reset();
    assertTrue(state.gyms.isEmpty());
  }

  @Test
  void malformedSnapshotsAreAtomic() {
    var state = new EventState();
    new EventWire.Observation(EventWire.CHALLENGER, terminal("TITLE", "IN_PROGRESS"))
        .apply(state, null, 1000);
    new EventWire.Observation(
            EventWire.GYMS,
            json(
                "{\"gyms\":{\"WATER\":{\"type\":\"WATER\",\"open\":true},\"FIRE\":{\"type\":\"FIRE\",\"open\":\"false\"}}}"))
        .apply(state, null, 2000);
    assertEquals(1, state.gyms.size());
    assertEquals(1000, state.gyms.get("FIRE").observed());
  }
}
