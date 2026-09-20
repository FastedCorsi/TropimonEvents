package fr.tropimon.events;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ConnectionMiracleTest {
  private final UUID player = new UUID(0, 1);

  @Test
  void reconnectRestoresOnlyUnexpiredMiraclesAfterRecognition() {
    var state = new EventState();
    state.connectionChanged(player, 1000);
    state.region(1000);
    state.systemMessage("- Shiny x2 (end in 1 hour)", 1000);
    state.systemMessage("- IVs +10 (end in 5 seconds)", 1000);
    state.raidBar(new UUID(0, 2), "Raid: Charizard", 1000);
    state.gyms.put("FIRE", new GymObservation("FIRE", "TestLeader", true, "", 1000));
    state.connectionChanged(player, 2000); // DISCONNECT
    state.connectionChanged(player, 8000); // INIT
    assertTrue(state.visible(8000).isEmpty()); // Never show Tropimon miracles on another server.
    state.region(8000);
    assertEquals(1, state.visible(8000).size());
    assertEquals(3601000, state.visible(8000).getFirst().end());
    assertTrue(state.gyms.isEmpty());
    assertNull(state.raidBoss(EventState.Kind.RAID));
    state.connectionChanged(player, 9000);
    state.connectionChanged(player, 10000);
    state.region(10000);
    assertEquals(3601000, state.visible(10000).getFirst().end());
  }

  @Test
  void freshMessagesOverrideRestoredDataAndAccountSwitchClearsIt() {
    var state = new EventState();
    state.connectionChanged(player, 1000);
    state.region(1000);
    state.systemMessage("- Shiny x2 (end in 1 hour)", 1000);
    state.connectionChanged(player, 2000);
    state.connectionChanged(player, 3000);
    state.systemMessage("- Shiny x2 (end in 20 minutes)", 3000);
    state.region(4000);
    assertEquals(1203000, state.visible(4000).getFirst().end());
    state.connectionChanged(player, 5000);
    state.systemMessage("No boosts are currently active.", 6000);
    state.region(7000);
    state.region(8000);
    assertTrue(state.visible(8000).isEmpty());
    state.systemMessage("- Shiny x2 (end in 1 hour)", 9000);
    state.connectionChanged(player, 10000);
    state.connectionChanged(new UUID(0, 3), 11000);
    state.region(12000);
    assertTrue(state.visible(12000).isEmpty());
  }
}
