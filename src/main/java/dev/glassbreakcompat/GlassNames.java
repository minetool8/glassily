package dev.glassbreakcompat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Decides which blocks count as glass purely from their registry name. */
public final class GlassNames {
    private static final Map<Block, Boolean> CACHE = new ConcurrentHashMap<>();

    private GlassNames() {}

    public static void clearCache() {
        CACHE.clear();
    }

    public static boolean matches(BlockState state) {
        return CACHE.computeIfAbsent(state.getBlock(), GlassNames::compute);
    }

    private static boolean compute(Block block) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id == null) return false;
        String path = id.getPath();

        if (!containsAny(path, keywords())) return false;
        if (containsAny(path, excludes())) return false;

        // Blocks with their own block entity would lose that data when swapped for a damaged-glass block.
        if (block instanceof EntityBlock) return false;

        // Panes (vanilla, stained, most modded) and plain full cubes are what Better Glass Breaking can model.
        if (block instanceof IronBarsBlock) return true;
        try {
            VoxelShape shape = block.defaultBlockState()
                    .getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            return Block.isShapeFullBlock(shape);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean containsAny(String path, List<? extends String> words) {
        for (String w : words) {
            if (!w.isEmpty() && path.contains(w.toLowerCase())) return true;
        }
        return false;
    }

    private static List<? extends String> keywords() {
        try {
            return CompatConfig.NAME_KEYWORDS.get();
        } catch (IllegalStateException notLoaded) {
            return CompatConfig.DEFAULT_KEYWORDS;
        }
    }

    private static List<? extends String> excludes() {
        try {
            return CompatConfig.NAME_EXCLUDES.get();
        } catch (IllegalStateException notLoaded) {
            return List.of();
        }
    }
}
