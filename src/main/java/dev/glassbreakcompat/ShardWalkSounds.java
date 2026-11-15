package dev.glassbreakcompat;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

/** Client only. Tracks where shards are lying and plays a crunch when any player walks over them. */
public final class ShardWalkSounds {
    /** block position (BlockPos#asLong) of the cell a shard is resting in -> number of shards there */
    private static final Map<Long, Integer> RESTING = new HashMap<>();
    private final Map<Integer, Long> nextSound = new HashMap<>();

    public static void add(long key) {
        RESTING.merge(key, 1, Integer::sum);
    }

    public static void remove(long key) {
        RESTING.computeIfPresent(key, (k, v) -> v <= 1 ? null : v - 1);
    }

    public static void clear() {
        RESTING.clear();
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || RESTING.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused() || !ClientCompatConfig.WALK_SOUND.get()) return;

        long now = mc.level.getGameTime();
        for (Player p : mc.level.players()) {
            if (!p.onGround() || p.isShiftKeyDown() || p.isSpectator()) continue;
            double moved = Math.hypot(p.getX() - p.xo, p.getZ() - p.zo);
            if (moved < 0.03) continue;
            if (nextSound.getOrDefault(p.getId(), 0L) > now) continue;

            AABB bb = p.getBoundingBox();
            int y = (int) Math.floor(p.getY() + 0.01);
            int shards = 0;
            for (int bx = (int) Math.floor(bb.minX); bx <= (int) Math.floor(bb.maxX); bx++) {
                for (int bz = (int) Math.floor(bb.minZ); bz <= (int) Math.floor(bb.maxZ); bz++) {
                    shards += RESTING.getOrDefault(BlockPos.asLong(bx, y, bz), 0);
                }
            }
            if (shards <= 0) continue;

            float volume = (float) (ClientCompatConfig.WALK_VOLUME.get() * Math.min(1.0, 0.5 + shards * 0.1));
            float pitch = 1.1f + mc.level.random.nextFloat() * 0.6f;
            mc.level.playLocalSound(p.getX(), p.getY(), p.getZ(), SoundEvents.GLASS_BREAK,
                    SoundSource.PLAYERS, volume, pitch, false);
            nextSound.put(p.getId(), now + 7);
        }
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut e) {
        clear();
        nextSound.clear();
    }
}
