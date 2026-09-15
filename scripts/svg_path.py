"""Tokenise, flatten and transform SVG path data.

Enough of the grammar for the icon work in this repo: M/L/H/V/C/S/Q/T/A/Z in both
absolute and relative form. Transforms are uniform scale plus translate, which is all
that is needed to drop a 256-unit source glyph into the 108-unit adaptive-icon viewport.
"""
import math
import re

# Parameter count per command, in the order the SVG grammar lists them.
ARITY = {"M": 2, "L": 2, "H": 1, "V": 1, "C": 6, "S": 4, "Q": 4, "T": 2, "A": 7, "Z": 0}

NUM = re.compile(r"[-+]?(?:\d*\.\d+|\d+\.?)(?:[eE][-+]?\d+)?")
TOKEN = re.compile(r"([MmLlHhVvCcSsQqTtAaZz])|" + NUM.pattern)


def tokenise(d):
    """Path string to [(command, [floats]), ...], repeats expanded to one command each."""
    out = []
    pos = 0
    cmd = None
    nums = []

    def flush():
        if cmd is None:
            return
        n = ARITY[cmd.upper()]
        if n == 0:
            out.append((cmd, []))
            return
        # An implicit repeat of M continues as L, per the grammar.
        first = True
        for i in range(0, len(nums) - n + 1, n):
            c = cmd if first else ("L" if cmd == "M" else "l" if cmd == "m" else cmd)
            out.append((c, nums[i:i + n]))
            first = False

    for m in TOKEN.finditer(d):
        if m.group(1):
            flush()
            cmd = m.group(1)
            nums = []
        else:
            nums.append(float(m.group(0)))
    flush()
    # A leading relative moveto has no previous point, so the spec reads it as absolute.
    if out and out[0][0] == "m":
        out[0] = ("M", out[0][1])
    return out


def transform(d, scale, tx, ty):
    """Uniform scale then translate. Relative deltas scale only; absolutes also shift."""
    parts = []
    for cmd, p in tokenise(d):
        u = cmd.upper()
        rel = cmd.islower()
        q = list(p)
        if u in ("M", "L", "T"):
            q = [p[0] * scale + (0 if rel else tx), p[1] * scale + (0 if rel else ty)]
        elif u == "H":
            q = [p[0] * scale + (0 if rel else tx)]
        elif u == "V":
            q = [p[0] * scale + (0 if rel else ty)]
        elif u in ("C", "S", "Q"):
            q = []
            for i in range(0, len(p), 2):
                q += [p[i] * scale + (0 if rel else tx), p[i + 1] * scale + (0 if rel else ty)]
        elif u == "A":
            q = [
                p[0] * scale, p[1] * scale, p[2], p[3], p[4],
                p[5] * scale + (0 if rel else tx), p[6] * scale + (0 if rel else ty),
            ]
        parts.append(cmd + " ".join(f"{v:g}" for v in q))
    return "".join(parts)


def _arc(x0, y0, rx, ry, rot, laf, sf, x1, y1, steps=48):
    if rx == 0 or ry == 0 or (x0 == x1 and y0 == y1):
        return [(x1, y1)]
    phi = math.radians(rot)
    cosp, sinp = math.cos(phi), math.sin(phi)
    dx, dy = (x0 - x1) / 2.0, (y0 - y1) / 2.0
    x1p, y1p = cosp * dx + sinp * dy, -sinp * dx + cosp * dy
    lam = x1p * x1p / (rx * rx) + y1p * y1p / (ry * ry)
    if lam > 1:
        rx *= math.sqrt(lam)
        ry *= math.sqrt(lam)
    num = rx * rx * ry * ry - rx * rx * y1p * y1p - ry * ry * x1p * x1p
    den = rx * rx * y1p * y1p + ry * ry * x1p * x1p
    co = math.sqrt(max(0.0, num / den)) * (-1 if laf == sf else 1)
    cxp, cyp = co * rx * y1p / ry, -co * ry * x1p / rx
    cx = cosp * cxp - sinp * cyp + (x0 + x1) / 2.0
    cy = sinp * cxp + cosp * cyp + (y0 + y1) / 2.0

    def ang(ux, uy, vx, vy):
        d = (ux * vx + uy * vy) / (math.hypot(ux, uy) * math.hypot(vx, vy))
        a = math.acos(max(-1.0, min(1.0, d)))
        return -a if ux * vy - uy * vx < 0 else a

    th0 = ang(1, 0, (x1p - cxp) / rx, (y1p - cyp) / ry)
    dth = ang((x1p - cxp) / rx, (y1p - cyp) / ry, (-x1p - cxp) / rx, (-y1p - cyp) / ry)
    if not sf and dth > 0:
        dth -= 2 * math.pi
    elif sf and dth < 0:
        dth += 2 * math.pi
    pts = []
    for i in range(1, steps + 1):
        t = th0 + dth * i / steps
        ex, ey = rx * math.cos(t), ry * math.sin(t)
        pts.append((cosp * ex - sinp * ey + cx, sinp * ex + cosp * ey + cy))
    return pts


def _cubic(p0, p1, p2, p3, steps=24):
    out = []
    for i in range(1, steps + 1):
        t = i / steps
        u = 1 - t
        out.append((
            u * u * u * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t * t * t * p3[0],
            u * u * u * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t * t * t * p3[1],
        ))
    return out


def flatten(d):
    """Path to a list of point lists, one per subpath."""
    subs = []
    cur = []
    x = y = 0.0
    start = (0.0, 0.0)
    prev_c2 = None
    for cmd, p in tokenise(d):
        u = cmd.upper()
        rel = cmd.islower()
        if u == "M":
            if cur:
                subs.append(cur)
            x, y = (x + p[0], y + p[1]) if rel else (p[0], p[1])
            start = (x, y)
            cur = [(x, y)]
            prev_c2 = None
        elif u in ("L", "T"):
            x, y = (x + p[0], y + p[1]) if rel else (p[0], p[1])
            cur.append((x, y))
            prev_c2 = None
        elif u == "H":
            x = x + p[0] if rel else p[0]
            cur.append((x, y))
            prev_c2 = None
        elif u == "V":
            y = y + p[0] if rel else p[0]
            cur.append((x, y))
            prev_c2 = None
        elif u in ("C", "S"):
            if u == "C":
                c1 = (x + p[0], y + p[1]) if rel else (p[0], p[1])
                c2 = (x + p[2], y + p[3]) if rel else (p[2], p[3])
                ex, ey = (x + p[4], y + p[5]) if rel else (p[4], p[5])
            else:
                c1 = (2 * x - prev_c2[0], 2 * y - prev_c2[1]) if prev_c2 else (x, y)
                c2 = (x + p[0], y + p[1]) if rel else (p[0], p[1])
                ex, ey = (x + p[2], y + p[3]) if rel else (p[2], p[3])
            cur += _cubic((x, y), c1, c2, (ex, ey))
            prev_c2 = c2
            x, y = ex, ey
        elif u == "Q":
            c = (x + p[0], y + p[1]) if rel else (p[0], p[1])
            ex, ey = (x + p[2], y + p[3]) if rel else (p[2], p[3])
            c1 = (x + 2.0 / 3 * (c[0] - x), y + 2.0 / 3 * (c[1] - y))
            c2 = (ex + 2.0 / 3 * (c[0] - ex), ey + 2.0 / 3 * (c[1] - ey))
            cur += _cubic((x, y), c1, c2, (ex, ey))
            prev_c2 = None
            x, y = ex, ey
        elif u == "A":
            ex, ey = (x + p[5], y + p[6]) if rel else (p[5], p[6])
            cur += _arc(x, y, abs(p[0]), abs(p[1]), p[2], int(p[3]), int(p[4]), ex, ey)
            prev_c2 = None
            x, y = ex, ey
        elif u == "Z":
            cur.append(start)
            x, y = start
            prev_c2 = None
    if cur:
        subs.append(cur)
    return subs


def bounds(paths):
    pts = [pt for d in paths for sub in flatten(d) for pt in sub]
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    return min(xs), min(ys), max(xs), max(ys)
