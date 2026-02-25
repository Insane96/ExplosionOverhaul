package insane96mcp.explosionoverhaul.mixin.accessor;

import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Explosion.class)
public interface ExplosionAccessor {
    @Accessor
    Level getLevel();

    @Accessor
    DamageSource getDamageSource();

    @Accessor
    ExplosionDamageCalculator getDamageCalculator();

    @Accessor
    Entity getSource();

    @Accessor
    boolean getFire();

    @Accessor
    RandomSource getRandom();
}
