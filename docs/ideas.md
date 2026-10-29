# Nwyfre — Ideation Scratchpad

**Status: ideation only.** Nothing here is designed, committed, or scheduled. This is a map of
options and rough costs so we can argue about direction before writing code.

`CLAUDE.md` holds the rules; `docs/ARCHITECTURE.md` holds the how. This file holds the *maybe*.
When an idea here graduates, it becomes a spec in `specs/` and this entry gets deleted.

---

## Contents

- [The spine: awakening](#the-spine-awakening)
- [Blood](#blood)
- [Shapeshifting](#shapeshifting)
- [Monster hunting: silver and iron](#monster-hunting-silver-and-iron)
- [Faerie circles](#faerie-circles)
- [Cauldron and brews](#cauldron-and-brews)
- [Herbs and trees](#herbs-and-trees)
- [Stones and gems](#stones-and-gems)
- [What is art and what is code](#what-is-art-and-what-is-code)
- [New Java extension points these would need](#new-java-extension-points-these-would-need)
- [Open questions](#open-questions)

---

## The spine: awakening

The ritual system already carries almost all of this. Stage ids are free-form `ResourceLocation`s
with nothing hardcoding them, and `StagePrerequisite` already gates a ritual on a stage. So an
awakening ladder — rite grants stage, stage unlocks next rite — is **expressible in data today**
except for one missing piece: there is no effect that grants a stage. `nwyfre:run_command` could
fake it, which is exactly the sign that `nwyfre:grant_stage` should be a real effect type.

### Shape of the ladder

Five or so rungs, each a rite, each gated on the one before:

| Stage | Reads as | Unlocks |
| :--- | :--- | :--- |
| `nwyfre:stirring` | you notice | the first circle, basic offerings |
| `nwyfre:listening` | you can be told things | herb lore, stone assay, Jade and book reveals |
| `nwyfre:naming` | you can address a thing | binding rites, warding, fae bargains |
| `nwyfre:blooding` | you can pay | blood rites, bonds, sacrifice tiers |
| `nwyfre:turning` | you can *be* another thing | shapeshifting |

Naming them for the act rather than the number ("The Rite of First Naming") does the Arthurian work
for free, and reads better in the book than "Tier 3".

### Three ideas for keeping creep out

1. **Awakening buys breadth, never magnitude.** A rung unlocks *kinds* of rite and *kinds* of
   reagent, and never multiplies a number. Magnitude stays where it already is: `potency`, which is
   additive and only arrives via augments the player must physically assemble. This is the single
   most important invariant if we want to avoid the Ars problem, and the architecture already leans
   that way.
2. **Every rung costs something irreversible-ish.** A rite that takes a year's growth, a felled
   hawthorn, a bound bond — progression as expenditure, not accumulation.
3. **Rungs gate *information* as much as power.** `listening` does not make you stronger; it makes
   the world legible (stone qualities, herb properties, what a creature fears). That is the quest
   feel — you are seeking knowledge, and the mod withholds it until you have earned it.

### The bardic thread

You flagged bardic magic and it has nowhere to live yet. `RitualDemands` currently has exactly two
kinds — kill a thing, use a thing. A third, **a recitation**, is a small codec addition and a very
Celtic one:

- **Cheapest:** a "recital" item (a harp, a stave) that must be *used* near the altar — this is
  already `item_to_use`, no Java at all, just flavour and a sprite.
- **Middle:** a demand satisfied by a nearby note block or jukebox playing a specific thing, or by
  holding right-click for N ticks while the rite "listens". Needs a new demand type.
- **Rich:** ogham or vowel sequences entered as a short input pattern, with failure costing the
  offering. Fun, and the most likely to annoy people on the twentieth casting.

Worth noting: bardic magic implies **custom sound assets**, which is a whole asset category you
did not list. A chant, a harp line, a cry. Cheap per asset, but it is you making them.

---

## Blood

### Ideas

1. **Blood with provenance.** A vial item whose Data Component records *whose* blood it is —
   entity type, and for a player their UUID. Rites then ask for "blood of a drowned" rather than
   "blood". NeoForge ships a component-matching ingredient (`DataComponentIngredient`); verify its
   21.1 form before relying on it, but if it holds, recipes stay pure data. This one component
   unlocks most of the rest of the section.
2. **Blood ties.** A bond is a per-player attachment holding bonded UUIDs, formed by a rite with two
   players standing in the circle. Make the bond *symmetrical in harm*, not just benefit: you share
   a fraction of each other's damage, you can always sense each other's direction and state, and
   dying while bonded costs the survivor too. That shape is both better flavour and self-limiting —
   a bond is a liability, so nobody takes six of them.
3. **Consumption with a ledger.** Drinking blood gives something short and real, and increments a
   decaying "thirst" attachment. High thirst does not debuff you directly (boring); it makes the
   world notice — Baobhan Sith begin to find you, the Marbh Bheo rise where you sleep. Cost
   expressed as *attention* rather than a stat penalty is more interesting and much more Celtic.
4. **Sacrifice tiers.** `RitualDemands.Sacrifice` already takes an entity tag, so tiering is free:
   `#nwyfre:lesser_sacrifice`, `#nwyfre:named_sacrifice`. The interesting version is requiring the
   sacrifice be *something you raised* — a named, tamed, or long-lived mob — which is a small NBT
   check and a genuinely uncomfortable mechanic.

### Implementation notes

Attachments, not capabilities, for thirst and bonds — and both want `.copyOnDeath()`, since losing
your bonds by dying would be a bug rather than drama. Bond state should sync as an absolute value
(the whole set, like stages) rather than as deltas. Surfacing the bond belongs in Jade or a HUD
element fed by a payload, never by reading the attachment client-side — that trap is already
documented in `ARCHITECTURE.md`.

### Art vs code

**Art:** vial and chalice sprites (small), plus the creatures that come for you (large — see below).
**Code:** everything else. Blood particles can tint an existing vanilla sprite set; the bond
"thread" between players is a particle line, no asset needed.

---

## Shapeshifting

The heaviest item on your list, and the one where the art-vs-code question actually changes the
design. Three honest options:

1. **Abilities only, no model swap.** A shape is a package of attribute modifiers, movement rules,
   and a restriction, plus a visual shroud of particles. Crow: slow-fall, near-silence, cannot carry
   iron. Hare: speed and leap, cannot fight. Wolf: tracking and bite, cannot perform rites. **Zero
   custom art. Days of work, not weeks.** And arguably better progression — the shapes become about
   trade-offs rather than a power spike.
2. **Real model swap.** Cancel the player renderer client-side and draw a GeckoLib model in its
   place. Well-trodden ground, mid-cost in code, and **needs a finished model, texture and
   animation set per shape** from you. It is the version people will screenshot.
3. **Possession.** You leave your body standing and take control of a real entity — camera via
   `setCamera`, with input forwarding as the genuinely hard part. More code than (2), less art (the
   raven is a mob you would want anyway), and the most mythologically correct of the three: the
   Morrígan does not so much *become* a crow as she *is* already there as one.

**Recommendation:** build (1), ship (1), and treat (2) as a later visual upgrade that changes no
gameplay code. That ordering lets shapeshifting exist long before the models do.

### Two framing ideas

- **Three-fold gating.** The Morrígan is three. Make each shape its own final rite rather than one
  "unlock shapeshifting", so even the endgame is steady progression and a player may never get all
  three.
- **Every shape forbids something.** The restriction is the point. A shape you can enter with no
  cost is just a buff in a costume.

---

## Monster hunting: silver and iron

### Ideas

1. **Material weakness as pure data.** Two sets of tags — weapon tags (`#nwyfre:silver_weapons`,
   an iron equivalent) and entity tags (`#nwyfre:vulnerable_to_silver`,
   `#nwyfre:vulnerable_to_iron`) — and one damage hook that cross-references them. Perhaps forty
   lines of Java, after which every pack author and InControl! rule can extend it without us. This
   is the right first implementation and it should land before any new creature does.
2. **Iron as a ward, not a weapon.** Far more interesting than bonus damage: fae cannot *approach*
   iron. Veto their targeting, break their pathing, make an iron nail in a doorframe actually mean
   something, and make a faerie ring inert for anyone carrying iron. All event-level code, no
   assets, and it turns base-building into a magical act.
3. **Hunting yields reagents, not gear.** A Marbh Bheo drops grave-dust; a Baobhan Sith drops blood
   that is both useful and dangerous. If hunting outputs ritual inputs rather than swords, the whole
   economy stays ritual-shaped and the no-creep rule enforces itself.
4. **Silver: own the ingot, skip the ore.** See below — this one needs more than a bullet.

### Where silver actually comes from

"Consume `#c:ingots/silver` and let a pack supply it" is the obvious move, and it is the broken
one. Worth spelling out before it gets built.

**The trap.** NeoForge 21.1.265 ships `c:ingots/` for copper, gold, iron and netherite — **and
nothing else**. There is no `c:ingots/silver`; it is a community convention that exists only when
some mod creates it. So "just consume the tag" means that in a pack with no silver mod, the tag is
empty, and an empty tag does not fail loudly — `Ingredient.getItems()` hands back a single
`minecraft:barrier` named "Empty Tag: …" while `test(...)` matches nothing. The recipe loads, shows
up in JEI, and never fires. Both halves of that are already live traps in `ARCHITECTURE.md`. A
silently dead progression gate is worse than no silver mechanic at all.

**Three options, and what each costs:**

| Approach | Cost | Fails how |
| :--- | :--- | :--- |
| Tag only, no item of our own | no art | Dead feature in any pack without a silver mod — silently. |
| Our own full ore chain | ore + deepslate + raw + ingot + nugget + tools, plus worldgen tuning | Two silvers in a pack that already has one. |
| **Our own ingot, no ore** | **one sprite** | Nothing much. |

**The recommendation: register our own silver ingot, put it in `c:ingots/silver` ourselves, and
have nothing in Nwyfre ever name a concrete item.** Code and recipes reference only the tag, plus
`#nwyfre:silver_weapons` for the damage hook. Three things follow:

- The tag is never empty, so the mechanic always works, standalone or in a pack.
- A pack that brings Thermal or Mekanism drops its silver into the same tag and the two become
  interchangeable for free. No conditions, no compat code, no `ModList` check.
- We are not maintaining an ore. Skipping the ore is what turns an eight-texture job into a
  one-texture job, and ore generation is the part that actually needs balancing against every other
  mod in a pack.

**Then make getting it interesting, which is the real argument.** Mining silver is not an Arthurian
quest. Winning it is. If silver does not come out of the ground, it has to come from somewhere with
a story attached — a barrow hoard, a fae bargain, a rite that draws it out of moonlight and lead —
and that is exactly the ritual-shaped economy idea (3) above is after. It also means silver stays
scarce by *fiction* rather than by tuning an ore's vein size, which is the kind of balancing that
breaks the moment a pack adds a new dimension to strip-mine.

If you later decide you do want silver in the stone after all, the fallback can be gated properly
rather than guessed at: NeoForge ships `TagEmptyCondition` (`neoforge:tag_empty`), so our ore's
recipes and worldgen can be conditioned on nothing else having supplied silver. Worth knowing the
escape hatch exists; not worth building before there is a reason.

### Art vs code

**Art:** the creatures. This is the single largest art cost on the whole list — each wants a model,
texture, and animation set, and the Celtic bestiary you are drawing on (Marbh Bheo, Baobhan Sith,
Cù Sìth, the Washer at the Ford) is all things with no vanilla analogue to reskin. Budget
accordingly; consider shipping *three* excellent creatures rather than ten adequate ones.
**Code:** all the weakness and warding math, spawn rules (hand to InControl!), loot, AI.

---

## Faerie circles

Best value-per-effort item on your list — almost entirely code, almost no art, enormous flavour.

> **Designed: `specs/faerie-rings.md`.** Detection from vanilla mushrooms, the iron rule, and the
> query API are settled there. What stays below is the part still in the air — what a ring should
> actually *do* once you are standing in one.

### Ideas still open

1. **A ring is a place with a rule, not a machine.** Three flavours of what entering does; pick one
   or two. A **time-slip** — you step out and hours or days have passed — is the most Celtic
   mechanic available and nearly free to implement via world time plus a fatigue attachment. A
   **market** that only appears under conditions and trades in things money cannot buy. A **door**
   to a Faerie dimension.
2. **The bargain is the content.** Entering uninvited costs; entering with the right offering buys.
   Lean on the folklore rule set — do not eat the food, do not give your name, do not accept a gift —
   and enforce them as actual mechanics. Rules the player can learn and exploit beat a stat block.
3. **Seeded rings in worldgen.** Vanilla generates mushroom patches, never circles, so until this
   lands every ring is one a player built. Folklore is fine with rings simply appearing; a
   biome-gated feature would make them something you *find*, which is a different and better game.

### Art vs code

**Art:** optionally one custom ring flower or fungus. Otherwise none — vanilla mushrooms carry it.
**Code:** time-slip, the bargain rules, worldgen if we want seeded rings in specific biomes.

---

## Cauldron and brews

### Ideas

1. **One brew, three deliveries.** The key structural idea: a brew is a *data object* (a component
   carrying effects, colour, potency) and it can be delivered three ways — **bottled** (drink),
   **painted** (coat a weapon or arrows), **burned** (an area aura). Author the brew once, get three
   items' worth of content. Anything else triples the data volume for no gain.
2. **Reuse the prerequisite tree as the brew's gate.** `PrerequisiteContext` is deliberately an
   interface of narrow queries rather than a player or a level, so moon phase, weather, biome and
   time gating all work for a cauldron *verbatim* — no new code, and no new JSON vocabulary for
   authors to learn. That is a real architectural win and we should take it.
3. **Burning reuses `nwyfre:area_effect`.** A burning cauldron is already almost exactly what that
   ritual effect does. Same registry, same effect type, new trigger.
4. **Quality from process, not only ingredients.** Heat duration, stirring, when you brewed it. Gives
   a skill ceiling without adding a number to the top end — the good brewer gets consistency, not
   escalation.

### Implementation notes

Coating is a Data Component on the weapon carrying brew id plus uses remaining, decremented on hit.
Arrows need the component to survive onto the fired entity, which is the one genuinely fiddly part.
A new `nwyfre:brew` recipe type (mirroring `RitualRecipe`'s shape) keeps `/reload`, client sync and
KubeJS `ServerEvents.recipes` for free — the same reason rituals are a recipe type.

### Art vs code

**Art:** a cauldron (a block model gets you most of the way; GeckoLib only if you want it to seethe)
and **one greyscale bottle sprite**. That last point matters: a single greyscale bottle plus a
programmatic tint layer yields every brew in the mod for free, the way vanilla potions work. Do not
hand-paint one sprite per brew.
**Code:** the brew registry, the three delivery mechanisms, tinting, bubbling particles.

---

## Herbs and trees

### Ideas

1. **Herbs are cheap content and should be the volume filler.** One 16x16 texture plus a shared bush
   or tall-flower block class gets you a plant. Blockstates, models, loot tables and placement are
   all datagen, and `NwyfreBlockStateProvider` and `NwyfreBlockLootSubProvider` already exist and
   just need extending. Realistically: an afternoon of pixel work per handful of herbs.
2. **Trees punch above their art cost.** Log side, log top, planks, leaves, sapling — five textures —
   with the grower and the worldgen feature as code. Each tree then carries a *rule*, which is where
   the flavour lives: **rowan** wards (plant it round your base and fae will not cross),
   **hawthorn** is theirs (fell one near a ring and something comes), **yew** touches the dead and
   the Marbh Bheo, **hazel** holds knowledge, **blackthorn** is for cursing, **alder** for
   water-work.
3. **The ogham alphabet is a free content list.** It is a tree alphabet, so it hands you species
   names, an ordering and a lore structure already — and the glyph blocks that carry those names
   now exist (`OghamLetter`, generated by `tools/generate_ogham_textures.py`), so a rowan sapling
   and the Luis glyph can share a name and a meaning from the start.
4. **A preparation chain is steady progression without power.** Fresh → dried → ground → infused,
   each step a block and a wait, each tier gating brews. Adds depth and time cost without adding a
   single multiplier.

### Art vs code

**Art:** one texture per herb; five per tree; optionally ogham glyphs if not generated.
**Code:** growth, placement, datagen, the consequence rules (felling, warding), the drying chain.

---

## Stones and gems

Mostly a *data and documentation* problem, and the codebase already shows the answer.

### Ideas

1. **A `nwyfre:stone_quality` datapack registry.** Map an item or tag to `{ element affinity,
   potency, keywords }`. Rites and brews then ask for "a stone of water affinity" instead of naming
   jade, and a pack can add its own gems with no Java. Follows `Element` and `RitualPattern` exactly.
2. **Generate the book pages from the registry.** `RitualPatternMultiblocks` already generates
   Patchouli multiblocks from pattern data precisely because maintaining a second hand-written copy
   guarantees the two eventually disagree. A gem table is the same problem with worse consequences —
   generate it.
3. **Discovery, not a data dump.** Qualities stay hidden until *assayed* — a per-player attachment
   of known stones, with the book entry gated on a Patchouli flag. This is the difference between a
   wiki page and a quest, and it gives the `listening` rung something concrete to unlock.
4. **Surface it where the player is already looking.** A tooltip line, or Jade on an ore block, both
   reading the same registry. Cheap, and it makes the data discoverable without building a UI.

### Art vs code

**Art:** a handful of gem sprites at most, and only for stones vanilla lacks.
**Code:** registry, generated pages, the assay mechanic, tooltips.

---

## What is art and what is code

The thing you actually asked for, consolidated.

### You must make this

| Asset | Scale | Notes |
| :--- | :--- | :--- |
| Creature models, textures, animations | **Large** — the dominant cost | GeckoLib. Nothing vanilla to reskin. Prefer few and excellent. |
| Shapeshift forms | Large, **but optional** | Only for the literal model-swap version. Ability-only shapes need none. |
| Boss | Large | Model, animations, probably a bespoke arena. |
| Tree sets | Small each (~5 textures) | Best flavour-per-pixel on the list. |
| Herb textures | Tiny each | Volume content. Batch them. |
| Cauldron | Small | A block model suffices; GeckoLib only for seething. |
| Item sprites | Tiny each | Vials, blades, talismans, instruments. |
| One greyscale bottle | Tiny, **high leverage** | Tinted programmatically into every brew. |
| Silver ingot | **One sprite** | Ship the ingot, skip the ore — see [Where silver actually comes from](#where-silver-actually-comes-from). |
| Patchouli illustrations | Medium, optional | The book works without them; it is much better with them. |
| Sounds — chants, harp, cries | Small each | **You did not list these.** Bardic magic needs them. |

### Code can do this with no art at all

- **Ritual circle shapes and matching** — already proven; the pattern-and-tag design absorbs new
  blocks without new pattern files.
- **Particles** — reuse vanilla sprite sets and tint them. `Element.colour` exists for exactly this,
  so elemental particle variants are free.
- **Ogham glyphs** — done, and worth treating as the proof of the point: all 26 are straight strokes
  generated by a script, with zero hand-drawn pixels.
- **Brew colours** — one base sprite, tinted per brew.
- **All numeric systems** — weakness multipliers, bond math, thirst, potency, attribute modifiers
  for shapes. Data-driven per `CLAUDE.md`, so KubeJS can retune them.
- **Faerie ring detection and time-slip** — no assets whatsoever.
- **Iron warding** — pathing and targeting vetoes.
- **Worldgen placement** — rings, trees, herbs, ores.
- **Patchouli multiblocks and generated lore pages** — from live registry data.
- **Jade and JEI surfaces** — tooltips, stage filtering, slot layouts.
- **Auras, screen tints, post-processing** — shape vision, thirst, ring interiors.

### The honest summary

Every system on your list is code-cheap and art-expensive in exactly one place: **creatures**. If
the art budget is the constraint, the ordering writes itself — faerie circles, iron warding, blood
bonds, brews, herbs and stone lore are all nearly artless and deliver most of the feel. The
bestiary and the literal shapeshift models are where the months go, and both can land late without
blocking anything else.

---

## New Java extension points these would need

Worth seeing together, because it is a short list — the data-driven architecture is doing its job.

| Need | Kind | For |
| :--- | :--- | :--- |
| `nwyfre:grant_stage` | ritual effect | The awakening ladder. Needed first; `run_command` is a smell. |
| `nwyfre:in_faerie_ring` | prerequisite | Ring-sited rites. |
| `nwyfre:blood_bond` | ritual effect | Two-player bonding. |
| A recitation demand | `RitualDemands` field | Bardic rites. |
| `nwyfre:brew` | recipe type | Cauldron. The largest new piece. |
| `nwyfre:stone_quality` | datapack registry | Gem lore. |
| Material-weakness hook | event handler plus tags | Silver and iron. No new registry. |
| Shape state | attachment plus modifiers | Shapeshifting, ability-only version. |

Everything else on the list is data, tags, assets, or worldgen.

---

## Open questions

1. **How many creatures are we actually willing to make?** This number decides whether monster
   hunting is a pillar or a garnish, and it should be settled before anything else here.
2. **Is shapeshifting a model swap or an ability package?** Changes the design, not just the art.
3. **Does the Faerie realm want to be a dimension**, or is a ring with rules enough? A dimension is
   a large commitment that mostly buys atmosphere.
4. **Is blood a resource or a transgression?** As a plain crafting input it loses its teeth; if it
   is always costly, players will route around it.
5. **Single-player or multiplayer first?** Blood ties are the only genuinely multiplayer-shaped idea
   here, and they are dead weight in a solo pack.
