# Chess.com Auto Next Game

macOS desktop 1.29.7. Open **Chess.com** in Tung Tung Client, choose a clock,
turn **Auto Next Game** on, then start the first game normally. After your own
game ends, a cancellable pause runs and one new matchmaking search starts.
Pause from the board badge or turn the desktop switch off. One board owns the
connection; **Use This Board** transfers it. Keep Tung Tung Client open.

Three board connections:

- **Your Chess Window**: the client's separate persistent Chess.com web window.
  Sign in normally in that window; Chrome's login is not copied.
- **Google Chrome**: load the packaged `chess-extension` folder with Chrome's
  **Developer mode → Load unpacked**, then press **Connect Chrome** in Tung.
  Pairing automatically attaches existing top-level Chess.com tabs. Chrome's
  installed Chess.com web app uses the same extension. No cookie, history,
  debugger or native messaging access is requested.
- **Installed Chess app on Mac** (`com.chess.iphone`, the iPhone app): press
  **Connect Chess App** and grant **Tung Tung Client** Accessibility access in
  macOS. This system permission allows UI control; the implementation targets
  only this Chess process and requires its window to be in front. It reads
  whitelisted time/result/control labels, and never selects a square or move.

Chrome's unpacked extension installation and macOS Accessibility grant require
explicit confirmation when performed by an agent. They are not enabled by the
desktop installer. Pairing uses a persistent random local key, loopback HTTP
only, and a pinned extension ID `kbaaahmkgknhojdafppilohndobfpdci`. No game,
account, position, move or credential data is sent to a remote service.

Popular web clocks include 1, 3, 5, 10 and 30 minutes, plus increments. Custom
web clocks support 1–180 minutes and 0–180 bonus seconds, if Chess.com offers
that control. The installed iPhone app supports its actual presets: 1, 1+1,
2+1, 3, 3+2, 5, 5+2, 5+5, 10, 10+5, 15+10, 20, 30 and 60. An unavailable
clock pauses with a message rather than starting a different time. Reviews,
spectating, subscription dialogs and unrelated pages pause automation. First
games, chess moves, rematches and subscription offers are never automated.

## Architecture and checks

`engine.js` contains the browser state machine and current DOM adapter. The MV3
extension and `native.js` WKWebView transport share it. Native Swift owns saved
settings and one-board leases (`ChessSession`), the local bridge (`ChessBridge`),
the owned board (`ChessWindow`), and a separate native AX plan/controller.
No Minecraft connection or running game is required.

Build with `.tools/build-mac-1.29.7.py`; audit with
`python3 scripts/audit-chess-desktop.py`. Run
`python3 scripts/install-chess-desktop.py --apply` only while Tung's desktop
is closed. This installer updates both existing desktop copies with rollback
backups, and never writes the Minecraft profile or core.

170 checks passed: browser lifecycle 49, DOM adapter routes/results 8, native settings/lease 25, real local
HTTP 13, native AX plan fixtures 27, MV3 boundaries 12, production UI fixtures
32, and current public guest lobby clock selection 4. Evidence is in
`artifacts/chess-auto-next/`. Live post-game matchmaking and actual native AX
actions have not yet been exercised. No live games were started for QA.

On this Mac the Chrome extension was installed from the app's bundled
`Contents/Resources/chess-extension` directory and paired successfully. A real
Chrome lobby heartbeat was acknowledged by Tung with zero queued games.
Native Accessibility is awaiting the user's administrator authentication;
see `artifacts/chess-auto-next/connection-report.json` for actual setup status.

Desktop version is **1.29.7**; bundled and installed Minecraft core stays at
the verified **1.29.5**. The separate **1.29.6 Auto Totem candidate** remains
untouched and awaits the previously deferred Minecraft gameplay checks.
