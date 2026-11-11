package qouteall.imm_ptl.core.mc_utils;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import java.util.stream.Stream;

/** Preserves the published portal NBT format across the ValueInput/ValueOutput migration. */
public final class NbtCompatibility {
    private NbtCompatibility() {}

    public static final MapCodec<CompoundTag> ROOT =
            new MapCodec<>() {
                @Override
                public <T> Stream<T> keys(DynamicOps<T> ops) {
                    return Stream.empty();
                }

                @Override
                public <T> DataResult<CompoundTag> decode(DynamicOps<T> ops, MapLike<T> input) {
                    CompoundTag tag = new CompoundTag();
                    input.entries()
                            .forEach(
                                    entry ->
                                            ops.getStringValue(entry.getFirst())
                                                    .result()
                                                    .ifPresent(
                                                            key ->
                                                                    tag.put(
                                                                            key,
                                                                            ops.convertTo(
                                                                                    NbtOps.INSTANCE,
                                                                                    entry
                                                                                            .getSecond()))));
                    return DataResult.success(tag);
                }

                @Override
                public <T> RecordBuilder<T> encode(
                        CompoundTag input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                    input.forEach(
                            (key, value) -> prefix.add(key, NbtOps.INSTANCE.convertTo(ops, value)));
                    return prefix;
                }
            };

    public static CompoundTag save(Entity entity) {
        TagValueOutput output =
                TagValueOutput.createWithContext(
                        ProblemReporter.DISCARDING, entity.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    public static void load(Entity entity, CompoundTag tag) {
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, entity.registryAccess(), tag));
    }
}
