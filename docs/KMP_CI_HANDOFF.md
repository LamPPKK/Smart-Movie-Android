# Compose Multiplatform CI handoff

Status captured on 18 August 2026 after GitHub Actions run [32047761144](https://github.com/LamPPKK/Android.Smart.Movie/actions/runs/32047761144).

## Confirmed state

The release train and catalog contract checks pass. The Ubuntu shared job also passes desktop tests, desktop compilation, JavaScript distribution, Wasm distribution, and web artifact upload. The remaining failure is isolated to `:composeApp:createDistributable` in the macOS, Windows, and Ubuntu portable-image matrix.

All three packaging jobs reject these unlisted artifacts under strict Gradle dependency verification:

- `org.jetbrains.compose:gradle-plugin-internal-jdk-version-probe:1.11.1` JAR;
- `org.jetbrains.compose:gradle-plugin-internal-jdk-version-probe:1.11.1` module metadata.

The `macos-15` runner is arm64 and additionally rejects `org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:1.11.1` POM metadata. The reported configuration-cache serialization error is a consequence of the rejected `jdkVersionProbeJar` file collection, not an independent application error.

## Files inspected

- `multiplatform/gradle/verification-metadata.xml`
- `.github/workflows/multiplatform-ci.yml`
- `.github/workflows/release-multiplatform.yml`
- `multiplatform/composeApp/build.gradle.kts`

## Completed repair attempts

1. Commit `bbb8c95` added verified Linux checksums for the Compose desktop POM and Node.js 24 runtime. The next run progressed to the Wasm Node.js 25 runtime.
2. Commit `44be68d` added the Node.js 25 Linux checksum from the official `SHASUMS256.txt`. The next run progressed to Binaryen.
3. Commit `1c11b74` added the official Binaryen Linux checksum and Maven Central checksums for Linux/Windows Compose Desktop and Skiko runtimes. This made the complete shared desktop/JS/Wasm job pass, then exposed the packaging-only JDK probe and macOS arm64 metadata above.

No dependency verification was disabled or relaxed. Every committed checksum was compared with its publisher's checksum before being added.

## Recommended next diagnostic branch

Generate verification metadata for `:composeApp:createDistributable` on each matrix OS rather than adding the next reported artifact one at a time:

```bash
./gradlew --write-verification-metadata sha256 :composeApp:createDistributable
```

Collect the metadata diff from macOS arm64, Windows x64, and Ubuntu x64, merge only the platform-specific artifacts actually resolved by those jobs, and compare each value with Maven Central before committing. Then rerun the full Compose Multiplatform CI matrix once. This should also reveal whether the macOS arm64 path needs a matching Skiko runtime after the platform POM is accepted.

## Separate external blocker

The Apple repository's contract-sync workflow requires the repository secret `ANDROID_CONTRACT_SYNC_TOKEN`. The Android snapshot already pins canonical commit `e92cda7049eacc351bb3eedf6d30dcd95e0817d6`, but future automated contract updates will remain red until a least-privilege token with Contents and Pull requests access to `LamPPKK/Android.Smart.Movie` is configured.
