package fr.tropimon.events;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class BaronAlertsTest {
  @Test
  void oneSoundPerPokemonAcrossUnloadAndReload() {
    var alerts = new BaronAlerts();
    var id = UUID.randomUUID();
    assertTrue(alerts.discover(id, 1000));
    assertFalse(alerts.discover(id, 1001));
    assertFalse(alerts.discover(id, 90000));
    alerts.reset();
    assertTrue(alerts.discover(id, 100000));
  }

  @Test
  void simultaneousDiscoveriesAreGroupedWithoutDelayedSpam() {
    var alerts = new BaronAlerts();
    var second = UUID.randomUUID();
    assertTrue(alerts.discover(UUID.randomUUID(), 1000));
    assertFalse(alerts.discover(second, 1100));
    assertFalse(alerts.discover(second, 5000));
    assertTrue(alerts.discover(UUID.randomUUID(), 5000));
  }
}
