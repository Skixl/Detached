package com.skixl.detached.networking;

import com.skixl.detached.Detached;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 *
 * @param replaceExisting whether to clear existing client data before applying incoming data
 * @param positions block positions with updated override information
 * @param masks override bitmask associated with each corresponding block position
 */
public record OverrideSyncPayload(boolean replaceExisting, long[] positions, int[] masks) implements CustomPacketPayload {

    public static final Identifier OVERRIDE_SYNC_PAYLOAD_ID = Detached.modId("override_sync");
    public static final CustomPacketPayload.Type<@NotNull OverrideSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(OVERRIDE_SYNC_PAYLOAD_ID);

    public static final StreamCodec<FriendlyByteBuf, OverrideSyncPayload> CODEC =
            CustomPacketPayload.codec(OverrideSyncPayload::write, OverrideSyncPayload::read);


    private void write(FriendlyByteBuf buf) {
        buf.writeBoolean(replaceExisting);

        // block positions
        int size = positions.length;
        buf.writeVarInt(size);

        // for each block position, write as long and it's override bitmask
        for (int i = 0; i < size; i++) {
            buf.writeLong(positions[i]);
            buf.writeVarInt(masks[i]);
        }
    }

    private static OverrideSyncPayload read(FriendlyByteBuf buf) {
        boolean replaceExisting = buf.readBoolean();

        // read block positions
        int size = buf.readVarInt();
        long[] positions = new long[size];
        int[] masks = new int[size];

        // for each block position, read long and it's override bitmask
        for (int i = 0; i < size; i++) {
            positions[i] = buf.readLong();
            masks[i] = buf.readVarInt();
        }

        return new OverrideSyncPayload(replaceExisting, positions, masks);
    }

    @Override
    public CustomPacketPayload.Type<OverrideSyncPayload> type() {
        return TYPE;
    }
}
