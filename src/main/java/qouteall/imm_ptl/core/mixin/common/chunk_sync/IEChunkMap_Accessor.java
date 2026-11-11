package qouteall.imm_ptl.core.mixin.common.chunk_sync;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
@Mixin(ChunkMap.class)
public interface IEChunkMap_Accessor {
    @Accessor("visibleChunkMap") Long2ObjectLinkedOpenHashMap<ChunkHolder> ip_visibleChunks();
}
