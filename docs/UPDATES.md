# Updates

## Current release

Desktop **1.32.0**, game core **1.32.0**, Minecraft Java **26.3**, Fabric Loader **0.19.5**, Java **25**.
Desktop app and game core are separate components. Updating the app alone does
not update code already running inside Minecraft.

## Easy update

1. Download the new archive for your OS and read its changelog.
2. Close Minecraft, Launcher and Tung Tung Client.
3. Mac: replace the app in Applications. Windows: extract the complete new
   folder and start its EXE, preserving your previous folder as a rollback copy.
4. Open Control center, verify the Minecraft folder and install the Fabric core.
5. Launch the Tung Tung profile, join a world and verify the app/core versions.
6. Verify Client Maps and Map in Slot are available under Texture Packs → Map Art.

Do not combine several versions of the core in one mods folder. Keep worlds,
configuration and `.client_maps` data when updating. Never share account files.

## Future plans — not implemented yet

- A release feed with the latest version and download links per OS.
- An in-app comparison of installed desktop and game-core versions.
- A clear update notification and download button.
- A guided restart/install workflow with rollback information.
- Windows runtime validation for each release.

This release uses manual updates. There is no working automatic updater or
published update endpoint yet. Future features have no promised date.

## Maintainer release checklist

- Build core and adapted Map in Slot with `./gradlew build` using Java 25.
- Build Mac and Windows packages with their respective build scripts.
- Include licenses, installation instructions, changelog and SHA256SUMS.
- Verify hashes, ZIP CRCs and mod versions in every package.
- Check Mac native self-test and both architectures; test Windows on Windows.
- Publish binaries as release assets, source as a separate archive; never add
  account data, worlds, logs, pairing descriptors or local token files.
- Publish version metadata only once the release assets actually exist.

Simple Voice Chat: **2.6.27+26.3**. Профиль игры — **Tung Tung Client 26.3**. Диагностический Voice Test в пакет не входит. Обновление мода проверено при запуске на Mac; проблема голосового подключения к DonutSMP пока не подтверждена как исправленная.
