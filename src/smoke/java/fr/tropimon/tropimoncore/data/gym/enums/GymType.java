package fr.tropimon.tropimoncore.data.gym.enums;

public enum GymType {
  FIRE,
  WATER;

  public fr.tropimon.tropimoncore.data.teleport.TeleportRequest getTeleportRequest(
      GymWarpType warp, java.util.UUID player) {
    return new fr.tropimon.tropimoncore.data.teleport.TeleportRequest(name(), warp.name(), player);
  }
}
