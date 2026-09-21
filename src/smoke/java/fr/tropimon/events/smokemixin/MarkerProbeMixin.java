package fr.tropimon.events.smokemixin;

import fr.tropimon.events.BaronMarker;
import fr.tropimon.events.BaronSmoke;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BaronMarker.class)
abstract class MarkerProbeMixin {
  @Inject(method = "draw", at = @At("RETURN"), remap = false)
  private static void count(Matrix4f matrix, float x, float y, float width, float height,
      float red, float green, float blue, float alpha, Object renderer, boolean shiny, CallbackInfo ci) {
    if (shiny) BaronSmoke.shinyMarkers++;
    else BaronSmoke.markers++;
  }
}
