# Research: Phomemo P12 Feasibility

Desk research as of 2026-10-22, plus this project's own hardware results from 2026-10-30 onward. Entries marked **VERIFIED** were measured by this project; everything else is still unverified. The P12 (Amazon B0FRMC2PHR) arrived on 2026-10-30. Its firmware is 0.1.9, which differs from the only third-party report (1.0.0).

Evidence labels used throughout:

- **MFR**: manufacturer claim (Phomemo product pages, quick start guides, help portal).
- **RETAIL**: retailer listing text.
- **README**: claim in a third-party project's documentation.
- **SOURCE**: behavior of third-party source code at the inspected commit.
- **3P-HW**: hardware result reported by a third party (issue, measurement log).
- **UNKNOWN**: no adequate evidence.

- **VERIFIED**: measured by this project on its own P12, with the date and firmware. Details are in [hardware/](hardware/).

## Summary

- **Feasibility is good but not proven.** Several independent projects print to the P12 with a short, sniffed byte stream. There is no handshake, authentication, SDK, or cloud step (SOURCE, 3P-HW).
- **Both transports have third-party hardware reports.** Bluetooth Classic SPP/RFCOMM works on a P12 (3P-HW, tapesmith). BLE GATT works on a P12 and on a P12 Pro (3P-HW, phomymo issues #45 and #9).
- **The RFCOMM vs. Web Bluetooth discrepancy is explained by dual-mode hardware, probably.** The Python tools use RFCOMM and phomymo uses BLE, and both report printing. A dual-mode chip is the likely reason, but no one has documented a single unit doing both.
- **Print density is decoded.** Print Master sends `1F 11 02 n` before every job: 1 Light, 2 Standard, 4 Heavy (VERIFIED 2026-10-30, FW 0.1.9, from a capture). That likely explains the third-party report of output too light until Print Master has printed once (phomymo #45). Whether the three levels look different on tape, and whether they are enough for thermal-transfer ribbon, is not yet measured.
- **Recommendation: an independent Kotlin implementation of the documented protocol facts.** No code is copied. The reasons are under Recommendation.

## Hardware and Media

| Topic | Finding | Evidence |
|---|---|---|
| P12 vs. P12 Pro | Same 203 dpi, 12 mm tape and 82×82×61 mm body. The P12 runs on 6×AAA, with USB-C on some listings; the P12 Pro has a 1000 mAh Li-ion battery. Both list "thermal and thermal transfer". | MFR (quick start guides; P12 Pro product JSON) |
| Protocol difference | None found. phomymo matches names `P12 PRO`, `P12PRO` and `P12` to one `p12` protocol, 12 bytes/line, 203 dpi. | SOURCE; 3P-HW for both models |
| Firmware variants | At least two: a third-party P12 reports FW 1.0.0; this project's unit reports **0.1.9**. Hardware revisions are UNKNOWN. | 3P-HW (tapesmith); **VERIFIED** 2026-10-30 |
| Amazon B0FRMC2PHR | Titled "Phomemo P12 … Thermal Transfer Ribbon Printer". Model "P12", green, AAA or USB-C power, so a base P12. Its spec rows say "300 x 300" resolution, which conflicts with MFR 203 dpi and is probably boilerplate. Phomemo's own store sells green only as a P12 Pro SKU. | RETAIL; verify on arrival |
| Printing on ribbon | Satin, fabric and some other tapes are thermal-transfer cassettes with a carbon ribbon: "align the label and carbon tape", remove the carbon after printing. No source shows a ribbon-specific printer command; every implementation sends the same raster. | MFR; SOURCE |
| Consumables | Shared 12 mm tapes "for P12/P12PRO": paper and plastic (4 m), satin ribbon (2 m, non-adhesive), iron-on fabric, glitter. Print Master can identify media by scanning a QR code on the box. | MFR |
| Media chip / RFID | No evidence of a chip. No code reads one. | UNKNOWN |
| Mirror option | MFR troubleshooting ties a mirror setting to thermal-transfer labels. Exact semantics unclear. | MFR; needs app inspection |
| Cutter | Manual lever. MFR says not to cut fabric or ribbon with it. | MFR |

## Protocol Findings

All working P12 code traces back to one byte stream "sniffed from Android app Print Master". It was published by soburi under MIT, and later reproduced byte-for-byte on hardware by tapesmith.

### Transport

| Item | Finding | Evidence |
|---|---|---|
| Classic SPP | RFCOMM via SPP UUID `00001101-0000-1000-8000-00805f9b34fb`. A channel number is not documented; on Android, use an SDP lookup (`createRfcommSocketToServiceRecord`). **Print Master on Android uses RFCOMM after an SDP lookup, with up to 662 bytes per frame; replies arrive 6–8 ms after each query.** tapesmith measured about 60 ms on FW 1.0.0. | SOURCE; 3P-HW (tapesmith); **VERIFIED** 2026-10-30, FW 0.1.9 (Print Master capture) |
| BLE GATT | Service `0xFF00`, write `0xFF02` (write-without-response preferred), notify `0xFF03`. phomymo writes 128-byte chunks with 20 ms gaps and does no explicit MTU handling. Print Master on Android does not use BLE for the P12. | SOURCE; 3P-HW (phomymo #9 P12 Pro, #45 P12); **VERIFIED** 2026-10-30 that Print Master uses Classic |
| BLE write ACKs | On the sibling D30, every write is acknowledged with a `01 01` notification, and the vendor app uses MTU 200 with 182-byte chunks. For the P12 this is UNKNOWN. | D30 analogy only (labelfab) |
| Dual-mode | Plausible. The P12 replies `1A 17 03` to `1F 11 38`; on the D30 that means a JieLi BR/EDR+BLE chip. | Inference |
| Pairing / bonding | Windows needs pairing to get a COM port. BLE works unbonded in Web Bluetooth. Probably one connection at a time: tapesmith says to disconnect the phone app first. PIN: UNKNOWN. | README; SOURCE |
| Advertised name | `P12` (as shown by Print Master). The serial starts with `Q245`, like the serial-style names of other Phomemo models (e.g. `Q199…`). | **VERIFIED** 2026-10-30, FW 0.1.9 |
| USB data | UNKNOWN; tapesmith lists it as experimental. | README |

### Raster and Geometry

| Item | Finding | Evidence |
|---|---|---|
| Pixel format | 1 bit per pixel, MSB = leftmost dot, 1 = burn | SOURCE (soburi, tapesmith, phomymo) |
| Line width | 12 bytes = 96 head dots per raster line | SOURCE; 3P-HW |
| Printable dots | soburi assumes dots 8–95. tapesmith measured dots **4–91** (88 dots, about 11 mm), but still ships offset 8 as its default. MFR claims a 9 mm effective width. **Needs calibration.** | Conflicting |
| Resolution | 203 dpi (8 dots/mm). Feed-axis scale: 98.5 mm per 100 mm requested on a third-party unit (factor 1.0152); **100.5–100.8 mm on this project's unit (factor 0.994)**. Units differ, so the factor stays user-calibratable. | MFR; 3P-HW; **VERIFIED** 2026-10-30, FW 0.1.9 |
| Orientation | A raster line runs across the 12 mm tape, and rows advance along the tape. **Print Master rotates landscape designs 90° counterclockwise**: letter tops face the low-numbered dots, and the end of the text prints first. Text rotated this way reads upright, left to right, as the tape leaves the printer to the right. The third-party tools rotate clockwise. | SOURCE; 3P-HW; **VERIFIED** 2026-10-30, FW 0.1.9 (capture and print) |
| Leader / trailer | About 8 mm blank before the content (head-to-cutter distance), and about 8–16 mm after. Matches the vendor app's behavior. | 3P-HW |
| Media type | Continuous (`1F 11 19` → `1A 0C 0B`). No gap or die-cut handling. | 3P-HW |

### Job Framing

**Print Master 5.23.1 on FW 0.1.9 (VERIFIED 2026-10-30, capture):**

1. Status queries only when connecting, not per job. See [the capture](hardware/2026-10-30-print-master-capture.md#connection-sequence).
2. Density `1F 11 02 n`.
3. `1B 40`, then `1D 76 30 00 0C 00 <rows LE16>`.
4. The raster in one stream, with no pauses beyond RFCOMM framing.
5. Nothing after the raster: no feed command.
6. The printer replies `1A 0F 0C` when the job has printed, 4–8 s after the last byte for a 33 mm label, longer at heavier density.

This project's encoder follows this framing.

**Older framing**, as observed in source and reproduced on hardware on FW 1.0.0 (3P-HW, tapesmith "prints identically"):

1. Six query packets, each written separately, each followed by a short read or wait: `1F1138`, `1F11111F11121F11091F1113`, `1F1109`, `1F11191F1111`, `1F1119`, `1F1107`. They are status queries, not configuration. One project observed that skipping them left the previous label's tail in the leader (anecdotal, later reverted).
2. Header `1B 40` (ESC @), then `1D 76 30 00 0C 00 <rows LE16>` (GS v 0, 12 bytes wide).
3. The raster: `rows × 12` bytes. soburi sends it in one write, tapesmith in 256-row writes under a single header, phomymo in 128-byte BLE chunks.
4. Feed: `1B 64 0D 1B 64 0D` (ESC d 13, twice).
5. The P12 sends no reply to the header, raster or feed, and tapesmith observed no job-complete signal; it waits `length_mm / 60 mm/s + 3 s` before disconnecting. On FW 0.1.9 the printer does send `1A 0F 0C` on completion, as the D30 does.

An older alternative framing uses 48-byte lines and `1F 11 02 04` (S1artie/InternetOfTrash, unlicensed, README claim). It suggests the firmware tolerates wider lines. It is not recommended.

### Controls and Limits

| Item | Finding | Evidence |
|---|---|---|
| Density | **`1F 11 02 n`**, sent by Print Master before every job: 1 Light, 2 Standard, 4 Heavy. These are the only levels Print Master offers. Visible effect on tape not yet recorded. The M110-style `1B 4E 04 n` was not seen. | **VERIFIED** 2026-10-30, FW 0.1.9 (capture); D30 analogy |
| Peak load | **A weak USB-C supply switches the printer off on heavy rows.** On one charger, rows of 88 and 96 burned dots switched it off mid-job, leaving a dashed fragment of the first row. On a 15 W USB-C power bank, 88 dots per row and a 96-dot solid bar printed at Standard density. The minimum supply is not yet known. | **VERIFIED** 2026-10-30, FW 0.1.9 ([first prints](hardware/2026-10-30-first-prints.md)) |
| Heat / speed | No speed command. ESC 7 (`1B 37 …`) heat parameters are deliberately blocked by tapesmith as an overheating risk. | SOURCE |
| Feed control | Print Master sends no feed command on FW 0.1.9; blank leader and trailer rows are part of the raster. ESC d 13 ×2 is proven only on FW 1.0.0. An ESC J variant was reverted. | **VERIFIED** 2026-10-30 (capture); SOURCE; 3P-HW |
| Max length | A 308 mm single job (2,465 rows, about 29.6 KB) printed in full over SPP without pacing. Longer jobs are untested. GS v 0 allows 65535 rows (about 8 m); a single 2 m ribbon needs about 16,000 rows. | **VERIFIED** 2026-10-30, FW 0.1.9, up to 308 mm |
| Copies | No P12 copies command is known; tools loop whole jobs. The D30 iOS app sends `1F 11 21 01`, untested on the P12. | SOURCE |
| Cancel | No cancel command is known. The approach is to pad the remaining declared rows with zeros; this is unverified. | SOURCE |
| Auto shutdown | Print Master shows and sets an auto-shutdown time (15 minutes on this unit). Opening its screen sends `1F 11 0E 1F 11 4B` and gets `1A 09 03`. Neither query is understood or allowlisted. | **VERIFIED** 2026-10-30 (capture) |

### Status Replies

Replies are framed `1A <tag> <payload>`. Measured on a P12 with FW 1.0.0 over SPP (3P-HW, tapesmith). Rows marked **V** were also observed by this project on FW 0.1.9 on 2026-10-30:

| Query | Reply | Meaning |
|---|---|---|
| `1F1108` | `1A 04 nn` | Battery percent. **V**: `64` = 100% |
| `1F1112` | `1A 05 99` / `98` | Cover **open / closed: inverted relative to phomymo's decoding**. Also sent unsolicited. **V**: `98` with the cover closed |
| `1F1111` | `1A 06 89` | Tape present. An **empty roll still reported `89`, and the job silently printed nothing.** **V**: `89` with tape loaded |
| `1F1119` | `1A 0C 0B` | Continuous media. **V** |
| `1F1109` | `1A 08` + 15 ASCII | Serial number. **V** |
| `1F1107` | `1A 07 01 00 00` | Firmware 1.0.0. **V**: `1A 07 00 01 09` = 0.1.9 |
| `1F1113` | `1A 03 A8` | Unknown (phomymo calls it heat state). **V** |
| `1F1138` | `1A 17 03` | Unknown (D30: chip type). **V** |
| (after a job) | `1A 0F 0C` | Job printed. **V** only; not seen on FW 1.0.0 |
| `1F110E 1F114B` | `1A 09 03` | Unknown; probably auto shutdown. **V** only |

Overheating, paper-out and cover errors during printing have not been observed on a P12.

### Never Send

These come from the D30 analogy (labelfab):

- `1F 11 0F`: firmware-update mode, a brick risk.
- `1B 4E 08`: serial rewrite, which breaks model detection.
- ESC 7 heat parameters.

The driver must use an allowlist of commands, not pass-through.

### Manufacturer Services

The plain stream prints without login, cloud, SDK, license check or special consumables (3P-HW). In Print Master, login is optional and documented only for cloud template saving (MFR help portal).

## Candidate Audit

Inspected 2026-10-22. No code was executed; nothing was copied.

| Project | Commit | Language | License at commit | Maintenance and tests | P12 evidence | Fit |
|---|---|---|---|---|---|---|
| [soburi/phomemo_p12](https://github.com/soburi/phomemo_p12) | `3c1bf0d4237c92321a51f600c66bdc0b5640e529` (2023-05-03) | Python | MIT (© 2022 Timo Dobbrick) | Dormant since 2023; no tests | Origin of the sniffed stream; reproduced on hardware by tapesmith | Protocol reference. About 60 lines, so a port would be a rewrite anyway. Has a crop-signature bug. |
| [PepperPhil/phomemo_p12](https://github.com/PepperPhil/phomemo_p12) | `58a14a2bbe3aacf88ac44d22e4af37884b7e764a` (2026-03-05) | Python + Flask | MIT | Fork; rendering and web UI changes only; no tests | Protocol identical to soburi | Nothing new. Its web UI runs a debug server on `0.0.0.0`. |
| [transcriptionstream/phomymo](https://github.com/transcriptionstream/phomymo) | `1f58d3f0e7f941b9143277cda828380149e56855` (2026-05-17) | JavaScript (Web Bluetooth) | **Unclear**: README says MIT, `package.json` says ISC, no LICENSE file has ever existed | Active; 107 commits; Playwright UI tests only | 3P-HW over BLE: #9 (P12 Pro, prints correctly), #45 (P12, too light) | Best BLE reference (UUIDs, chunking). **Do not copy.** |
| [essendyx/tapesmith](https://github.com/essendyx/tapesmith) | `a92cbe12664be716e980ec8f1585a34507f0f842` (2026-10-04) | Python + React | MIT; bundles DejaVu, Tabler (MIT) and Simple Icons (CC0) | New (first commit 2026-09-30); anonymous author; extensive tests including a golden byte stream | **Best evidence**: dated P12 measurements over SPP (`docs/hardware/`, in German). BLE marked unverified. | Good design model: device profiles, a status decoder, a command allowlist, calibration and a density test strip. |
| [sengine-cloud/labelfab](https://github.com/sengine-cloud/labelfab) | `782101a8e500cf9f340f8279b210ca19d4e1a135` (2026-10-02) | Python | MIT | Active; tests | D30 only | The deepest sibling-device notes (SPP and BLE captures). Use as analogy only. |
| [vivier/phomemo-tools](https://github.com/vivier/phomemo-tools) | `2b767486d4590676f1ffb61c7ca870984978a980` (2026-10-18) | Python (CUPS) | GPL-3.0 | Active; 467 stars | **No P12** | ESC/POS framing reference for other models; a possible source for a later second printer |
| nc6/p12_label_printing, S1artie/InternetOfTrash | `f631fecb…`, `0e26cd94…` | Nix, Python | **None** | Minimal | Patches and alternative framing | Facts only; not reusable |

No P12 Android app, Kotlin library or Home Assistant integration was found.

### Recommendation

**Implement independently in Kotlin from the documented protocol facts.** Contribute findings upstream where they help, for example the inverted cover status and density results.

- **Why not contribute upstream:** no candidate is an Android project. soburi is dormant; phomymo is a browser app whose license is unclear.
- **Why not reuse code:** the reusable MIT code is tiny Python, so a port is a rewrite. Protocol facts are not copyrightable expression; credit soburi and tapesmith as references in the README.
- **What to take from tapesmith:** its design ideas (device profile with calibration, table-driven status decoder, command allowlist, density test strip) are worth reproducing independently.
- **Re-check before reuse:** if a later task does reuse code or assets, re-check the license at that revision and record it.

## Print Master Observations

- **Developer:** listed on Play as Zhuhai Quyin (Quin) Technology; on iOS as PHOMEMO PTE. LTD.
- **Play listing:** 1M+ downloads, 2.9★. In-app purchases $0.49–$39.99. Data safety declares optional email and user IDs, plus shared device IDs, installed apps and diagnostics (analytics) (developer-declared store listing, read 2026-10-22).
- **iOS listing:** "Contains Advertising"; weekly, monthly and annual subscriptions from $2.99 to $39.99.
- **Documented features:** the feature guide (help portal) is summarized in the matrix in [PRODUCT.md](PRODUCT.md).
- **Subscriptions and saved templates:** recent reviews (August 2026) say saving labels and templates now needs a subscription, and some report printing gated. These are anecdotes, not a documented policy. What basic printing requires today **needs current-app inspection**. Phomemo publishes no per-feature list of what is paid.
- **Android pairing:** Print Master pairs inside the app and needs location turned on (MFR).

## Android and Google Play Requirements

Official sources, as of 2026-10-22:

- **Registration:**
  - US$25 one-time fee; minimum age 18; identity verification.
  - Personal accounts must verify a physical Android 10+ device through the Play Console app.
  - For a personal account, Google shows the legal name, country and developer email; the full address is shown only if the app is monetized. ([6112435](https://support.google.com/googleplay/android-developer/answer/6112435), [13628312](https://support.google.com/googleplay/android-developer/answer/13628312), [14316361](https://support.google.com/googleplay/android-developer/answer/14316361))
- **New personal accounts:** must run a closed test with **at least 12 testers opted in continuously for 14 days** before applying for production. ([14151465](https://support.google.com/googleplay/android-developer/answer/14151465))
- **Target API:**
  - New apps and updates must target **API 36** from 2026-08-31 (extension available to 2026-11-01). ([target-sdk](https://developer.android.com/google/play/requirements/target-sdk))
  - 16 KB page-size support only matters for native libraries. ([page-sizes](https://developer.android.com/guide/practices/page-sizes))
- **Policy and packaging:**
  - A Data safety form is required even when no data is collected.
  - A privacy policy URL is required for every app, plus a link or text inside the app.
  - AAB plus Play App Signing; an app-provided signing key is allowed before the first open or production release. ([10787469](https://support.google.com/googleplay/android-developer/answer/10787469), [10144311](https://support.google.com/googleplay/android-developer/answer/10144311), [9842756](https://support.google.com/googleplay/android-developer/answer/9842756))
- **Android developer verification:**
  - Enforced since 2026-09-30 only for installs from seven participating stores in four countries. Global enforcement on certified devices is planned for 2027.
  - **ADB installs are exempt**, so local development is unaffected.
  - GitHub or F-Droid APKs will need the package name and signing-key fingerprints registered. The free "limited distribution" tier caps at 20 devices. ([developer-verification](https://developer.android.com/developer-verification), [open-source registration](https://developer.android.com/developer-verification/guides/open-source-app-registration))
- **Bluetooth permissions:**
  - API 31+: `BLUETOOTH_SCAN` (with `neverForLocation`) and `BLUETOOTH_CONNECT`.
  - API ≤30: legacy `BLUETOOTH`, `BLUETOOTH_ADMIN` and location.
  - `CompanionDeviceManager` scans Classic and BLE devices without the location permission, but does not connect. ([bt-permissions](https://developer.android.com/develop/connectivity/bluetooth/bt-permissions), [companion pairing](https://developer.android.com/develop/connectivity/bluetooth/companion-device-pairing))
- **F-Droid:**
  - FLOSS toolchain; no Google Play Services or Firebase; declare anti-features.
  - Reproducible builds are optional, but allow one signature across Play, GitHub and F-Droid. ([Inclusion Policy](https://f-droid.org/docs/Inclusion_Policy/), [Reproducible Builds](https://f-droid.org/docs/Reproducible_Builds/))

## Local Tooling (2026-10-22)

Machine: macOS 26.7.1, arm64, Bluetooth on.

- **Present:** `adb` 1.0.41 (Homebrew `android-platform-tools`).
- **Missing:** a usable JDK (`/usr/bin/java` is the macOS stub with no runtime), Android Studio, the Android SDK, an emulator, Gradle, and Kotlin.
- **Attached adb devices:** two Android TV boxes (NVIDIA SHIELD on API 30; onn 4K Pro on API 34). Neither is a suitable phone test target.
- **Update, later the same day:**
  - The maintainer installed Android Studio 2026.2 with Homebrew. It bundles JBR 25, so the JDK gap is closed.
  - The Android SDK is not downloaded yet; Android Studio's first-run setup wizard installs it.
  - The test phone is the maintainer's Pixel 9 Pro. It also serves Play device verification.
  - No Android 11-or-older phone is available. The emulator covers only the permission flow for Android 8–11, not printing.

## Hardware Validation Plan

The minimal experiment is run once the maintainer buys a P12 and authorizes printer interaction.

### Equipment

- One P12. Record its SKU, color, power type and serial prefix, and query its firmware with `1F1107`.
- One plain adhesive tape and one satin ribbon tape. Iron-on fabric is optional.
- The Pixel 9 Pro, with HCI snoop logging available in Developer options.
- Calipers or a steel ruler.

### Steps

0. **Baseline capture (optional, recommended).**
   1. Print a simple label from Print Master with HCI snoop logging on.
   2. Repeat at its lowest and highest density settings.
   3. Record whether printing needs login or a subscription.

   This is the cheapest way to decode the density command and to see which transport the vendor app uses on Android.

   Capture procedure on the Pixel:
   1. Install Print Master, and skip login if the app allows it.
   2. In Developer options, set "Enable Bluetooth HCI snoop log" to Enabled.
   3. Turn Bluetooth off and on again.
   4. Print.
   5. Run `adb bugreport` from the Mac. The capture is inside the zip at `FS/data/misc/bluetooth/logs/`.
   6. Turn snoop logging off afterwards.

   Analysis needs Wireshark or a small parser, which is a separate install decision. Keep the raw log outside the repository.
1. **Discovery.** Record:
   - Classic discovery and BLE scan names;
   - the SDP records (SPP present?);
   - the GATT services (`FF00`/`FF02`/`FF03`?);
   - pairing prompts.
2. **Status.** Over SPP, send the six documented queries plus battery (`1F1108`); log the replies with timing.
3. **Test patterns on adhesive tape, over SPP.**
   - T1, width (`TestPatterns.widthCalibration`): a solid bar, twelve byte-wide blocks stepping across the tape, and a one-dot-per-step wedge at each edge. The blank wedge steps at each edge give the first and last printable dot.
   - T2, length (`TestPatterns.lengthRuler`): a 100 mm ruler with 1, 5 and 10 mm ticks. Measure, and compute the length factor.
   - T3, quality (`TestPatterns.qualityStrip`): a solid block, 1-dot and 2-dot checkerboards, and horizontal and vertical hairlines. The bring-up app adds 6 pt and 10 pt text and a QR code with 3-dot and 4-dot modules (scan it), since these need Android font and QR rendering.
   - Also measure the leader and trailer.
4. **Repeat jobs.**
   - Five jobs back to back, using the computed wait between them.
   - One 300 mm repeating-motif job.
   - Then a 1 m job, but only if 300 mm succeeded.
   - Watch for streaks, truncation, or a hung printer.
5. **BLE.** Repeat T1 and T3 over GATT. Record:
   - the negotiated MTU;
   - any per-write notifications;
   - which chunk size and pacing work.
6. **Ribbon.**
   - Print T3 and a sample gift message on satin ribbon tape.
   - Judge legibility, edge sharpness, and smudge resistance with a finger-rub test.
   - Check whether mirroring is needed.
7. **Density.**
   - Only after the baseline capture, try the observed density command at its observed values. Compare T3 output on both media.
   - Do not send ESC 7, `1F110F`, `1B4E08`, or any command not seen in the capture or documented above.
8. **Faults.** Open the cover during a job, print with an empty roll, disconnect mid-job, and print on low battery. Record the status replies and printer recovery.

Store sanitized results under `docs/hardware/` with the date and firmware. Keep raw HCI logs out of Git: they contain MAC addresses and are ignored by `.gitignore`.

### Success Criteria

- Over at least one transport, an app this project wrote prints T1–T3 on adhesive tape:
  - length error ≤ 1 mm per 100 mm after calibration;
  - the printable window identified to the dot;
  - the QR code scans.
- Five consecutive jobs succeed without a power cycle or Print Master involvement.
- Satin ribbon text is legible and acceptable for gift use at some density this project can set.
- No authentication, cloud, or manufacturer software is needed after power-on.

### Failures That Change the Plan

- **Nothing prints, or the printer needs authentication:** stop app work. Analyze the capture; reconsider the hardware choice before more investment.
- **Prints, but too light without Print Master** (the #45 symptom):
  - decoding density from the capture becomes a release blocker;
  - a "print once from Print Master" workaround is not acceptable for a release.
- **Only one transport works:** implement only that transport. If both work, prefer SPP first: it has the strongest P12 hardware evidence, needs no chunk pacing, and is what the vendor's Android app uses on the sibling D30.
- **Long jobs fail:** add job chaining with a measured per-job cap. Ribbon designs longer than the cap print as several jobs, with the seam documented.
- **Printable width differs from 88 dots:** adjust the canvas constants. Calibration remains user-adjustable.
- **Ribbon output unusable at any density:** ribbon stays the goal. Investigate media and mirroring; consider whether the P12 is the right first printer.
