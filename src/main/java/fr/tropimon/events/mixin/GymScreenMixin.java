package fr.tropimon.events.mixin;

import fr.tropimon.events.EventsClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
abstract class GymScreenMixin {
  @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
  private void backgroundGymSnapshot(Screen screen, CallbackInfo ci) {
    if (screen != null && EventsClient.GYMS.consumeScreen(screen.getClass().getName())) ci.cancel();
  }
}
