package dev.skycase.mixin;

import dev.skycase.core.HideSet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.world.entity.player.Player;
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

    // SkyblockAPI does not post a HudElement for every vanilla piece (the food bar slipped through):
    // cancel the vanilla extractors directly. {@code private void extractFood(GuiGraphicsExtractor, Player, int, int)},
    // {@code private void extractPlayerHealth(GuiGraphicsExtractor)}, {@code private void extractVehicleHealth(GuiGraphicsExtractor)},
    // {@code private void extractCrosshair(GuiGraphicsExtractor, DeltaTracker)} -- all javap-confirmed on 26.2.
    @Inject(method = "extractFood", at = @At("HEAD"), cancellable = true)
    private void skyCase$hideFood(GuiGraphicsExtractor graphics, Player player, int y, int x, CallbackInfo ci) {
        if (HideSet.INSTANCE.hidingHud()) ci.cancel();
    }

    @Inject(method = "extractPlayerHealth", at = @At("HEAD"), cancellable = true)
    private void skyCase$hideHealth(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (HideSet.INSTANCE.hidingHud()) ci.cancel();
    }

    @Inject(method = "extractVehicleHealth", at = @At("HEAD"), cancellable = true)
    private void skyCase$hideVehicleHealth(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (HideSet.INSTANCE.hidingHud()) ci.cancel();
    }

    @Inject(method = "extractCrosshair", at = @At("HEAD"), cancellable = true)
    private void skyCase$hideCrosshair(GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        if (HideSet.INSTANCE.hidingHud()) ci.cancel();
    }
}
