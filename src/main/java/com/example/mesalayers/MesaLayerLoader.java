package com.example.mesalayers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MesaLayerLoader extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().create();
    private static volatile List<Layer> LAYERS = List.of();

    /**
     * A set of blocks picked from the registry by namespace and path, so a layer can
     * use a mod's blocks without hardcoding every ID. Resolved when the world loads.
     */
    public record Pool(String namespace, String pathSuffix, List<String> excludeContains) {
        public static final Codec<Pool> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("namespace").forGetter(Pool::namespace),
            Codec.STRING.optionalFieldOf("path_suffix", "").forGetter(Pool::pathSuffix),
            Codec.STRING.listOf().optionalFieldOf("exclude_contains", List.of()).forGetter(Pool::excludeContains)
        ).apply(i, Pool::new));

        public List<BlockState> resolve() {
            return BuiltInRegistries.BLOCK.keySet().stream()
                .filter(id -> id.getNamespace().equals(namespace))
                .filter(id -> id.getPath().endsWith(pathSuffix))
                .filter(id -> excludeContains.stream().noneMatch(id.getPath()::contains))
                .sorted() // stable order so the same seed gives the same result
                .map(id -> BuiltInRegistries.BLOCK.get(id))
                .filter(b -> b != Blocks.AIR)
                .map(Block::defaultBlockState)
                .toList();
        }
    }

    public record Layer(Optional<Block> block, Optional<Pool> pool, List<String> requiresMods,
                        Optional<Integer> yMin, Optional<Integer> yMax,
                        int count, int minThickness, int maxThickness) {
        public static final Codec<Layer> CODEC = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("block").forGetter(Layer::block),
            Pool.CODEC.optionalFieldOf("pool").forGetter(Layer::pool),
            Codec.STRING.listOf().optionalFieldOf("requires_mods", List.of()).forGetter(Layer::requiresMods),
            Codec.INT.optionalFieldOf("y_min").forGetter(Layer::yMin),
            Codec.INT.optionalFieldOf("y_max").forGetter(Layer::yMax),
            Codec.INT.optionalFieldOf("count", 1).forGetter(Layer::count),
            Codec.INT.optionalFieldOf("min_thickness", 1).forGetter(Layer::minThickness),
            Codec.INT.optionalFieldOf("max_thickness", 3).forGetter(Layer::maxThickness)
        ).apply(i, Layer::new));

        List<BlockState> states() {
            if (pool.isPresent()) return pool.get().resolve();
            return block.map(b -> List.of(b.defaultBlockState())).orElse(List.of());
        }
    }

    public MesaLayerLoader() {
        super(GSON, "mesa_layers");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager rm, ProfilerFiller profiler) {
        List<Layer> out = new ArrayList<>();
        files.entrySet().stream()
            .sorted(Map.Entry.comparingByKey()) // deterministic order
            .forEach(e -> Layer.CODEC.parse(JsonOps.INSTANCE, e.getValue())
                .resultOrPartial(err -> LOGGER.error("Bad mesa layer {}: {}", e.getKey(), err))
                .ifPresent(layer -> {
                    // silently skip layers whose required mods aren't installed.
                    for (String modId : layer.requiresMods()) {
                        if (!ModList.get().isLoaded(modId)) {
                            LOGGER.info("Mesa layer {} skipped: mod '{}' is not loaded", e.getKey(), modId);
                            return;
                        }
                    }
                    if (layer.block().isPresent() == layer.pool().isPresent()) {
                        LOGGER.error("Mesa layer {} skipped: give exactly one of 'block' or 'pool'", e.getKey());
                        return;
                    }
                    // Registry codec silently falls back to air for unknown ids, so reject that.
                    if (layer.block().isPresent() && layer.block().get() == Blocks.AIR) {
                        LOGGER.error("Mesa layer {} skipped: unknown block or air", e.getKey());
                        return;
                    }
                    if (layer.yMin().isPresent() != layer.yMax().isPresent()) {
                        LOGGER.error("Mesa layer {} skipped: y_min and y_max must be given together", e.getKey());
                        return;
                    }
                    out.add(layer);
                }));
        LAYERS = List.copyOf(out);
        LOGGER.info("Loaded {} mesa layer definition(s)", out.size());
    }

    private static BlockState pick(List<BlockState> states, RandomSource random) {
        return states.size() == 1 ? states.get(0) : states.get(random.nextInt(states.size()));
    }

    /** Called from the mixin after vanilla has filled the band array. */
    public static void apply(BlockState[] bands, RandomSource random) {
        for (Layer l : LAYERS) {
            List<BlockState> states = l.states();
            if (states.isEmpty()) {
                LOGGER.info("Mesa layer skipped at world load: its block pool matched no blocks");
                continue;
            }
            if (l.yMin().isPresent()) {
                BlockState state = pick(states, random);
                int lo = Math.min(l.yMin().get(), l.yMax().get());
                int hi = Math.max(l.yMin().get(), l.yMax().get());
                for (int y = lo; y <= hi; y++) {
                    bands[Math.floorMod(y, bands.length)] = state;
                }
            } else {
                int min = Math.max(1, l.minThickness());
                int max = Math.max(min, l.maxThickness());
                for (int n = 0; n < l.count(); n++) {
                    int thickness = min + random.nextInt(max - min + 1);
                    int start = random.nextInt(bands.length);
                    BlockState state = pick(states, random);
                    for (int j = 0; j < thickness && start + j < bands.length; j++) {
                        bands[start + j] = state;
                    }
                }
            }
        }
    }
}
