package fr.tropimon.events;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Bounded session memory; simultaneous discoveries produce one chime. */
final class BaronAlerts {
  private final Set<UUID> seen = new HashSet<>();
  private long nextSound;

  boolean discover(UUID pokemon, long now) {
    if (seen.size() >= 8192 || !seen.add(pokemon)) return false;
    if (now < nextSound) return false;
    nextSound = now + 3000;
    return true;
  }

  void reset() {
    seen.clear();
    nextSound = 0;
  }
}
