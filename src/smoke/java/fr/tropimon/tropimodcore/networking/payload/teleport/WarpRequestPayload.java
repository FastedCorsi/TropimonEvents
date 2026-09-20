package fr.tropimon.tropimodcore.networking.payload.teleport;

import fr.tropimon.tropimoncore.data.teleport.TeleportRequest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Synthetic server transport; never included in delivery JARs. */
public record WarpRequestPayload(TeleportRequest request) implements CustomPayload {
  public static final Id<WarpRequestPayload> ID = new Id<>(Identifier.of("events_smoke", "warp"));
  public static final PacketCodec<RegistryByteBuf, WarpRequestPayload> CODEC =
      PacketCodec.of(
          (value, buffer) -> {
            buffer.writeString(value.request.gym());
            buffer.writeString(value.request.warp());
            buffer.writeUuid(value.request.player());
          },
          buffer ->
              new WarpRequestPayload(
                  new TeleportRequest(
                      buffer.readString(), buffer.readString(), buffer.readUuid())));

  public Id<? extends CustomPayload> getId() {
    return ID;
  }
}
