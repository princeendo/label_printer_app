# Repository Instructions

<!-- BEGIN AGENT PROFILE BOOTSTRAP -->
<!-- SOURCE SHA256: d5a06d5c885fcd56aa5e3fed2e790cbb82b0e67ccdbb8f47e2c470763e7c8825 -->
## Agent Profile

Before substantial planning or implementation:

1. Read `README.md`.
2. Read `docs/agent/PREFERENCES.md`.
3. Read relevant architecture or subsystem documentation.
4. Read `docs/agent/DECISIONS.md` before reconsidering established design choices.
5. Consult `docs/agent/LEARNINGS.md` when useful, but treat it as non-authoritative.

Small and obvious tasks do not require exhaustive repository reading. Explicit current-task instructions override standing preferences. Repository requirements, contracts, documented decisions, and repository-specific preferences override global defaults.

If documented preferences reasonably answer an ordinary engineering question, use them instead of asking again. Ask about ambiguity that is materially consequential, destructive, expensive to reverse, architecturally significant, or genuinely unresolved.

### Preference Learning

- Apply the maintainer's answer to the current task first.
- If a clarification is likely to recur, check whether it is already documented.
- Record an apparently reusable but unconfirmed preference in `docs/agent/LEARNINGS.md` with scope, confidence, context, and likely future value.
- Do not promote weak or one-off evidence into authoritative preferences.
- Record significant project decisions in `docs/agent/DECISIONS.md`, not in the learnings queue.
- Promote repeated or explicitly confirmed repository preferences into the local section of `docs/agent/PREFERENCES.md`.
- Propose cross-repository preferences for conservative review in the canonical agent-profile repository; do not silently mutate global preferences.

When the `agent-profile` command is available, `agent-profile check .` is safe during read-only work. Run `agent-profile sync .` only when repository edits are authorized. The committed snapshot remains authoritative for this repository when the command is unavailable.
<!-- END AGENT PROFILE BOOTSTRAP -->

## Project

A free, GPL-3.0-only Android app for printing ribbons and labels on the Phomemo P12. Read `TASKS.md` for the current status and next task, and `docs/PLAN.md` for phase scope and acceptance criteria. Read `docs/RESEARCH.md` before touching printer code.

- `TASKS.md` is the only status tracker. Update it in the same change that completes, adds, or blocks a task; do not repeat status in other documents.

- Work only within the phase the maintainer has approved. Do not add an Android scaffold, architecture layers, or placeholder tests ahead of that phase.
- Confirmed decisions live in `docs/agent/DECISIONS.md`. Proposals in `docs/PLAN.md` are not decisions until confirmed.
- When hardware results confirm or change a printer fact, update `docs/RESEARCH.md` and mark it VERIFIED with the date and firmware.
- Never send printer commands outside the documented allowlist; the "Never Send" list is in `docs/RESEARCH.md`.
- New source files carry `SPDX-License-Identifier: GPL-3.0-only`.
- Keep HCI snoop logs, device serials, MAC addresses, and signing material out of Git.
- Once the Gradle build exists, validate with its wrapper; documentation-only changes need only `git diff --check` and `agent-profile check .`.
