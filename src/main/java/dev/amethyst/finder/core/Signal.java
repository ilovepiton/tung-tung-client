package dev.amethyst.finder.core;

public enum Signal {
    CHEST, BARREL, SHULKER, ENDER_CHEST, HOPPER, FURNACE,
    CRAFTING, ANVIL, ENCHANTING, BEACON, REDSTONE, BED, TORCH, PORTAL, SPAWNER,
    AMETHYST, BUDDING, BUD, CLUSTER, ARMOR_STAND,
    DISPENSER, DROPPER, CONSTRUCTION;

    public boolean isAmethyst() { return this == AMETHYST || this == BUDDING || this == BUD || this == CLUSTER; }
    public boolean isStorage() {
        return switch (this) {
            case CHEST, BARREL, SHULKER, ENDER_CHEST, HOPPER, FURNACE, DISPENSER, DROPPER -> true;
            default -> false;
        };
    }
    public boolean isInfrastructure() {
        return isStorage() || this == CRAFTING || this == ANVIL || this == ENCHANTING
                || this == BEACON || this == REDSTONE;
    }
}
