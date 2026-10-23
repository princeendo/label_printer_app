# Implementation Plan

This page holds the phases and their acceptance criteria. Current status and open actions are tracked in [TASKS.md](../TASKS.md). Each phase starts only after the previous phase meets its acceptance criteria and the maintainer approves it.

- Confirmed decisions: [DECISIONS.md](agent/DECISIONS.md).
- Evidence: [RESEARCH.md](RESEARCH.md).
- Scope and feature matrix: [PRODUCT.md](PRODUCT.md).

## Proposed Defaults

These are recommendations. None of them is accepted until the maintainer confirms it; accepted defaults move to `DECISIONS.md`.

| Proposal | Assessment |
|---|---|
| Offline-first, no account | **Recommend.** Nothing in the protocol needs a network. Shipping without the `INTERNET` permission makes the privacy claim verifiable, keeps the Data safety form at "no data collected", and fits F-Droid. |
| No ads or subscription | **Recommend.** This follows from the free-app decision and is the clearest difference from Print Master. |
| Local designs with portable import, export and backup | **Recommend.** A versioned JSON document format, plus a zip archive when images are embedded, through Android's Storage Access Framework. Exact storage (plain files vs. a database) is decided in Phase 3. |
| Quick print alongside a visual editor | **Recommend.** Quick print is the shortest path to a useful ribbon. Both share one document model. |
| Native Kotlin with Jetpack Compose | **Recommend.** It is first-class for Android Bluetooth and Canvas text rendering. A cross-platform framework adds a bridge for Bluetooth and rendering with no current iOS need. |
| Small rendering/printer boundary | **Recommend, concretely:** the app renders a design to a `MonoRaster` (1-bit, 96 dots wide, N rows). A pure-Kotlin `printer` module turns a raster plus job options into a write sequence for a `Transport` and decodes status replies. No plugin registry until a second printer exists. |
| Simulated printer or preview mode | **Recommend.** A `SimulatedP12` transport decodes the exact bytes back into an image and emits plausible status replies. It serves unit tests, UI tests, and testers without hardware. |

Further proposals from the research:

- **Allowlist, not pass-through.** Send only allowlisted commands; never ESC 7, firmware-update or serial-write commands.
- **Transports.** SPP first, BLE second, unless the hardware experiment says otherwise. Use `CompanionDeviceManager` for pairing.
- **Android levels.** `minSdk` 26, `targetSdk` 36.
- **Open materials.** Open fonts (OFL) and icons (for example Material Symbols, Apache-2.0); [ZXing](https://github.com/zxing/zxing) core (Apache-2.0) for barcodes in R2. Re-check each license when it is adopted.
- **Rendering tests.** Compare 1-bit rasters against reviewed golden images. Choose the framework in Phase 3; Roborazzi on Robolectric and instrumented tests are both candidates.

## Architecture Sketch (Proposed)

```text
app (Android, Compose)                      printer (pure Kotlin/JVM)
  document model + editor UI                  MonoRaster
  Android Canvas renderer --MonoRaster-->     P12 profile (dots, offset, length factor)
  persistence, import/export                  P12 job encoder  -> write plan
  Bluetooth transports (SPP, BLE) <--------   Transport interface
  printer screen                              P12 status decoder, command allowlist
                                              SimulatedP12, test patterns
```

The document model stays in `app` until something else needs it.

## Testing Strategy

| Layer | What it covers | Hardware needed |
|---|---|---|
| JVM unit tests (`printer`) | Byte framing, bit packing and order, the offset/width window, row counts, chunking and pacing math, status decoding (including concatenated or duplicated replies and the inverted cover), the allowlist, simulated-printer round trip | No |
| JVM unit tests (`app` logic) | Ribbon layout math (repeats, spacing, end margins, total length), document serialization round trip and versioning | No |
| Rendering tests | Fixed documents rendered to 1-bit rasters and compared with reviewed golden images; font and threshold regressions | No |
| Emulator UI tests | Editor, quick print, library, import/export, and print-flow states using `SimulatedP12` | No (the emulator cannot reach a real printer) |
| Manual hardware checklist | The test patterns and media checks from RESEARCH, run on every transport or firmware change and before each release | Yes |

## Phases

### Phase 0: Research Closure

**Work:**

- Maintainer decisions (below).
- The purchase.
- Toolchain installation.
- Optionally, inspect the current Print Master app: what is gated, ribbon controls, mirror semantics, density range.

**Done when:**

- the printer, tapes and phone are available;
- a JDK and the Android SDK are installed;
- the remote repository is confirmed usable.

### Phase 1: Protocol Core Without Hardware (next task)

**Goal:** a tested pure-Kotlin `printer` module that produces the documented P12 byte stream and decodes status replies, plus a simulated printer. Brief below.

**Acceptance:**

- `./gradlew :printer:check` passes.
- Encoded test patterns match independently derived expected bytes.
- `SimulatedP12` round-trips every pattern back to the same raster.
- No Android scaffold exists yet beyond what the build needs.

### Phase 2: Hardware Bring-Up Experiment

**Goal:** answer the feasibility question on real hardware.

**Work:**

- Add a minimal `app` with one debug bring-up screen:
  - pair or select a device;
  - connect over SPP or BLE;
  - send queries;
  - print T1–T3 and long or repeated jobs;
  - show and export a log.
- Run the [Hardware Validation Plan](RESEARCH.md#hardware-validation-plan).

**Acceptance:**

- The plan's success criteria are met.
- Results are recorded in `docs/hardware/` and RESEARCH is updated to **VERIFIED** where measured.
- The printer profile defaults are updated from measurements.
- If a failure from "Failures That Change the Plan" occurs, stop and revise this plan with the maintainer.

**Requires:** maintainer authorization to interact with the printer.

### Phase 3: Document Model, Editor and Persistence

**Work:**

- Versioned document model.
- Android renderer producing a `MonoRaster`.
- Quick print and the editor: text, open fonts, icons, basic shapes and borders, basic image import, move/resize/rotate/layer.
- Faithful preview, showing the printable band, leader and trailer.
- Local library with starter templates.
- Export, import and backup.
- Printer screen: status, calibration, density if decoded.

**Acceptance:**

- Rendering golden tests and UI tests pass using `SimulatedP12`.
- A design survives export → reinstall → import unchanged.
- Printed output matches the preview within the calibration tolerance.

### Phase 4: Ribbon Workflows

**Work:**

- Ribbon length and repeat count.
- Motif and message repetition with spacing.
- End margins for bow tails.
- Event favors (N copies with spacing).
- Media presets: adhesive and satin, with density and mirror where needed.
- Job chaining if the hardware sets a length cap.

**Acceptance:**

- A 1 m repeating ribbon and a 10-favor batch print correctly on satin ribbon tape.
- Layout math is unit-tested.
- Seams between chained jobs are documented and visible in the preview.

### Phase 5: Release Hardening and Publication

**Work:**

- Error recovery: disconnect, cover open, tape out.
- Accessibility pass.
- Permissions flow on Android 8–16.
- Final public name. The application ID is already decided.
- Generate the app signing key and back it up offline; supply it to Play App Signing (see DECISIONS).
- Privacy policy page and in-app link.
- Data safety form.
- Register the package for developer verification.
- Play listing assets created independently.
- Closed test: at least 12 testers for 14 days on a personal account.
- F-Droid metadata.

**Acceptance:**

- A production access application is submitted.
- A signed release and its source are tagged.

**Requires:** maintainer approval for the Play registration fee, publishing, and any public announcement. Local APK development and testing never depend on Play.

### Phase 6: Feature Expansion

R2 and later, from the [PRODUCT.md](PRODUCT.md) feature matrix:

- QR codes and barcodes;
- dates and serial numbering;
- print history;
- grouping and locking;
- CSV batch and variable fields;
- tables;
- FLOSS-only OCR and voice input.

Additional printers follow once the P12 is validated; [vivier/phomemo-tools](https://github.com/vivier/phomemo-tools) (GPL-3.0) is a reference for other Phomemo models. Each feature ships with tests and a feature-matrix update.

## Next Task Brief: Phase 1

To be executed by a new agent session after the maintainer approves Phase 1 and the toolchain prerequisite is met.

**Prerequisites:**

- Android Studio's bundled JetBrains Runtime is available. As of 2026-10-22 it is Android Studio 2026.2 with JBR 25, at `/Applications/Android Studio.app/Contents/jbr/Contents/Home`.
- The maintainer has approved adding a Gradle wrapper to the repository.

Do not install system-wide tools without approval.

**Steps:**

1. **Create the build.** A Gradle (Kotlin DSL) build with a version catalog and committed wrapper, and a single `printer` module using the Kotlin/JVM plugin.
   - Use a Gradle version that supports the available JDK.
   - Set the JVM target explicitly.
   - Package: `io.github.princeendo.labelprinter.printer`. Use JUnit 5 or `kotlin.test`, plus ktlint or the Kotlin formatter the maintainer prefers (default: ktlint via Gradle plugin). Put `SPDX-License-Identifier: GPL-3.0-only` in source file headers.
2. **Write the types in `printer`.** All code is written independently from the facts in [RESEARCH.md](RESEARCH.md); copy no third-party code.
   - `MonoRaster`: width in dots, row count, packed MSB-first rows, 1 = burn.
   - `P12Profile`: 96 head dots and 12 bytes per line. Default printable offset 4 and width 88, from the third-party measurement; both are overridable. Length factor 1.0 by default.
   - `P12JobEncoder`: produces a write plan:
     1. the six query packets, each followed by a reply wait;
     2. `1B 40`;
     3. `1D 76 30 00 0C 00 <rows LE16>`;
     4. raster chunks with a configurable size;
     5. `1B 64 0D 1B 64 0D`;
     6. a computed post-job wait.

     It rejects rasters wider than 96 dots or longer than 65535 rows.
   - `P12StatusDecoder`: parses a byte stream into `1A`-tagged replies for the documented tags. Handles concatenated, partial, duplicated and unsolicited replies. Decodes `1A 05 99` as cover **open**.
   - `CommandAllowlist`: only documented commands may be sent.
   - `Transport` interface: suspend write, flow of incoming bytes, close.
   - `SimulatedP12` transport: parses the plan back into a `MonoRaster`, records jobs, and answers queries with configurable status.
   - `TestPatterns`:
     - T1: dot-index ticks across 96 dots;
     - T2: a 100 mm ruler at 203 dpi;
     - T3: solid block, checkerboard, hairlines.
3. **Write the tests.**
   - Byte-exact headers for several row counts, including the little-endian boundary at 256 rows.
   - Bit order and the offset window.
   - Chunk boundaries.
   - Decoder edge cases.
   - The allowlist refuses ESC 7, `1F 11 0F` and `1B 4E 08`.
   - Simulated round trip for every pattern.
   - Derive expected bytes by hand in the tests rather than from another project's output files.
4. **Update the docs.** Update `README.md` with build and test commands. Update RESEARCH or PLAN only if a finding changes.
5. **Validate.** Run `./gradlew :printer:check` and `git diff --check`. Report results without claiming any hardware behavior.

**Out of scope:** the Android app module, Bluetooth code, the UI, and any printer interaction.

## Open Decisions

The 2026-10-22 answers on license, application ID and distribution are recorded in [DECISIONS.md](agent/DECISIONS.md). The answers on hardware, toolchain, test phone and the Print Master capture are tracked as tasks in [TASKS.md](../TASKS.md). Still open:

- **Public name and branding.** Needed before Phase 5 store assets.
- **Proposed defaults.** The table above stays proposed until the maintainer accepts it, or until Phase 3 starts.
