package fr.tropimon.events;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class EventStateTest {
  @Test
  void liveMiracleFormatsAndWrittenDurations() {
    var s = new EventState();
    assertTrue(s.accept("- Shiny x2 (end in 1 hour, 17 minutes and 53 seconds)", true, 1000));
    assertTrue(s.accept("- XP x2 (end in 20 minutes and 31 seconds)", true, 1000));
    assertTrue(s.accept("- Talent Caché 10% (end in 18 minutes and 9 seconds)", true, 1000));
    assertTrue(s.accept("- IVs +10 (end in 18 minutes and 10 seconds)", true, 1000));
    assertEquals(4, s.visible(1000).size());
    assertEquals(4674000, s.visible(1000).getFirst().end());
    assertEquals(4673000, EventState.parseDuration("1 heure, 17 minutes et 53 secondes"));
    assertEquals(1000, EventState.parseDuration("1 second"));
    assertEquals(-1, EventState.parseDuration("1 hour junk 2 seconds"));
    assertTrue(s.accept("TestPlayer triggered a XP x2 for one hour !", true, 2000));
    assertTrue(
        s.accept(
            "TestPlayer a augmenté la durée du Shiny x2 (fin dans 2 heures et 3 secondes)",
            true,
            3000));
    assertFalse(s.accept("TestPlayer: - XP x2 (end in 20 minutes)", true, 4000));
    assertFalse(s.accept("- XP x2 (end in 20 minutes)", false, 4000));
    assertTrue(s.visible(8000000).isEmpty());
  }

  @Test
  void rawNetworkAnnouncementsDoNotRequireClientTimestamp() {
    var s = new EventState();
    assertTrue(
        s.accept(" TestPlayer a déclenché un Raid ! (Clique pour te téléporter)", true, 1000));
    assertEquals(EventState.Kind.RAID, s.visible(1000).getFirst().kind());
    assertFalse(
        s.accept(
            "[12:30]     TestPlayer a déclenché un Raid ! (Clique pour te téléporter)",
            true,
            1100));
    assertTrue(s.accept(" TestPlayer triggered a Boost Shiny x2 for an hour !", true, 2000));
    assertTrue(s.accept("ꌈ No boosts are currently active.", true, 3000));
    assertEquals(1, s.visible(3000).size());
    assertTrue(s.accept("ꌈ Suppression des objets au sol dans 1 minute", true, 4000));
    assertFalse(
        s.accept(
            "ꈎ TestPlayer:  TestPlayer a déclenché un Raid ! (Clique pour te téléporter)",
            true,
            5000));
    assertFalse(
        s.accept(
            "ꌃ TestPlayer TestPlayer a déclenché un Raid ! (Clique pour te téléporter)",
            true,
            5000));
    assertFalse(
        s.accept("TestPlayer: TestPlayer triggered a Boost XP x2 for an hour !", true, 5000));
    assertTrue(s.accept(" TestPlayer has started a Mega raid ! (Click to teleport)", true, 6000));
    assertTrue(s.accept("TestPlayer has started a raid !", true, 7000));
  }

  @Test
  void systemOnlyAndSpoofing() {
    var s = new EventState();
    String b = "[12:30]  TestPlayer triggered a Boost Shiny x2 for an hour !";
    assertFalse(s.accept(b, false, 1000));
    assertFalse(s.accept("[12:30] TestPlayer: " + b, true, 1000));
    assertFalse(
        s.accept(
            "[12:30] ꌂ TestPlayer  TestPlayer triggered a Boost Shiny x2 for an hour !",
            true,
            1000));
    assertTrue(s.accept(b, true, 1000));
    assertEquals(3601000, s.visible(1000).getFirst().end());
  }

  @Test
  void dedupAndReset() {
    var s = new EventState();
    String b = "[12:30]  TestPlayer triggered a Boost XP x2 for an hour !";
    s.accept(b, true, 1000);
    assertFalse(s.accept(b, true, 1500));
    assertEquals(3601000, s.visible(1500).getFirst().end());
    s.reset();
    assertTrue(s.visible(1500).isEmpty());
    assertNull(s.points);
  }

  @Test
  void raidIsNotInferredActive() {
    var s = new EventState();
    assertTrue(
        s.accept(
            "[12:30] TestPlayer a déclenché un Mega Raid ! (Clique pour te téléporter)",
            true,
            1000));
    assertEquals(0, s.visible(1000).getFirst().end());
    assertTrue(s.visible(1000).getFirst().status(1000).contains("inconnu"));
    assertTrue(s.visible(901001).isEmpty());
  }

  @Test
  void emptyBoostListAndExpiration() {
    var s = new EventState();
    s.accept("[12:30] TestPlayer triggered a Boost XP x2 for an hour !", true, 1000);
    s.accept("[12:30] ꌈ No boosts are currently active.", true, 2000);
    assertTrue(s.visible(2000).isEmpty());
    s.accept("[12:30] ꌈ Suppression des objets au sol dans 1 minute", true, 2000);
    assertEquals(1, s.visible(3000).size());
    assertTrue(s.visible(62000).isEmpty());
  }

  @Test
  void explicitRemainingAndMalformed() {
    var s = new EventState();
    assertTrue(s.accept("[12:30] - Boost Shiny x2 (end in 1h 20m)", true, 1000));
    assertEquals(4801000, s.visible(1000).getFirst().end());
    assertEquals(-1, EventState.parseDuration("99 years"));
    assertFalse(s.accept("[12:30] - Boost Shiny x2 (end in 9000h)", true, 2000));
  }

  @Test
  void newEventInvalidatesProgress() {
    var s = new EventState();
    s.definition("A", "", 9999, java.util.Map.of(), 1000);
    s.progress(100, 10, 1);
    s.definition("B", "", 99999, java.util.Map.of("HUNTS", 5), 2000);
    assertNull(s.points);
    assertEquals(5, s.objectives.get("HUNTS"));
    s.reset();
    assertFalse(s.serverRecognized);
  }
}
