# Search Chunk and Auto Relog

Search Chunk is an independent mature-amethyst heuristic. Its red full-height columns use current completed scans of chunks actually received from the server. The default is four mature clusters, configurable from 1 to 64. Natural geodes and the user's own simulation can produce the same clusters; a marker is not proof of another player or an unraided base.

Auto Relog arms once. After the body stays at or below the configured Y for one second, it turns off, disconnects to a cancellable countdown and attempts the same server once. Default Y: 0. Delay: 5 seconds, adjustable 3–30. Escape or Cancel reconnect stops the countdown. Manual disconnect, Nearby Player Logout and failed connection attempts do not create a reconnect loop. There are no automatic camera, movement or trident actions.

Both modules have independent keybinds in their gear menu. Freecam keeps its existing separate manual keybind. Search Chunk continues scanning freshly received data after a normal reconnect.

Storage/model highlights use horizontal range through the full received world height. Off-screen models do not consume the visible model limit. Marker retention is bounded at 8192 per chunk with a deterministic position sample when crowded; model faces and visible objects have separate budgets. No client can display blocks that the server has not sent.

## References

- Public Anubis module descriptions: https://www.anubisclient.cc/features and https://www.anubisclient.cc/features?category=base-raid (Sus Chunk Finder and Auto Relog). No Anubis source code is copied into this project.
- Valve CS2 logo: https://cdn.fastly.steamstatic.com/apps/csgo/images/csgo_react/cs2/logo_cs2_header.svg, used by https://www.counter-strike.net/cs2 . Valve trademarks remain Valve's property.

## Verification

Gradle tests: 123 JUnit cases, plus farmChecks and searchChunkChecks. The latter covers one-shot/cancel timing, mature-cluster thresholds, full-height reservoir sampling, projection from above/below and hidden geometry not consuming face budgets. UI fixtures cover settings, independent keybind requests, layout and the official logo. A live multiplayer reconnect has not been claimed as tested.
