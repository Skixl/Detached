package com.skixl.detached;

import com.skixl.detached.interaction.ConnectionDetacher;
import net.fabricmc.api.ModInitializer;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Detached implements ModInitializer {
	public static final String MOD_ID = "detached";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ConnectionDetacher.register();
	}

	public static Identifier modId(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
