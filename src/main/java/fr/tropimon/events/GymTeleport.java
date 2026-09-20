package fr.tropimon.events;

import java.util.UUID;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.text.Text;

/** Optional official-client adapter, invoked only by an explicit click on an open gym. */
final class GymTeleport {
  static void visit(GymObservation gym) {
    var client = MinecraftClient.getInstance();
    if (client.player == null || !EventsClient.STATE.serverRecognized || !gym.open()) return;
    try {
      // Same VISITOR request as Tropimon's TeleportGymWidget; server validates the destination.
      var type = Class.forName("fr.tropimon.tropimoncore.data.gym.enums.GymType");
      var warp = Class.forName("fr.tropimon.tropimoncore.data.gym.enums.GymWarpType");
      Object selected = type.getField(gym.type()).get(null);
      Object request =
          type.getMethod("getTeleportRequest", warp, UUID.class)
              .invoke(selected, warp.getField("VISITOR").get(null), client.player.getUuid());
      var requestType = Class.forName("fr.tropimon.tropimoncore.data.teleport.TeleportRequest");
      var payloadType =
          Class.forName("fr.tropimon.tropimodcore.networking.payload.teleport.WarpRequestPayload");
      var payload = (CustomPayload) payloadType.getConstructor(requestType).newInstance(request);
      if (!ClientPlayNetworking.canSend(payload.getId())) throw new IllegalStateException();
      ClientPlayNetworking.send(payload);
      client.setScreen(null);
    } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
      client.player.sendMessage(
          Text.literal("Téléportation indisponible via le client officiel Tropimon."),
          false);
    }
  }
}
