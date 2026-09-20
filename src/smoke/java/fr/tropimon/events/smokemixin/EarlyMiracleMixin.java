package fr.tropimon.events.smokemixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.network.packet.s2c.play.GameMessageS2CPacket;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reproduces Tropimon's messages before the client JOIN notification. Test JAR only. */
@Mixin(ClientPlayNetworkHandler.class)
abstract class EarlyMiracleMixin {
  @Inject(method = "onGameJoin", at = @At("HEAD"))
  private void early(GameJoinS2CPacket packet, CallbackInfo ci) {
    if (!Boolean.getBoolean("tropimon.smoke") || !MinecraftClient.getInstance().isOnThread())
      return;
    var handler = (ClientPlayNetworkHandler) (Object) this;
    for (String text :
        java.util.List.of(
            "- Shiny x2 (end in 1 hour and 13 seconds)",
            "- Talent Caché 10% (end in 39 minutes and 40 seconds)",
            "- IVs +10 (end in 39 minutes and 41 seconds)"))
      handler.onGameMessage(new GameMessageS2CPacket(Text.literal(text), false));
  }
}
