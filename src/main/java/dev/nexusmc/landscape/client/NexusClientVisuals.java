package dev.nexusmc.landscape.client;

import dev.nexusmc.landscape.NexusLandscape;
import dev.nexusmc.landscape.worldgen.NexusBiomeTags;
import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = NexusLandscape.MOD_ID, value = Dist.CLIENT)
public final class NexusClientVisuals {
    private NexusClientVisuals() {
    }

    @SubscribeEvent
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        SodiumCompatibility.logCompatibilityOnce();

        if (event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }

        VisualProfile profile = profileAtCamera(event);
        if (profile == null) {
            return;
        }

        event.setRed(blend(event.getRed(), profile.red(), profile.colorStrength()));
        event.setGreen(blend(event.getGreen(), profile.green(), profile.colorStrength()));
        event.setBlue(blend(event.getBlue(), profile.blue(), profile.colorStrength()));
    }

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (event.getType() != FogType.NONE) {
            return;
        }

        VisualProfile profile = profileAtCamera(event);
        if (profile == null) {
            return;
        }

        event.scaleNearPlaneDistance(profile.nearDistanceScale());
        event.scaleFarPlaneDistance(profile.farDistanceScale());
        event.setCanceled(true);
    }

    private static VisualProfile profileAtCamera(ViewportEvent event) {
        if (event.getCamera().getEntity() == null) {
            return null;
        }

        BlockPos position = BlockPos.containing(event.getCamera().getPosition());
        Holder<Biome> biome = event.getCamera().getEntity().level().getBiome(position);

        if (biome.is(NexusBiomeTags.HAS_DEEP_DARK_RIFTS)) {
            return VisualProfile.DEEP_DARK;
        }
        if (biome.is(NexusBiomeTags.MYCELIAL_REACHES)) {
            return VisualProfile.MYCELIAL;
        }
        if (biome.is(NexusBiomeTags.VOLCANIC_PROVINCES)) {
            return VisualProfile.VOLCANIC;
        }
        if (biome.is(NexusBiomeTags.HUMID_KARST_REGION)) {
            return VisualProfile.HUMID_KARST;
        }
        return null;
    }

    private static float blend(float original, float target, float strength) {
        return original + (target - original) * strength;
    }

    private record VisualProfile(
        float red,
        float green,
        float blue,
        float colorStrength,
        float nearDistanceScale,
        float farDistanceScale
    ) {
        private static final VisualProfile HUMID_KARST =
            new VisualProfile(0.48F, 0.68F, 0.62F, 0.18F, 0.82F, 0.78F);
        private static final VisualProfile VOLCANIC =
            new VisualProfile(0.43F, 0.29F, 0.23F, 0.16F, 0.90F, 0.86F);
        private static final VisualProfile MYCELIAL =
            new VisualProfile(0.46F, 0.34F, 0.58F, 0.20F, 0.78F, 0.73F);
        private static final VisualProfile DEEP_DARK =
            new VisualProfile(0.05F, 0.08F, 0.12F, 0.30F, 0.60F, 0.52F);
    }
}
