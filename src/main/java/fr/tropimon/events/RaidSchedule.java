package fr.tropimon.events;

import java.time.*;

/** Public Tropimon timetable observed in Hunter Board; it does not prove a raid has started. */
final class RaidSchedule {
  private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
  private static final LocalTime[] TIMES = {
    LocalTime.MIDNIGHT,
    LocalTime.of(4, 0),
    LocalTime.of(10, 30),
    LocalTime.of(15, 30),
    LocalTime.of(18, 30),
    LocalTime.of(20, 0),
    LocalTime.of(21, 30)
  };

  static long next(long now) {
    var current = Instant.ofEpochMilli(now).atZone(PARIS);
    for (var time : TIMES) {
      long candidate = current.toLocalDate().atTime(time).atZone(PARIS).toInstant().toEpochMilli();
      if (candidate > now) return candidate;
    }
    return current.toLocalDate().plusDays(1).atStartOfDay(PARIS).toInstant().toEpochMilli();
  }
}
