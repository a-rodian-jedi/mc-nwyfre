# Nwyfre

An Arthurian/Celtic-themed magic mod for **Minecraft 1.21.1** on **NeoForge**, featuring deep
magic systems, creature and loot scaling, custom boss fights, game stages, extra dimensions,
and unique gear types such as Talismans.

Nwyfre is built to be **modpack-first**: recipes, mob scaling, progression stages, loot, spell
costs, and documentation are all meant to be configurable and expandable through KubeJS,
InControl!, and Patchouli without recompiling Java.

## Requirements

| | |
| :-- | :-- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.265 |
| Java | 21 |

## Development

```bash
./gradlew build        # compile and package
./gradlew test         # headless JUnit suite (no client launch)
./gradlew runClient    # launch a dev client with all integrations
./gradlew runServer    # launch a dev dedicated server
./gradlew runData      # run data generators into src/generated/resources
```

## Integrations

Resolved automatically by Gradle; versions live in `gradle.properties`.

| Library | Role | Scope |
| :--- | :--- | :--- |
| GeckoLib | Skeletal animation for entities, bosses, gear, relics | Required |
| Curios API | Talisman / Amulet / Ring accessory slots | Required |
| JEI | Stage-aware recipe and item visibility | Optional |
| KubeJS | Scriptable data, events, recipe hooks | Optional |
| Patchouli | The in-game *Book of Nwyfre* | Optional |
| Jade | HUD block and entity information | Optional |
| InControl! | Dynamic spawn and loot rules | Optional |

Optional integrations are compiled against as `compileOnly` and loaded at dev runtime via
`localRuntime`, so Nwyfre never publishes a hard dependency on them.

## Project Guidelines

Architecture rules, coding standards, and the testing strategy live in [CLAUDE.md](CLAUDE.md).

## License

All Rights Reserved. Minecraft mappings are covered by Mojang's mapping license — see
[TEMPLATE_LICENSE.txt](TEMPLATE_LICENSE.txt) and <https://github.com/NeoForged/NeoForm/blob/main/Mojang.md>.
