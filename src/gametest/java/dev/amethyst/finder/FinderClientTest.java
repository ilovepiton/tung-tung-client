package dev.amethyst.finder;

import dev.amethyst.finder.core.Finding;
import dev.amethyst.finder.core.Signal;
import dev.amethyst.finder.ui.FinderScreen;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

@SuppressWarnings("UnstableApiUsage")
public final class FinderClientTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 800);
        try (var world = context.worldBuilder().create()) {
            world.getConnection().waitForChunksRender();
            world.getServer().runCommand("tp @a 8 8 8");
            world.getServer().runCommand("setblock 20 4 20 chest");
            world.getServer().runCommand("setblock 21 4 20 hopper");
            world.getServer().runCommand("setblock 22 4 20 purple_shulker_box");
            world.getServer().runCommand("setblock -24 4 20 beacon");
            world.getServer().runCommand("fill 48 4 0 55 4 7 amethyst_block");
            world.getServer().runCommand("setblock 48 5 0 budding_amethyst");
            world.getServer().runCommand("setblock 48 6 0 amethyst_cluster");
            world.getConnection().waitForClientboundPackets();
            context.runOnClient(client -> {
                var c = ChunkFinderClient.CONFIG;
                c.enabled = true; c.notifications = false; c.radiusChunks = 5; c.budgetMs = 2;
                c.sensitivity = 65; c.storage = true; c.builds = true; c.amethyst = true;
                c.language = "ru";
                client.options.guiScale().set(2);
                client.resizeGui();
                ChunkFinderClient.SCANNER.clear();
            });
            context.waitFor(client -> ChunkFinderClient.SCANNER.findings().stream().anyMatch(f -> f.evidence().count(Signal.SHULKER) == 1), 600);
            context.waitFor(client -> ChunkFinderClient.SCANNER.findings().stream().anyMatch(f -> f.kind() == Finding.Kind.GEODE), 600);
            context.runOnClient(client -> {
                var list = ChunkFinderClient.SCANNER.findings();
                Finding storage = list.stream().filter(f -> f.evidence().count(Signal.SHULKER) == 1).findFirst().orElseThrow();
                if (storage.evidence().count(Signal.CHEST) != 1 || storage.evidence().count(Signal.HOPPER) != 1)
                    throw new AssertionError("Client block counts do not match the server fixture");
                ChunkFinderClient.SCANNER.waypoint = storage;
            });
            context.getInput().pressKey(ChunkFinderClient.MENU);
            context.waitForScreen(FinderScreen.class);
            context.takeScreenshot("amethyst-menu-ru");
            context.setScreen(() -> new FinderScreen(1));
            context.takeScreenshot("amethyst-methods-ru");
            context.setScreen(() -> new FinderScreen(2));
            context.takeScreenshot("amethyst-findings-ru");
            context.setScreen(() -> null);
            context.takeScreenshot("amethyst-hud");
            world.getServer().runCommand("setblock 48 6 0 air");
            world.getConnection().waitForClientboundPackets();
            context.waitFor(client -> ChunkFinderClient.SCANNER.findings().stream()
                    .anyMatch(f -> f.evidence().chunkX == 3 && f.evidence().chunkZ == 0 && f.disappearedClusters() == 1), 600);
            context.runOnClient(client -> {
                try {
                    var path = FindingExporter.export(ChunkFinderClient.SCANNER.findings(), "test:overworld");
                    String csv = java.nio.file.Files.readString(path);
                    if (!csv.contains("cluster_change") || !csv.contains("test:overworld")) throw new AssertionError("CSV export lost findings");
                } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
            });
            context.runOnClient(client -> {
                ChunkFinderClient.CONFIG.storage = false;
                ChunkFinderClient.CONFIG.builds = false;
                ChunkFinderClient.SCANNER.refresh(client);
                if (ChunkFinderClient.SCANNER.findings().stream().anyMatch(f -> f.kind() == Finding.Kind.ACTIVITY))
                    throw new AssertionError("Disabled storage/build methods leaked into findings");
                ChunkFinderClient.CONFIG.storage = true; ChunkFinderClient.CONFIG.builds = true;
                ChunkFinderClient.CONFIG.language = "en";
                client.options.guiScale().set(3); client.resizeGui();
                ChunkFinderClient.SCANNER.refresh(client);
            });
            context.setScreen(FinderScreen::new);
            context.takeScreenshot("amethyst-menu-compact-en");
            context.setScreen(() -> null);
            context.waitTick();
            context.getInput().pressKey(ChunkFinderClient.TOGGLE);
            context.waitFor(client -> !ChunkFinderClient.CONFIG.enabled);
            context.runOnClient(client -> {
                if (ChunkFinderClient.CONFIG.enabled) throw new AssertionError("Toggle key failed");
            });
        }
        context.waitFor(client -> client.level == null);
        context.waitTick();
        context.runOnClient(client -> {
            if (!ChunkFinderClient.SCANNER.findings().isEmpty() || ChunkFinderClient.SCANNER.waypoint != null)
                throw new AssertionError("Findings leaked across world disconnect");
        });
        ChunkFinderClient.LOGGER.info("AMETHYST E2E PASS: client block scan, natural geode, cluster change, export, menu, compact UI, toggle and disconnect");
    }
}
