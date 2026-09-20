package fr.tropimon.events;

import static org.junit.jupiter.api.Assertions.*;

import java.time.*;
import org.junit.jupiter.api.Test;

class RaidScheduleTest {
  private static long at(String date) {
    return LocalDateTime.parse(date).atZone(ZoneId.of("Europe/Paris")).toInstant().toEpochMilli();
  }

  @Test
  void fiveMinuteReminderNeverInventsRaidStart() {
    var state = new EventState();
    long now = at("2026-09-21T10:25:00");
    assertTrue(state.visible(now).isEmpty());
    state.region(now - 1000);
    assertTrue(state.visible(now - 1000).isEmpty());
    var notice = state.visible(now).getFirst();
    assertEquals(EventState.Kind.NEXT_RAID, notice.kind());
    assertEquals(at("2026-09-21T10:30:00"), notice.end());
    assertTrue(state.visible(notice.end()).isEmpty());
    assertNull(state.raidBoss(EventState.Kind.RAID));
  }

  @Test
  void parisScheduleHandlesMidnightAndDaylightSaving() {
    assertEquals(at("2026-09-22T00:00:00"), RaidSchedule.next(at("2026-09-21T23:59:00")));
    assertEquals(at("2026-03-29T04:00:00"), RaidSchedule.next(at("2026-03-29T01:59:00")));
    assertEquals(at("2026-10-25T04:00:00"), RaidSchedule.next(at("2026-10-25T02:59:00")));
    assertEquals("1h 2m 3s", EventState.duration(3723000));
    assertEquals("1m 0s", EventState.duration(60000));
    assertEquals("1s", EventState.duration(1));
    assertEquals("0s", EventState.duration(-1000));
  }

  @Test
  void observedRaidDurationComesOnlyFromExplicitServerSignal() {
    var state = new EventState();
    state.accept("TestPlayer a déclenché un Raid !", true, 1000);
    assertEquals(0, state.visible(1000).getFirst().end());
    assertFalse(state.accept("Player: Le Raid se termine dans 10 minutes", true, 2000));
    assertTrue(state.accept("Le Raid se termine dans 10 minutes", true, 2000));
    assertEquals(602000, state.visible(2000).getFirst().end());
    state.region(3000);
    assertEquals(602000, state.visible(3000).getFirst().end());
    assertTrue(state.accept("Le Raid est terminé.", true, 4000));
    assertTrue(state.visible(4000).isEmpty());
    assertTrue(state.accept("Mega Raid ends in 3 minutes and 2 seconds.", true, 5000));
    assertEquals(187000, state.visible(5000).getFirst().end());
  }
}
