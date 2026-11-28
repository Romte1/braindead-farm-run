# Braindead Farm Run

A RuneLite plugin for planning and tracking **allotment, herb, tree and fruit tree** runs.

> Status: early development. The project skeleton is in place; gameplay features are being
> built phase by phase. See [PLAN.md](PLAN.md) for the roadmap.

## What it will do

- Track patch states and growth timers for allotments, herbs, trees and fruit trees.
- Plan what to plant, what to bring and which patches to visit.
- Choose a specific seed or sapling per patch type; **Best** uses the highest-level crop your
  Farming level allows.
- Order the route and pick the best available teleport for each leg.
- Group supplies into equipment, tools, seeds, runes, travel and payments.
- Show the next step and highlight the relevant patch and inventory items.
- Provide a Farm run tab in the bank with stable placeholders and noted/unnoted labels.
- Optionally send each stop to Shortest Path for a walking line.

## Scope

v1 focuses on the essentials:

- Allotment, herb, tree and fruit tree patches.
- Timers, supplies, route and bank tab.
- Optional Shortest Path integration.

Not included in v1: history, profit, yield, XP tracking, farming contracts, social links or
banners.

## Build and run

Requirements: Java 11 and the Gradle wrapper.

```bash
# Build and run tests
./gradlew build --no-daemon

# Launch the development client
./gradlew run --no-daemon
```

On Windows:

```powershell
.\gradlew.bat build --no-daemon
.\gradlew.bat run --no-daemon
```

## Configuration

The plugin uses the `braindeadfarmrun` config group. Settings will be added as each phase lands.

## Development

- [PLAN.md](PLAN.md) — architecture, phases and acceptance criteria.
- [AGENTS.md](AGENTS.md) — engineering and Plugin Hub guidelines.

## Data sources

Crop growth stages, patch data and teleport requirements are validated against the OSRS Wiki
and RuneLite's Time Tracking plugin. Item, object, varbit and interface IDs use RuneLite's
`gameval` constants where available.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
