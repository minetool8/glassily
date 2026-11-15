package dev.glassbreakcompat;

import com.mojang.logging.LogUtils;
import dev.betterglassbreaking.forge.GlassConfig;
import dev.betterglassbreaking.forge.compat.TaczCompat;
import dev.glassbreakcompat.compat.TaczFallback;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(GlassBreakCompat.ID)
public class GlassBreakCompat {
    public static final String ID = "glassbreakcompat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public GlassBreakCompat() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CompatConfig.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientCompatConfig.SPEC);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
                (ModConfigEvent.Reloading e) -> GlassNames.clearCache());

        MinecraftForge.EVENT_BUS.register(new SprintImpactHandler());
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            MinecraftForge.EVENT_BUS.register(new ShardWalkSounds());
        }

        if (ModList.get().isLoaded("tacz")) {
            TaczFallback.register();
        }
    }

    private void onServerStarted(ServerStartedEvent event) {
        boolean tacz = ModList.get().isLoaded("tacz");
        LOGGER.info("[glassbreakcompat] ready. kickshatter={} tacz={}",
                ModList.get().isLoaded("kickshatter"), tacz);
        try {
            if (tacz && !GlassConfig.current().taczCompat()) {
                LOGGER.warn("[glassbreakcompat] Better Glass Breaking has taczCompat=false; "
                        + "TACZ bullets will use my fallback path instead of BGB's native one.");
            }
            if (tacz) {
                LOGGER.info("[glassbreakcompat] BGB native TACZ adapter active: {}", TaczCompat.active());
            }
            if (GlassConfig.current().healing()) {
                LOGGER.warn("[glassbreakcompat] BGB 'healing' is ON: broken glass will repair itself over time. "
                        + "Set healing=false in BGB's config if broken glass must stay broken.");
            }
        } catch (Throwable t) {
            LOGGER.warn("[glassbreakcompat] diagnostics skipped: {}", t.toString());
        }
    }
}
