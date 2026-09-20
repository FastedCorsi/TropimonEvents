package fr.tropimon.events.smokemixin;

import fr.tropimon.events.BaronOutline;
import fr.tropimon.events.BaronSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BaronOutline.class)
abstract class OutlineProbeMixin {
  @Inject(method = "draw", at = @At("HEAD"), remap = false)
  private static void count(CallbackInfo ci) {
    BaronSmoke.outlines++;
  }
}
