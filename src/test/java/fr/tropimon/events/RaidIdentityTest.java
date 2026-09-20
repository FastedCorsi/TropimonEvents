package fr.tropimon.events;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RaidIdentityTest {
  @Test
  void explicitBossLabelOnlyAndAmbiguity() {
    var s = new EventState();
    var a = new UUID(0, 1);
    var b = new UUID(0, 2);
    s.raidBar(a, "Charizard", 1);
    assertNull(s.raidBoss(EventState.Kind.RAID));
    s.raidBar(a, "Raid : Charizard", 2);
    assertEquals("Charizard", s.raidBoss(EventState.Kind.RAID).pokemon());
    s.raidBar(b, "Raid : Gengar", 3);
    assertNull(s.raidBoss(EventState.Kind.RAID));
    s.removeRaidBar(b);
    assertEquals("Charizard", s.raidBoss(EventState.Kind.RAID).pokemon());
    s.removeRaidBar(a);
    assertNull(s.raidBoss(EventState.Kind.RAID));
    assertTrue(s.visible(4).getFirst().status(4).contains("inconnu"));
  }

  @Test
  void newAnnouncementAndSessionNeverReuseOldPokemon() {
    var s = new EventState();
    s.raidBar(new UUID(0, 1), "Méga Raid : Méga Dracaufeu X", 1);
    assertNotNull(s.raidBoss(EventState.Kind.MEGA));
    s.accept("[12:30] TestPlayer a déclenché un Mega Raid ! (Clique pour te téléporter)", true, 2);
    assertNull(s.raidBoss(EventState.Kind.MEGA));
    s.raidBar(new UUID(0, 2), "Raid : Pikachu", 3);
    s.reset();
    assertNull(s.raidBoss(EventState.Kind.RAID));
  }
}
