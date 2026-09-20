package fr.tropimon.events.mixin;

import fr.tropimon.events.EventsClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.BossBarS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Passive observation; never changes or cancels the original boss bar. */
@Mixin(ClientPlayNetworkHandler.class)
abstract class RaidBarMixin {
  @Inject(method = "onBossBar", at = @At("HEAD"))
  private void observe(BossBarS2CPacket packet, CallbackInfo ci) {
    if (!MinecraftClient.getInstance().isOnThread() || !EventsClient.STATE.serverRecognized) return;
    EventsClient.observeBossBar(packet);
  }
}
