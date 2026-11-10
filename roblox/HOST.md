# Runtime host contract (protocol 1)

A compatible host is a prerequisite. This document is an integration contract, not instructions for installing an injector or obtaining code execution in a third-party game. A stock Roblox client cannot load `TungTungRobloxRuntime.luau` from the desktop app or issue desktop localhost HTTP requests through a normal LocalScript. Private-server access does not change that.

## Runtime API

```luau
local Runtime = require(pathToApprovedModuleScript)
local runtime = Runtime.new({
    transport = transportAdapter, -- Optional; supplied by the compatible host.
    serverAdapter = nil,         -- Optional; only a trusted game-side API may supply this.
})
runtime:start()
runtime:dispatch({command="setModule", moduleId="bloom", enabled=true, requestId="example-1"})
runtime:configure({flySpeed=48, walkSpeed=32, freecamSpeed=36, fov=90, shaderPreset="warm"})
local state = runtime:snapshot()
runtime:destroy() -- Required on host unload/reload/disconnection.
```

Only one runtime should be active per player. Destroy the old runtime before creating a replacement. The host must destroy the runtime if its native pairing disconnects or is replaced; otherwise active effects can remain local until the runtime unloads or the player respawns. The runtime itself does not read pairing credentials or make HTTP requests.

## Injected transport

Implement these two ordinary callbacks:

```luau
local transportAdapter = {}
function transportAdapter:subscribe(handler)
    -- Route already-authenticated native commands to handler(command).
    -- The handler returns an action/toggle receipt.
    -- Return an unsubscribe function; the runtime calls it on destroy.
    return function() end
end
function transportAdapter:publish(snapshot, receipt)
    -- Deliver observed state, and an optional command receipt, to the native host.
    -- Never mark the game connected before the real runtime starts publishing.
end
```

Both callbacks must return promptly without yielding from the render loop: enqueue authenticated network work in the compatible host. When the final published snapshot has `connected:false`, send native `disconnect` rather than treating it as a fresh live `state`; native liveness is session/heartbeat-based. Do not reconnect automatically after the runtime has been destroyed.

The native companion's authenticated loopback service provides its own connect, poll, state, and acknowledgement endpoints. The compatible host—not this runtime—must implement that transport, hold its pairing credentials privately, and obey the native service's session and queue rules. Do not put native tokens in ReplicatedStorage, replicated attributes, chat, logs, URLs, or shared game data.

### Desktop loopback protocol

The running Mac app writes its private descriptor at:

```text
~/Library/Application Support/TungTungClient/Roblox/connection.json
```

Its directory has mode `0700`, and the descriptor has mode `0600`. It contains `protocolVersion:1`, `endpoint:"http://127.0.0.1:<ephemeral-port>/v1/"`, `key`, `processId`, and `requiresCompatibleRuntime:true`. The key changes when the native transport restarts; the descriptor is removed on a normal shutdown. A compatible local host reads this file through its own already-authorized filesystem/network adapter. Ordinary Roblox LocalScripts have no such disk access.

The Windows app uses the same protocol and descriptor fields at
`%LOCALAPPDATA%\TungTungClient\Roblox\connection.json`. Its private directory
uses the current Windows user's ACL rather than Unix permission modes. Use the
app's **Open Pairing Folder** button to find the active platform's descriptor.
The Windows ACL implementation has been compiled; it still needs a Windows
machine for a live permissions and WebView2 check.

Every request uses **HTTP/1.1 POST**, `Content-Type: application/json`, a correct `Content-Length`, and `X-Tung-Roblox-Key: <descriptor key>`. Do not send an `Origin` header: the native endpoint rejects browser-origin requests. The server also rejects duplicate headers, chunked transfer encoding, unexpected trailing data, headers above 8 KiB, and bodies outside 2–65,536 bytes. Responses are JSON with `Connection: close`; use a new request connection. A peer must send its full request within four seconds. Do not print the key or the complete descriptor.

| Route appended to descriptor endpoint | Request body | Successful response |
|---|---|---|
| `connect` | `{"protocolVersion":1,"snapshot":snapshot}` | `{"ok":true,"sessionId":"…","heartbeatSeconds":1,"expiresAfterSeconds":5}` |
| `poll` | `{"sessionId":"…"}` | `{"ok":true,"commands":[…]}` |
| `state` | `{"sessionId":"…","snapshot":snapshot}` | `{"ok":true}` |
| `ack` | `{"sessionId":"…","requestId":"…","ok":true,"snapshot":snapshot}` | `{"ok":true}` |
| `disconnect` | `{"sessionId":"…"}` | `{"ok":true}` |

For a failed command, send `ok:false` and `error:"brief explanation"` on `ack`, together with a current full snapshot. Native authentication failures return 403; protocol/session/validation failures return 400 with an error string. A successful `connect` replaces the previous session and clears its queue. Stop and destroy the old runtime on replacement.

Poll approximately once per second. Any valid poll/state/ack refreshes the heartbeat; five seconds without one expires the session. An unacknowledged command times out after seven seconds, and at most 32 native commands may be pending. Poll removes commands from the delivery queue. Dispatch each `requestId` at most once; never blindly repeat one-shot impulses after an uncertain network acknowledgement. A lost session must reconnect and establish fresh state.

`state` and `ack` require a full snapshot whose `revision` is **strictly greater** than the last accepted snapshot. Serialize these writes in revision order and coalesce obsolete heartbeat snapshots. A receipt-bearing `publish` should become one `ack`, not both an independent `state` and a duplicate acknowledgement. Each pending request must be acknowledged exactly once. Settings receipts use the queued command's request ID retained by the adapter.

Native `verified:true` means the local runtime session passed the authenticated protocol. It does **not** mean that a game's server accepted movement, that this is a Roblox-official client, or that a third-party game is compatible. Native state deliberately retains `connectionScope:"local-runtime"` and `serverConfirmed:false`.

The host must route only commands from the currently paired session. Do not evaluate arbitrary strings, load remote code, or forward arbitrary Roblox instance/property writes. Use the runtime's fixed catalog and validator.

## Commands

```json
{"command":"setModule","moduleId":"fly","enabled":true,"requestId":"r1"}
{"command":"setModule","moduleId":"dash","enabled":true,"requestId":"r2"}
{"command":"playerAction","action":"spectate","targetUserId":123456,"requestId":"r3"}
{"command":"playerAction","action":"friend_fling","targetUserId":123456,"requestId":"r4"}
{"command":"setSettings","settings":{"flySpeed":48,"walkSpeed":32,"freecamSpeed":36,"fov":90,"shaderPreset":"warm"}}
```

Canonical `{id, enabled, value, targetUserId, requestId}` commands are also accepted. `value` applies a module-specific numeric parameter. All numeric settings must be finite and are clamped to the catalog ranges. Unknown module IDs, invalid toggle types, missing player targets, NaN, and infinity are rejected. `configure()` stages settings atomically, so an invalid preset does not partially change the other settings.

Actions (`dash`, `self_fling`, `slide`, `respawn`, `return_spawn`, `friend_fling`) are one-shot commands: their receipts and snapshots have `enabled:false`. Repeating an action is subject to its cooldown. Toggles stay enabled until disabled, removed by lifecycle cleanup, or mutually excluded by another camera mode.

Example receipt:

```json
{"requestId":"r1","id":"fly","ok":true,"enabled":true,"status":"Applied locally · server may correct","appliedLocally":true,"serverConfirmed":false}
```

To acknowledge a native queued command, the host uses the native request's `sessionId` and `requestId`, the returned `ok` value, `runtime:snapshot()`, and its `status` as the error text on failure. Settings receipts do not themselves echo a request ID; the adapter retains it from the queued command.

## Snapshots

`snapshot()` increments a monotonic `revision` for the lifetime of this runtime and returns:

```json
{
  "protocol":1,
  "runtimeVersion":"1.0.0",
  "revision":12,
  "connected":true,
  "hostRequired":true,
  "serverConfirmed":false,
  "player":{"userId":123456,"name":"PlayerName"},
  "characterReady":true,
  "players":[{"userId":123456,"name":"PlayerName","displayName":"Player","isLocal":true,"flingAllowed":false}],
  "modules":[{"id":"fly","enabled":true,"available":true,"scope":"local","effectScope":"self","status":"Applied locally · server may correct","serverConfirmed":false}],
  "settings":{"flySpeed":48,"walkSpeed":32,"freecamSpeed":36,"fov":90,"shaderPreset":"warm"},
  "observed":{"walkSpeed":16,"anchored":false,"health":100}
}
```

The real modules array always contains all 24 entries; the shortened example above is illustrative. Native `scope` is `local`, `server`, or `unavailable`. `effectScope` provides extra runtime detail (`self`, `client`, or `server-required`). `available` means this runtime has an implementation and its required adapter, not that the server guarantees the effect. `connected` means the runtime has started inside a compatible host. It says nothing about connection to a public Roblox service or universal game compatibility. The native parser accepts up to 200 unique players with real positive numeric UserIds; the runtime omits Studio's synthetic negative UserIds and truncates its player list at 200.

## Optional authorized game adapter

`serverAdapter:request(command)` is reserved for a trusted game-side implementation. This package does not install one in a third-party game. It must validate the acting player's permissions, target identity, distance, rate limits, and explicit per-session target opt-in **on the game server before performing Fling Friends**. Client-reported consent is not authoritative.

Return `{serverConfirmed=true, targetConsented=true}` only after that server confirms the requested action. Respawn requires `{serverConfirmed=true}`. Without a trusted adapter both cards remain unavailable. The client runtime never applies an impulse to another player's character, and it never treats a native toggle as proof of that player's consent.

For Fling Friends the trusted adapter must additionally expose a **read-only synchronous cache** method `getConsent(targetUserId)`. It returns `{serverConfirmed=true,targetUserId=targetUserId,consented=true}` only while the trusted game's server confirms the target's current explicit per-session opt-in. Missing, failed, mismatched, or unconfirmed receipts produce `players[].flingAllowed:false` and prevent the runtime from requesting the action. This method must not make a yielding HTTP request from the render loop. The server must still recheck consent when applying the action; a cached UI flag cannot authorize it on its own.
