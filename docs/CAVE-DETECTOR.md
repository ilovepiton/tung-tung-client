# Sus Chunk and Auto Cave Detector

Open **World Tools → Finder**. Both modules have their own toggle and optional keybind in the gear menu.

- **Sus Chunk**: red full-height columns for mature-amethyst candidates. Default: four mature clusters. Your previous settings and keybind survive the name change.
- **Auto Cave Detector**: green full-height columns where no underground air, water or lava cavities were detected. Scans start automatically after enabling it.

Cave Detector checks every block in all 256 columns, from world minimum Y + 5 through five blocks below the received opaque surface of each column (ocean floor under water). A void column, unfinished scan or detected cavity excludes the chunk. A completed solid scan counts as green. Changed chunks lose their result immediately and are rescanned; unloading, disconnecting, disabling or changing world clears the current display. Scans use a 1 ms tick budget and only chunks already sent to your client. Range and visible limit use existing chunk highlight controls; the nearest 64 candidates share the renderer limit.

Green means **no cavities detected in received terrain**, not certainty that the real server terrain has no caves, and not proof of a base. Surface openings, liquids and built rooms below the scanned surface can exclude a chunk too. Nothing requests hidden chunks or moves your character. Auto Relog remains a separate single reconnect; it does not enable Freecam or move the player.
