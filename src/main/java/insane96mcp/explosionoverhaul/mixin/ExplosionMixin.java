package insane96mcp.explosionoverhaul.mixin;

import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Explosion.class)
public abstract class ExplosionMixin {
    /*@Definition(id = "level", field = "Lnet/minecraft/world/level/Explosion;level:Lnet/minecraft/world/level/Level;")
    @Definition(id = "addParticle", method = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V")
    @Definition(id = "EXPLOSION", field = "Lnet/minecraft/core/particles/ParticleTypes;EXPLOSION:Lnet/minecraft/core/particles/SimpleParticleType;")
    @Expression("this.level.addParticle(EXPLOSION, ?, ?, ?, ?, ?, ?)")
    @WrapOperation(method = "finalizeExplosion", at = @At("MIXINEXTRAS:EXPRESSION"))
    private void onExplosionEmitterParticle(Level level, ParticleOptions particleData, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, Operation<Void> original) {
		if (!Feature.isEnabled(ExplosionOverhaul.class) || !ExplosionOverhaul.disableEmitterParticles)
            original.call(level, particleData, x, y, z, xSpeed, ySpeed, zSpeed);
	}*/
}
