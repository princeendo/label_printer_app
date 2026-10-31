# label_printer_app

A free, open-source Android app for designing and printing gift-wrapping ribbons and adhesive labels, starting with the Phomemo P12. `label_printer_app` is a working name; public branding is undecided.

## Status

**Phase 2 in progress: hardware bring-up.** The `printer` module encodes P12 print jobs, decodes status replies, refuses undocumented commands, and includes a simulated printer and calibration patterns, all unit-tested without hardware. The `app` module is a single debug screen for bring-up: it discovers devices, connects over Bluetooth Classic (SPP) or BLE, sends status queries, prints calibration patterns and text and QR samples, and logs every byte. It has been exercised only against the simulated printer so far. The P12 arrived on 2026-10-30; nothing has been verified on it yet. The main open risk is the P12's print-density control.

## Scope

- **Primary:** personalized gift-wrapping ribbon, including repeating messages, bows and event favors.
- **Also in scope:** general adhesive labels.
- **Long term:** functional coverage comparable to Phomemo's Print Master app, with an independent design and assets.
- **Proposed:** offline-first, no account, no ads, no subscription.
- **Hardware:** other printers come after P12 support is validated.

## Documentation

- [TASKS.md](TASKS.md): current status and open tasks.
- [docs/PRODUCT.md](docs/PRODUCT.md): requirements, workflows, and the feature matrix.
- [docs/RESEARCH.md](docs/RESEARCH.md): protocol findings with evidence labels, an audit of existing projects, Play requirements, and the hardware validation plan.
- [docs/PLAN.md](docs/PLAN.md): phased plan, acceptance criteria, open decisions, and the Phase 1 brief.
- [docs/agent/DECISIONS.md](docs/agent/DECISIONS.md): confirmed project decisions.
- [AGENTS.md](AGENTS.md): instructions for coding agents.

## Next Step

See [TASKS.md](TASKS.md). In short: when the printer arrives, run the Print Master capture and the Phase 2 hardware bring-up.

## Build and Test

The build uses the Gradle wrapper and Android Studio's bundled JDK. On macOS:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew check
```

`check` runs the unit tests, ktlint, and Android lint. Run `./gradlew ktlintFormat` to fix formatting. The Android SDK location comes from `local.properties` (`sdk.dir=$HOME/Library/Android/sdk`), which is machine-local and not committed.

To install the bring-up app on a connected phone or emulator:

```bash
./gradlew :app:installDebug
```

The app writes its session log to app-private storage. On a debug build, read it with:

```bash
adb shell run-as io.github.princeendo.labelprinter cat files/bringup-log.txt
```

The log masks serial numbers after their first four characters and never records Bluetooth addresses.

## Acknowledgements

The protocol research relies on public work by [soburi/phomemo_p12](https://github.com/soburi/phomemo_p12), [essendyx/tapesmith](https://github.com/essendyx/tapesmith), [transcriptionstream/phomymo](https://github.com/transcriptionstream/phomymo), and [sengine-cloud/labelfab](https://github.com/sengine-cloud/labelfab). No code from these projects is included.

Phomemo and Print Master are trademarks of their respective owners. This project is not affiliated with them.

## License

[GPL-3.0-only](LICENSE) (SPDX: `GPL-3.0-only`).
