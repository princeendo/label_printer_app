# 2026-10-30: Unit Details and Print Master Baseline Capture

Validation plan step 0. Everything here was measured by this project on its own P12. The raw HCI log stays outside the repository; it contains the printer's serial number and Bluetooth address, plus unrelated traffic from other paired devices.

## Unit

| Item | Value | Source |
|---|---|---|
| Advertised name | `P12` | Print Master's Printer Management screen |
| Firmware | 0.1.9 (`1F 11 07` → `1A 07 00 01 09`) | Print Master screen and the capture agree |
| Serial prefix | `Q245` (15 characters in all) | Print Master screen; `1A 08` reply |
| Battery | 100% (`1F 11 08` → `1A 04 64`) | Capture |
| Auto shutdown | 15 minutes (a Print Master setting) | Print Master screen |
| Color, power type | Not yet recorded | Maintainer to read from the unit |

The firmware differs from the only third-party report (1.0.0, tapesmith), so facts from that report apply to this unit only where they are re-measured.

## Setup

- Pixel 9 Pro, Android 17 (API 37), HCI snoop log set to Enabled in Developer options.
- Print Master 5.23.1 (`com.project.aimotech.printmaster`), installed 2026-10-30.
- White adhesive tape.
- The printer was power-cycled and reconnected after logging was enabled.

## Transport

Print Master connected over **Bluetooth Classic RFCOMM** after an SDP lookup. No BLE (ATT) traffic to the printer appeared at any point. RFCOMM carried up to 662 bytes per frame.

## Connection Sequence

Sent once when Print Master connected, before any print. Replies arrived 6–8 ms after each write.

| Write | Replies |
|---|---|
| `1F 11 38` | `1A 17 03` |
| `1F 11 11 1F 11 12 1F 11 09 1F 11 13 1F 11 09` | `1A 06 89`, `1A 05 98`, `1A 08` + serial, `1A 03 A8`, `1A 08` + serial |
| `1F 11 19` | `1A 0C 0B` |
| `1F 11 11` (twice) | `1A 06 89` each |
| `1F 11 38 1F 11 12 1F 11 13 1F 11 07` | `1A 17 03`, `1A 05 98`, `1A 03 A8`, `1A 07 00 01 09` |
| `1F 11 19` (twice), `1F 11 11`, `1F 11 19` | `1A 0C 0B`, `1A 06 89` |
| `1F 11 07`, 2 s later | `1A 07 00 01 09` |

The cover was closed throughout, and it reported `98`, confirming that `98` means closed on this unit.

Opening Printer Management sent two undocumented queries:

| Write | Replies | Interpretation |
|---|---|---|
| `1F 11 0E 1F 11 4B` | `1A 09 03` only | Unknown. Probably the auto-shutdown setting, but that is not established. |
| `1F 11 08` | `1A 04 64` | Battery 100% |

## Print Jobs

Three prints of the same `TEST` label, mirror printing off and quantity 1, at Light, Standard and Heavy in that order. Each job was exactly:

```text
1F 11 02 nn                        density: Light 01, Standard 02, Heavy 04
1B 40                              ESC @
1D 76 30 00 0C 00 0B 01            GS v 0, 12 bytes per row, 0x010B = 267 rows
<3204 raster bytes>                no pauses beyond RFCOMM framing
```

- **No status queries were sent before a job and no feed command after it.** The job ended at the last raster byte.
- **The printer reported completion** with `1A 0F 0C` after each job: 4.2 s after the last raster byte at Light, 6.1 s at Standard, and 8.1 s at Heavy.
- The density command went out 39 ms before the raster header at Light and Standard, and 345 ms before it at Heavy.
- The three rasters were byte-identical; only the density byte changed.

### Raster Layout

- Inked dots: 35..72 of 0..95. The word was centered about 6 dots above the middle of the head.
- Inked rows: 25..145 of 267. That leaves 25 blank rows (3.1 mm) before the content and 121 (15.1 mm) after it.
- Text orientation: the tops of the letters face the low-numbered dots, and the last letter is printed first. That is the landscape design rotated 90° counterclockwise into head orientation.

## Still to Record

- Whether Light, Standard and Heavy differ visibly on the printed labels.
- How the printed text reads relative to the direction the tape leaves the printer.
- The length of each printed label.
- Whether Print Master asked for sign-in or a subscription before printing.
