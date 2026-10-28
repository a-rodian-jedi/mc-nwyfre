# Rituals

Two halves. [Part 1](#part-1--performing-the-rite-of-first-breath) walks you through performing a
ritual in-game. [Part 2](#part-2--writing-your-own-rituals) is the reference for writing your own.

Nothing here needs Java. A ritual is three JSON files, two of which are shareable between rituals.

---

## Part 1 — Performing the Rite of First Breath

The smallest ritual in the mod. It needs a stick and a block of dirt, has no prerequisites at all,
and works at any hour, in any weather, anywhere. If it refuses, the circle is wrong — and the circle
will tell you what is wrong.

### 1. Get the blocks

Creative, or paste these into chat:

```
/give @s nwyfre:ritual_altar
/give @s nwyfre:ritual_bowl
/give @s nwyfre:chalk_glyph 4
/give @s minecraft:stick
/give @s minecraft:dirt
```

Find a flat patch of ground. The chalk glyph is a flat decal and **needs a solid block beneath it**
— it pops off if you place it over a hole or on top of grass that later gets broken.

### 2. Build the circle

Face **north** (press F3 and check that `Facing` reads `north`). Everything below is described from
there, because **direction is part of a ritual's meaning and circles never rotate**.

```
        NORTH
      ▓   ▒   ▓          ▓ = chalk glyph  (4, at the corners)
          ·              ▒ = ritual bowl  (1, due north of the altar)
      ▓   ■   ▓          ■ = ritual altar (1, in the middle)
                         · = leave empty
        SOUTH
```

Concretely:

1. Place the **ritual altar**.
2. Place a **ritual bowl** one block due **north** of it.
3. Place **four chalk glyphs** at the four diagonal corners — northwest, northeast, southwest,
   southeast — each one block out from the altar.

Leave the east, west and south cells empty. Six blocks total.

### 3. Make the offering

**Right-click the bowl holding a block of dirt.** The dirt sits visibly in the bowl.

North is the quarter of **Earth**, and dirt is an earthen thing — that correspondence is the whole
idea behind where bowls go. (Right-click the bowl empty-handed to take the dirt back out.)

### 4. Perform it

**Hold a stick and right-click the altar.**

The altar lights, the offering is drawn out of its bowl after a moment, and about three seconds
later an **amethyst shard** appears at the altar. The altar goes dark again.

That is a complete ritual: a circle, an offering, a token, an outcome.

### 5. Try an augment

Place a **chest within 3 blocks** of the altar and put **glowstone dust** in it. Do the ritual again
— the same circle, the same dirt, the same stick — and you get **four** shards instead of one.

The glowstone is **not consumed**. An augment sits apart from the circle and acts as a reusable
catalyst, not an ingredient. (A pack can change that; see `consumeAugments` in the config.)

### 6. Try getting it wrong

This is worth doing deliberately, because the diagnostics are most of what makes rituals playable.

| Do this | What you get |
| :--- | :--- |
| Break one glyph, then right-click the altar with a stick | Glowing particles hover exactly where the missing glyph goes, and the chat says how many marks are missing |
| Remove the bowl entirely and retry | Particles mark where the bowl belongs — a different message, because not knowing bowls exist is a different problem from a gapped circle |
| Empty the bowl and retry | *"The circle waits on its offerings: Earth (north): Dirt"* — named by element **and** direction |
| Hold a diamond instead of a stick and retry | *"The circle is whole, but it does not answer to this"*, followed by what it does answer to |
| Right-click the altar **empty-handed** | The same explanation, without having to guess at a token |

An empty-handed right-click on an altar is always safe and always tells you the current state. It is
the single most useful thing to know.

### Where to go next

Three more rituals ship with the mod, all using the larger `nwyfre:lesser_circle` (a 7×7 ring with
bowls at all four quarters):

| Ritual | Token | Offerings | Does |
| :--- | :--- | :--- | :--- |
| Rite of Sunrise | Clock | coal (S), feather (E) | Skips to dawn. **Night only** |
| Rite of Clear Skies | Wheat Seeds | water bucket (W), feather (E) | Clears the weather. Must be built **on dirt**, overworld only |
| Vigil of Mending | Candle | golden carrot (N), glistering melon slice (W) | **Persistent**: leaves a lit candle granting Regeneration in a 16×16×16 cube |

The Vigil is worth seeing, because persistent rituals behave differently: the candle stays in the
altar, **right-click the altar to snuff or relight it**, and an unlit candle does not burn down — so
you can put it out and save it. When its time is spent the candle is destroyed.

Note an altar hosting a lit candle is busy and will not accept another ritual until the candle burns
out or you take it.

---

## Part 2 — Writing your own rituals

### The three files

| File | Holds | Shared? |
| :--- | :--- | :--- |
| `data/<ns>/nwyfre/element/<id>.json` | an element, and the direction it sits at | by every pattern |
| `data/<ns>/nwyfre/ritual_pattern/<id>.json` | the circle: what blocks go where | by every ritual using that circle |
| `data/<ns>/recipe/ritual/<id>.json` | the ritual itself | one per ritual |

> **Mind the doubled namespace.** Elements and patterns are *datapack registries*, and a datapack
> registry outside the `minecraft` namespace nests under its own: the path really is
> `data/yourpack/`**`nwyfre`**`/ritual_pattern/`. Put the file one level too shallow and it loads as
> **nothing at all** — no crash, no warning. This is the single easiest mistake to make here.
>
> Recipes are ordinary vanilla recipes and do *not* double up: `data/yourpack/recipe/ritual/`.
> Their ids include the subfolder, so that file is `yourpack:ritual/<id>`.

Most packs never need to write an element or a pattern — reuse `nwyfre:lesser_circle` and the four
built-in elements, and write only a recipe.

### A minimal ritual

Three fields are required: a circle, something to start it, and an outcome.

```jsonc
{
  "type": "nwyfre:ritual",
  "pattern": "nwyfre:lesser_circle",
  "activation_item": { "item": "minecraft:clock" },
  "effect": { "type": "nwyfre:set_time", "time": "sunrise" }
}
```

### The full shape

```jsonc
{
  "type": "nwyfre:ritual",

  "pattern": "nwyfre:lesser_circle",
  "activation_item": { "item": "minecraft:amethyst_shard" },

  // Keyed by element, not a flat list. The pattern decides WHERE each bowl is.
  "offerings": {
    "nwyfre:earth": { "tag": "c:crops" },
    "nwyfre:fire":  { "item": "minecraft:coal" }
  },

  "duration": 120,          // ticks; default 100
  "persistent": false,      // default false
  "burn_ticks": 24000,      // persistent only: how long the candle lasts while lit

  // Checked once, when the ritual STARTS.
  "prerequisites": {
    "type": "nwyfre:all_of",
    "terms": [
      { "type": "nwyfre:stage", "stages": ["mypack:bronze_age"] },
      { "type": "nwyfre:time_of_day", "time": "night" }
    ]
  },
  "sustain_prerequisites": false,   // re-check every tick and interrupt if they stop holding

  // What the player must do WHILE it runs.
  "sacrifice":   { "tag": "minecraft:zombies", "display_name": "mypack.sacrifice.zombies" },
  "item_to_use": { "item": "minecraft:experience_bottle" },

  "effect": { "type": "nwyfre:summon", "entity": "minecraft:wolf", "count": 2, "tame": true },

  "augments": [
    { "requires": [{ "item": "minecraft:nether_star" }],
      "effect":   { "type": "nwyfre:summon", "entity": "minecraft:iron_golem" } },
    { "requires": [{ "tag": "c:gems/diamond" }, { "tag": "c:gems/diamond" }],
      "potency": 2 }
  ],

  "icon": { "id": "minecraft:wolf_spawn_egg" }    // shown in JEI and the guidebook
}
```

### Effects — what a ritual does

The `type` is a key into the `nwyfre:ritual_effect` registry.

| Effect | Fields | Notes |
| :--- | :--- | :--- |
| `nwyfre:set_time` | `time`: `sunrise`/`noon`/`sunset`/`midnight`, or `ticks` | Always moves **forwards**. Asking for sunrise at noon reaches tomorrow's sunrise — rewinding the clock would undo a day of crop growth and hunger. Cannot be persistent |
| `nwyfre:set_weather` | `weather`: `clear`/`rain`/`thunder`, `duration` | Clearing also cancels a thunderstorm |
| `nwyfre:area_effect` | `effect`, `amplifier`, `radius`, `interval`, `affect_players`, `affect_others` | `radius: 8` is a 16×16×16 cube. The natural choice for a persistent ritual. **`potency` adds to the radius** |
| `nwyfre:give_item` | `result` (a full item stack) | Lands in a bowl on top of the altar if there is one, else drops. Because `result` is a full stack with components, this covers enchanted output too. Cannot be persistent |
| `nwyfre:summon` | `entity`, `count`, `nbt`, `tame` | **`potency` adds to the count.** Cannot be persistent |
| `nwyfre:run_command` | `commands`: one string or a list | The escape hatch for anything Nwyfre has no effect for. Runs at permission level 2 from the altar |

A persistent ritual whose effect cannot repeat is **rejected when the datapack loads**, not silently
at play time. A candle handing out a diamond every second is a mistake, not a design.

### Prerequisites — when a ritual may start

A single term, usually a composite. Every term explains itself to the player when it fails, which is
why this is a purpose-built tree rather than NeoForge's `ICondition`.

| Term | Fields |
| :--- | :--- |
| `nwyfre:all_of` | `terms` — all must hold. **Empty passes** |
| `nwyfre:any_of` | `terms` — one must hold. **Empty fails** |
| `nwyfre:not` | `term` |
| `nwyfre:stage` | `stages`, `mode`: `all` (default) / `any` |
| `nwyfre:time_of_day` | `time`: `day`/`dusk`/`night`/`dawn`, or `min`+`max` ticks |
| `nwyfre:moon_phase` | `phases`: list of 0–7. **0 is the FULL moon** |
| `nwyfre:weather` | `weather`: `clear`/`rain`/`thunder` |
| `nwyfre:biome` | `biomes`: an id, a list, or `"#some:tag"` |
| `nwyfre:dimension` | `dimension` |
| `nwyfre:elevation` | `min`, `max` — altar Y, inclusive |
| `nwyfre:block_at` | `offset` (default `[0,-1,0]`, i.e. underneath), `blocks` |
| `nwyfre:fluid_at` | `offset` (default `[0,0,0]`, i.e. the altar itself), `fluids` |
| `nwyfre:pack_condition` | `condition`: any NeoForge `ICondition` |

Three things to know:

- They are evaluated **when the ritual starts**, not when the datapack loads. That is what makes
  biome, weather and time gating possible at all.
- `sustain_prerequisites: true` re-checks every tick. **Off by default**, deliberately: a long
  night-only ritual would otherwise destroy itself at sunrise.
- `nwyfre:pack_condition` is the one term that **cannot explain itself** — `ICondition` carries no
  human-readable description, so a failure only produces a generic line. Prefer a native term.
- A `nwyfre:stage` term refuses a ritual started by **automation**, because a hopper has no stage
  set to check. Flip `allowUnattendedStagedRituals` if your pack wants that.

### Augments — bending a ritual

Items left in **any container within 3 blocks** of the altar, read through the item-handler
capability, so a vanilla chest, a barrel or a modded crate all work.

```jsonc
"augments": [
  { "requires": [{ "item": "minecraft:nether_star" }],
    "effect": { "type": "nwyfre:set_time", "time": "sunset" } },   // swaps the outcome
  { "requires": [{ "tag": "c:gems/diamond" }, { "tag": "c:gems/diamond" }],
    "potency": 2 }                                                  // same outcome, stronger
]
```

- **First match in declaration order wins.** Order the list most-specific first; precedence is
  yours to control, not a hidden specificity rule.
- Repeated requirements mean repeated items: two diamond entries need **two separate** diamonds.
- An augment with no `requires` is rejected at load — it would match every ritual.
- Resolved **once, at start**, and snapshotted. Emptying the chest half-way changes nothing.
- Not consumed by default (`consumeAugments`).

### Patterns — drawing a circle

```jsonc
{
  "offset_y": 0,
  "layers": [
    [ "gEg",
      "_A_",
      "g_g" ]
  ],
  "mapping": {
    "_": { "type": "nwyfre:any" },
    "A": { "type": "nwyfre:altar" },
    "g": { "type": "nwyfre:tag", "tag": "nwyfre:chalk_glyphs", "display": "nwyfre:chalk_glyph" },
    "E": { "type": "nwyfre:tag", "tag": "nwyfre:ritual_bowls",
           "display": "nwyfre:ritual_bowl", "offering": "nwyfre:earth" }
  }
}
```

The grid reads like a map with **north at the top**: row index increases **south** (+Z), column
index increases **east** (+X).

Rules:

- Exactly **one** `nwyfre:altar` cell. It fixes the X/Z origin, and Y comes from
  `offset_y + layerIndex` — so the altar cell must land on **Y 0**. With the default `offset_y` of
  0 that means drawing it in `layers[0]`.
- Every row in a layer must be the same length.
- Two cells may not claim the same element.
- Any cell may carry `offering: <element>` to make it a bowl, and `display: <block>` to tell
  previews what to draw when the predicate is a tag.

Cell types: `nwyfre:any` (filler), `nwyfre:air` (must be clear), `nwyfre:block`, `nwyfre:tag`,
`nwyfre:altar`.

**Patterns never rotate or mirror.** Direction carries meaning, so a rotating matcher would silently
put bowls at the wrong elements.

Prefer `nwyfre:tag` over `nwyfre:block`: a pattern written against `#nwyfre:ritual_bowls` keeps
working when new bowl materials are added to that tag.

### Elements

```jsonc
{ "colour": 8017466, "direction": "north" }
```

Four ship with the mod — `nwyfre:earth` (north), `nwyfre:air` (east), `nwyfre:fire` (south),
`nwyfre:water` (west). A pack can add a fifth; `direction` is display metadata, used by the
diagnostics to say *"Earth (north)"* and by JEI to lay slots out on a compass. Give it a name with
an `element.<ns>.<path>` language key.

### KubeJS

The recipe type is a normal recipe type, so `ServerEvents.recipes` works with no integration code:

```js
ServerEvents.recipes(event => {
  event.custom({
    type: 'nwyfre:ritual',
    pattern: 'nwyfre:lesser_circle',
    activation_item: { item: 'minecraft:clock' },
    offerings: { 'nwyfre:fire': { item: 'minecraft:coal' } },
    effect: { type: 'nwyfre:set_time', time: 'sunrise' }
  })
})
```

Ritual lifecycle events are posted on the game bus: `NwyfreRitualEvent.Starting` (**cancellable** —
cancelling leaves everything untouched), `.Completed`, and `.Stopped`.

### What the other mods show you

| Mod | What you get |
| :--- | :--- |
| **JEI** | A **Rituals** category. Look up the ritual altar to see every rite. Offering slots are laid out on a **compass**, matching where the bowls go; the token is in the middle, augments along the bottom. Prerequisites, the circle name and persistent burn time are written above |
| **Jade** | Point at an altar to see what it is working on, how many offerings it has taken, whether it is waiting on you, and — for a standing flame — how long the candle has left. Point at a bowl to see its offering without disturbing it |
| **Patchouli** | The *On Rites* entry shows the Lesser Circle as a walk-around hologram, generated from the pattern file itself so it can never disagree with what the game demands |
| **KubeJS** | `event.custom({...})` for authoring, plus a `Nwyfre` global |

A ritual gated on a **game stage** is hidden from JEI until you have that stage. Every other kind of
prerequisite is shown as text instead — a night-only ritual stays visible in the daytime, because
you have not failed to progress, you have just looked at the wrong hour.

### KubeJS bindings

```js
Nwyfre.hasStage(player, 'nwyfre:awakening')
Nwyfre.grantStage(player, 'mypack:bronze_age')   // -> true if it actually changed
Nwyfre.revokeStage(player, 'mypack:bronze_age')
Nwyfre.clearStages(player)                        // -> how many were removed
Nwyfre.getStages(player)

// Discovering what vocabulary is available, since other mods can add to these
Nwyfre.getRitualEffectTypes()
Nwyfre.getRitualPrerequisiteTypes()
Nwyfre.getRitualPatterns(level)
Nwyfre.getElements(level)
Nwyfre.getRituals(level)
Nwyfre.ritualExists(level, 'nwyfre:ritual/sunrise')
```

Stage changes go through the normal path, so persistence, client sync and the cancellable
`NwyfreStageChangeEvent` all still fire.

### Config

`config/nwyfre-common.toml`, `[rituals]`:

| Key | Default | Purpose |
| :--- | :--- | :--- |
| `durationMultiplier` | 1.0 | global pace |
| `strictOfferings` | true | a bowl the recipe does not name must be **empty** |
| `consumeAugments` | false | catalyst vs. ingredient |
| `augmentSearchRadius` | 3 | how far to look for a container |
| `sacrificeRange` | 8 | blocks |
| `itemUseRange` | 16 | blocks |
| `persistentTickInterval` | 20 | ticks between beats of a lit candle |
| `allowUnattendedStagedRituals` | false | may automation satisfy a stage gate |
| `diagnosticsRateLimitTicks` | 20 | throttles the diagnostics |

Leaving `strictOfferings` on is what keeps two similar rituals unambiguous. Without it, a circle laid
out for a four-element rite also satisfies a two-element one, and which fires comes down to recipe
iteration order.

### When a ritual does not fire

Right-click the altar **empty-handed** — that always reports the current state. Beyond that:

| Symptom | Cause |
| :--- | :--- |
| The ritual does not exist at all (no diagnostic mentions it) | Check the log for `Invalid ritual recipe`. A malformed recipe is **skipped**, not fatal |
| Patterns or elements seem absent | The doubled namespace. They belong in `data/<ns>/nwyfre/ritual_pattern/` |
| *"the nwyfre:altar cell must land on Y 0"* | `offset_y` and the layer the altar is drawn in disagree |
| Bowls answer to the wrong element | The grid is upside down. Row 0 is **north** |
| A persistent recipe is rejected at load | Its effect cannot repeat. Only `set_weather` and `area_effect` can |
| A pack ritual never matches, but a Nwyfre one does | Another recipe matched first. Make the activation item or offerings more specific |
| A stage-gated ritual will not start from a hopper | Working as intended — see `allowUnattendedStagedRituals` |
