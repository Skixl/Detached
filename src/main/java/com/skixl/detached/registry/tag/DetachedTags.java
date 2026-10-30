package com.skixl.detached.registry.tag;

import com.skixl.detached.Detached;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public class DetachedTags {
    public static class Blocks {
        public static final TagKey<Block> BY_AXE = createTag("detachable_by_axe");
        public static final TagKey<Block> BY_PICKAXE = createTag("detachable_by_pickaxe");

        public static final TagKey<Block> GLASS_PANES = createFabricTag("glass_panes");
        public static final TagKey<Block> COPPER_BARS = createFabricTag("bars/copper");

        private static TagKey<Block> createTag(String name) {
            return TagKey.create(Registries.BLOCK, Detached.modId(name));
        }

        private static TagKey<Block> createFabricTag(String name) {
            return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("c", name));
        }
    }
}
