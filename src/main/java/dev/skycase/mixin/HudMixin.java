package dev.skycase.mixin;

import dev.skycase.core.HideSet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the boss bar and the action-bar text (Hypixel's "100/100❤ 100/100✎ Mana" line) while a
 * reveal plays. Both live outside SkyblockAPI's HudElement set, hence a mixin. Render skip only.
 * Targets confirmed via javap on 26.2: {@code private void extractBossOverlay(GuiGraphicsExtractor,
 * DeltaTracker)} and {@code private void extractOverlayMessage(GuiGraphicsExtractor, DeltaTracker)}.
 */
@Mixin(Hud.class)
public abstract class HudMixin {
    @Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true)
    private void skyCase$hideBossBar(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (HideSet.INSTANCE.hidingHud()) ci.cancel();
    }

    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void skyCase$hideActionBar(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (HideSet.INSTANCE.hidingHud()) ci.cancel();
    }
}
