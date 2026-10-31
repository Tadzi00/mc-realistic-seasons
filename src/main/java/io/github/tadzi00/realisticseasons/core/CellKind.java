package io.github.tadzi00.realisticseasons.core;

/** Classification of a single block for room detection. */
public enum CellKind {
    /** Air or any non-solid block air can flow through (torch, flower, carpet...). Part of the room volume. */
    OPEN,
    /** A wall/floor/ceiling block. Has an insulation value. */
    SOLID,
    /** A closed door: counts as sealed wall. */
    DOOR_CLOSED,
    /** Open door/trapdoor/fence gate, glass pane, fence, iron bars: a small leak, not traversed. */
    SMALL_LEAK,
    /** Chunk not loaded / outside of the world: the scan aborts as "outside". */
    UNKNOWN
}
