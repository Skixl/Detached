package com.skixl.detached.networking;

import com.skixl.detached.world.saveddata.DetachedConnections;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;

public final class DetachedNetworking {
    private DetachedNetworking() {}

    private static final int BATCH_SIZE = 4096; // entries per snapshot packet

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(OverrideSyncPayload.TYPE, OverrideSyncPayload.CODEC);


        ServerPlayConnectionEvents.JOIN.register((handler, sender, server)
                -> syncDimensionOverrides(handler.getPlayer()));


        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination)
                -> syncDimensionOverrides(player));


        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive)
                -> syncDimensionOverrides(newPlayer));
    }


    /**
     * syncs all overrides in the player's current dimension
     * the first packet clears the clients existing overrides
     */
    public static void syncDimensionOverrides(ServerPlayer player) {
        ServerLevel level = player.level();

        if (ServerPlayNetworking.canSend(player, OverrideSyncPayload.TYPE)) {

            Long2IntMap overrides = DetachedConnections.get(level).rawMasks();

            long[] positions = new long[BATCH_SIZE];
            int[] masks = new int[BATCH_SIZE];

            int size = 0;
            boolean firstPacket = true;

            for (Long2IntMap.Entry entry : overrides.long2IntEntrySet()) {
                positions[size] = entry.getLongKey();
                masks[size] = entry.getIntValue();
                size++;

                if (size == BATCH_SIZE) {
                    sendBatch(player, positions, masks, size, firstPacket);
                    firstPacket = false;
                    size = 0;
                }
            }

            // always send at least one packet, so an empty dimension still clears the client
            if (size > 0 || firstPacket) {
                sendBatch(player, positions, masks, size, firstPacket);
            }
        }
    }


    // ------------------------------------- helpers -------------------------------------
    private static void sendBatch(ServerPlayer player, long[] positions, int[] masks, int size, boolean firstPacket) {
        ServerPlayNetworking.send(player, new OverrideSyncPayload(
                firstPacket,
                Arrays.copyOf(positions, size),
                Arrays.copyOf(masks, size)
        ));
    }

    /** tells every player in the level that one position changed (a mask of 0 removes it) */
    public static void broadcast(ServerLevel level, long pos, int mask) {
        OverrideSyncPayload payload = new OverrideSyncPayload(false, new long[]{pos}, new int[]{mask});

        for (ServerPlayer player : level.players()) {
            if (ServerPlayNetworking.canSend(player, OverrideSyncPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

}
