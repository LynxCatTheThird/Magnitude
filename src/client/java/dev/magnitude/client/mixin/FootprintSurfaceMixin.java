package dev.magnitude.client.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Surface changes invalidate decoration directly instead of checking all cached blocks per frame. */
@Mixin(ClientLevel.class)
public abstract class FootprintSurfaceMixin {
    @Inject(method="setBlocksDirty",at=@At("HEAD"))
    private void magnitude$surfaceChanged(BlockPos pos,BlockState oldState,BlockState newState,CallbackInfo ci){
        if(oldState!=newState)dev.magnitude.client.visual.FootprintRenderer.invalidate(pos);
    }
}
