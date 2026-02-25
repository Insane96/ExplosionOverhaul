package insane96mcp.explosionoverhaul;

import insane96mcp.insanelib.setup.ILModConfig;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;

@Mod(ExplosionOverhaul.MOD_ID)
public class ExplosionOverhaul {
    public static final String MOD_ID = "explosionoverhaul";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static ILModConfig CONFIG;

    public ExplosionOverhaul(IEventBus modEventBus, ModContainer modContainer) {
        CONFIG = new ILModConfig(location("main"), "Single Module", ModConfig.Type.COMMON, modEventBus, ExplosionOverhaul.class.getClassLoader());
        modContainer.registerConfig(ModConfig.Type.COMMON, CONFIG.spec);
    }

    public static ResourceLocation location(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
