# Nwyfre — Architecture & Development Notes

Working notes for developing Nwyfre. `CLAUDE.md` holds the *rules*; this file holds the *how* —
where things live, how the pieces fit, and the recipes for extending each system.

Keep this current as systems land. If a section here disagrees with the code, the code wins — fix
the doc.

---

## Contents

- [Project layout](#project-layout)
- [Testing layers](#testing-layers)
- [Test data vs. production data](#test-data-vs-production-data)
- [Game stages](#game-stages)
- [Rituals](#rituals)
- [Persistent player data](#persistent-player-data)
- [Networking](#networking)
- [Patchouli book layout](#patchouli-book-layout)
- [Integration dependency policy](#integration-dependency-policy)
- [Live traps](#live-traps)

---

## Project layout

| Path | Ships in jar? | Purpose |
| :--- | :--- | :--- |
| `src/main/java` | **Yes** | Production code. |
| `src/main/resources` | **Yes** | Production assets and data, including the real Patchouli book. |
| `src/test/java` | No | Headless JUnit. No Minecraft server; runs in seconds. |
| `src/test/resources` | No | Fixtures for JUnit — stub books and the like. |
| `src/gametest/java` | **No** | In-game GameTests, run against a live server. |
| `src/gametest/resources` | **No** | Fixtures GameTests need, e.g. structure templates. |
| `src/generated/resources` | **Yes** | Datagen output. Do not hand-edit. |

`src/gametest` is its own source set, registered with the mod for dev runs but never packed — the
`jar` task only sees `src/main`. See the `sourceSets` and `addModdingDependenciesTo` wiring in
`build.gradle`.

### Production packages

| Package | Holds |
| :--- | :--- |
| `com.nwyfre.nwyfre` | Mod entry point, client entry point, config. |
| `...registry` | `DeferredRegister` holders (attachments, and later items/blocks/entities). |
| `...stage` | The game stage system and its public API. |
| `...ritual` | The ritual system: recipe, effects, prerequisites, patterns, diagnostics. |
| `...block`, `...block.entity` | Blocks and block entities. |
| `...datagen` | Data generators. Output goes to `src/generated/resources`. |
| `...network` | Payload definitions and registration. |
| `...client` | Client-only code. **Must never be loaded on a dedicated server.** |

New packages are created when the first real class needs them — not ahead of time.

---

## Testing layers

Three layers, each with a different job. Pick the cheapest one that can actually catch the bug.

| Layer | Command | Use it for |
| :--- | :--- | :--- |
| JUnit | `./gradlew test` | Pure logic, codecs, data-file lints. Seconds. |
| GameTest | `./gradlew runGameTestServer` | Anything needing a live server, world, or real player. |
| Manual | `./gradlew runClient` | Rendering, GUIs, feel. |

**Prefer JUnit.** Reach for a GameTest only when the thing under test genuinely needs the game —
attachments actually persisting, events firing through the real bus, commands executing.

### Writing a GameTest

Every GameTest needs a structure template, even a logic-only one. `nwyfre:empty` (a 1x1x1 air
template at `src/gametest/resources/data/nwyfre/structure/empty.nbt`) exists for that — reuse it.

```java
@GameTestHolder(Nwyfre.MODID)     // sets the default template namespace to "nwyfre"
@PrefixGameTestTemplate(false)    // don't prefix template names with the class name
public final class MyGameTests {

    @GameTest(template = "empty")
    public static void somethingHolds(GameTestHelper helper) {
        ServerPlayer player = unplacedPlayer(helper);   // see below - do NOT use the mock helper
        if (!someCondition) {
            helper.fail("explain what was expected");
        }
        helper.succeed();   // required - a test that never succeeds fails on timeout
    }
}
```

#### Getting a player: do not use `makeMockServerPlayerInLevel()`

`GameTestHelper.makeMockServerPlayerInLevel()` looks like the obvious choice and **does not work in
this project.** It calls `PlayerList.placeNewPlayer`, which fires the join handlers of every loaded
mod; KubeJS then tries to push `kubejs:sync_server_data` down a connection that cannot accept play
payloads, and *every* test fails before reaching its own assertions:

```
Payload kubejs:sync_server_data may not be sent to the client!
```

Construct the player directly instead, without placing it. Attachments, events and persistence are
all entity-level concerns and none of them need a connection:

```java
private static ServerPlayer unplacedPlayer(GameTestHelper helper) {
    ServerLevel level = helper.getLevel();
    return new ServerPlayer(
            level.getServer(),
            level,
            new GameProfile(UUID.randomUUID(), "nwyfre-test-player"),
            ClientInformation.createDefault());
}
```

Nothing is registered anywhere, so there is nothing to clean up — and no deprecated API in sight.
Copy `StageGameTests.unplacedPlayer`. If a test genuinely needs a *connected* player, it needs a
different approach than a GameTest.

Rules worth remembering:

- **Always call `helper.succeed()`**, or the test fails on timeout rather than on its assertion.
- **Use `nwyfre_test:` ids** for stages and other test data, so tests never depend on real content.
- Avoid registering permanent event listeners. If a test needs one, gate it behind a flag that is
  reset in `finally`, so a mid-test failure cannot leak behaviour into the next test (see
  `StageGameTests.attachVetoListener`).
- **Read the log, not just the exit code.** A GameTest server that fails to *start* still reports
  `BUILD SUCCESSFUL`. Confirm the line `All N required tests passed :)` actually appears.
- If startup dies on `DirectoryLock` / "another process has locked a portion of the file", an
  orphaned dev server is still holding `run/world`. See the gotchas below.

---

## Test data vs. production data

**The content author owns production data outright.** Editing the real Patchouli book, renaming an
entry, rewriting prose — none of that may break a test. A test that asserts on real content turns
every content edit into a build failure, so we do not write those.

The split:

- **Assertions about behaviour** go against **fixtures** in `src/test/resources`, which never
  change. `fixtures/book/valid` and `fixtures/book/legacy` are stub books whose only job is to pin
  down what the lint accepts and rejects.
- **Assertions about production data** check **structural invariants only** — things an author
  cannot legitimately violate. `BookOfNwyfreLayoutTest` checks the real book's *layout*, never its
  content.

Applying this to a new system:

1. Build the check so it takes a path or root rather than hardcoding the production location.
2. Test the check against a good fixture and a deliberately broken one.
3. Point it at production data asserting only invariants.

The broken fixture should reproduce a failure that actually happened. `fixtures/book/legacy`
reproduces the `use_resource_pack: false` mistake that silently skipped the real book.

---

## Game stages

Stages gate progression: recipes, item use, JEI visibility, Patchouli entries, InControl! rules.

**Stage ids are free-form `ResourceLocation`s.** There is no registry of valid stages and nothing
hardcodes a stage name, so a pack can invent `mypack:bronze_age` from a KubeJS script with no Java
change. That is deliberate — don't add a stage registry without a concrete reason.

### Reading stages

```java
if (Stages.has(player, ResourceLocation.fromNamespaceAndPath("nwyfre", "awakening"))) { ... }

PlayerStages stages = Stages.get(player);
stages.hasAll(required);   // every one    (an empty requirement passes)
stages.hasAny(candidates); // at least one (an empty candidate set fails)
```

Safe on both sides. The client only knows what has been synced to it — fine for display and
filtering, never trust it for authority.

### Changing stages

Server-side only, via `ServerPlayer`:

```java
boolean changed = Stages.grant(player, stage);
boolean removed = Stages.revoke(player, stage);
int cleared     = Stages.clear(player);
```

Each returns whether anything **actually** changed. `false` means either the player was already in
that state, or a listener cancelled the event — callers should use it to skip redundant work.
Persistence and client sync are handled inside; don't do either by hand.

Operators and pack authors get `/nwyfre stage grant|revoke|clear|list` (level 2; `list` is open).

### Reacting to stage changes

`NwyfreStageChangeEvent` fires on the **game bus**, server-side, and is **cancellable**:

```java
@SubscribeEvent
static void onStageChange(NwyfreStageChangeEvent event) {
    if (event.isGrant() && event.getStage().equals(GATED)) {
        event.setCanceled(true);   // veto it
    }
}
```

It fires only when the change would really alter the stage set — re-granting an owned stage fires
nothing. Cancelling leaves the player untouched and makes the originating call return `false`.

### Adding a new gate

1. Read through `Stages`, never the attachment.
2. Decide the side. Authority is server-side; the client copy is for display only.
3. If the gate affects the client (JEI, Patchouli, tooltips), confirm the data is synced.
4. Test the logic in JUnit with `nwyfre_test:` ids; add a GameTest only if it needs a live player.
5. Document the stage in the Patchouli book if players need to know about it.

---

## Rituals

> Player- and pack-author-facing documentation lives in **`guides/rituals.md`**: how to perform a
> ritual in-game, and the full JSON reference. This section covers only how the code is arranged.

A ritual is **entirely data**. Only the *kinds* of thing a ritual can do live in Java, which is what
lets a pack re-tier Nwyfre without compiling anything. Three file types, two of them shareable:

| File | What it is |
| :--- | :--- |
| `data/<ns>/nwyfre/element/<id>.json` | an element: colour plus the compass direction it sits at |
| `data/<ns>/nwyfre/ritual_pattern/<id>.json` | the circle: a layered char grid of cell predicates |
| `data/<ns>/recipe/ritual/<id>.json` | the ritual itself, a `nwyfre:ritual` recipe |

Elements and patterns are **datapack registries**; the recipe is a vanilla recipe type, which is what
buys `/reload`, client sync and KubeJS `ServerEvents.recipes` for nothing.

### Offerings are keyed by element

North is Earth, east Air, south Fire, west Water. The pattern says *where* each element's bowl sits;
the recipe says *what* goes in it. Consequences worth knowing:

- **Patterns never rotate or mirror.** Direction carries meaning, so a rotating matcher would
  silently put bowls at the wrong elements. Authors draw the circle facing the right way.
- In a pattern layer, **row index increases south (+Z) and column east (+X)**, so a layer reads like
  a map with north at the top. `PatternMatcherTest` pins this down; if it ever flipped, every
  shipped circle would break quietly.
- Exactly one cell is `nwyfre:altar`. It fixes the X/Z origin, and Y comes from
  `offset_y + layerIndex`, so that cell must land on Y 0.

### The three extension points

| Add a... | Where | Needs Java? |
| :--- | :--- | :--- |
| ritual | a recipe JSON | no |
| circle, element | a datapack registry JSON | no |
| *kind* of outcome | `NwyfreRitualEffects` + a `RitualEffect` | yes |
| *kind* of gate | `NwyfrePrerequisites` + a `RitualPrerequisite` | yes |

Both registries are real registries, so another mod can contribute to them. `nwyfre:run_command` is
the escape hatch for packs that need an outcome Nwyfre has no effect for.

### Prerequisites, not ICondition

Prerequisites are a composable tree (`all_of` / `any_of` / `not` over stage, time, moon, weather,
biome, dimension, elevation, block, fluid). They are deliberately **not** NeoForge `ICondition`:
that type is built for load-time filtering and cannot describe itself, which is why Occultism needed
a whole package of wrappers and visitors to turn a failing condition into a readable sentence. Every
term here carries `describe()`, and that one method serves the diagnostics, JEI and the guidebook.
`nwyfre:pack_condition` wraps an `ICondition` for packs that want one, with the caveat that it can
only describe itself generically.

Two rules to remember:

- They are evaluated **at ritual start**, not at datapack load. That is what makes biome, weather and
  time gating work at all.
- `sustain_prerequisites` re-checks every tick and is **off by default**, so a long night-only ritual
  does not destroy itself at sunrise.
- Empty `all_of` passes and empty `any_of` fails, matching `PlayerStages.hasAll` / `hasAny`.

### Integrations

Each lives in its own class under `...integration.<mod>` and is reached only through the host mod's
own discovery mechanism, so the class never loads when that mod is absent.

| Mod | Entry point | Discovered by |
| :--- | :--- | :--- |
| JEI | `integration.jei.NwyfreJeiPlugin` | `@JeiPlugin` |
| Jade | `integration.jade.NwyfreJadePlugin` | `@WailaPlugin` |
| KubeJS | `integration.kubejs.NwyfreKubeJsPlugin` | `kubejs.plugins.txt` at the jar root |
| Patchouli | `integration.patchouli.NwyfrePatchouliIntegration` | our own event handler, `ModList`-guarded |

Three things worth knowing:

- **JEI hides on stages only.** `NwyfreJeiPlugin.hiddenByStages` walks the prerequisite tree for
  `StagePrerequisite` and withholds the recipe when those are unmet — that is CLAUDE.md §1's
  stage-aware JEI rule. Every other prerequisite is *displayed* instead, because a night-only ritual
  must not vanish from JEI in the daytime. The walk deliberately does **not** descend into `any_of`
  or `not`: there a stage term is one alternative or an inverted requirement, so an unmet stage does
  not mean the ritual is unreachable.
- **JEI lays offering slots on a compass**, reading each element's direction from the synced element
  registry. That is why the registry declares a network codec.
- **Patchouli multiblocks are generated from the patterns**, not hand-written
  (`RitualPatternMultiblocks`). Maintaining a second copy of a circle's shape would guarantee the
  two eventually disagree, and the book is where that does the most damage. Registration happens on
  `ServerStartedEvent` and `OnDatapackSyncEvent` rather than at mod setup, because patterns are a
  datapack registry and do not exist until something loads them. The generated id is
  `nwyfre:pattern/<pattern namespace>/<pattern path>`.

KubeJS needs no integration code to *author* rituals — `nwyfre:ritual` is an ordinary recipe type,
so `event.custom({...})` works. The plugin adds a `Nwyfre` global for progression and registry
introspection.

### Testability is designed in

Two deliberate choices keep nearly all ritual coverage in JUnit rather than GameTest:

- `PatternMatcher` takes a `Function<BlockPos, BlockState>`, not a level, so the matcher runs against
  a plain `Map`.
- `PrerequisiteContext` is an **interface** of the narrow queries terms need (including
  `stages()` rather than a player), so no `ServerLevel` is required.

Copy that pattern for anything new here. What genuinely needs a GameTest is wiring: datapack
registries loading, tag-form ingredients resolving, attachments persisting.

---

## Persistent player data

**Use Data Attachments, not capabilities.** On NeoForge 1.21.1 capabilities are non-persistent
behaviour lookups; attachments are what survive a save. Reserve `RegisterCapabilitiesEvent` for
genuine behaviour lookups on items, blocks and entities.

```java
public static final Supplier<AttachmentType<PlayerStages>> PLAYER_STAGES =
        ATTACHMENT_TYPES.register("player_stages", () -> AttachmentType
                .builder(PlayerStages::new)
                .serialize(PlayerStages.CODEC)   // omit and it will NOT persist
                .copyOnDeath()                   // omit and it is lost on death
                .build());
```

Register the `DeferredRegister` on the **mod** event bus in the `Nwyfre` constructor.

Pitfalls:

- `.serialize(...)` is what makes data persist. Without it, everything silently resets on reload.
- `.copyOnDeath()` is what makes it survive dying. Progression almost always wants it.
- Mutating the value in place does not mark the holder dirty — call `setData` afterwards.
  `Stages.markChanged` is the pattern.
- Attachments serialise under the NBT key `neoforge:attachments`, each under its registry id.
  Useful for asserting persistence in a GameTest.

---

## Networking

Payloads live in `...network`, are registered on `RegisterPayloadHandlersEvent` (mod bus), and are
versioned via `event.registrar("1")`.

```java
public record MyPayload(...) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MyPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(Nwyfre.MODID, "my_payload"));
    public static final StreamCodec<ByteBuf, MyPayload> STREAM_CODEC = StreamCodec.composite(...);
}
```

**Never load client classes from a handler's enclosing class.** Reference the client class only from
*inside* the handler body — it executes client-side only, so the class is never loaded on a
dedicated server:

```java
registrar.playToClient(TYPE, STREAM_CODEC,
        (payload, context) -> context.enqueueWork(() -> ClientThing.accept(payload)));
```

Stage sync sends the **whole set**, not a delta — it is tiny, and an absolute value cannot drift out
of sync if a packet is missed around login. Resync points live in `StageSyncEvents` (login, respawn,
dimension change); in-play changes are pushed by `Stages`.

**Always unit-test a `StreamCodec` round trip.** A mismatched encode/decode pair desyncs clients
silently instead of throwing. `StageSyncPayloadTest` is the pattern: encode, decode, and assert the
buffer was fully consumed.

---

## Patchouli book layout

Patchouli 1.20 removed the datapack content layout. The book is split across two trees:

```
src/main/resources/data/nwyfre/patchouli_books/book_of_nwyfre/book.json      <- definition only
src/main/resources/assets/nwyfre/patchouli_books/book_of_nwyfre/en_us/
        categories/<category>.json
        entries/<category>/<entry>.json
```

`book.json` **must** set `"use_resource_pack": true`. Get this wrong and the book is skipped with a
single log line — no crash, no build failure. `BookOfNwyfreLayoutTest` guards against it.

Every mechanic, ritual, boss and talisman needs an entry (`CLAUDE.md` §4). Use `flag` /
`advancement` locks so entries appear only once the player has earned them.

---

## Integration dependency policy

| Scope | Meaning | Libraries |
| :--- | :--- | :--- |
| `implementation` | Structural. Nwyfre cannot run without it. | GeckoLib, Curios |
| `compileOnly` + `localRuntime` | Optional. Compiled against, loaded in dev, never a published dependency. | JEI, KubeJS, Patchouli, Jade, InControl! |

Versions live in `gradle.properties`, each with the Maven it came from. **Verify a version exists
before pinning it** — check the repo's `maven-metadata.xml`, or Modrinth's
`/v2/project/<slug>/version?loaders=["neoforge"]&game_versions=["1.21.1"]` for mods with no
developer Maven.

Guard optional integrations at runtime with `ModList.get().isLoaded("<modid>")` and keep each
integration's code in its own class, so that class only loads when the mod is present.

A hard dependency must also be declared in `src/main/templates/META-INF/neoforge.mods.toml`. Do that
when code actually starts using it, not when the Gradle dependency is added — declaring it early
makes the mod refuse to load over something nothing uses yet.

---

## Live traps

**Only standing problems belong here** — things that can still bite tomorrow. Bugs that were found
and fixed are not tracked; the fix and its reasoning live in a comment next to the code.

- **Removing an attachment does not clear it on the client.** NeoForge's
  `AttachmentHolder.deserializeAttachments` iterates only the keys *present* in the incoming tag and
  `put`s each one; a key that is absent is left alone. So a `removeData` on the server never reaches
  a client copy, and stale attachment data survives until the holder is rebuilt (a respawn, a chunk
  reload). This showed up as a Jade tooltip still reporting a finished ritual as "Working".
  **Never read an attachment client-side for display** -- use Jade's `IServerDataProvider`, or sync
  an absolute value through a payload or the block entity's own NBT, where absence is representable.
- **A block entity holding an item renders nothing without a `BlockEntityRenderer`.** Obvious in
  hindsight, invisible in practice: the data is there, Jade reports it, and the block looks empty.
  `HeldOfferingRenderer` draws the altar's and the bowls' contents.
- **`useWithoutItem` is reached ONLY when `useItemOn` returns `PASS_TO_DEFAULT_BLOCK_INTERACTION`**
  -- even when the player's hand is empty. Both halves of this bite:
  - Returning `PASS_TO_DEFAULT_BLOCK_INTERACTION` while *holding* an item falls through to the
    empty-hand path, so a stray click with anything in hand ran the destructive branch (it emptied
    a full ritual bowl, and cancelled a running ritual).
  - Returning `FAIL`/`SUCCESS` for an empty hand silently disables the empty-hand path entirely.

  The rule: branch on `stack.isEmpty()` first and pass through for an empty hand; return `FAIL` when
  holding something that should do nothing. `RitualBowlBlock` and `RitualAltarBlock` both do.
- **`Ingredient.getItems()` hides an empty tag behind a `minecraft:barrier`.** A tag that does not
  exist yields an ingredient whose `getItems()` returns *one* item -- a barrier named
  "Empty Tag: ..." -- while `test(...)` matches nothing. So a recipe referencing a typo'd tag loads,
  shows up in JEI, and silently never fires. Check **`Ingredient.hasNoItems()`**, never
  `getItems().length`. `RitualDataGameTests.everyShippedIngredientResolvesToSomething` guards every
  shipped recipe.
- **There is no `c:coals` tag.** The coal/charcoal tag is vanilla `minecraft:coals`. NeoForge's `c:`
  namespace has `c:crops`, `c:gems/*`, `c:ores/*` and friends, but not coals -- verify a `c:` tag
  exists before using it.
- **A particle referencing a non-existent sprite renders as the missing-texture checkerboard**, with
  no warning. Notably `minecraft:end_rod` is *not* a sprite; vanilla's end-rod particle is drawn
  from the `glitter_*` set. Particle definitions are therefore generated by
  `NwyfreParticleDefinitionProvider`, which fails `runData` naming the bad sprite -- a headless unit
  test cannot see vanilla textures, so datagen is the only layer where this can be checked.
- **A datapack registry outside the `minecraft` namespace nests under its own namespace.** The
  directory is `data/<pack>/<registry-namespace>/<registry-path>/`, so `nwyfre:ritual_pattern` loads
  from `data/nwyfre/nwyfre/ritual_pattern/` -- note the doubled `nwyfre`. Put the files one level
  too shallow and the registry is simply **empty**: no crash, no log line. `RitualDataGameTests`
  exists because of this.
- **Recipe ids include the subfolder.** `data/nwyfre/recipe/ritual/sunrise.json` is
  `nwyfre:ritual/sunrise`, not `nwyfre:sunrise`.
- **`ItemStack` does not override `equals`.** Vanilla compares stacks with `ItemStack.matches`, so a
  record carrying an `ItemStack` (such as `RitualRecipe`'s icon) falls back to identity for that
  field and is never equal across a round trip. Compare field by field -- see
  `RitualRecipeCodecTest.assertEquivalent`.
- **Chaining `.validate(...)` straight onto `RecordCodecBuilder.create(...)` defeats type
  inference.** Assign the raw codec to a field first, then validate it. Both `RitualPattern` and
  `RitualRecipe` do.
- **A headless JUnit bootstrap has no tags and no datapack registries.** `Bootstrap.bootStrap()` plus
  `RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)` gives the static registries
  only, so `"#some:tag"` ingredients and biome references cannot resolve there. Cover those forms
  with shipped data a GameTest asserts on instead.
- **`EventBusSubscriber.Bus` is deprecated for removal.** Omit `bus = ...`; NeoForge routes by event
  type. Verify from the log — a misrouted mod-bus event leaves the handler silently unregistered:
  `Subscribing @EventBusSubscriber class ... to the mod event bus`.
- **KubeJS has unresolvable transitive deps** (`animated-gif-lib-for-java` declares the malformed
  range `[animated-gif-lib-1.7,)`; `better-advanced-tooltips` is on no public Maven). Take it with
  `transitive = false`; its shipped jar JarJars what it needs. Re-check on every version bump.
- **`makeMockServerPlayerInLevel` breaks every GameTest** when the integrations are loaded, because
  `placeNewPlayer` fires KubeJS's join handler. Build an unplaced `ServerPlayer` instead — see
  [Writing a GameTest](#writing-a-gametest).
- **Stopping a dev run can orphan the game JVM.** Killing the Gradle wrapper does not always kill
  the forked server, and the survivor keeps `run/world/session.lock` held, so the next
  `runServer` / `runGameTestServer` dies on `DirectoryLock`. Find it and kill it:

  ```powershell
  Get-CimInstance Win32_Process -Filter "Name like '%java%'" |
    Where-Object { $_.CommandLine -match 'RunProgramArgs' } |
    Select-Object ProcessId, CreationDate
  ```

- **`loadedMods` on a run config does not exclude jar mods.** It governs which dev source-set mods
  are loaded; mods coming from `localRuntime`/`implementation` jars load regardless. There is no
  quick way to run a dev task with the integrations switched off.
- **A GameTest server that fails to start still reports `BUILD SUCCESSFUL`.** Only a *test* failure
  produces a non-zero exit. Always check for `All N required tests passed :)`.
- **Gradle 10 deprecations** are reported by the current build. Not urgent; expect noise on a
  wrapper bump.
