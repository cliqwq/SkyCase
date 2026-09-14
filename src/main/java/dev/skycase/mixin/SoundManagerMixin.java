package dev.skycase.mixin;

import dev.skycase.core.CaseOverlay;
import dev.skycase.core.HideSet;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Task 15: mutes non-mod sounds while {@link HideSet#getMute()} is true -- a Scatha pre-roll/roll --
 * except our own tick sound, tagged via {@link CaseOverlay#getPlayingOwnSound()} (set around the one
 * {@code soundManager.play(...)} call {@code CaseOverlay} makes, so it's never accidentally muted by
 * this same mixin). Purely cosmetic: nothing here touches packets, pickup, or input.
 *
 * Target confirmed via javap on 26.2's minecraft-merged-deobf jar:
 * {@code public SoundEngine.PlayResult play(SoundInstance);}
 */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {

    @Inject(method = "play", at = @At("HEAD"), cancellable = true)
    private void skyCase$muteDuringScatha(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        if (HideSet.INSTANCE.getMute() && !CaseOverlay.INSTANCE.getPlayingOwnSound()) {
            cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
        }
    }
}
