package fr.tropimon.events.smokemixin;

import fr.tropimon.events.BaronSmoke;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.sound.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-only observation; no sound behavior changes. */
@Mixin(SoundManager.class)
abstract class SoundProbeMixin {
  @Inject(method = "play(Lnet/minecraft/client/sound/SoundInstance;)V", at = @At("HEAD"))
  private void countChime(SoundInstance sound, CallbackInfo ci) {
    if (sound.getId().toString().equals("minecraft:block.note_block.bell")) BaronSmoke.chimes++;
  }
}
