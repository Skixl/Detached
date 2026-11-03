package com.skixl.detached.networking;

import com.skixl.detached.world.saveddata.DetachedConnections;

public class DetachedClientData {
    private DetachedClientData() {}

    private static final DetachedConnections INSTANCE = new DetachedConnections();

    public static DetachedConnections get() {
        return INSTANCE;
    }
}
