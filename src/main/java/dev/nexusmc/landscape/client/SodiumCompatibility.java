package dev.nexusmc.landscape.client;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

final class SodiumCompatibility {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TARGET_PREFIX = "0.8.12";
    private static boolean checked;

    private SodiumCompatibility() {
    }

    static void logCompatibilityOnce() {
        if (checked) {
            return;
        }
        checked = true;

        ModList.get().getModContainerById("sodium").ifPresentOrElse(container -> {
            String version = container.getModInfo().getVersion().toString();
            if (version.startsWith(TARGET_PREFIX)) {
                LOGGER.info(
                    "Nexus Landscape client visuals enabled with supported Sodium {}",
                    version
                );
            } else {
                LOGGER.warn(
                    "Nexus Landscape targets Sodium 0.8.12.x; found {}. "
                        + "Visuals will use the renderer-independent NeoForge path.",
                    version
                );
            }
        }, () -> LOGGER.info(
            "Sodium is not installed; Nexus Landscape client visuals will use vanilla rendering"
        ));
    }
}
