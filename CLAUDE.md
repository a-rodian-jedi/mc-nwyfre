# Project Guidelines: Nwyfre (Minecraft 1.21.1 NeoForge Mod)

## Project Overview & Design Philosophy
**Nwyfre** is an Arthurian/Celtic-themed magic mod built for Minecraft 1.21.1 on **NeoForge**. It features deep magic systems, creature/loot scaling, custom boss fights, game stages, extra dimensions, and unique gear types (e.g., Talismans).

### Core Architectural Goal
**Maximum Customizability & Modpack Integration:**
Nwyfre must be designed so that almost all features (recipes, mob scaling, progression stages, loot, spell costs, and documentation) are configurable and expandable via **KubeJS**, **InControl!**, and **Patchouli** without requiring Java recompilation.

### Companion Document
`docs/ARCHITECTURE.md` is the developer handbook. The division of labour:
* **This file** holds the rules and invariants — what you must and must not do.
* **`docs/ARCHITECTURE.md`** holds the recipes, worked code examples, and the list of *live* traps.

Do not duplicate long examples here; add them to `docs/ARCHITECTURE.md` and keep the rule here. When the two disagree, **the code wins** — fix whichever document is stale.

**Do not accumulate history in either document.** Record a trap only while it can still bite; once a bug is fixed, the fix and its reasoning belong in a comment beside the code, not in a changelog here.

---

## Coding Standards & Testing Guidelines

### Implementation Philosophy
* **Write Minimal Necessary Code:** Implement the leanest, simplest design that fulfills the quality requirements and functional spec. Avoid over-engineering or premature abstractions.
* **Surgical Edits over Full Rewrites:** Do not randomly refactor or rewrite working implementations for small feature changes or minor tweaks. Maintain stability by localized edits.
* **APIs First, Mixins Second:** Always check for native NeoForge events, Data Components, Data Attachments, registries, or capabilities before reaching for bytecode manipulation. Mixins remain available and approved when vanilla hardcoding demands them.
* **Report Whether Anything Changed:** Mutating API methods should return whether they actually changed state (see `Stages.grant`). Callers rely on this to skip redundant persistence, network syncs, and event posts.

### Source Set Layout
Three source sets. **Only `src/main` ships in the published jar** — the `jar` task sees nothing else.

| Path | Ships? | Purpose |
| :--- | :--- | :--- |
| `src/main/java`, `src/main/resources` | **Yes** | Production code, assets, and data — including the real Patchouli book. |
| `src/test/java`, `src/test/resources` | No | Headless JUnit and its fixtures. |
| `src/gametest/java`, `src/gametest/resources` | **No** | In-game GameTests and their fixtures (structure templates etc.). |
| `src/generated/resources` | **Yes** | Datagen output. Never hand-edit. |

New production packages are created when the first real class needs them — never ahead of time.

### Testing Strategy (ModDevGradle + JUnit + GameTest)
Three layers. **Pick the cheapest layer that can actually catch the bug.**

| Layer | Command | Use for |
| :--- | :--- | :--- |
| JUnit | `./gradlew test` | Pure logic, codecs, data-file lints. Seconds. |
| GameTest | `./gradlew runGameTestServer` | Anything needing a live server, world, or real player. |
| Manual | `./gradlew runClient` | Rendering, GUIs, feel. |

* **Off-Client Unit Verification:** We use **ModDevGradle** with JUnit integration (`testedMod = mods.nwyfre`) to run unit tests in a headless environment. Use this to verify critical paths without launching the full Minecraft client runtime.
* **Prefer JUnit.** Reach for a GameTest only when the thing under test genuinely needs the game — attachments actually persisting, events firing through the real bus, commands executing.
* **Test Real Code Paths:** Write unit tests against actual implementation logic. Never mock or re-implement business logic inside test cases (this tests nothing and introduces false positives).
* **Prioritize Critical Paths:** Focus coverage on high-risk, core systems first: mana math calculations, damage/attribute scaling algorithms, stage progression locks, and custom recipe validation.
* **Keep Tests Fast & Performant:** Keep tests lightweight and targeted so the test suite runs in seconds. Do not bloat tests with excessive setups or redundant assertions.

#### Test Data vs. Production Data
**The content author owns production data outright.** Editing the real Patchouli book, renaming an entry, or rewriting prose must **never** break a test. A test that asserts on real *content* turns every content edit into a build failure — do not write those.

* **Assertions about behaviour** go against **fixtures** in `src/test/resources`, which never change.
* **Assertions about production data** check **structural invariants only** — things an author cannot legitimately violate (see `BookOfNwyfreLayoutTest`, which checks the real book's layout and nothing about its content).
* Build each check to take a path/root rather than hardcoding the production location, so the same code lints both a fixture and the real data.
* Pair every good fixture with a deliberately-broken one, and prefer breaking it the way a real failure actually happened.
* Use a dedicated test namespace (e.g. `nwyfre_test:`) for stage ids and other test data, so tests never depend on real content.

#### Writing a GameTest
* Annotate the holder `@GameTestHolder(Nwyfre.MODID)` (sets the template namespace) and `@PrefixGameTestTemplate(false)`.
* Every GameTest needs a structure template, even a logic-only one. Reuse `nwyfre:empty` at `src/gametest/resources/data/nwyfre/structure/empty.nbt`.
* **Always call `helper.succeed()`** — a test that never succeeds fails on timeout rather than on its assertion.
* **Do NOT use `GameTestHelper.makeMockServerPlayerInLevel()`.** It calls `PlayerList.placeNewPlayer`, which fires every loaded mod's join handler; KubeJS then pushes `kubejs:sync_server_data` down a connection that cannot accept play payloads and **every test fails before reaching its assertions**. Construct an unplaced `ServerPlayer` instead — copy `StageGameTests.unplacedPlayer`.
* Avoid permanently registering event listeners. If a test needs one, gate it behind a flag reset in `finally`, so a mid-test failure cannot leak behaviour into the next test.
* **Read the log, not just the exit code.** A GameTest server that fails to *start* still reports `BUILD SUCCESSFUL`. Confirm `All N required tests passed :)` actually appears.

---

## Required Integrations & Developer Ecosystem

| Library | Role in Nwyfre | Integration Target | Scope |
| :--- | :--- | :--- | :--- |
| **Curios API** | Accessory/Equipment slots | Talismans, Amulets, and Rings. | Required |
| **GeckoLib** | 3D Skeletal Animations | Entities, Bosses, Animated Gear, and Magic Relics. | Required |
| **KubeJS** | Scriptable Data & Events | Expose custom events, stage checks, and recipe hooks to JS. | Optional |
| **InControl!** | Dynamic Mob & Loot Control | Custom spawn rules, staging-based mob scaling, and dimension rules. | Optional |
| **Patchouli** | In-game Guidebook | Maintain the *"Book of Nwyfre"* (see §4 for the required layout). | Optional |
| **Jade** | HUD Block/Entity Information | Custom dynamic target info (Mana level, Boss Shields, Magic Altar status). | Optional |
| **JEI** | Recipe & Item Indexing | Stage-aware item/recipe visibility and custom crafting category displays. | Optional |

### Dependency Scope Policy
| Gradle scope | Meaning | Applies to |
| :--- | :--- | :--- |
| `implementation` | **Required.** Nwyfre is structurally built on it and cannot run without it. | GeckoLib, Curios |
| `compileOnly` + `localRuntime` | **Optional.** Compiled against, loaded in dev, never published as a dependency. | JEI, KubeJS, Patchouli, Jade, InControl! |

* Use `localRuntime` (not `runtimeOnly`) for optional mods so Nwyfre never publishes a hard dependency on them.
* **Guard optional integrations at runtime** with `ModList.get().isLoaded("<modid>")`, and keep each integration's code in its own class so that class only loads when the mod is present.
* Versions live in `gradle.properties`, each commented with the Maven it came from. **Verify a version exists before pinning it** — check the repo's `maven-metadata.xml`, or Modrinth's `/v2/project/<slug>/version?loaders=["neoforge"]&game_versions=["1.21.1"]` for mods with no developer Maven. Never guess a version string.
* A required dependency must also be declared in `src/main/templates/META-INF/neoforge.mods.toml` — but only **once code actually uses it**. Declaring it earlier makes the mod refuse to load over something nothing needs yet.
* **KubeJS must be taken with `transitive = false`** (and Rhino declared explicitly). Its POM lists runtime-scope extras that cannot resolve; its shipped jar JarJars what it actually needs. Keep this in place across version bumps.

---

## Development Rules & Feature Architecture

### 1. Game Stages & JEI Integration
* **Stage-Aware Progression:** Always check the player's active game stage before allowing recipe crafting or item usage.
* **Go through `Stages`, never the attachment.** `com.nwyfre.nwyfre.stage.Stages` is the only entry point for reading or changing stages. It handles persistence, client sync, and event posting.
  * Read: `Stages.has(player, stage)`, `Stages.get(player)` → `PlayerStages` (`hasAll` requires every stage and passes when empty; `hasAny` needs one and fails when empty).
  * Change (server-side, `ServerPlayer` only): `Stages.grant` / `revoke` / `clear`, each reporting whether anything actually changed.
* **Stage ids are free-form `ResourceLocation`s.** There is no registry of valid stages and **nothing hardcodes a stage name**, so a pack can invent `mypack:bronze_age` from KubeJS with no Java change. This is deliberate — do not add a stage registry without a concrete reason.
* **Authority is server-side.** The client only knows what has been synced to it. That is fine for display and filtering; never trust it for authority.
* **Operator/pack tooling:** `/nwyfre stage grant|revoke|clear|list` (level 2; `list` is open).
* **Adding a new gate:** read through `Stages`; decide the side; if the gate affects the client (JEI, Patchouli, tooltips) confirm the data is synced; test the logic in JUnit with `nwyfre_test:` ids; document the stage in the Patchouli book if players need to know about it.
* **JEI Recipe Filtering:**
  * Implement custom JEI `IRecipeCategory` handlers or leverage KubeJS JEI events to **hide locked recipes and unobtained tier items** when the player has not reached the required game stage.
  * Ensure recipes and items unlock dynamically on the client when a stage milestone is achieved.

### 2. KubeJS & Modpack Customizability
* **Expose Custom Events:** Always post custom NeoForge events (e.g., `NwyfreSpellCastEvent`, `NwyfreStageChangeEvent`, `NwyfreBossPhaseEvent`) so KubeJS scripts can cancel, modify, or extend them.
  * Implemented so far: **`NwyfreStageChangeEvent`** — game bus, server-side, cancellable. `NwyfreSpellCastEvent` and `NwyfreBossPhaseEvent` land with the systems that post them.
  * **Event conventions:** fire on the game bus; fire only when the change would *really* alter state (re-granting an owned stage fires nothing); make it cancellable where a pack could plausibly veto it; and a cancelled event must leave state untouched and make the originating call report `false`.
* **Data-Driven Magic & Scaling:**
  * Do not hardcode magic spell stats, cooldowns, or mob level multipliers in Java files. Use JSON Data Components, Data Maps, or Registry configs accessible to KubeJS.
* **Custom Recipe Types:** Register distinct NeoForge Recipe Types/Serializers for Nwyfre crafting (e.g., Altar Crafting, Runic Anvil) so pack developers can write recipes via KubeJS server scripts (`ServerEvents.recipes`).

### 3. InControl! Compatibility
* Add entity tags, NBT tags, and Data Attachments to Nwyfre entities so pack authors can easily write rules in `spawns.json` or `drops.json` using InControl! criteria (e.g., `stage`, `dimension`, `nwyfre_magical_tier`).

### 4. Patchouli Documentation ("Book of Nwyfre")
* All magical mechanics, rituals, bosses, and talismans **must** have a corresponding entry in the Patchouli guidebook.
* **Book layout (Patchouli 1.20+):** Patchouli removed the data-pack content layout, so the book is split across two trees and `book.json` **must** set `"use_resource_pack": true`:
  * `src/main/resources/data/nwyfre/patchouli_books/book_of_nwyfre/book.json` — the book definition, and nothing else.
  * `src/main/resources/assets/nwyfre/patchouli_books/book_of_nwyfre/en_us/` — all `categories/` and `entries/` content.
  * Putting entries under `data/` instead fails at load with `use_resource_pack set to false ... removed in 1.20` and the book is silently skipped.
* When adding a new item, block, or mechanic in code, suggest or generate the corresponding Patchouli JSON entry. Use stage locks (`flag` / `advancement` checks) in Patchouli JSONs where appropriate.

### 5. Jade HUD Display
* Register a `WailaPlugin` (`@WailaPlugin`) implementing `IWailaPlugin` for standard Jade HUD integration.
* Provide dynamic tooltips for Nwyfre blocks (e.g., Altar charge, Mana storage) and entities (e.g., Boss stage, shield health, magic weakness) to integrate seamlessly without polluting the standard crosshair view.

### 6. Curios API Integration
* Register custom slot identifiers (e.g., `talisman`) in data generators (`curios/slots/talisman.json`).
* Implement `ICurioItem` on custom gear to handle passive attributes, tick events, and rendering hooks on the player. Curios' own capability is the right mechanism here — but any *persistent* state the gear needs still belongs in a Data Attachment or Data Component, not a capability.

### 7. GeckoLib Setup
* Animated entities, armor, and items must extend GeckoLib base classes (`GeoEntity`, `GeoItem`, `GeoArmorItem`, `GeoBlockEntity`).
* Maintain clean organization of Blockbench assets:
  * Models: `assets/nwyfre/geo/`
  * Animations: `assets/nwyfre/animations/`
  * Textures: `assets/nwyfre/textures/`

---

## Technical Specifications & Conventions
* **Target Version:** Minecraft 1.21.1 / NeoForge (21.1.265 at time of writing). Do **not** "upgrade" the Minecraft or NeoForge line as incidental cleanup — the integration set above is why we are on 1.21.1.
* **Build System:** ModDevGradle (`net.neoforged.moddev`).
* **Language:** Java 21.
* **Data Components:** Use Minecraft 1.21's native **Data Components** (`DataComponentType`) instead of legacy NBT stacks for custom item properties.
* **Persistent Entity/Player Data — use Data Attachments, NOT capabilities:** On NeoForge 1.21.1 capabilities are *non-persistent behaviour lookups*; **Data Attachments** (`AttachmentType`, registered to `NeoForgeRegistries.Keys.ATTACHMENT_TYPES`) are what survive a save. Use attachments for player mana, progression stages, and entity level stats. Reserve `RegisterCapabilitiesEvent` for genuine behaviour lookups (item/block/entity interfaces).
  * `.serialize(Codec)` is what makes the data persist — omit it and everything silently resets on reload.
  * `.copyOnDeath()` is what makes it survive dying — progression almost always wants it.
  * Mutating a value in place does **not** mark the holder dirty; call `setData` afterwards (see `Stages.markChanged`).
  * Attachments serialise under the NBT key `neoforge:attachments`, each under its registry id.
* **`@EventBusSubscriber` — omit the `bus` attribute:** `EventBusSubscriber.Bus` is deprecated for removal on 21.1.265; NeoForge routes by event type. A misrouted mod-bus event leaves the handler **silently unregistered**, so confirm from the log: `Subscribing @EventBusSubscriber class ... to the mod event bus`.
* **Client-Only Code:** Keep it in `...client` and never load it from a class that a dedicated server touches. Reference client classes only from *inside* a handler body that executes client-side only.

### Networking Conventions
* Payloads live in `...network`, are registered on `RegisterPayloadHandlersEvent` (mod bus), and are versioned via `event.registrar("1")`.
* **Prefer absolute state over deltas** for small payloads — an absolute value cannot drift out of sync if a packet is missed around login. Stage sync sends the whole set.
* **Resync on login, respawn, and dimension change** (see `StageSyncEvents`), in addition to pushing changes as they happen.
* **Always unit-test a `StreamCodec` round trip.** A mismatched encode/decode pair desyncs clients *silently* instead of throwing. Encode, decode, and assert the buffer was fully consumed (see `StageSyncPayloadTest`).
* Guard sends against players with no live connection — machine-driven "fake players" are real `ServerPlayer` instances that were never placed on a connection.

---

## Running the Mod

| Command | Expectation |
| :--- | :--- |
| `./gradlew build` | Compiles and packages cleanly, warnings included. Runs the JUnit suite. |
| `./gradlew test` | Headless JUnit suite passes in seconds. |
| `./gradlew runGameTestServer` | Prints `All N required tests passed :)`. |
| `./gradlew runClient` | Launches to the main menu with **no errors** in the log. |
| `./gradlew runServer` | Reaches `Done (Ns)!` with no client-only class leaking into common code. |
| `./gradlew runData` | Runs data generators into `src/generated/resources`. |

**Persistent operational trap:** stopping a dev run can orphan the forked game JVM, which keeps `run/world/session.lock` held and makes the next `runServer`/`runGameTestServer` die on `DirectoryLock`. Find and kill the survivor:

```powershell
Get-CimInstance Win32_Process -Filter "Name like '%java%'" |
  Where-Object { $_.CommandLine -match 'RunProgramArgs' } |
  Select-Object ProcessId, CreationDate
```
