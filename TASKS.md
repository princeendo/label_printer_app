# Tasks

The single status tracker for this project.

- Phase scope and acceptance criteria are in [docs/PLAN.md](docs/PLAN.md); durable decisions are in [docs/agent/DECISIONS.md](docs/agent/DECISIONS.md).
- Update this file in the same change that completes, adds, or blocks a task.
- Move finished items to **Done** with the date, and prune **Done** once a phase closes.

**Current phase:** 0, research closure. Phase 1 can start once the toolchain is ready and approved.

## Maintainer

- [ ] Finish Android Studio first-run setup so the Android SDK is downloaded (default location `~/Library/Android/sdk`).
- [ ] Confirm which tapes came with or were ordered alongside the P12. The experiment needs at least one satin ribbon tape and one white adhesive tape.
- [ ] Approve Phase 1, including adding a Gradle wrapper to the repository.

## Ready for an Agent

- [ ] **Phase 1: P12 protocol core without hardware.** Follow the [Phase 1 brief](docs/PLAN.md#next-task-brief-phase-1). Blocked only by the maintainer approval above.

## Waiting on Hardware

These follow the [Hardware Validation Plan](docs/RESEARCH.md#hardware-validation-plan).

- [ ] On arrival, record the unit's details in `docs/hardware/`: model label, color, power type, firmware (`1F1107`), and serial prefix. Keep the full serial number and MAC address out of Git.
- [ ] Print Master baseline capture on the Pixel 9 Pro: install Print Master and walk through the capture procedure together (plan step 0).
- [ ] Phase 2: build the bring-up screen, run plan steps 1–8, and record the results; update RESEARCH entries to VERIFIED.

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
  - Android Studio 2026.2 installed with JBR 25.
  - Pixel 9 Pro designated as the test phone.
  - Initial repository setup committed and pushed to `origin/main`.
