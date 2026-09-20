package fr.tropimon.events.mixin;

import fr.tropimon.events.EventsClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reads the system packet even when another mod hides its chat rendering. */
@Mixin(ClientPlayNetworkHandler.class)
abstract class SystemMessageMixin {
  @Inject(method = "sendChatCommand", at = @At("HEAD"))
  private void manualGymNavigation(String command, CallbackInfo ci) {
    EventsClient.GYMS.manualCommand(command);
  }

  @Inject(method = "onGameMessage", at = @At("HEAD"))
  private void observe(GameMessageS2CPacket packet, CallbackInfo ci) {
    if (MinecraftClient.getInstance().isOnThread() && !packet.overlay())
      EventsClient.systemMessage(packet.content().getString());
  }
}
