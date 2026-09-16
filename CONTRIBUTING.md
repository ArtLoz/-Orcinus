# Contributing to Orcinus

Thank you for helping. Issues and pull requests are welcome in English or
Russian.

## Before you start

- Read [`docs/architecture.md`](docs/architecture.md): features depend on
  domain use cases and the design system, never on each other; the engine is
  reached only through `:slicing:api`.
- For anything larger than a fix, open an issue first so the approach can be
  agreed on.
- OrcaSlicer is a pinned submodule in `upstream/OrcaSlicer` and is never edited
  in place. Android-specific behaviour lives in `engine/` and
  `slicing/native`; see [`docs/upstream.md`](docs/upstream.md) for updates.

## Making a change

1. Build the engine dependencies once and the app as described in
   [`README.md`](README.md).
2. Keep changes focused, and match the style of the surrounding code: Kotlin
   official style, 4-space indentation, KDoc on public types.
3. Run the checks that cover your change:

   ```powershell
   ./gradlew.bat assembleDebug :domain:test :data:notices:test :feature:about:testDebugUnitTest
   ```

   Engine changes also need the device test suites and, when the G-code can
   change, the comparison with desktop OrcaSlicer
   ([`docs/golden-comparison.md`](docs/golden-comparison.md)).
4. Say in the pull request what you verified on a device and what only
   compiles.

## Dependencies and licenses

Orcinus is distributed under the GNU AGPL v3.0, so every dependency must have a
compatible license. When you add or update a native library, rebuild the
engine dependencies and run `python scripts/notices/update_notices.py`, which
regenerates the notices shown in the app. Maven dependencies are collected
automatically at build time.

## License of contributions

By submitting a contribution you agree that it is distributed under the GNU
Affero General Public License v3.0, the license of the project.
