package insane96mcp.explosionoverhaul.mixin.accessor;

import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Level.class)
public interface LevelAccessor {
    @Invoker
    Explosion.BlockInteraction invokeGetDestroyType(GameRules.Key<GameRules.BooleanValue> gameRule);
}
