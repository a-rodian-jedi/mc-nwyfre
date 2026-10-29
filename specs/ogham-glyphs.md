# Ogham Glyphs — Chalk and Surfaces

The glyph blocks work. Two things about them do not:

1. **Twenty-six items is an unusable way to pick a letter.** Writing a word means hunting through a
   creative tab, and in survival it would mean twenty-six craftable blocks. The alphabet needs a
   *control*, not a shelf.
2. **They only lie flat.** Ogham was cut on standing stones and trees — up an edge, read bottom to
   top. Floor-only is the wrong half of the alphabet's natural home, and it blocks both wall
   decoration and vertical ritual circles.

Both are fixable without touching what already works: the alphabet, the textures, the stem
geometry, or the tag.

---

## Decision: keep one block per letter

The obvious fix for (1) is to collapse twenty-six blocks into one with a `LETTER` property. **Do
not.** `PatternCell.Predicate` matches block-or-tag and nothing else, so the moment letters become
a blockstate property, a ritual pattern can no longer demand a *specific* letter — and vertical
circles make letter-precise patterns more valuable, not less. A circle that must spell a word is
the interesting thing here.

The twenty-six blocks are not the problem. The twenty-six **items** are.

> **Blocks do not need a `BlockItem`.** Drop all twenty-six and replace them with one Chalk item
> that places the right block. Pattern precision is kept, the UX problem goes away entirely, and it
> is a *smaller* change than collapsing the blocks would be.

Nothing currently renders a glyph as an item — `PatternCell.previewBlock()` has no callers yet — so
removing the block items breaks no existing consumer. Keep the lang keys: blocks still need names
for diagnostics, Jade and F3, and `LangKeyCoverageTest` reads the real block list, so they stay
covered automatically.

---

## The Chalk item

One item. It knows which mark it is about to draw, and it draws it where you click.

### Components

```java
record ChalkMark(Optional<OghamLetter> letter) { }   // empty = the bare stem
record Inscription(List<ChalkMark> marks, int cursor) { }
```

`nwyfre:chalk_mark` carries the selected mark; `nwyfre:chalk_inscription` is the phase-2 word mode.
Modelling the bare stem as an *absent letter* rather than a 26th enum constant keeps `OghamLetter`
exactly the alphabet — `OghamLetterTest` asserts five aicmí of five, and a stem constant would make
that assertion a lie.

### Behaviour

| Input | Effect |
| :--- | :--- |
| Use on a block face | Place the current mark's glyph on that face. Damage the chalk. Advance the inscription cursor if there is one. |
| Sneak + use in air | Open the mark picker. |
| Sneak + scroll | Step one mark along the alphabet. |

Chalk is **damageable** rather than stack-consumed, so one piece writes a whole circle. Glyph
blocks drop **nothing** when broken — chalk smudges, and they already break instantly.

`specs/rituals.md` anticipates chalks "infused with various materials that may tie them to certain
elements". This item is where that lands: elemental chalk is a variant of this item, so do not bake
the assumption of a single chalk into the placement code.

### Picking a letter

A **grid picker screen** on sneak-use: twenty-six cells drawing the actual block textures, click to
select. Twenty-six is far too many to cycle one at a time as the primary control, but sneak-scroll
is the right *adjacent* control — once you are on Beith, nudging to Luis should not need a screen.

Both routes end at the same place: a serverbound `SelectChalkMarkPayload`. Per CLAUDE.md's
networking conventions it lives in `...network`, registers on `RegisterPayloadHandlersEvent` with
`event.registrar("1")`, and **needs a `StreamCodec` round-trip unit test** — a mismatched pair
desyncs silently instead of throwing. The handler must verify the player is actually holding chalk;
the client is not authority for what is in a hand.

The screen is client-only and must be reached only from inside a client-side branch, never
referenced from a class a dedicated server loads.

### Phase 2: write a word

The picker gets a text field. Type `NEMED`, and the chalk transliterates to marks and walks the
cursor as you click along the ground. This is the feature that makes writing ogham pleasant rather
than a twenty-six-way menu dance, and it is why `Inscription` carries a cursor rather than the
chalk just holding one letter.

Transliteration is content, not logic: the Latin→ogham map handles the `NG` digraph and the letters
ogham has no sign for (`J K V W X Y`), which traditionally fold onto their nearest sound. It wants
a unit test with the digraph and the folds pinned, and it wants to live in data so a pack can
disagree.

---

## Surfaces

### The state model

Two properties replace today's single horizontal `AXIS`:

| Property | Values | Meaning |
| :--- | :--- | :--- |
| `FACING` | 6 directions | Which way the painted side points, i.e. which face it is stuck to. |
| `STEM` | `Direction.Axis` — X, Y, Z | The world axis the stem runs along. |

Twelve combinations are meaningful (two in-plane axes per face); the other six are nonsense —
`facing=up, stem=y` cannot be drawn. They are never produced by placement and map to an arbitrary
variant in the blockstate.

Six dead states is the price of `STEM` being **self-describing**: `stem=y` obviously means an
upright stem in F3 and in a `/setblock`, where a two-valued `along`/`across` enum would mean nothing
without also reading the facing. Worth it.

**The 180° flip stays unexposed**, as it is today. Ogham's above/below is defined by reading
direction, so flipping a glyph end-for-end renders it identical to a *different letter*. That is a
way to write nonsense, not expressiveness. Fixed convention: a line reads west→east, north→south,
and bottom→top.

### Models: two per glyph, and why that is forced

One model and blockstate rotations cannot do this, and the reason is worth writing down so nobody
spends an afternoon rediscovering it.

Blockstate variants offer `x` and `y` rotation only — there is no `z`. Rotation about X fixes the X
axis; rotation about Y maps any vector into the horizontal plane. So a stem drawn along the model's
X axis **can never end up vertical**, whatever x/y pair is applied. A wall glyph read bottom-to-top
— the authentic form — is unreachable from a single model.

So each glyph needs two models over the same single texture:

| Model | Stem in UV space |
| :--- | :--- |
| `block/ogham_<name>` | along model X |
| `block/ogham_<name>_turned` | along model Z — the same geometry with `rotation: 90` on the painted face's UV |

With both, every one of the twelve states is reachable: the horizontal-stem states come from the
first model, the vertical-stem wall states from the second, and `y` rotation picks which of the four
walls. Still **one texture per letter** — the second model is UV bookkeeping, not art.

`uvlock` stays **off** throughout: the texture must turn with the model, or the stem stops being
centred and the line breaks at every seam.

The exact twelve `(model, x, y)` triples are small enough to settle during implementation. The
acceptance criterion is what matters and it is checkable by eye: **on each of the six faces, two
adjacent glyphs sharing a stem axis must show one unbroken line, with strokes on a consistent
side.** That is a `runClient` checklist item — rendering is not GameTest-able.

### Placement

`getStateForPlacement` reads `context.getClickedFace()` for `FACING`, then picks `STEM`:

1. **Continue a line.** If an adjacent glyph on the same face has a stem running toward us, take its
   axis. This is today's `chooseAxis` rule generalised from two candidate directions to the four
   in-plane neighbours of the clicked face, and it is what makes writing a word a matter of walking
   along clicking.
2. **Otherwise, default by face.** Floor and ceiling: the player's horizontal facing axis, as now.
   Walls: **vertical**, because that is the authentic form and it is the thing that is impossible
   today.

### Support and survival

Today's rule — a sturdy face below, pop off when it goes — generalises to the block opposite
`FACING`. `canSurvive` and `updateShape` both need the facing-relative form, and the collision shape
becomes a thin plate against the supporting face rather than always the floor.

**Not waterloggable.** Doubling the state count for chalk is a poor trade, and the better answer is
the flavourful one: water **washes a glyph away**. A circle ruined by rain is a real consideration
for where you site a rite, and it costs a `neighborChanged` check rather than six hundred extra
blockstates.

---

## Vertical rituals come almost free

Worth stating plainly, because it sounds like a large feature and is not.

`RitualPattern` is **already 3D** — it is a list of layers with an `offset_y`, and the matcher reads
`BlockState` per position. `PatternCell` matches block-or-tag and ignores blockstate properties
entirely, so it does not care whether a glyph is lying down or stuck to a wall.

So a wall-mounted circle needs **no change to the pattern format, the matcher, or the recipe**. It
becomes authorable the moment glyphs can be placed vertically. Two existing constraints carry over
unchanged and should be said out loud to whoever authors the first one:

- Exactly one cell is `nwyfre:altar`, and it fixes the origin at layer Y 0.
- Patterns never rotate or mirror. A vertical circle is therefore authored against one specific
  wall direction — which for a wall is natural rather than limiting.

The Patchouli multiblock generation (`RitualPatternMultiblocks`) reads the same pattern data, so a
vertical circle documents itself with no extra work.

---

## Migration

Dev-only mod, no release, so this is a note rather than a plan:

- The twenty-six block items vanish. Existing stacks go with them.
- `axis` becomes `facing` + `stem`. Glyphs in existing test worlds will not survive the property
  change cleanly.
- `NwyfreBlockLootSubProvider` changes from `dropSelf` to no drop; `NwyfreItems.OGHAM_GLYPHS` goes;
  the creative tab shows Chalk; `NwyfreBlockStateProvider` emits twelve variants and two models per
  glyph instead of two variants and one.
- `tools/generate_ogham_textures.py` is **untouched**. The textures, the stem centring and the
  alphabet are all still right.

---

## Testing

| Layer | Covers |
| :--- | :--- |
| JUnit | `ChalkMark` codec, and the `SelectChalkMarkPayload` stream-codec round trip with the buffer asserted fully consumed. |
| JUnit | Stem selection as a pure function of (clicked face, neighbouring stems, player facing) — continuation on each of the six faces, and each default. |
| JUnit | Latin→ogham transliteration: the `NG` digraph, the unmapped-letter folds, and an empty string. |
| JUnit | `OghamLetterTest` as-is. The alphabet does not change, and `ChalkMark` modelling the stem as an absent letter is what keeps that true. |
| GameTest | Placing on all six faces yields the expected state; removing the support pops the glyph; chalk takes damage; a glyph drops nothing. |
| Manual | The twelve-orientation render checklist above, and that the picker is actually pleasant. |

---

## Phasing

1. **Surfaces and Chalk.** `FACING`/`STEM`, the generalised support rule, the two models, the Chalk
   item with the grid picker, block items removed. This is the whole of the user-visible fix.
2. **Fluency.** Sneak-scroll, inscription mode, transliteration.
3. **Elemental chalks**, folding into the chalk plans already in `specs/rituals.md`.

Vertical ritual circles need nothing beyond phase 1 — they become authorable as data the moment
glyphs stick to walls.

---

## Open questions

1. **What is chalk made of, and how much does one piece write?** Durability is the only real balance
   lever on circle-building, and it is untuned.
2. **Should the picker show transliterations, tree names, or both?** It is the main place the
   alphabet is taught. Tree names are the flavour; letters are what you need to spell with.
3. **Does a glyph on a wall need to be readable from behind?** Currently it is invisible from the
   far side, like a painting. Probably correct.
4. **Should water washing glyphs away be configurable?** It is a real difficulty knob for anyone
   building a rite in the rain.
