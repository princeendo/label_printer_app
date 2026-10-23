# Project Decisions

This document records significant architectural and project decisions. It is authoritative for decisions that remain current. Do not record trivial implementation choices.

## Entry Template

### YYYY-MM-DD: Decision title

- **Decision:** What was decided.
- **Context:** The problem, constraint, or trigger.
- **Rationale:** Why this choice best fit the available evidence.
- **Consequences:** Important tradeoffs, follow-up work, or constraints.
- **Alternatives considered:** Material alternatives and why they were not selected.

## Decisions

Proposed architecture and product defaults that are not yet accepted live in `docs/PLAN.md` under "Proposed defaults"; they move here only when the maintainer confirms them.

### 2026-10-22: GPLv3 licensing and a free official app

- **Decision:** The project is open source under the GNU GPL version 3, and the official application is free. The repository records this as `GPL-3.0-only` (SPDX) with the unmodified FSF license text in `LICENSE`.
- **Context:** The maintainer chose GPLv3 and a free official app at project start. "GPLv3" does not by itself say whether later GPL versions are allowed.
- **Rationale:** `-only` is the narrower reading of the maintainer's choice and can be widened to `-or-later` by the copyright holder at any time; narrowing later would need every contributor's agreement.
- **Consequences:** Reused third-party code must be GPLv3-compatible and keep its notices. The maintainer reconfirmed GPLv3 on 2026-10-22, and `-only` stays as the recommended reading; the copyright holder can still widen it to `-or-later`. Commercial redistribution by others is permitted by the license and is not a concern to revisit.
- **Alternatives considered:** `GPL-3.0-or-later` (pending confirmation).

### 2026-10-22: Phomemo P12 is the first and only initial printer

- **Decision:** The first supported hardware is the Phomemo P12. Other printers follow only after P12 printing is validated on real hardware.
- **Context:** The maintainer's primary use is gift-wrapping ribbon printed on a P12-class device. No printer has been purchased or tested yet.
- **Rationale:** One validated driver is a better foundation than speculative multi-printer support.
- **Consequences:** The printer boundary stays small and concrete (see `docs/PLAN.md`); no plugin system until a second printer exists. All P12 protocol facts remain unverified until the hardware experiment in `docs/RESEARCH.md` passes.
- **Alternatives considered:** Starting with multi-printer abstractions or a different model; rejected as speculative.

### 2026-10-22: Product scope centered on ribbons, with adhesive labels in scope

- **Decision:** Gift-wrapping ribbons are the primary use case. Personalized bows, event favors, repeating ribbon messages, and general adhesive labels are within intended scope. The long-term ambition is functional coverage comparable to Phomemo's Print Master app.
- **Context:** Maintainer-stated goals.
- **Rationale:** Ribbon workflows drive the first release; adhesive labels share the same rendering and printing path.
- **Consequences:** Feature parity means comparable capabilities only: the interface, assets, fonts, and branding are independently designed or appropriately licensed, never copied.
- **Alternatives considered:** None material.

### 2026-10-22: Research and collaborative design precede implementation

- **Decision:** Protocol research, product design, and a minimal hardware experiment come before any Android application scaffold.
- **Context:** P12 compatibility is unconfirmed and no hardware is available yet.
- **Rationale:** The riskiest unknown is whether an independent app can drive the printer reliably; an editor built before that is answered may be wasted.
- **Consequences:** `docs/PLAN.md` gates each phase on the previous one's acceptance criteria. Public branding remains unresolved; `label_printer_app` is a working name.
- **Alternatives considered:** Building the editor first with a simulated printer; deferred to after the hardware experiment.

### 2026-10-22: Application ID and code namespace

- **Decision:** Use `io.github.princeendo.labelprinter` as both the Kotlin package namespace and the Android application ID.
- **Context:** Code needs a namespace before Phase 1, and Play and developer verification bind an application ID permanently once published.
- **Rationale:** It is tied to the maintainer's GitHub account, which the project controls, and it does not depend on the undecided public name.
- **Consequences:** The public name can change freely, but the application ID cannot change after the first Play upload. If public branding needs a different ID, change it before that upload.
- **Alternatives considered:** A brand-based reverse domain; deferred because no brand or domain exists.

### 2026-10-22: Distribution channels and signing

- **Decision:** Distribute through Google Play, GitHub Releases, and F-Droid, all signed with one app signing key that the maintainer generates and holds. Play App Signing is used with that key supplied, not a Google-generated one.
- **Context:** Android refuses updates across signatures. Developer verification (global in 2027) registers each signing-key fingerprint.
- **Rationale:** One key lets users update across sources, makes reproducible F-Droid builds publishable under the developer signature, and needs only one fingerprint registered.
- **Consequences:**
  - The key must be generated and backed up offline before the first public release. Losing it blocks updates and package registration.
  - The key must be supplied to Play before the first open-test or production release.
  - F-Droid inclusion requires a FLOSS-only build: no Google Play Services or Firebase.
- **Alternatives considered:** A Google-generated Play key, with separate keys elsewhere; rejected because users could not move between sources.
