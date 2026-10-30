package com.skixl.detached.world.saveddata;

import com.mojang.serialization.Codec;
import com.skixl.detached.Detached;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * One int per block position, made of five independent 2-bit slots:
 *
 *   bits 8-9 : post (up)
 *   bits 6-7 : east
 *   bits 4-5 : north
 *   bits 2-3 : west
 *   bits 0-1 : south
 *
 * ex.
 *   00 01 10 00 00
 *   will have the east face as detached and the north face
 *   as forced
 *
 * Arm slot shift = Direction.get2DDataValue() * 2 (south 0, west 1, north 2, east 3).
 * Slot values are the ordinal of {@link Override}.
 *
 * any reference to "post" is just the "up" block state
 */
public class DetachedConnections extends SavedData {

    /** What a slot holds. The ordinal is the stored value. */
    public enum Override {
        NONE, // 00: natural behavior
        OFF,  // 01: arm detached / post hidden
        ON;   // 10: arm forced / post shown

        private static final Override[] VALUES = values();

        static Override fromBits(int bits) {
            if (bits < VALUES.length) {
                return VALUES[bits];
            }

            return NONE; // 0b11 is invalid -> NONE
        }
    }

    private static final int SLOT_MASK = 0b11; // this 0b is throwing me off
    private static final int POST_SHIFT = 8;
    private static final int VALID_BITS = 0b1111111111; // bits 0-9, the only ones in use

    /** Returns the bit shift for a direction's 2-bit arm slot (south 0, west 2, north 4, east 6). */
    private static int shift(Direction d) {
        return d.get2DDataValue() * 2;
    }

    private final Long2IntOpenHashMap masks = new Long2IntOpenHashMap();

    /** key (string): block position converted to a string
     * value (integer): encoded override bitmask */
    public static final Codec<DetachedConnections> CODEC =
            Codec.unboundedMap(Codec.STRING, Codec.INT)
                    .xmap(DetachedConnections::new, DetachedConnections::toStringMap);

    public static final SavedDataType<@NotNull DetachedConnections> TYPE = new SavedDataType<>(
            Detached.modId("detached_connections"),
            DetachedConnections::new,
            CODEC,
            DataFixTypes.LEVEL);

    public DetachedConnections() {}

    private DetachedConnections(Map<String, Integer> serializedData) {
        serializedData.forEach((key, encodedMask) -> {
            int mask = encodedMask & VALID_BITS; // keeps the lowest 10 bits using bitwise AND
            if (mask != 0) {
                masks.put(Long.parseLong(key), mask);
            }
        });
    }

    // used for serialization in codec
    private Map<String, Integer> toStringMap() {
        Map<String, Integer> output = new HashMap<>();

        for (Long2IntMap.Entry longEntry : masks.long2IntEntrySet()) {
            // output.put(block position as long as string, retrieve bitmask);
            output.put(Long.toString(longEntry.getLongKey()), longEntry.getIntValue());
        }

        return output;
    }

    public static DetachedConnections get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }




    // ------------------------------------- slots -------------------------------------
    // ze heart of it all :)

    private Override read(BlockPos blockPos, int shift) {
        // return fromBits((bitmask integer associated with blockPos) >> shift) & lowest two bits);
        // >> shift will move bits all the way right; ex 00 01 10 00 00 -> 00 00 00 01 10
        return Override.fromBits((masks.get(blockPos.asLong()) >> shift) & SLOT_MASK);
    }

    private void write(BlockPos blockPos, int shift, Override value) {
        long key = blockPos.asLong();
        int existingBitmask = masks.get(key);

        /* (SLOT_MASK << shift) so I can isolate two bits I want to modify
         * &'ing ~(SLOT_MASK << shift) with the existingBitmask preserved already existing values
         * bitwise OR inserts the new value to write */
        int updatedBitmask = (existingBitmask & ~(SLOT_MASK << shift)) | (value.ordinal() << shift);
        if (updatedBitmask == existingBitmask) return;

        if (updatedBitmask == 0) {
            masks.remove(key);
        } else {
            masks.put(key, updatedBitmask);
        }
        setDirty();
    }

    public boolean hasOverrides(BlockPos blockPos) {
        return masks.containsKey(blockPos.asLong());
    }

    public void clear(BlockPos blockPos) {
        if (masks.remove(blockPos.asLong()) != 0) {
            setDirty();
        }
    }




    // ------------------------------------- arms -------------------------------------
    public Override getArm(BlockPos blockPos, Direction direction) {
        return read(blockPos, shift(direction));
    }

    public void setArm(BlockPos blockPos, Direction direction, Override value) {
        write(blockPos, shift(direction), value);
    }

    public boolean isDetached(BlockPos blockPos, Direction direction) {
        return getArm(blockPos, direction) == Override.OFF;
    }

    public boolean isForced(BlockPos blockPos, Direction direction) {
        return getArm(blockPos, direction) == Override.ON;
    }

    public void setDetached(BlockPos blockPos, Direction direction) {
        setArm(blockPos, direction, Override.OFF);
    }

    public void setForced(BlockPos blockPos, Direction direction) {
        setArm(blockPos, direction, Override.ON);
    }

    public void clearArm(BlockPos blockPos, Direction direction) {
        setArm(blockPos, direction, Override.NONE);
    }




    // ------------------------------------- wall (post portion) -------------------------------------
    public Override getPost(BlockPos blockPos) {
        return read(blockPos, POST_SHIFT);
    }

    public void setPost(BlockPos blockPos, Override value) {
        write(blockPos, POST_SHIFT, value);
    }

    /** 1 = forced shown, -1 = forced hidden, 0 = no override. */
    public int getUpOverride(BlockPos blockPos) {
        return switch (getPost(blockPos)) {
            case ON -> 1;
            case OFF -> -1;
            default -> 0;
        };
    }

    public void setUpOverride(BlockPos blockPos, boolean up) {
        if (up) {
            setPost(blockPos, Override.ON);
        } else {
            setPost(blockPos, Override.OFF);
        }
    }

    public void clearUpOverride(BlockPos blockPos) {
        setPost(blockPos, Override.NONE);
    }
}