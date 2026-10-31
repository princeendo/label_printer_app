# 2026-10-30: First Prints From the Bring-Up App

Validation plan steps 2 and 3 (partial). P12 firmware 0.1.9, powered by USB-C with no batteries fitted. White adhesive tape. Pixel 9 Pro, bring-up app at the state of this change, over SPP.

## Connection and Status

- The app connected over SPP to the bonded P12 in 360 ms.
- The connect queries returned the same replies Print Master received (see [the capture](2026-10-30-print-master-capture.md#connection-sequence)): firmware 0.1.9, cover closed, tape present, continuous media, serial prefix `Q245`.

## T1 Switched the Printer Off

- T1 (`TestPatterns.widthCalibration`, 488 rows) was sent at Standard density, framed as Print Master frames jobs.
- The whole job was written. About 6.6 s later the link dropped, with HCI disconnect reason 0x08 (connection timeout), and no `1A 0F 0C` arrived.
- The printer was found switched off. It powered on again normally, and status read as before.
- T1 starts with a solid bar burning all 96 dots for 16 rows. Print Master's `TEST` label burned at most 37 dots in any row.

## Solid-Bar Ladder

`TestPatterns.loadBar`: a 2 mm solid bar of N dots, centered in the printable band, with blank tape before and after. Each width was sent as a separate job at Standard density, stopping at the first failure.

### First Charger (Rating Unknown)

Jobs ended 2 mm after each bar.

| Dots per row | Result | `1A 0F 0C` after the last byte |
|---|---|---|
| 24 | Printed | 2.2 s |
| 48 | Printed | 0.9 s |
| 72 | Printed, but stayed inside the printer until the next run pushed it out | 0.9 s |
| 88 | **Printer switched off**; only a dashed fragment of the first row printed; link dropped 5.4 s after the header | none |

The failed T1 job left the same mark: a dashed fragment of its first, solid row.

### Second Supply: 15 W USB-C Power Bank

A magnetic 10,000 mAh power bank labelled 15 W (exact model not identified). Jobs ended 10 mm after each bar.

| Dots per row | Result | `1A 0F 0C` after the last byte |
|---|---|---|
| 24 | Printed solid | 3.4 s after the job started |
| 48 | Printed solid | 2.2 s |
| 72 | Printed solid | 2.2 s |
| 88 | Printed solid | 2.2 s |

### Findings

- **The first charger was the limit, not the printer.** On it, a row of 88 or more burned dots switched the printer off. On the 15 W power bank, 88 dots per row printed solid, and so did T1's 96-dot bar.
- **When power fails, the printer prints a dashed fragment of the first heavy row and switches off.** The fragments are evenly spaced, about 0.75 mm apart, and the solid bars show small notches along their edges at the same spacing. That suggests the head heats dots in groups of about 6. Not confirmed.
- **`1A 0F 0C` means the job printed.** The 72-dot bar reported complete on the first charger and was later found on the tape.
- **Jobs need blank tape after the content.** No feed command is sent, so a job's last few millimetres stay behind the cutter until the next job pushes them out. 10 mm of blank was enough; Print Master's sample label ended with 15 mm. The calibration patterns now end with 10 mm of blank.
- A 14 mm job completed about 2.2 s after its last byte, roughly 6.5 mm/s at Standard density.

## T1 on the Power Bank

T1 printed in full at Standard density, including its 96-dot solid bar; `1A 0F 0C` arrived 12.1 s after the job started.

Provisional reading from a photo, good to about ±1 dot: dot 0 lies at one edge of the tape and dot 95 at the other. About 4–5 dots at the low edge do not print; at the high edge, the wedge inks from its first step, so dot 95 prints. That suggests a printable window of about dots 5..95 (91 dots). The third-party default, 4..91, would waste the high edge. `P12Profile` defaults are unchanged until a ruler reading confirms the window.

## T2 on the Power Bank

T2 (100 mm ruler, length factor 1.0) printed at Standard density; `1A 0F 0C` arrived 17.6 s after the job started, about 6.3 mm/s for 110 mm. Measured with a tape measure from the 0 mm line to the 100 mm line: 3 31/32 in (100.8 mm) on the inch scale and about 10.05 cm on the metric scale. **The printer over-feeds by about 0.6%**, within the 1 mm per 100 mm criterion even uncorrected. The default `P12Profile.lengthFactor` is now 0.994 (100 ÷ 100.6). The 300 mm ruler confirmed it (below).

## T3 and Text + QR on the Power Bank

Printed at Standard density, length factor 0.994, as consecutive jobs. T3 (39 mm) completed 7.2 s after the job started; the text and QR sample (110 mm) after 17.7 s.

- **T3:** the solid block is evenly black; the 1-dot checkerboard is an even grey, and the 2-dot checkerboard visibly coarser; hairlines across and along the tape every 4 dots are continuous. The solid block burns 88 dots per row for 5 mm, so full-band solids print on this supply.
- **Orientation:** text rotated as Print Master rotates it reads upright, left to right, with the right-hand end leaving the printer first.
- **Text:** 6 pt and 10 pt sans-serif are crisp and legible.
- **QR codes:** both the 3-dot and 4-dot module codes decoded correctly (`P12 QR 3-DOT`, `P12 QR 4-DOT`) with ZXing 3.5.4, from a photo of the print. Not yet tried with a phone camera.

## 300 mm Ruler on the Power Bank

Plan step 4, long job. One 2,465-row job (308 mm including the trailer, about 29.6 KB of raster) at Standard density, length factor 0.994. `1A 0F 0C` arrived 47.0 s after the job started, about 6.6 mm/s.

- **The whole job printed cleanly**: the 300 mm end mark was present, with no gaps, streaks or truncation. The printer accepts a job far larger than any plausible receive buffer, streamed over SPP without pacing.
- **Length: the 0 to 300 mm span measured very close to 300 mm** with a tape measure, confirming the 0.994 length factor.
- The 1 m job is deferred to the ribbon phase, where 1–2 m single jobs are the real use case; it costs too much tape to run on adhesive tape only to repeat this result.

## Still to Record

- The first charger's rating.
- The T1 wedge readings, measured with a ruler: the inked length of each wedge (16 mm minus the inked length = unprintable dots at that edge).
