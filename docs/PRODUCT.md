# Product

`label_printer_app` (working name) is a free, open-source Android app for designing and printing gift-wrapping ribbons and adhesive labels. Its application ID is `io.github.princeendo.labelprinter`; the public name is undecided.

Confirmed decisions are recorded in [DECISIONS.md](agent/DECISIONS.md). Everything else on this page is a **proposed** requirement until the maintainer accepts it.

## Users and Use Cases

- **Primary:** personal gift wrapping. A name, message or motif printed on satin ribbon tape, sometimes repeated along the ribbon.
- **In scope:**
  - personalized bows;
  - event favors (the same message on many short pieces);
  - repeating ribbon messages;
  - general adhesive labels for home and organizing.
- **Not targeted for the first release:** business and inventory labeling at scale (barcodes, spreadsheets). These come later.

## Proposed Requirements

These are proposed, not yet accepted:

- **Offline-first.** No account, no network permission in the first release, no analytics or ads, no subscription.
- **Local saved designs.** Designs are stored on the device, with a portable file format for export, import and full backup.
- **Two entry points:**
  - **Quick print:** type text, pick a preset, print.
  - **Editor:** a richer visual editor for composing a design.
- **Faithful preview.** What you see is the actual 1-bit raster at 203 dpi, including printable width, leader/trailer waste, and the total length.
- **Simulated printer.** Developers and testers without hardware can use a simulated printer that renders the exact bytes that would be sent.
- **Recoverable printing.** Clear status and recovery when the printer disconnects, the cover opens, or the tape runs out.
- **Independent materials.** Original UI, bundled fonts under OFL or a similar license, and icons under a GPLv3-compatible license. No Print Master assets or branding.
- **Android support.** `minSdk` 26 (proposed) and `targetSdk` 36, as Play requires for new apps.

## Core Workflows

1. **Quick ribbon:**
   1. Type a message.
   2. Choose "satin ribbon" and a length, or a repeat count.
   3. Preview.
   4. Print.
2. **Designed ribbon:**
   1. Compose a motif from text, icons and shapes.
   2. Set the repeat spacing and end margins, so the bow tails stay blank.
   3. Preview the full length.
   4. Save and print.
3. **Event favors:** one short design printed N times, with optional cut marks or spacing between copies.
4. **Adhesive label:** design a fixed-length label (e.g. 40 mm), save it, and reprint it later.
5. **Library:** browse saved designs and starter templates; duplicate, export, import, and back up everything.
6. **Printer setup:**
   1. Pair a printer.
   2. View its status (battery, cover).
   3. Calibrate the printable offset and length factor.
   4. Set density per media type.

## Physical Constraints

From [RESEARCH.md](RESEARCH.md); not yet verified by this project.

- **Printable area:** the tape is 12 mm wide but the printable band is only about 88 dots (about 11 mm) at 203 dpi; the manufacturer says 9 mm. Designs are therefore one strip, up to 88 px tall, of arbitrary length.
- **Blank tape:** about 8 mm of leader before the content and about 8–16 mm after it. Short labels waste tape; the preview must show this.
- **Long ribbons:** the maximum job length is unknown, so long ribbons may need several jobs.
- **QR codes:** a version 1 QR code needs 21 modules plus a quiet zone. At 3–4 dots per module, the 88-dot band caps QR codes at small payloads such as a short URL. Third parties report that 4-dot modules scan. Linear barcodes run along the tape and are limited by bar height, not length.

## Feature Matrix

**Print Master evidence key:**

- **Doc:** documented in the Phomemo help portal or store listing.
- **Retail:** retailer claim only.
- **Inspect:** needs current-app inspection.
- **Business only:** documented only for other printer models.

**Planned release:**

- **R1:** first public release.
- **R2:** the next release.
- **Later:** after R2.
- **—:** not planned.

| Area | Print Master evidence | P12 / model notes | Planned |
|---|---|---|---|
| Text: font, size, bold/italic, alignment, letter and line spacing, multiline | Doc (134 fonts; character pitch, line spacing) | Multiline is limited by the 88-dot height | R1 (a curated set of bundled open fonts) |
| Vertical text, auto-fit to height | Inspect | | R1 auto-fit; vertical text later |
| Icons and symbols | Doc; Retail says 1,000+ | | R1 (a curated open-licensed set) |
| Borders, lines, shapes | Doc; Retail says 500+ borders | | R1 basic shapes and borders |
| Image import (threshold or dither) | Doc | 1-bit output | R1 basic; R2 adjustments |
| Layout: move, resize, rotate, layer, lock, group | Partly Inspect | | R1 move, resize, rotate, layer; R2 group and lock |
| Ribbon length, repeated motifs, spacing, end margins | Not documented; "unlimited length" is Retail | Max job length unknown | **R1 (core)** |
| Mirror (for transfer media) | Doc (quick start guide), semantics unclear | Needs a hardware check | R1 if hardware testing shows it is needed |
| Fixed-size adhesive labels | Doc for business printers; QR code on the box identifies media | P12 tape is continuous | R1 (a fixed length on continuous tape) |
| Saved designs and starter templates | Doc; saving reportedly gated in 2026 (reviews) | | **R1 (local, free)** |
| Export, import, backup | Not documented (cloud saving needs login) | | R1 |
| Print history | Doc | | R2 |
| QR codes and barcodes | Doc (symbologies: Inspect) | Size limits above | R2 |
| Dates and times | Doc | | R2 |
| Serial numbering | Inspect | | R2 |
| CSV batch and variable fields | Doc for Excel import | | Later (CSV before XLSX) |
| Tables | Doc | Little room in 88 dots | Later |
| OCR, code scanning, voice input | Doc | Common libraries pull in Play Services, which conflicts with F-Droid | Later, with FLOSS options only |
| Printer status (battery, cover) | Doc | Cover status is inverted on the P12; tape-out is unreliable | R1 |
| Density | Doc | **Command unknown for the P12** | R1 if decoded (see PLAN) |
| Feed, copies, cancel | Inspect | No copies or cancel commands known | R1 (copies by repeating jobs; best-effort cancel) |
| Calibration (offset, length factor) | Not documented | Measured variation exists | R1 |
| PDF printing | Business only | Not useful for 12 mm tape | — (revisit with other printers) |
| Accounts, cloud sync | Doc (optional login) | | — unless a concrete need appears |
| Additional printers | Print Master supports many models | | After P12 validation |
