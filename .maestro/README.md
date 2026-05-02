# Maestro flows

Two end-to-end UI flows that exercise tonearm's golden paths from a real
ADB target. Each flow is also runnable step-by-step via mobile-mcp inside
Claude Code (the YAML actions map 1:1 onto `mcp__mobile__mobile_*` tool
calls).

| Flow | What it covers | Phase |
|------|----------------|-------|
| `play-track.yaml` | Permission grant → scan → tap track → NowPlaying → notification visible | G.5 |
| `delete-track.yaml` | Long-press → delete dialog → system consent → row gone | G.6 |

## Running with the Maestro CLI

```bash
# Once, to install the CLI:
curl -Ls "https://get.maestro.mobile.dev" | bash

# Pre-requisite: a test device + the app installed + test music pushed.
scripts/install.sh
scripts/push-test-music.sh

# Then:
maestro test .maestro/play-track.yaml
maestro test .maestro/delete-track.yaml
```

## Running through Claude Code (mobile-mcp)

The `mcp__mobile__*` tools (registered via `.mcp.json`) execute the same
actions interactively. A typical session:

```
mcp__mobile__mobile_launch_app(packageName="com.eight87.tonearm")
mcp__mobile__mobile_list_elements_on_screen()
mcp__mobile__mobile_click_on_screen_at_coordinates(...)
```

`scripts/start-avd.sh` boots the headless `medium_phone` AVD that the
project's CLAUDE.md targets for headless test runs.
