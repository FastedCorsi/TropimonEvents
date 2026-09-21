package fr.tropimon.events.mixin;

import fr.tropimon.events.BaronOutline;
import fr.tropimon.events.BaronMarker;
import fr.tropimon.events.BaronTracker;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Optional Xaero adapter: outline its existing sprite after its visibility/permission checks. */
@Pseudo
@Mixin(targets = "xaero.hud.minimap.radar.render.element.RadarRenderer", remap = false)
abstract class XaeroBaronMixin {
  @Unique private boolean events$baron;
  @Unique private boolean events$shiny;
  @Unique private Args events$iconArgs;

  @Inject(method = "setupRenderForEntity", at = @At("HEAD"), remap = false)
  private void rememberBaron(Entity entity, CallbackInfo ci) {
    events$baron = BaronTracker.isBaron(entity);
    events$shiny = events$baron
        && ((com.cobblemon.mod.common.entity.pokemon.PokemonEntity) entity).getPokemon().getShiny();
    events$iconArgs = null;
  }

  @ModifyArgs(
      method = "renderIcon",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lxaero/common/minimap/render/MinimapRendererHelper;prepareMyTexturedColoredModalRect(Lorg/joml/Matrix4f;FFIIFFFFIFFFFLxaero/common/graphics/renderer/multitexture/MultiTextureRenderTypeRenderer;)V"),
      remap = false)
  private void redOutline(Args args) {
    if (!events$baron) return;
    events$iconArgs = args;
    BaronOutline.draw(
        args.get(0),
        args.get(1),
        args.get(2),
        args.get(3),
        args.get(4),
        args.get(5),
        args.get(6),
        args.get(8),
        args.get(9),
        (float) args.get(10) * (float) args.get(13));
  }

  @Inject(method = "renderIcon", at = @At("RETURN"), remap = false)
  private void baronBadge(CallbackInfo ci) {
    Args args = events$iconArgs;
    events$iconArgs = null;
    if (args == null) return;
    BaronMarker.draw(args.get(0), args.get(1), args.get(2), args.get(5), args.get(6),
        args.get(10), args.get(11), args.get(12), args.get(13), args.get(14), events$shiny);
  }
}
