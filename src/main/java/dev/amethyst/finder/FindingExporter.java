package dev.amethyst.finder;

import dev.amethyst.finder.core.Finding;
import dev.amethyst.finder.core.Signal;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class FindingExporter {
    private FindingExporter() {}
    public static Path export(List<Finding> findings, String dimension) throws IOException {
        Path folder = FabricLoader.getInstance().getGameDir().resolve("amethyst-exports");
        Files.createDirectories(folder);
        Path out = folder.resolve("findings-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".csv");
        StringBuilder csv = new StringBuilder("\uFEFFdimension,chunk_x,chunk_z,center_x,sample_y,center_z,signal_score,kind,observed_at_ms,reasons,chests,barrels,shulkers,hoppers,budding_amethyst,clusters,spawners,armor_stands,observed_bud_growth,disappeared_clusters\r\n");
        for (Finding f : findings) {
            var e = f.evidence();
            csv.append(quoted(dimension)).append(',').append(e.chunkX).append(',').append(e.chunkZ).append(',')
                    .append(f.x()).append(',').append(f.y()).append(',').append(f.z()).append(',').append(f.score()).append(',')
                    .append(f.kind()).append(',').append(e.observedAt).append(',').append(quoted(String.join("; ", f.reasons())));
            for (Signal s : List.of(Signal.CHEST, Signal.BARREL, Signal.SHULKER, Signal.HOPPER, Signal.BUDDING, Signal.CLUSTER, Signal.SPAWNER, Signal.ARMOR_STAND))
                csv.append(',').append(e.count(s));
            csv.append(',').append(f.observedGrowth()).append(',').append(f.disappearedClusters()).append("\r\n");
        }
        Files.writeString(out, csv.toString(), StandardCharsets.UTF_8);
        return out;
    }
    private static String quoted(String value) { return '"' + value.replace("\"", "\"\"") + '"'; }
}
