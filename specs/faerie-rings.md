# Faerie Rings

A ring of mushrooms is a door. This pass builds the **framework**: notice when a player is standing
inside one, decide whether the ring will have them, and say so. Nothing happens yet beyond the
saying — that is deliberate, so the detection and the gating can be proven before anything is built
on top.

Graduated from `docs/ideas.md`. The ideas there that this does *not* implement — time-slip, markets,
a Faerie dimension, ring-sited rites — all hang off the API in [What this hands to the next
thing](#what-this-hands-to-the-next-thing).

---

## Scope

| In | Out |
| :--- | :--- |
| Detecting a ring from vanilla blocks | Generating rings in worldgen |
| Qualifying the **site** a ring stands on | An index of known rings (phase 2 — see [Cost](#cost-and-when-we-look)) |
| Knowing when a player is inside one | Anything happening when they are |
| Iron refusing the connection | Iron breaking a ritual, iron warding mobs |
| A message on every transition | Time-slip, markets, dimensions, trades |
| A query API and a cancellable event | The `nwyfre:in_faerie_ring` prerequisite |

**No new blocks and no new worldgen.** Rings are made of vanilla mushrooms. Vanilla does not
generate mushroom *circles*, so in practice the first rings will be player-built — which is fine for
proving the framework, and folklore is perfectly happy for a ring to simply appear. Seeding them in
worldgen is a later, separate job.

---

## What counts as a ring

The hard part is that a ring in the world is never neat. A fixed radius template would match almost
nothing, so this is a **loose matcher**: gather the candidate blocks, then ask whether they *behave*
like a ring.

Deliberately **not** a `RitualPattern`. Patterns are exact grids that never rotate — right for a
drawn circle an author controls, wrong for a ragged thing that grew.

### The algorithm

Given a position (the player's feet) and a block lookup:

1. **Gather.** Every block in `#nwyfre:faerie_ring_blocks` within `searchRadius` horizontally and
   `verticalSlack` vertically. Bail immediately if there are none — the overwhelmingly common case.
2. **Enough of them.** At least `minBlocks`.
3. **Centroid.** Mean X and Z of the gathered positions. This is the ring's centre; nothing has to
   tell us where the centre is, which is what makes the player's own position a usable starting
   point.
4. **Size.** Mean distance from centroid `r̄` must fall in `[minRadius, maxRadius]`.
5. **Roundness.** Every block's distance from the centroid within `radiusTolerance` of `r̄`. This is
   what rejects a mushroom *patch*: a blob has blocks near the centre, a ring does not.
6. **Spread.** Bucket each block into one of eight octants around the centroid; require at least
   `minOctants` occupied. This is what rejects an *arc* — six mushrooms in a neat curve down one
   side would otherwise pass every test above.
7. **Inside.** The player's horizontal distance from the centroid must be no more than `r̄`.

Returns `Optional<FaerieRing>`:

```java
public record FaerieRing(BlockPos centre, double radius, List<BlockPos> members) { }
```

### Shape of the code

```java
static Optional<FaerieRing> detect(Predicate<BlockPos> isRingBlock, BlockPos origin, Vec3 standingAt, Settings settings)
```

Takes a **predicate, not a level** — the same choice `PatternMatcher` makes, and for the same
reason: the whole matcher then runs against a plain `Set<BlockPos>` in JUnit with no server. Keep it
that way; everything interesting here is geometry, and geometry does not need a world.

### Known judgement calls

- **A square passes.** A 7×7 hollow square has corners at r≈4.95 and edge midpoints at r≈3.5, a
  spread of 1.45 — inside the default tolerance. Players build squares. Letting them through is the
  friendlier behaviour; tightening `radiusTolerance` below ~1.4 is the lever if that turns out to be
  wrong.
- **Rings do not nest.** Two concentric rings read as one blob and fail roundness. Acceptable.
- **The tag decides what a ring is made of.** Default: brown and red mushroom. A pack wanting
  rings of amethyst or bone is one tag file away, which is the point.

---

## Which rings count

### Provenance is not knowable, and chasing it is the wrong fix

There is no way to tell a naturally-generated ring from one a player built, because **a `BlockState`
carries no record of who placed it**. A mushroom from worldgen, one spread by a random tick in the
dark, one placed by hand, and one dropped in by `/fill` are byte-identical.

It could be tracked — record placements as they happen — but such an index is wrong by construction.
It would miss every ring that existed before the feature, every mushroom that spread on its own
(`MushroomBlock.randomTick` calls `setBlock` directly and fires no place event), and anything a
command or datapack created. The result is two identical-looking rings behaving differently with
nothing in the world to explain why. That is worse than not distinguishing them at all.

**So do not ask who made it. Ask where it stands.**

### What a tossed handful of mushrooms gets you

Two separate answers, because "toss" hides a distinction:

- **Dropped items never count.** Detection reads block states at positions; a mushroom lying on the
  ground as an item entity is invisible to it. You have to actually *place* them.
- **A placed ring does count — and should.** Building one is legitimate work, and folklore is
  perfectly happy for a ring to be made as well as found. What stops a mushroom farm becoming an
  infinite supply of doors is not provenance, it is that **the site has to qualify.**

### Site qualification

| Requirement | Gates | Why |
| :--- | :--- | :--- |
| Open sky over the centre | is it a ring | No rings in your storage room. |
| Natural ground beneath — `#nwyfre:faerie_ring_ground` | is it a ring | Grass, podzol, moss, mycelium, rooted and coarse dirt. Not courtyard paving. |
| Biome — `#nwyfre:faerie_ring_biomes` | is it a ring | Forest, flower forest, meadow, old growth, swamp, dark forest. |
| Night | does it **open** | A reason to come back after dark rather than a switch. |

The split matters: the first three decide whether the thing **is a ring**, the last whether the ring
**opens**. You can find a ring by daylight and recognise it for what it is; it works after dark.
That is a much better shape than a binary, and it gives the denial message a second job.

### The qualification is a prerequisite tree, not Java

This is the reuse win and it should shape the implementation.

`RitualPrerequisite` is already a composable tree — `all_of` / `any_of` / `not` over time of day,
moon phase, weather, biome, dimension, elevation, block and fluid — every term carries `describe()`,
and it evaluates against `PrerequisiteContext`, an interface of narrow queries rather than a level.
Point that context's origin at the **ring centre** instead of an altar and every one of those terms
works unchanged.

So ring qualification should be **a `RitualPrerequisite` loaded from data**, not conditions compiled
into the detector:

- A pack retunes what counts as a faerie ring using the same JSON vocabulary it already uses for
  rites. Nothing new to learn, nothing new to document.
- `describe()` supplies the denial message for free — "It must be night." — in the same voice as the
  ritual diagnostics.
- It stays unit-testable with no world, because the context is an interface.

Two small changes it needs:

1. **A sky query.** No existing term covers "can see sky". Add `canSeeSky(BlockPos)` to
   `PrerequisiteContext` and a `nwyfre:sky_visible` term in `WorldPrerequisites`. One method, one
   term, and it is reusable by rites that should only run under open sky.
2. **A rename.** `altarPos()` is named for the only caller it has ever had, and a ring centre is not
   an altar. Rename it `origin()` — a mechanical refactor across the terms, and it stops the
   interface lying about what it is.

---

## Iron

> "Wielding or wearing" — the carry rule is deliberately **not** inventory-wide. Every player has an
> iron pickaxe in a pocket, so an inventory-wide check would deny everyone always and the mechanic
> would read as broken rather than as folklore.

Checked slots: **main hand, off hand, and the four armour slots.** An item denies the connection if
it is in `#nwyfre:cold_iron`.

The tag ships populated with the things whose *material* is iron — the iron tool and armour sets,
chainmail, shears, bucket, flint and steel, shield, and the `c:` iron material tags
(`#c:ingots/iron`, `#c:nuggets/iron`, `#c:storage_blocks/iron`, `#c:raw_materials/iron`). A pack
retunes this by editing one tag; emptying it switches the mechanic off entirely, which is the
supported way to disable it.

**Curios slots are not checked in this pass.** They should be eventually — a talisman is worn — but
reaching for Curios means declaring it in `neoforge.mods.toml`, and CLAUDE.md is explicit that a
required dependency goes in there only once code actually uses it. Worth doing as its own small
change alongside the first real talisman.

### Testability

The slot check takes a `Predicate<ItemStack>` rather than resolving the tag itself. A headless JUnit
bootstrap has no tags at all (a live trap in `ARCHITECTURE.md`), so injecting the predicate is what
keeps this testable outside the game; a GameTest covers the real tag resolving.

---

## State and messages

### Where the state lives

A **non-serialized Data Attachment** on the player:

```java
record RingPresence(Optional<BlockPos> ringCentre, Connection connection, Optional<Component> refusal) { }
enum Connection { NONE, OPEN, DENIED }
```

`refusal` is why it is closed, carried as a ready-made `Component`: the cold-iron line, or a
qualification term's own `describe()`. Keeping the reason as text rather than an enum is what lets a
pack add a condition and have the message come out right with no Java.

Attachment rather than a static map, so it dies with the player and cannot leak. **No
`.serialize(...)`, and that is the point** — presence is re-derived every scan, and persisting "you
are inside a ring" across a reload would be a stale lie the moment the world loads with the player
somewhere else. This is the rare case where omitting `serialize` is correct rather than the bug
`ARCHITECTURE.md` warns about.

The centre is carried so that walking from one ring directly into another re-fires rather than
looking like no change at all.

### Transitions

Connection is a function of three things: standing inside a qualifying ring, the qualification
prerequisites passing *now*, and not carrying iron. Only the last two can change while you stand
still, and both produce a refusal.

| Was | Now | Message |
| :--- | :--- | :--- |
| `NONE` | `OPEN` | The ring opens to you. |
| `NONE` | `DENIED` | The refusal — cold iron, or the failing term's own `describe()`. |
| `OPEN` | `DENIED` | The refusal. (You drew iron; or dawn came.) |
| `DENIED` | `OPEN` | The ring opens to you. |
| `OPEN` | `NONE` | The ring lets you go. |
| `DENIED` | `NONE` | *(silent — nothing was ever open)* |

A changed `refusal` on an already-`DENIED` presence re-messages, so swapping one obstacle for
another still tells you what is now in the way.

Messages go to the **action bar** by default, not chat: this is ambient state, and chat would fill
with it on every step across a ring's edge. `messageMode` switches to chat or off.

---

## Cost and when we look

Scanned on `PlayerTickEvent.Post`, server side, every `scanIntervalTicks` (default 20).

**The "bail if there are no ring blocks" step is not free**, and it would be easy to read it as if it
were. Discovering that there are none still means visiting every position in the box. That early-out
saves the geometry; it does not save the scan.

The real numbers: a 13×13 box with ±2 vertical slack is 845 `getBlockState` calls. Dropping the
slack to ±1 — mushrooms sit on the ground, and so do the player's feet — makes it 507. Once per
second per player that is roughly 10–25µs; twenty players is well under a millisecond per second.
Real, but not a problem.

### Gate before scanning

The fix is not a cheaper scan, it is **not scanning most players at all** — and the site
requirements above are already exactly the right gates. Each is a single lookup, ordered
cheapest-first:

| Gate | Cost | Removes |
| :--- | :--- | :--- |
| Dimension | a field read | everyone in the Nether and the End |
| `canSeeSky` at the player | one heightmap read | everyone indoors, in a cave, or under a roof |
| Biome at the player | one biome lookup | everyone outside the ring biomes |

A player in a desert, a mineshaft or their own base never reaches the box scan. What survives all
three — standing outdoors, in a forest biome, under open sky — is a small fraction of players and is
exactly the population worth checking.

**No "has the player moved" shortcut.** It is the tempting next optimisation and it breaks the first
thing anyone will try: building a ring around yourself while standing still. The gates above are
worth far more and cost nothing in correctness.

### If that is still not enough: an index

Phase 2, and only on measurement. Build it as a **cache in front of the detector, never a
replacement:**

- `BlockEvent.EntityPlaceEvent` and `EntityMultiPlaceEvent` on a ring block → run detection around
  that position; if a ring completes, store it.
- `BlockEvent.BreakEvent` → invalidate any stored ring containing that position.
- Store it in a **serialized Data Attachment on `ChunkAccess`** (supported on 21.1), keyed by the
  chunk holding the ring's centre. It then persists and unloads with the chunk for nothing.
- The per-player check becomes a distance test against the 3×3 chunks around them — effectively
  free, and cheap enough to run every tick.

**Keep the gated poll as a backstop even then.** The index has the same blind spots that make
provenance tracking unworkable: random-tick mushroom spread fires no place event, and nothing fires
for pre-existing worlds, `/fill`, or datapack placement. An index alone would silently miss rings.

**Recommendation: build the gates now and not the index.** The gates are required by the design
regardless, they are three lookups, and they make the poll cheap enough that the index is
speculative work. Measure before adding it.

---

## Events

Mirrors `NwyfreStageChangeEvent` — game bus, server side, fired only on a real change.

| Event | Cancellable | Meaning |
| :--- | :--- | :--- |
| `NwyfreFaerieRingEvent.Connect` | **yes** | The ring is about to open. Cancelling leaves it closed. |
| `NwyfreFaerieRingEvent.Disconnect` | no | It closed. |

Both carry the `ServerPlayer` and the `FaerieRing`. Iron and the qualification tree are both
evaluated *before* `Connect`, so `Connect` firing always means "this would otherwise have worked" —
packs are not handed an event they have to re-derive the answer from. Packs disagreeing about iron
have the tag, packs disagreeing about the site have the qualification data, and packs needing a
genuinely dynamic veto have the cancel.

Per CLAUDE.md's event conventions: a cancelled `Connect` must leave the attachment in `DENIED` with
a refusal set, so the player is never told something opened when it did not.

---

## What this hands to the next thing

```java
public final class FaerieRings {
    public static Optional<FaerieRing> around(BlockGetter level, BlockPos pos);  // fresh detection
    public static Optional<FaerieRing> current(Player player);   // last scan, no work
    public static boolean connected(Player player);              // inside, and the ring consented
}
```

`current` and `connected` are reads of the attachment and cost nothing, so a tick-rate consumer
should use those; `around` is for one-off questions about a position with no player involved.

Two consumers are already in view and should shape the API now:

- **`nwyfre:in_faerie_ring` prerequisite.** `PrerequisiteContext` is an interface of narrow queries
  precisely so terms stay testable, so this means adding a `faerieRing()` query to it rather than
  handing the term a level.
- **KubeJS.** `Nwyfre.isInFaerieRing(player)` and `Nwyfre.faerieRingAt(level, pos)` on the existing
  bindings object.

---

## Config

A `Config.FAERIE_RINGS` block, all of it server-side. **Geometry and cost only** — what makes a
*site* qualify lives in the prerequisite data, not here, so the two are not configured in two
different places.

`searchRadius` 6 · `verticalSlack` 1 · `minBlocks` 6 · `minRadius` 2.0 · `maxRadius` 5.5 ·
`radiusTolerance` 1.5 · `minOctants` 6 · `scanIntervalTicks` 20 · `ironDenies` true ·
`messageMode` ACTION_BAR

---

## Testing

| Layer | Covers |
| :--- | :--- |
| JUnit | The detector, against a `Set<BlockPos>`: clean ring passes; blob fails on roundness; arc fails on octants; too few fails; player outside fails; too large and too small fail; the 7×7 square case pinned either way so a tolerance change is a visible decision. |
| JUnit | The iron check against an injected `Predicate<ItemStack>` — hand, off-hand, each armour slot, and nothing. |
| JUnit | The transition table, as a pure function of `(wasPresence, inRing, refusal)` → `(newPresence, message?)`. Including a changed refusal on an already-denied presence. |
| JUnit | Qualification against a stub `PrerequisiteContext` — the existing `TestPrerequisiteContext` already does this for rites, so the new `nwyfre:sky_visible` term is the only new thing to cover. |
| GameTest | The tags actually resolving, the attachment surviving a tick, the event firing, a cancel landing in `DENIED`, and the three pre-scan gates short-circuiting. |
| Manual | That the message reads well and does not spam at a ring's edge, and that a ring built on open grass at night actually opens. |

---

## Open questions

1. **Is a player-built ring supposed to be as good as a found one?** This design says yes, and
   moves the scarcity into the site and the hour. The alternative — only worldgen rings work —
   needs an index that is provably incomplete, and would mean the feature does nothing at all until
   worldgen lands. Worth revisiting if a qualifying ring turns out to be too easy to farm.
2. **Should a denied player be told why?** "Cold iron" names the cause, which teaches the rule. The
   alternative — an opaque refusal the player has to work out — is more folkloric and more annoying.
3. **Should the ring blocks themselves have to be undisturbed for a while?** A "settling" period
   would stop a ring being thrown down on the spot, at the cost of a timer per ring and a confusing
   silence. Probably not worth it if the site gates hold.
4. **Does breaking a ring block while connected eject you mid-rite?** Matters only once rites are
   ring-sited; the `Disconnect` event is the hook either way.
