package com.skixl.detached;

import com.skixl.detached.networking.DetachedClientData;
import com.skixl.detached.networking.OverrideSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public class DetachedClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(OverrideSyncPayload.TYPE, (payload, context)
                -> DetachedClientData.get().applyOverrides(payload.replaceExisting(), payload.positions(), payload.masks()));

        /* clear cached client data so overrides from a previous server don't carry
         * over to another server
         */
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client)
                -> DetachedClientData.get().clearAllOverrides());
    }
}
