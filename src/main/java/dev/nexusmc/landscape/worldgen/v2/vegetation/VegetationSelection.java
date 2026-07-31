package dev.nexusmc.landscape.worldgen.v2.vegetation;

public record VegetationSelection(
    String profileId,
    double treeDensity,
    double shrubDensity,
    double groundDensity,
    double flowerDensity,
    double deadwoodDensity,
    double rockDensity,
    double oldGrowthDensity,
    boolean treesAllowed,
    boolean terrestrialAllowed
) {
}
