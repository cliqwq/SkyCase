package dev.skycase.mixin;

import dev.skycase.core.HiddenEntities;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Task 13: hides a dragon floor-drop ItemEntity from rendering (item, name tag, everything) for as
 * long as its UUID is in {@link HiddenEntities#getHidden()} -- purely a client-side render skip;
 * packets/pickup are never touched. Mixin must be Java (Kotlin cannot be a Mixin target/annotation
 * processor input).
 *
 * Target confirmed via javap on 26.2's minecraft-merged-deobf jar:
 * {@code public <E extends net.minecraft.world.entity.Entity> boolean shouldRender(E,
 * net.minecraft.client.renderer.culling.Frustum, double, double, double);}
 */
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void skyCase$hideDragonLoot(
        E entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir
    ) {
        if (entity instanceof ItemEntity && HiddenEntities.INSTANCE.getHidden().contains(entity.getUUID())) {
            cir.setReturnValue(false);
        }
    }
}
