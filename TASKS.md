# Tasks

The single status tracker for this project.

- Phase scope and acceptance criteria are in [docs/PLAN.md](docs/PLAN.md); durable decisions are in [docs/agent/DECISIONS.md](docs/agent/DECISIONS.md).
- Update this file in the same change that completes, adds, or blocks a task.
- Move finished items to **Done** with the date, and prune **Done** once a phase closes.

**Current phase:** 2, hardware bring-up. The printer arrived on 2026-10-30.

## Maintainer

- [ ] Obtain a satin ribbon tape. Only white adhesive tape is on hand, so plan step 6 (ribbon) is blocked until one arrives.

## Phase 2 Hardware Run

These follow the [Hardware Validation Plan](docs/RESEARCH.md#hardware-validation-plan). The maintainer authorized printer interaction on 2026-10-30, limited to the command allowlist.

- [ ] Record the unit's color and power type in [the capture notes](docs/hardware/2026-10-30-print-master-capture.md#unit); the name, firmware and serial prefix are recorded.
- [ ] Document a minimum power supply for users: a 15 W USB-C power bank prints full-width rows, and the first charger (rating unknown) did not (see [first prints](docs/hardware/2026-10-30-first-prints.md)).
- [ ] Plan steps 1–5, 7 and 8 with the bring-up app on white adhesive tape; record results, update RESEARCH entries to VERIFIED, and update `P12Profile` defaults from measurements.
  - Done: SPP connection and status; T1–T3, text and QR; the length factor (0.994); eight consecutive jobs; a 300 mm single job. See [first prints](docs/hardware/2026-10-30-first-prints.md).
  - Remaining: the printable window to the dot (T1 wedge lengths); density comparison (step 7); BLE (step 5); faults (step 8).
- [ ] The 1 m single job (plan step 4), deferred to the ribbon run.
- [ ] Plan step 6 (ribbon), once satin ribbon tape is on hand.

## Later

- [ ] Phases 3–6: editor and persistence, ribbon workflows, release hardening, feature expansion. See [docs/PLAN.md](docs/PLAN.md#phases).
- [ ] Choose the public name and branding before Phase 5 store assets.
- [ ] Accept or revise the proposed defaults in [docs/PLAN.md](docs/PLAN.md#proposed-defaults) before Phase 3.
- [ ] Before the first public release: generate the app signing key and back it up offline (see DECISIONS).

## Done

- 2026-10-30:
  - Print Master baseline capture (plan step 0): SPP transport, density `1F 11 02 n`, job framing, the `1A 0F 0C` completion reply, and the unit's firmware (0.1.9). The encoder, allowlist and bring-up app now follow Print Master's framing. See [the capture](docs/hardware/2026-10-30-print-master-capture.md).
  - Maintainer approved the Phase 2 bring-up app and printer interaction within the command allowlist.
  - **Phase 2 bring-up app:** Android `app` module (AGP 9.5, Compose, `minSdk` 26, `targetSdk` 36, no `INTERNET` permission) with SPP and BLE transports, device discovery and SDP lookup, status queries, T1–T3, text and QR samples, repeat and long jobs, a raster preview, and a timed, redacted log with export. Exercised on the API 37 emulator against `SimulatedP12`: every job round-tripped to the sent raster with no protocol errors, including the 1 m ruler. Not yet run on real hardware.
- 2026-10-22:
  - Repository set up with agent-profile, GPLv3 license, research, product scope, and phased plan.
  - Maintainer decisions recorded: GPLv3 (kept as GPL-3.0-only), application ID, and distribution channels with one signing key.
  - GitHub remote confirmed reachable (empty repository).
  - P12 purchased (Amazon B0FRMC2PHR); awaiting delivery.
  - Android Studio 2026.2 installed with JBR 25. Android SDK installed (platform android-37, build-tools 36.0.0).
  - Pixel 9 Pro designated as the test phone.
  - Initial repository setup committed and pushed to `origin/main`.
  - **Phase 1 complete:**
    - Gradle 9.8.2 wrapper (checksum-pinned).
    - Kotlin 2.4.21 `printer` module: raster, profile, job encoder, status decoder, command allowlist, `runJob`, `SimulatedP12`, and T1–T3 patterns.
    - 44 unit tests and ktlint pass via `./gradlew check`. No hardware involved.
    - See the [Phase 1 brief](docs/PLAN.md#phase-1-brief).
