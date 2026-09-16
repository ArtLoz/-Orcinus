# OrcaSlicer upstream policy

The official OrcaSlicer repository is included as a Git submodule at
`upstream/OrcaSlicer`. The exact release and commit are recorded in
`upstream/orca.lock.json` as a human-readable companion to the Git submodule
pointer.

## Rules

1. Do not implement Android features by editing the submodule directly.
2. Put platform integration and compatibility code under `slicing/native`.
3. Keep OrcaSlicer types behind the `slicing:api` interface.
4. If an upstream patch becomes unavoidable, document why an adapter cannot
   solve it and keep the patch small enough to rebase independently.
5. Every upstream update must pass the dependency-pin check, domain tests,
   Android build, native smoke test, and slicing regression fixtures before its
   pointer is committed.

## Verify the pinned source

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ./scripts/verify-upstream.ps1
```

This verifies the submodule commit and a clean worktree. Dependency versions
are not duplicated in this repository: `engine/deps` builds OrcaSlicer's own
recipes from the checkout, so the commit pins them too.

## Update to another release or commit

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File ./scripts/update-orca.ps1 -Ref v2.4.3
powershell -NoProfile -ExecutionPolicy Bypass -File ./scripts/engine.ps1 -Stage all -Target libslic3r_tests,fff_print_tests,orca_engine_adapter_tests
./gradlew.bat :domain:test :app:assembleDebug
```

Then run the device suites listed in [`docs/work-plan.md`](work-plan.md), and
regenerate the third-party notices, which read the license texts of the rebuilt
dependencies:

```powershell
python scripts/notices/update_notices.py
```

The update script refuses to overwrite local changes. It fetches the requested
ref with shallow history, checks it out in detached mode, and updates the lock
file. Review the upstream changes before staging the new submodule pointer.
