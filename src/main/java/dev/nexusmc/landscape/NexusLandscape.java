package dev.nexusmc.landscape;

import dev.nexusmc.landscape.command.RouteAuditCommand;
import dev.nexusmc.landscape.worldgen.CaveSanctumFeature;
import dev.nexusmc.landscape.worldgen.CoralAtollFeature;
import dev.nexusmc.landscape.worldgen.FloatingIslandFeature;
import dev.nexusmc.landscape.worldgen.HotSpringFeature;
import dev.nexusmc.landscape.worldgen.HumidKarstArchFeature;
import dev.nexusmc.landscape.worldgen.MycelialGroveFeature;
import dev.nexusmc.landscape.worldgen.RiverBankFeature;
import dev.nexusmc.landscape.worldgen.VolcanicCalderaFeature;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(NexusLandscape.MOD_ID)
public final class NexusLandscape {
    public static final String MOD_ID = "nexus_landscape";

    private static final DeferredRegister<Feature<?>> FEATURES =
        DeferredRegister.create(Registries.FEATURE, MOD_ID);

    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> HOT_SPRING =
        FEATURES.register("hot_spring", () -> new HotSpringFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> CAVE_SANCTUM =
        FEATURES.register("cave_sanctum", () -> new CaveSanctumFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> FLOATING_ISLAND =
        FEATURES.register("floating_island", () -> new FloatingIslandFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> RIVER_BANK =
        FEATURES.register("river_bank", () -> new RiverBankFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> HUMID_KARST_ARCH =
        FEATURES.register("humid_karst_arch", () -> new HumidKarstArchFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> VOLCANIC_CALDERA =
        FEATURES.register("volcanic_caldera", () -> new VolcanicCalderaFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> CORAL_ATOLL =
        FEATURES.register("coral_atoll", () -> new CoralAtollFeature(NoneFeatureConfiguration.CODEC));
    public static final DeferredHolder<Feature<?>, Feature<NoneFeatureConfiguration>> MYCELIAL_GROVE =
        FEATURES.register("mycelial_grove", () -> new MycelialGroveFeature(NoneFeatureConfiguration.CODEC));

    public NexusLandscape(IEventBus modBus) {
        FEATURES.register(modBus);
        NeoForge.EVENT_BUS.addListener(this::registerCommands);
    }

    private void registerCommands(RegisterCommandsEvent event) {
        RouteAuditCommand.register(event.getDispatcher());
    }
}
