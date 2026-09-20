package fr.tropimon.events;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GymRefreshTest {
  private static final String SCREEN =
      "fr.erusel.tropimodclient.client.gui.gym.navigator.GymNavigatorScreen";

  @Test
  void automaticResponseIsConsumedOnceAndNeverFlooded() {
    var sync = new GymRefresh();
    sync.reset(1000);
    assertFalse(sync.due(5999));
    assertTrue(sync.due(6000));
    sync.request(6000, () -> sync.manualCommand("gym open"));
    assertFalse(sync.due(999999));
    assertFalse(sync.consumeScreen("another.Screen"));
    assertTrue(sync.consumeScreen(SCREEN));
    assertFalse(sync.consumeScreen(SCREEN));
    assertFalse(sync.due(65999));
    assertTrue(sync.due(66000));
  }

  @Test
  void manualNavigationAndSessionChangesArePreserved() {
    var sync = new GymRefresh();
    sync.request(0, () -> {});
    sync.manualCommand("gym open");
    assertFalse(sync.consumeScreen(SCREEN));
    sync.request(60000, () -> {});
    sync.reset(61000);
    assertFalse(sync.consumeScreen(SCREEN));
    assertFalse(sync.due(61000));
  }
}
