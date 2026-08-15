package dev.nexusmc.landscape.worldgen.v2.tree;

/** Explicit anatomical role of one branch-graph segment. */
public enum SegmentRole {
    TRUNK(true, false),
    ROOT(false, false),
    LIVE_BRANCH(true, false),
    DEAD_BRANCH(false, true),
    SECONDARY_LEADER(true, false);

    private final boolean foliageSource;
    private final boolean deadWood;

    SegmentRole(boolean foliageSource, boolean deadWood) {
        this.foliageSource = foliageSource;
        this.deadWood = deadWood;
    }

    public boolean canCarryFoliage() {
        return foliageSource;
    }

    public boolean deadWood() {
        return deadWood;
    }
}
