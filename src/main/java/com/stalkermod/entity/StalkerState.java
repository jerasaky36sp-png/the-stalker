package com.stalkermod.entity;

public enum StalkerState {
    OBSERVE,
    STALK,
    CHASE,
    DISAPPEAR,
    /** Standing outside a window of the player's house, staring in. Appended last: synced by ordinal. */
    PEEK
}
