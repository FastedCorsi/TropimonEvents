package fr.tropimon.events.mixin;

import net.minecraft.client.render.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Optional access to Xaero's own texture batch; no additional render pass or dependency. */
@Pseudo
@Mixin(targets = "xaero.common.graphics.renderer.multitexture.MultiTextureRenderTypeRenderer", remap = false)
public interface XaeroIconBuffer {
  @Invoker(value = "begin", remap = false)
  BufferBuilder events$begin(int texture);
}
