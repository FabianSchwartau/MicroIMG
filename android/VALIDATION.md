# Android validation

Version 1.1.0 was validated on 2026-10-27. The application targets API 36 and supports API 26 (Android 8.0) and later. Runtime testing used an Android 11 / API 30 x86_64 emulator.

## Completed checks

- Release, debug and instrumentation APKs built successfully with AGP 8.13.2 and Gradle 8.13.
- Android `lintRelease`: no errors; two advisory warnings concerning the pinned Gradle version and optional Android 12 backup rules.
- Ten codec test groups passed: binary compatibility with the upstream desktop packer at grayscale levels 2 through 16, independent zlib fixtures, G4 runs and Deep-PX hexadecimal values, NDEF capacity boundaries, unordered/duplicate/missing/conflicting chunks, malformed input and bounded decompression, black image rendering, and saved ZIP/G4/DP file import without re-encoding.
- Thirty-seven Android NDEF/UI checks passed: record serialization and parsing, UTF-16 and MIME text, invalid records, compression/reconstruction pixel equality, all three formats, rotation, small tags, partial collections, write confirmation, editor locking and cancellation, lossless import through capacity changes and write preparation, failed import preserving the prior image, asynchronous file import, restored raster editing, and persistent light/dark themes.
- Actual Android document-picker import of a saved MicroIMG TXT file succeeded. The saved 128 × 96 image retained its resolution with the editor previously set to 64 pixels.
- Raster import and PNG export through Android's document picker were exercised during version 1.0.0 validation. The exported 64 × 48 PNG was checked for valid dimensions and chunk CRCs.
- Release 1.1.0 installed as an update over release 1.0.0 using the same local signing identity and a higher version code. The final release APK also installed and launched successfully.
- The updated interface was visually inspected. `apksigner` verified the release signature, and `aapt` confirmed the application ID, version, supported Android versions and NFC-only permission.
- JavaScript and build script syntax checks passed.

## Hardware testing still required

No real NFC-enabled Android phone or physical tags were available. NFC RF transfers, tag formatting, antenna positioning, OEM NFC behavior and interruption during a physical write have not been tested. Emulator checks cover NDEF records and write preparation, not RF communication.

The upstream `Pn/N#` format has no image identifier. Tags from different images with the same chunk count cannot always be distinguished; reset the read collection before scanning another image.

See [README.md](README.md#tests) for commands to repeat the automated checks. Test results describe the validated environment and do not establish compatibility with every Android device or tag family.
