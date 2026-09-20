# Tagsmith

An Android app for programming and managing NFC tags, built for someone who sells
NFC products to local businesses: write a review link to a card, keep track of what
went where, and look up any tag by tapping it.

The emotional centre of the app is the tap. Everything else is support around that
one moment.

This repository is **v1** of the build plan: scan, tag details, writing URL and
text, history, settings, and the error states that actually bite.

## Running it

```bash
./gradlew :app:installDebug     # a device with an NFC radio, or an emulator for the UI
./gradlew :app:assembleRelease  # R8-minified, ~1.9 MB
```

`local.properties` points at the Android SDK. `compileSdk`/`targetSdk` are 37,
`minSdk` is 26.

NFC cannot be emulated. On an emulator the app runs and every screen is reachable,
but the radio reports as absent and the scan screen shows its dead-end state — which
is itself one of the states worth looking at.

## What v1 does

| Area | State |
| --- | --- |
| Onboarding | Three slides, skippable, with the NFC-off and no-hardware variants |
| Home | Date, greeting, live stat row, recent activity, NFC-off banner |
| Scan | Waiting → detected → reading → result sheet → errors |
| Tag details | Identity, capacity meter, decoded records, raw hex, provenance |
| Write | URL and plain text, byte counter, verify/lock options, tap prompt, success |
| Lock | The heavy confirmation screen, hold-to-confirm |
| Erase | Confirm, then tap; writes an empty NDEF message |
| History | Day-grouped ledger, filters, detail view, CSV export |
| Settings | Theme, haptics, sounds, write defaults, clear history |
| Share target | Share a URL from any app and land in Write with it prefilled |

**Tags** and **Clients** keep their place in the bottom bar and explain what is
coming, so the shape of the finished app stays visible. Templates, batch mode and
the inventory itself are v2/v3.

## Design

The screens come from the `Tagsmith Screens` handoff: Material 3 structure on a warm
workshop palette — oak and paper neutrals with one ember accent (`#C4441C`), Archivo
throughout, JetBrains Mono wherever the screen shows data, and Modernist's flush-left
labels and hard 2px rules as the organising grammar.

Rules worth keeping when you extend it:

- **Nothing rounds a corner.** `--radius` is 0 on purpose; only circles and the phone
  silhouette are curved.
- **Button labels are flush left**, even when the button is wider than its label. The
  trailing icon sits at the far right.
- **Primary actions live in the bottom third**, within thumb reach — this is operated
  one-handed, standing up, in a café.
- **Data is mono.** UIDs, byte counts and chip types read as data, not as prose.
- **The tap owns the dark ground.** Scan, the tap prompt and the batch screen render on
  the dark field whatever the app theme is; `OnTapGround { }` does this.
- **Motion carries meaning.** Rings pulse while waiting, collapse on detection, and
  resolve into a check that draws itself in.

Colours are semantic roles on `Tagsmith.colors` (`ink`, `accent`, `rule`, `hairline`,
`danger`, …) rather than raw hex at the call site. Type is `TagsmithType`, named for
where a style is used rather than for its size.

## Architecture

Hand-wired dependencies — five collaborators, built in `AppContainer` and handed down
through `LocalAppContainer`. A DI framework would cost more than it saves here.

```
core/nfc/       NfcSession (the state machine), TagReader, TagWriter, payloads, failures
core/data/      Room: the tag ledger and the history log, behind LedgerRepository
core/settings/  DataStore-backed preferences
core/feedback/  Haptics and tones
ui/theme/       The palette, the type scale, OnTapGround
ui/components/  Rules, kickers, chips, meters, the rings, the drawn check
ui/<screen>/    One package per screen, ViewModel beside it
ui/nav/         Routes, the bottom bar, the single NavHost
```

### The session

`NfcSession` is the whole tap, as a state machine:

```
Idle → Waiting(op) → Detected(op) → Working(op) → Done(result) | Failed(op, failure)
```

Screens `arm()` it with a `TagOperation` (`Read`, `Write`, `Lock`, `Erase`, `Format`)
and watch `phase`. Every phase carries the operation it belongs to, so a screen draws
only its own taps — a result left over from a write never appears on the scan screen.

`MainActivity` owns the radio. It mirrors `session.armed` into
`NfcAdapter.enableReaderMode`, which is deliberate: reader mode suppresses the
platform's own NDEF handling, so tapping a URL tag inside Tagsmith never bounces the
operator out into a browser. It also listens for `ACTION_ADAPTER_STATE_CHANGED`, so
switching NFC on from the deep link brings the scan screen straight to life.

### The ledger

Every completed tap is recorded once, in `AppContainer`, by a collector on
`session.events`. Screens never write history themselves. `LedgerRepository.mergeTag`
keeps everything the operator set by hand — nickname, client, inventory flag — and
refreshes only what the radio just reported.

### Errors

`NfcFailure` carries the copy the screen shows, not just a code: a kicker, a headline
and a line of detail, plus whether it is recoverable. `TagWriter` maps the radio's
exceptions onto it, so `TagLostException` becomes "The tag moved too soon" at the one
place that knows the difference.

A verification mismatch is deliberately not thrown by the writer: the caller holds the
payload the operator typed, so it builds the expected-versus-read-back comparison.
That screen is amber, not red, and its primary action is Rewrite.

### Chip detection

`TagReader` asks NTAG chips for `GET_VERSION` (`0x60`) and reads the storage byte,
which is the only reliable way to tell a 213 from a 215 before reading anything. It
falls back to `MifareUltralight.getType()` and then to `Ndef.maxSize`.

## Known limits

- **Byte counter reference.** The write screen has no tag in hand, so it measures the
  payload against NTAG213's 144 bytes and says which chip it will fit. The real
  capacity check happens against the tag itself at write time, which raises
  `TooSmall` with both numbers.
- **The success screen's `uid`** is the tag just written, read back after the write —
  a second connection. On a tag pulled away immediately this can fail, and the write
  is reported as tag-lost even though the bytes landed. Verification catches this on
  the next tap.
- **`Format` is implemented but not reachable from the UI** — writing to an
  unformatted tag formats it in the same tap, which covers the case the board drew.

## Licences

Tagsmith bundles two open-source typefaces, both under the SIL Open Font
Licence 1.1 — see `licenses/`:

- **Archivo** — Omnibus-Type
- **JetBrains Mono** — JetBrains
