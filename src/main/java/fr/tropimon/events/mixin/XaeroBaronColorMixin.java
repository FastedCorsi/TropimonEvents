package fr.tropimon.events.mixin;

import fr.tropimon.events.BaronTracker;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Red fallback dot when Xaero cannot render the Pokemon icon. */
@Pseudo
@Mixin(targets = "xaero.hud.minimap.radar.color.RadarColorHelper", remap = false)
abstract class XaeroBaronColorMixin {
  @Inject(method = "getRadarColorHex", at = @At("HEAD"), cancellable = true, remap = false)
  private void redBaron(
      Entity entity,
      @Coerce Object color,
      @Coerce Object fallback,
      CallbackInfoReturnable<Integer> cir) {
    if (BaronTracker.isBaron(entity)) cir.setReturnValue(0xFFFF3535);
  }
}
