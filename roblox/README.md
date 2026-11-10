# Tung Tung Roblox runtime

This package contains the Roblox-side implementation for Tung Tung's 24-module Roblox section. It uses ordinary Roblox client APIs and an injected transport interface. It does **not** make a normal Roblox installation execute external scripts, and it does **not** include an injector, an executor, a Roblox binary patch, or an anti-cheat bypass.

**Compatibility requirement:** a creator-controlled LocalScript or another approved, already-compatible Luau host must load the module and supply the transport. Merely joining or paying for a private server does not provide that execution environment. The desktop app must show **Disconnected** until a real runtime session pairs and publishes its state. A native window alone cannot apply these tools to an unmodified third-party game.

## Package

- `dist/TungTungRobloxRuntime.luau`: portable, self-contained ModuleScript source; no remote `loadstring` or dynamic downloads.
- `src/`: the same implementation split into `Runtime`, `Catalog`, `Policy`, and `PropertyLease` ModuleScripts.
- `catalog.json`: the exact IDs, ranges, names, categories, and availability scopes used by this runtime.
- `HOST.md`: injected transport and command contract.
- `tests/`: deterministic tests using mocked standard Roblox APIs.
- `tools/build.py`: creates the portable runtime and catalog, compiles every Luau source, runs tests, and records hashes.

## What is implemented

| Module | What it applies | Scope |
|---|---|---|
| Fly | Modern `LinearVelocity`; WASD, Space up, Ctrl down; 1–300 studs/s | Own local character; server can correct |
| NoClip | Temporarily disables collisions on own character parts | Own local character; server can correct |
| Walk Speed | Sets own Humanoid WalkSpeed, 1–100 | Own local character; server can correct |
| Super Jump | Scales JumpPower and JumpHeight | Own local character; server can correct |
| Low Gravity | Mass-scaled `VectorForce` on own character; world gravity unchanged | Own local character; server can correct |
| Dash | Bounded forward impulse; two-second cooldown | Own local character; server can correct |
| Double Jump | One extra airborne jump, reset on landing | Own local character; server can correct |
| Air Jump | Repeat airborne jump; 0.25-second cooldown | Own local character; server can correct |
| Self Fling | Bounded forward/up impulse; three-second cooldown | Self only; server can correct |
| Fling Friends | Requests an authorized game-side action with server-confirmed target consent | Unavailable without a trusted game adapter |
| Slide | Grounded forward impulse; 1.5-second cooldown | Own local character; server can correct |
| Spin | Modern `AngularVelocity`, reversible AutoRotate | Own local character; server can correct |
| Freeze Self | Reversible own-root Anchored property | Own local character; server can correct |
| Respawn | Requests an authorized game-side character reload | Unavailable without a trusted game adapter |
| Return to Spawn | Returns to the character's saved spawn pivot | Own local character; server can correct |
| Spectate | Watches a selected living player's Humanoid | Local camera |
| Free Camera | Scriptable camera; WASD, Space/Ctrl; hold RMB to look | Local camera; body stays in the world |
| Wide FOV | 30–120-degree camera FOV | Local camera |
| Fullbright | Reversible brightness and ambient lighting | Local lighting |
| Golden Hour | Reversible ClockTime preset | Local lighting |
| Neon Bloom | Own `BloomEffect` | Local post-processing |
| Cinematic Focus | Own `DepthOfFieldEffect` | Local post-processing |
| Copper Grade | Own `ColorCorrectionEffect` | Local post-processing |
| Avatar Trails | Own attachments and `Trail` | Local avatar effect; visibility to others not promised |

“Shaders” means Roblox lighting and post-processing presets here; this package does not install a custom GPU shader engine. Warm, cinematic, vibrant, and clean presets configure the runtime's own Bloom/ColorCorrection effects. Render quality and the host game's scripts can affect the result.

## Lifecycle and state

Character changes clear on character removal, death, respawn, and runtime shutdown. Camera, FOV, mouse mode, lighting, and modified character properties use compare-and-restore leases: if the game has since written another value, shutdown preserves that newer value. Only instances created by this runtime are destroyed. Freecam and Spectate are mutually exclusive. Typing in a text box does not move the flying avatar or trigger air jumps. Local movement changes are reported as **Applied locally · server may correct**; they are never labelled as server-confirmed movement.

The runtime cannot detect every form of server correction. It reports observed WalkSpeed/Anchored changes and never claims that a locally applied physics effect is guaranteed to work in every game. Spectate is limited to characters replicated to this client, including streaming limits.

## Verification

The build report explicitly separates compilation and mocked API/lifecycle tests from live engine testing. At build time no Roblox Studio instance was connected; no live Roblox game or third-party server was tested. The test suite does not simulate Roblox's physics, replication, streaming, visual quality, or third-party anti-cheat.

To reproduce with the official Luau CLI available at `tools/luau-0.744`:

```sh
python3 roblox/tools/build.py
```

## Official API references

- [Roblox camera customization](https://create.roblox.com/docs/workspace/camera): Scriptable camera, CFrame, Focus, and FOV.
- [LinearVelocity](https://create.roblox.com/docs/reference/engine/classes/LinearVelocity): current force-limited velocity constraint.
- [Mover constraints](https://create.roblox.com/docs/physics/mover-constraints): VectorForce and AngularVelocity.
- [BasePart](https://create.roblox.com/docs/reference/engine/classes/BasePart): assembly velocity, impulses, and network ownership effects.
- [Post-processing effects](https://create.roblox.com/docs/environment/post-processing-effects): Bloom, depth of field, and color correction; graphics-quality limits.
- [Lighting](https://create.roblox.com/docs/reference/engine/classes/Lighting): local brightness, ambient lighting, and ClockTime.
- [Securing the client-server boundary](https://create.roblox.com/docs/scripting/security/client-server-boundary): server validation and permissions for actions affecting another player.
- [In-game HTTP requests](https://create.roblox.com/docs/cloud-services/http-service): Roblox HTTP-service constraints; a stock client is not a localhost HTTP adapter.
- [Official Luau 0.744 release](https://github.com/luau-lang/luau/releases/tag/0.744): compiler and CLI used by the included tests.
- [UserInputService](https://create.roblox.com/docs/reference/engine/classes/UserInputService): keyboard state, text-box focus, jump requests, and mouse delta (requires a locked mouse).
- [ContextActionService](https://create.roblox.com/docs/reference/engine/classes/ContextActionService): high-priority Freecam input capture with reversible action binding.
- [Humanoid](https://create.roblox.com/docs/reference/engine/classes/Humanoid): current JumpPower/JumpHeight, jump state, FloorMaterial, and death lifecycle.
