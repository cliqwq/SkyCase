package dev.skycase.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.skycase.core.HideSet;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Task 15: hides the held item (both hands) while {@link HideSet#getHand()} is true -- a Scatha
 * pre-roll/roll, same cosmetic-only render skip as {@link EntityRenderDispatcherMixin}. Packets,
 * pickup and input are never touched.
 *
 * Target confirmed via javap on 26.2's minecraft-merged-deobf jar:
 * {@code public void submitHandsWithItems(float, PoseStack, SubmitNodeCollector, LocalPlayer, int);}
 * (Scatha-Pro's target method of the same name on older Minecraft versions -- name/signature
 * independently re-verified here for 26.2, no code copied.)
 */
@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "submitHandsWithItems", at = @At("HEAD"), cancellable = true)
    private void skyCase$hideHeldItem(
        float partialTick, PoseStack poseStack, SubmitNodeCollector collector, LocalPlayer player, int combinedLight, CallbackInfo ci
    ) {
        if (HideSet.INSTANCE.getHand()) {
            ci.cancel();
        }
    }
}
