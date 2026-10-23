# Tasks

The single status tracker for this project.

- Phase scope and acceptance criteria are in [docs/PLAN.md](docs/PLAN.md); durable decisions are in [docs/agent/DECISIONS.md](docs/agent/DECISIONS.md).
- Update this file in the same change that completes, adds, or blocks a task.
- Move finished items to **Done** with the date, and prune **Done** once a phase closes.

**Current phase:** 1 is complete. Phase 2, hardware bring-up, starts when the printer arrives; its app code can be written before then.

## Maintainer

- [ ] Confirm which tapes came with or were ordered alongside the P12. The experiment needs at least one satin ribbon tape and one white adhesive tape.
- [ ] Approve writing the Phase 2 bring-up app before the printer arrives (see below).

## Ready for an Agent

- [ ] **Phase 2 bring-up app, against the simulator.** This needs maintainer approval.
  - Add an Android `app` module (Compose, `minSdk` 26, `targetSdk` 36, application ID `io.github.princeendo.labelprinter`).
  - Add SPP and BLE `Transport` implementations and a single debug screen:
    - pick a bonded or scanned device;
    - connect;
    - send status queries;
    - print T1–T3 plus text and QR samples;
    - show and export a log.
  - Exercise everything against `SimulatedP12` on the emulator.
  - Do not connect to real hardware until the printer is authorized.

## Waiting on Hardware

These follow the [Hardware Validation Plan](docs/RESEARCH.md#hardware-validation-plan).

- [ ] On arrival, record the unit's details in `docs/hardware/`: model label, color, power type, firmware (`1F1107`), and serial prefix. Keep the full serial number and MAC address out of Git.
- [ ] Print Master baseline capture on the Pixel 9 Pro: install Print Master and walk through the capture procedure together (plan step 0).
- [ ] Phase 2 hardware run: plan steps 1–8 with the bring-up app; record results, update RESEARCH entries to VERIFIED, and update `P12Profile` defaults from measurements.

## Later

- [ ] Phases 3–6: editor and persistence, ribbon workflows, release hardening, feature expansion. See [docs/PLAN.md](docs/PLAN.md#phases).
- [ ] Choose the public name and branding before Phase 5 store assets.
- [ ] Accept or revise the proposed defaults in [docs/PLAN.md](docs/PLAN.md#proposed-defaults) before Phase 3.
- [ ] Before the first public release: generate the app signing key and back it up offline (see DECISIONS).

## Done

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
