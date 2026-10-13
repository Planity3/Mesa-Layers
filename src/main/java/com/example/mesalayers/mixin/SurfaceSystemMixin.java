package com.example.mesalayers.mixin;

import com.example.mesalayers.MesaLayerLoader;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SurfaceSystem.class)
public class SurfaceSystemMixin {
    @Inject(method = "generateBands", at = @At("RETURN"), require = 1)
    private static void mesalayers$addBands(RandomSource random, CallbackInfoReturnable<BlockState[]> cir) {
        MesaLayerLoader.apply(cir.getReturnValue(), random);
    }
}
