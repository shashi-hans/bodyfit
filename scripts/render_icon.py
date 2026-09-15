"""Rasterise the launcher icon so it can be checked without installing a build.

    python3 scripts/render_icon.py app/src/main/res/drawable/ic_launcher_foreground.xml out

Writes out_384.png, out_96.png and out_48.png. 48 is roughly launcher size and is the
one that matters: detail that survives at 384 routinely turns to mush there.
"""
import sys
import xml.etree.ElementTree as ET

from PIL import Image, ImageDraw

from svg_path import flatten

NS = "{http://schemas.android.com/apk/res/android}"
SS = 8  # supersample factor, downscaled at the end for antialiasing
BACKGROUND = "#0F7A55"


def render(xml_path, out_path, size, bg=BACKGROUND, corner_frac=0.22):
    root = ET.parse(xml_path).getroot()
    vw = float(root.get(NS + "viewportWidth"))
    n = size * SS
    scale = n / vw

    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    dr = ImageDraw.Draw(img)
    dr.rounded_rectangle([0, 0, n - 1, n - 1], radius=corner_frac * n, fill=bg)

    for p in root.iter("path"):
        d = p.get(NS + "pathData")
        if not d:
            continue
        fill = p.get(NS + "fillColor")
        stroke = p.get(NS + "strokeColor")
        w = float(p.get(NS + "strokeWidth") or 0) * scale
        for pts in flatten(d):
            q = [(px * scale, py * scale) for px, py in pts]
            if fill and len(q) > 2:
                dr.polygon(q, fill=fill)
            if stroke and w > 0:
                if len(q) > 1:
                    dr.line(q, fill=stroke, width=max(1, int(round(w))))
                for px, py in q:  # round caps and joins
                    dr.ellipse([px - w / 2, py - w / 2, px + w / 2, py + w / 2], fill=stroke)

    img.resize((size, size), Image.LANCZOS).save(out_path)
    return out_path


if __name__ == "__main__":
    src, stem = sys.argv[1], sys.argv[2]
    for px in (384, 96, 48):
        render(src, f"{stem}_{px}.png", px)
    print("rendered 384/96/48")
