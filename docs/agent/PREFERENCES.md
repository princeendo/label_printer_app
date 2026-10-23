# Repository Agent Preferences

This file combines a synchronized snapshot of global defaults with authoritative repository-specific additions and overrides. Do not edit content inside the generated boundaries. Repository-specific content outside those boundaries overrides global defaults when they conflict.

## Global Defaults (Generated)

<!-- BEGIN GLOBAL AGENT PREFERENCES: GENERATED -->
<!-- SOURCE SHA256: 3b3433b9e44603ba96e8cc65cac916bbade1883ff4f88744660003cb919c848d -->
# Maintainer Development Preferences

These preferences are defaults for ordinary engineering decisions. They are not immutable rules and should not override facts established by the current task or repository.

Use this precedence order:

1. Explicit current-task instructions
2. Repository requirements, interfaces, contracts, and constraints
3. Existing documented architectural decisions
4. Repository-specific preferences
5. These global maintainer preferences
6. Reasonable engineering judgment
7. Ask the maintainer

If these sources provide a reasonable answer to an ordinary engineering question, use that answer instead of asking the maintainer. Ask when ambiguity is materially consequential, destructive, expensive to reverse, architecturally significant, or genuinely unresolved.

## Agent Behavior

- Inspect the repository and relevant runtime state before proposing implementation details. Do not substitute assumptions or generic conventions for evidence that is available locally.
- Ask clarification questions only when the answer would materially improve confidence, prevent meaningful rework, or resolve a consequential choice. Ask them together before the work starts, each with a recommended default. Resolve low-impact, reversible uncertainty with reasonable judgment.
- Once the up-front questions are answered, do not open another round. Choose the most defensible option, record the assumption where it belongs, build, and let the result be corrected. Stop again only for the destructive, expensive, or irreversible actions listed under Planning and Decisions.
- When presenting a plan, diagnosis, recommendation, or review, include a current confidence percentage and identify the important source of uncertainty.
- Treat planning as read-only. Do not edit files, install software, run mutating commands, or otherwise change system state until implementation is explicitly authorized.
- Once implementation is authorized, carry the agreed scope through implementation, relevant validation, documentation, and a clear completion report instead of stopping after routine intermediate steps.
- Communicate directly and concisely. Report material discoveries, tradeoffs, blockers, validation results, and anything left incomplete without narrating every routine action.
- Do not attach copyright, licensing, or safety caveats to ordinary work the maintainer has requested and is plainly entitled to do. Apply practical hygiene such as ignore rules and attribution without commentary, and do not repeat a caveat the maintainer has dismissed.
- Follow documented preferences rather than repeating a clarification that has already been answered. Current task instructions still override standing preferences.

## Planning and Decisions

- Ground plans in the actual repository structure, existing interfaces, current behavior, and available tooling.
- Match planning depth to risk and scope. Small obvious changes need only a short approach; cross-cutting, destructive, operational, or hard-to-reverse work needs explicit risks, validation, and rollback.
- When several approaches are valid, recommend one default that best fits the current repository and explain the material reason. Do not ask the maintainer to choose among equivalent low-impact options.
- Ask before destructive operations, expensive external calls, production changes, irreversible migrations, major architecture changes, or actions with unclear blast radius.
- Consult documented decisions before reopening established architecture. Record only significant, durable decisions; do not turn routine implementation details into decision records.

## Implementation Philosophy

- Prefer the smallest correct change that fully satisfies the request. Avoid opportunistic cleanup, unrelated refactors, speculative abstractions, and structure added only for possible future use.
- Keep code local until reuse or independent responsibility justifies extraction. Prefer fewer well-named components over layers that only redirect work.
- Preserve existing public behavior, persisted data, and external contracts unless the task requires a change. Do not add speculative backward-compatibility machinery when there is no concrete compatibility need.
- Follow established repository patterns unless they are directly responsible for the problem or conflict with stronger requirements.
- Make low-impact, reversible implementation choices autonomously and document any non-obvious judgment that future maintainers will need.
- Address root causes when they can be established without broadening the task. Do not hide failures with retries, fallbacks, or compatibility paths that obscure an unresolved defect.

## Dependencies and Environments

- Inspect the repository's declared environment and package-management conventions before running tools or changing dependencies.
- Add a dependency only when it materially improves correctness, maintainability, or implementation cost. Do not add a package merely to avoid a small amount of straightforward code.
- Keep dependency changes minimal, explicit, and recorded in the repository's normal manifest and lockfile. Avoid one-off installs that cannot be reproduced.
- Use the repository's existing language, runtime, formatter, test runner, and task tooling when practical rather than introducing parallel tooling.
- Keep optional or heavyweight dependencies separate when that reduces setup cost for users who do not need them.

## Testing and Validation

- Validate every meaningful change before reporting completion. Start with the narrowest relevant check and expand according to risk, shared impact, and repository requirements.
- For code changes, run the most specific useful tests plus applicable formatting, linting, type-checking, build, or syntax checks. For documentation-only changes, inspect the rendered structure or diff; do not run unrelated expensive suites.
- Test behavior and failure paths affected by the change, not only that files parse or commands start. Add tests for logic, data-protecting behavior such as a dry run writing nothing, and regressions of real defects. Do not add tests for trivial conditionals, string formatting, type coercion, or mock wiring; a fixture that needs many patches is testing wiring.
- Never claim a check passed when it was not run. State exactly what was verified and identify skipped or unavailable validation with the reason.
- Before handing over work the maintainer will run at real cost in compute, time, or external systems, trace the real execution path and dry-run the risky steps where possible. Tests that mock those steps verify only the agent's own assumptions and do not count as verification; state plainly what could not be verified.
- Treat repository-specific operational health checks as completion gates when documented. Preserve evidence needed to diagnose a failed validation.
- When a workflow reserves approval or authorization to a designated human, prepare the evidence and obtain an explicit decision. Never present agent analysis, automated checks, or silence as that human authorization.

## Documentation and Knowledge

- Keep user-facing documentation synchronized when behavior, commands, setup, defaults, dependencies, file paths, or workflows change.
- Put durable information in the repository's designated source of truth. Avoid duplicating transient task status across multiple documents.
- Record material findings, constraints, provenance, and operational outcomes where a future agent can discover them without the original conversation.
- Treat `LEARNINGS.md` as a non-authoritative staging area. A single clarification is evidence to review, not permission to create a permanent rule.
- Promote a candidate preference only when it is confirmed or supported by recurring evidence, and keep contextual or repository-specific rules at the narrowest correct scope.

## Errors, Secrets, and Artifacts

- Make errors clear, actionable, and appropriate to the interface. Preserve the original failure context without exposing sensitive values.
- Never hardcode, print, log, inspect unnecessarily, stage, or commit credentials, tokens, private keys, passwords, or secret-bearing runtime files.
- Fail safely when required inputs, storage, credentials, or preconditions cannot be verified. Do not silently continue with a risky fallback.
- Keep generated, cached, backup, temporary, and machine-local artifacts out of version control unless the repository explicitly treats them as reproducible source artifacts.
- Clean up task-created temporary resources when safe. Preserve diagnostic state when cleanup would make an unresolved failure harder to investigate.

## Repository and Git Practices

- Preserve unrelated user or agent changes in a dirty worktree. Never revert, overwrite, stage, or reformat unrelated work to simplify the current task.
- Before editing, inspect the relevant files. Before completion, inspect repository status and the complete task diff for accidental or sensitive changes.
- Follow repository-specific rules for branches, commits, pull requests, and pushes. Do not infer universal permission to commit or push from another repository's workflow.
- Stage only files belonging to the current task. Use concise commit messages that describe the completed change, and do not bypass hooks or validation to force completion.
- Write pull request descriptions to orient the reviewer: one or two sentences of purpose, the main changes at the level of behavior and design, and a pointer to the part that deserves the closest review. Omit test summaries, file or function lists, and future-work sections. Let length follow conceptual complexity rather than diff size.
- For operational, stateful, destructive, or migration work, establish a practical rollback or recovery path before applying the change and record it in the repository's designated log.
- Prefer deterministic, idempotent scripts for repeatable maintenance. Make reruns safe, provide useful output, and refuse malformed state rather than guessing destructively.
<!-- END GLOBAL AGENT PREFERENCES: GENERATED -->

## Repository-Specific Preferences

Add confirmed repository-specific preferences and explicit overrides here. Keep candidates in `LEARNINGS.md` until they have enough evidence to promote.

- Label hardware and protocol facts by evidence: manufacturer claim, README claim, source-code behavior, third-party report, or hardware-verified by this project. Never describe printer behavior as verified unless this project tested it on real hardware, and never claim an Android build, Bluetooth connection, or physical print succeeded without running it.
- Do not copy code, assets, fonts, or icons from another project or app without a GPLv3-compatible license recorded with its source and revision. A public repository is not a license.
- Avoid servers, accounts, analytics, and recurring service costs unless a concrete need is documented and approved.
- Do not purchase hardware, interact with a printer, or publish to Google Play without explicit maintainer authorization for that action.
