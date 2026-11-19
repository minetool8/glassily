package dev.glassbreakcompat;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides which blocks count as glass, purely from the registry name ("glass" / "window" by default).
 * <ul>
 *   <li>{@link #matches}: shapes Better Glass Breaking can model (full cubes, pane-style) -> procedural break.</li>
 *   <li>{@link #isOtherGlass}: every other glass-named block (slabs, stairs, arches, vertical slabs...) -> normal break.</li>
 * </ul>
 */
public final class GlassNames {
    private static final Map<Block, Boolean> BGB_CACHE = new ConcurrentHashMap<>();
    private static final Map<Block, Boolean> OTHER_CACHE = new ConcurrentHashMap<>();

    private GlassNames() {}

    public static void clearCache() {
        BGB_CACHE.clear();
        OTHER_CACHE.clear();
    }

    /** Glass shaped so that Better Glass Breaking can fracture it procedurally. */
    public static boolean matches(BlockState state) {
        return BGB_CACHE.computeIfAbsent(state.getBlock(), GlassNames::computeBgb);
    }

    /** Glass-named blocks that Better Glass Breaking cannot model (they break normally instead). */
    public static boolean isOtherGlass(BlockState state) {
        return OTHER_CACHE.computeIfAbsent(state.getBlock(), b -> named(b) && !computeBgb(b));
    }

    private static boolean computeBgb(Block block) {
        if (!named(block)) return false;
        // Blocks with their own block entity would lose that data when swapped for a damaged-glass block.
        if (block instanceof EntityBlock) return false;
        if (block instanceof IronBarsBlock) return true;
        try {
            VoxelShape shape = block.defaultBlockState()
                    .getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            return Block.isShapeFullBlock(shape);
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean named(Block block) {
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock) {
            return false;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id == null) return false;
        String path = id.getPath();
        return containsAny(path, keywords()) && !containsAny(path, excludes());
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
            return CompatConfig.DEFAULT_EXCLUDES;
        }
    }
}
