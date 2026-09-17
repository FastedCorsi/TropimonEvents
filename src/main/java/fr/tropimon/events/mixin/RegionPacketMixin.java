package fr.tropimon.events.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import fr.tropimon.events.EventWire;
import fr.tropimon.events.EventsClient;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.handler.DecoderHandler;
import net.minecraft.network.packet.s2c.common.CustomPayloadS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Identity-only observation after the real codec; no buffer reads or foreign services. */
@Mixin(DecoderHandler.class)
abstract class RegionPacketMixin {
  @WrapOperation(
      method = "decode",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/network/codec/PacketCodec;decode(Ljava/lang/Object;)Ljava/lang/Object;"))
  private Object region(
      PacketCodec<?, ?> codec,
      Object input,
      Operation<Object> original,
      ChannelHandlerContext context,
      ByteBuf buffer,
      List<Object> output) {
    var pending = EventWire.inspect((ByteBuf) input);
    Object decoded = original.call(codec, input);
    if (pending != null
        && decoded instanceof CustomPayloadS2CPacket packet
        && pending.id().equals(packet.payload().getId().id().toString())) {
      ClientConnection connection = context.pipeline().get(ClientConnection.class);
      var client = MinecraftClient.getInstance();
      client.execute(
          () -> {
            if (client.getNetworkHandler() != null
                && client.getNetworkHandler().getConnection() == connection)
              pending.apply(
                  EventsClient.STATE,
                  client.player == null ? null : client.player.getUuid(),
                  System.currentTimeMillis());
          });
    }
    return decoded;
  }
}
