#!/usr/bin/env python3
"""
Generates the Ogham chalk-glyph block textures into src/main/resources.

Ogham is drawn as straight strokes against a stem line, which makes the whole alphabet
procedural -- there is nothing here a hand-drawn sprite would do better, and generating it
keeps all 26 glyphs visually identical in weight and spacing.

The stem spans the full texture width at the exact vertical centre (rows 7 and 8), which is
what makes glyphs join seamlessly when laid side by side. Keep that invariant: move the stem
off centre and the line breaks at every block boundary.

Run from the repo root:  python tools/generate_ogham_textures.py

The alphabet below mirrors com.nwyfre.nwyfre.block.ogham.OghamLetter, which is the source of
truth. OghamTextureTest fails if the two ever drift apart.
"""

import os
import random

from PIL import Image

SIZE = 16
CHALK = (238, 235, 226)
JITTER = 12          # per-pixel RGB wobble, so the strokes read as chalk rather than vector
STEM_ROWS = (7, 8)   # dead centre of a 16px tile; both halves of a seam land here

OUT_DIR = os.path.join("src", "main", "resources", "assets", "nwyfre", "textures", "block")

# Stroke positions for a group of n strokes: spacing 3, centred on the stem's midpoint (7.5).
POSITIONS = {1: [7], 2: [6, 9], 3: [4, 7, 10], 4: [3, 6, 9, 12], 5: [1, 4, 7, 10, 13]}

# Aicme Muine leans, so its strokes need room sideways and pack tighter as the count rises --
# exactly how the reference draws them. Every stroke is parallel, so however dense the group
# gets the strokes stay LEAN_SPAN apart on any given row and never merge.
DIAGONAL_POSITIONS = {1: [7], 2: [5, 9], 3: [4, 7, 10], 4: [3, 6, 9, 12], 5: [3, 5, 7, 9, 11]}
LEAN = 3  # columns of travel from the stem to either end of the stroke

ABOVE_ROWS = range(2, 7)     # stops at 6, so the stroke touches the stem at row 7
BELOW_ROWS = range(9, 14)    # starts at 9, touching the stem at row 8
THROUGH_ROWS = range(2, 14)  # crosses the stem outright


# --- the alphabet, read off references/ogham.png -------------------------------------------

# (file name, aicme, strokes). The four counted aicmí are five letters each, 1-5 strokes.
LETTERS = [
    # Aicme Beithe -- strokes below the stem
    ("beith", "below", 1), ("luis", "below", 2), ("fearn", "below", 3),
    ("sail", "below", 4), ("nion", "below", 5),
    # Aicme hUatha -- strokes above the stem
    ("uath", "above", 1), ("dair", "above", 2), ("tinne", "above", 3),
    ("coll", "above", 4), ("ceirt", "above", 5),
    # Aicme Muine -- strokes crossing the stem at an angle
    ("muin", "diagonal", 1), ("gort", "diagonal", 2), ("ngeadal", "diagonal", 3),
    ("straif", "diagonal", 4), ("ruis", "diagonal", 5),
    # Aicme Ailme -- strokes crossing the stem square on
    ("ailm", "through", 1), ("onn", "through", 2), ("ur", "through", 3),
    ("edad", "through", 4), ("idad", "through", 5),
    # Forfeda -- the supplementary letters, each its own shape
    ("eabhadh", "forfid", "hatch"),
    ("ifin", "forfid", "double_x"),
    ("uilleann", "forfid", "elbow"),
    ("or", "forfid", "lozenge"),
    ("eamhancholl", "forfid", "cross"),
]


def blank():
    return Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))


def put(img, rng, x, y):
    """Draws one chalk pixel, ignoring anything off the tile."""
    if not (0 <= x < SIZE and 0 <= y < SIZE):
        return
    wobble = rng.randint(-JITTER, JITTER)
    img.putpixel((x, y), tuple(max(0, min(255, c + wobble)) for c in CHALK) + (255,))


def line(img, rng, x0, y0, x1, y1):
    """Bresenham, so diagonals come out as clean single-pixel runs."""
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        put(img, rng, x0, y0)
        if x0 == x1 and y0 == y1:
            return
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def stem(img, rng):
    """The druim. Spans edge to edge so adjacent glyphs join into one continuous line."""
    for y in STEM_ROWS:
        for x in range(SIZE):
            put(img, rng, x, y)


def vertical(img, rng, x, rows):
    for y in rows:
        put(img, rng, x, y)


def diagonal(img, rng, x):
    """A stroke leaning right, as the reference draws the Aicme Muine."""
    for y in THROUGH_ROWS:
        put(img, rng, x + round((7.5 - y) * LEAN / 5.5), y)


def draw_counted(img, rng, aicme, strokes):
    positions = DIAGONAL_POSITIONS[strokes] if aicme == "diagonal" else POSITIONS[strokes]
    for x in positions:
        if aicme == "above":
            vertical(img, rng, x, ABOVE_ROWS)
        elif aicme == "below":
            vertical(img, rng, x, BELOW_ROWS)
        elif aicme == "through":
            vertical(img, rng, x, THROUGH_ROWS)
        elif aicme == "diagonal":
            diagonal(img, rng, x)
        else:
            raise ValueError(f"unknown aicme: {aicme}")


def draw_forfid(img, rng, shape):
    if shape == "hatch":                      # Eabhadh: a crosshatch sitting above the stem
        for x in (2, 5, 8, 11, 14):
            vertical(img, rng, x, range(1, 7))
        for y in (1, 3, 5):
            line(img, rng, 2, y, 14, y)
    elif shape == "double_x":                 # Ifin: crossed strokes below the stem
        for cx in (4, 11):
            line(img, rng, cx - 3, 9, cx + 3, 14)
            line(img, rng, cx + 3, 9, cx - 3, 14)
    elif shape == "elbow":                    # Uilleann: a stroke up, a bent hook down
        vertical(img, rng, 5, range(1, 7))
        vertical(img, rng, 5, range(9, 15))
        line(img, rng, 5, 14, 11, 14)
        vertical(img, rng, 11, range(10, 15))
    elif shape == "lozenge":                  # Or: a diamond threaded onto the stem
        line(img, rng, 7, 1, 14, 8)
        line(img, rng, 14, 8, 8, 14)
        line(img, rng, 8, 14, 1, 7)
        line(img, rng, 1, 7, 7, 1)
    elif shape == "cross":                    # Eamhancholl: an X centred on the stem
        line(img, rng, 2, 2, 13, 13)
        line(img, rng, 13, 2, 2, 13)
    else:
        raise ValueError(f"unknown forfid shape: {shape}")


def main():
    os.makedirs(OUT_DIR, exist_ok=True)

    written = []
    for name, aicme, detail in LETTERS:
        img = blank()
        # Seeded per letter so re-running the script never produces a spurious diff.
        rng = random.Random(name)
        stem(img, rng)
        if aicme == "forfid":
            draw_forfid(img, rng, detail)
        else:
            draw_counted(img, rng, aicme, detail)
        path = os.path.join(OUT_DIR, f"ogham_{name}.png")
        img.save(path)
        written.append(path)

    # The blank stem: punctuation, and the spacer that carries a line between words.
    img = blank()
    stem(img, random.Random("stem"))
    path = os.path.join(OUT_DIR, "ogham_stem.png")
    img.save(path)
    written.append(path)

    print(f"wrote {len(written)} textures to {OUT_DIR}")


if __name__ == "__main__":
    main()
