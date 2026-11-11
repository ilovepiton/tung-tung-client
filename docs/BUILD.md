# Building from source

## Game core

Install Java 25 and run `./gradlew build` (`gradlew.bat build` on Windows).
The build downloads Gradle/Fabric dependencies. The small map-art and optional
voice API compile inputs are provided under `.tools` in this snapshot.
The patched Map in Slot JAR is produced by `mapInSlotPatchedJar`.

## Desktop applications

Mac requires macOS, Python 3 and Xcode command-line tools. Windows packaging
currently uses Python 3, PowerShell, .NET Framework 4.8 reference assemblies,
WebView2 and Newtonsoft under `.tools/windows-build`. This SDK cache is not
included in Git. The complete runtime apps are available in Releases.

Packaging also uses the texture-library inputs and pinned Fabric/voice files
listed in `macos/build.py` and `windows/build.py`. The source release archive
contains the texture library; the Git repository excludes that generated asset
cache. Voice Chat Mac packaging expects the matching Prism archive in `dist`.
Provision these inputs before running the desktop build scripts. A fresh clone
is not a complete offline desktop build environment.

No launcher accounts, authentication tokens, saved worlds, user configuration
or connection descriptors should be added to commits or release assets.

For desktop 1.30.8, run `python3 scripts/package-voice-profile.py` before the Mac voice build. It updates the existing 1.30.3 Prism snapshot to the pinned voice mod; the baseline archive must exist under `dist/`.
