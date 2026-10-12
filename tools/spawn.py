#!/usr/bin/env python3
"""Генерирует команды постройки спавна ClinderGrief (v3).

Главный остров: центр X=0 Z=0, пол Y=100 (игроки ходят на Y=101), радиус 48.
  точка спавна — 0.5 101 20.5, взгляд на север (на фонтан и гигантскую надпись);
  гигантская объёмная надпись ClinderGrief — в небе на севере (плоскость Z=-74, Y 114–133);
  павильоны NPC: Торговец (-22,-22), Скупщик (22,-22), Привилегии (-22,22), Гид (22,22);
  площадки NPC у площади: Аукцион (-7,-19), Кланы (7,-19), Ивенты (-19,7), Шахта (19,-7), Арена (-19,-7);
  тюрьма — 0.5 81 0.5 (внутри скалы под островом).
Остров шахты: центр (0, 100, 120), кубоид руды X -10..10, Y 85..99, Z 110..130, варп 0.5 101 104.5.
Остров арены: центр (-120, 100, 0), варп -120.5 101 18.5.
"""
import json
import math
import random
import sys

from PIL import Image, ImageDraw, ImageFont

R = 48
Y = 100
rnd = random.Random(1211)
cmds = []


def c(s):
    cmds.append(s)


def dist(x, z):
    return math.sqrt(x * x + z * z)


def disc_rows(r, cx=0, cz=0):
    rows = []
    for x in range(-int(r), int(r) + 1):
        zm = int(math.floor(math.sqrt(max(r * r - x * x, 0))))
        rows.append((cx + x, cz - zm, cz + zm))
    return rows


def text(s, color="white", bold=False):
    d = {"text": s, "color": color}
    if bold:
        d["bold"] = True
    return d


def snbt(o):
    if isinstance(o, dict):
        return "{" + ",".join(f"{k}:{snbt(v)}" for k, v in o.items()) + "}"
    if isinstance(o, list):
        return "[" + ",".join(snbt(v) for v in o) + "]"
    if isinstance(o, bool):
        return "true" if o else "false"
    return json.dumps(o, ensure_ascii=False)


def hx(col):
    return [int(col[i:i + 2], 16) for i in (1, 3, 5)]


def lerp_color(colors, t):
    pos = t * (len(colors) - 1)
    k = min(int(pos), len(colors) - 2)
    f = pos - k
    a, b = hx(colors[k]), hx(colors[k + 1])
    return "#%02x%02x%02x" % tuple(round(a[j] + (b[j] - a[j]) * f) for j in range(3))


def grad(s, colors, bold=True):
    n = max(1, len(s) - 1)
    return [text(ch, lerp_color(colors, i / n), bold) for i, ch in enumerate(s)]


FIRE = ["#ff2a00", "#ff8a00", "#ffd000", "#ff8a00", "#ff2a00"]


def holo(x, y, z, lines, scale=1.0, tag="minerus_holo"):
    comp = {"text": "", "extra": []}
    for i, (s, color, bold) in enumerate(lines):
        if i:
            comp["extra"].append(text("\n", "white"))
        comp["extra"].extend(grad(s, color, bold) if isinstance(color, list) else [text(s, color, bold)])
    c(f"summon minecraft:text_display {x} {y} {z} {{billboard:\"center\",alignment:\"center\",shadow:1b,"
      f"background:1342177280,line_width:300,text:{snbt(comp)},"
      f"transformation:{{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[{scale}f,{scale}f,{scale}f]}},"
      f"Tags:[\"{tag}\"]}}")


def island(cx, cz, r, top, bottom, top_block="minecraft:grass_block"):
    """Парящий остров: перевёрнутый конус из камня с вкраплениями, сверху — top_block."""
    for y in range(top - 1, bottom - 1, -1):
        k = (y - bottom) / (top - bottom)
        rr = max(1.5, r * (k ** 0.55))
        mat = "minecraft:dirt" if y >= top - 3 else ("minecraft:stone" if y % 5 else "minecraft:andesite")
        for x, z1, z2 in disc_rows(rr, cx, cz):
            c(f"fill {x} {y} {z1} {x} {y} {z2} {mat}")
        if y < top - 3:
            for _ in range(int(rr)):
                a = rnd.random() * 2 * math.pi
                bx, bz = cx + round((rr - 0.6) * math.cos(a)), cz + round((rr - 0.6) * math.sin(a))
                m = rnd.choice(["minecraft:tuff", "minecraft:cobblestone", "minecraft:mossy_cobblestone",
                                "minecraft:coal_ore", "minecraft:copper_ore", "minecraft:gravel", "minecraft:blackstone"])
                c(f"setblock {bx} {y} {bz} {m}")
    for x, z1, z2 in disc_rows(r, cx, cz):
        c(f"fill {x} {top} {z1} {x} {top} {z2} {top_block}")


def railing(cx, cz, r, y):
    ring = set()
    for x in range(-r - 1, r + 2):
        for z in range(-r - 1, r + 2):
            if r - 1.2 < dist(x, z) <= r + 0.5:
                ring.add((cx + x, cz + z))
    for x, z in sorted(ring):
        c(f"setblock {x} {y} {z} minecraft:polished_blackstone_bricks")
        c(f"setblock {x} {y + 1} {z} minecraft:polished_blackstone_bricks")
        c(f"setblock {x} {y + 2} {z} minecraft:polished_blackstone_brick_slab[type=bottom]")
    return ring


def clear_above(x1, z1, x2, z2, y1, y2):
    for y0 in range(y1, y2 + 1, 8):
        for x0 in range(x1, x2 + 1, 31):
            for z0 in range(z1, z2 + 1, 128):
                c(f"fill {x0} {y0} {z0} {min(x0 + 30, x2)} {min(y0 + 7, y2)} {min(z0 + 127, z2)} minecraft:air")


# ===================================================================== подготовка
c("forceload add -144 -96 47 143")
c("forceload add 0 -96 79 63")
c("minecraft:kill @e[type=minecraft:text_display,tag=minerus_holo]")
clear_above(-52, -78, 52, 52, Y + 1, 199)

# ===================================================================== главный остров
island(0, 0, R, Y, 55)
ring = railing(0, 0, R, Y + 1)
road_cells = set()
for a in range(-R + 1, R):
    for w in range(-3, 4):
        mat = "minecraft:polished_blackstone_bricks" if abs(w) == 3 else "minecraft:polished_andesite"
        for (x, z) in ((w, a), (a, w)):
            if (x, z) not in ring and dist(x, z) < R - 1:
                c(f"setblock {x} {Y} {z} {mat}")
                road_cells.add((x, z))
# кольцевая аллея r 30..33
for x in range(-34, 35):
    for z in range(-34, 35):
        d = dist(x, z)
        if 30 <= d < 33.2:
            c(f"setblock {x} {Y} {z} minecraft:{'polished_blackstone_bricks' if d < 30.7 or d > 32.5 else 'polished_andesite'}")
            road_cells.add((x, z))
# центральная площадь r <= 14
for x, z1, z2 in disc_rows(14.5):
    c(f"fill {x} {Y} {z1} {x} {Y} {z2} minecraft:polished_andesite")
for x in range(-16, 17):
    for z in range(-16, 17):
        d = dist(x, z)
        if 13.6 < d <= 14.6:
            c(f"setblock {x} {Y} {z} minecraft:polished_deepslate")
        elif 14.6 < d <= 15.4 and abs(x) > 3 and abs(z) > 3:
            c(f"setblock {x} {Y} {z} minecraft:polished_blackstone_bricks")
        elif 9.6 < d <= 10.4:
            c(f"setblock {x} {Y} {z} minecraft:smooth_stone")
        elif d <= 15.4:
            road_cells.add((x, z))

# ===================================================================== двухъярусный фонтан
for x, z1, z2 in disc_rows(6.5):
    c(f"fill {x} {Y - 1} {z1} {x} {Y - 1} {z2} minecraft:prismarine_bricks")
for x in range(-8, 9):
    for z in range(-8, 9):
        d = dist(x, z)
        if 5.5 < d <= 6.6:
            c(f"setblock {x} {Y} {z} minecraft:smooth_quartz")
            c(f"setblock {x} {Y + 1} {z} minecraft:smooth_quartz_slab[type=bottom]")
        elif d <= 5.5:
            c(f"setblock {x} {Y} {z} minecraft:water")
c(f"fill -1 {Y} -1 1 {Y + 3} 1 minecraft:quartz_bricks")
c(f"fill -1 {Y + 1} -1 1 {Y + 3} 1 minecraft:chiseled_quartz_block")
for x in range(-3, 4):
    for z in range(-3, 4):
        d = dist(x, z)
        if 1.5 < d <= 2.9:
            c(f"setblock {x} {Y + 4} {z} minecraft:smooth_quartz")
        elif d <= 1.5:
            c(f"setblock {x} {Y + 4} {z} minecraft:sea_lantern")
c(f"fill -1 {Y + 5} -1 1 {Y + 5} 1 minecraft:water")
for x, z in ((2, 2), (-2, 2), (2, -2), (-2, -2)):
    c(f"setblock {x} {Y + 5} {z} minecraft:water")
for y in range(Y + 6, Y + 10):
    c(f"setblock 0 {y} 0 minecraft:end_rod")
c(f"setblock 0 {Y + 5} 0 minecraft:quartz_pillar")

# жаровни вокруг фонтана
for bx, bz in ((8, 8), (-8, 8), (8, -8), (-8, -8)):
    c(f"setblock {bx} {Y + 1} {bz} minecraft:polished_blackstone_bricks")
    c(f"setblock {bx} {Y + 2} {bz} minecraft:campfire[lit=true]")

# ===================================================================== павильоны NPC
PAV = {
    "shop": (-22, -22, "dark_oak_log", "spruce_planks", "spruce_slab",
             [("Торговец", ["#3ddc84", "#a0ff6b"], True), ("Блоки, еда и ресурсы", "white", False), ("/shop", "yellow", False)]),
    "buyer": (22, -22, "dark_oak_log", "spruce_planks", "spruce_slab",
              [("Скупщик", ["#ffb347", "#ffd000"], True), ("Продайте руду и урожай", "white", False), ("/buyer", "yellow", False)]),
    "donate": (-22, 22, "stripped_cherry_log", "cherry_planks", "cherry_slab",
               [("Привилегии", ["#d18cff", "#ff6bd5"], True), ("7 косметических рангов", "white", False), ("/donate", "yellow", False)]),
    "guide": (22, 22, "stripped_birch_log", "birch_planks", "birch_slab",
              [("Гид", ["#6bcbff", "#00e5ff"], True), ("Правила и помощь", "white", False), ("/menu", "yellow", False)]),
}
pav_cells = set()
for key, (px, pz, log, planks, slab, label) in PAV.items():
    pav_cells |= {(px + dx, pz + dz) for dx in range(-6, 7) for dz in range(-6, 7)}
    c(f"fill {px - 4} {Y} {pz - 4} {px + 4} {Y} {pz + 4} minecraft:{planks}")
    c(f"fill {px - 4} {Y + 1} {pz - 4} {px + 4} {Y + 5} {pz + 4} minecraft:air")
    for dx in (-4, 4):
        for dz in (-4, 4):
            c(f"fill {px + dx} {Y + 1} {pz + dz} {px + dx} {Y + 5} {pz + dz} minecraft:{log}")
    c(f"fill {px - 5} {Y + 6} {pz - 5} {px + 5} {Y + 6} {pz + 5} minecraft:{planks}")
    c(f"fill {px - 4} {Y + 7} {pz - 4} {px + 4} {Y + 7} {pz + 4} minecraft:{slab}[type=bottom]")
    c(f"fill {px - 3} {Y + 7} {pz - 3} {px + 3} {Y + 7} {pz + 3} minecraft:{planks}")
    c(f"fill {px - 2} {Y + 8} {pz - 2} {px + 2} {Y + 8} {pz + 2} minecraft:{slab}[type=bottom]")
    c(f"fill {px - 1} {Y + 8} {pz - 1} {px + 1} {Y + 8} {pz + 1} minecraft:{planks}")
    c(f"setblock {px} {Y + 9} {pz} minecraft:{slab}[type=bottom]")
    for dx, dz in ((-3, -3), (3, -3), (-3, 3), (3, 3)):
        c(f"setblock {px + dx} {Y + 5} {pz + dz} minecraft:lantern[hanging=true]")
    sx = 1 if px < 0 else -1
    sz = 1 if pz < 0 else -1
    c(f"setblock {px + 3 * sx} {Y + 1} {pz} minecraft:barrel[facing=up]")
    c(f"setblock {px} {Y + 1} {pz + 3 * sz} minecraft:barrel[facing=up]")
    c(f"setblock {px + 3 * sx} {Y + 2} {pz} minecraft:potted_red_tulip")
    holo(px + 0.5, Y + 10.8, pz + 0.5, label, 1.5)

# ===================================================================== площадки NPC у площади
PADS = {
    "auction": (-7, -19, [("Аукцион", ["#ffd000", "#ff8a00"], True), ("Покупай и продавай", "white", False), ("/ah", "yellow", False)]),
    "clans": (7, -19, [("Кланы", ["#ff6b6b", "#ffb347"], True), ("Создай свой клан", "white", False), ("/clan", "yellow", False)]),
    "events": (-19, 7, [("Ивенты", ["#d18cff", "#6bcbff"], True), ("Мистический сундук", "white", False), ("/event", "yellow", False)]),
    "mine": (19, -7, [("Шахта", ["#c0c0c0", "#6bcbff"], True), ("Обновляется каждые 10 мин", "white", False), ("/warp mine", "yellow", False)]),
    "cases": (19, 7, [("Кейсы", ["#ffd36b", "#ff8a00"], True), ("Обычный • Редкий • Донатный", "white", False), ("/cases", "yellow", False)]),
    "arena": (-19, -7, [("PvP-Арена", ["#ff2a00", "#ff6b6b"], True), ("Сражайся без потери спавна", "white", False), ("/warp arena", "yellow", False)]),
}
for key, (px, pz, label) in PADS.items():
    c(f"fill {px - 1} {Y} {pz - 1} {px + 1} {Y} {pz + 1} minecraft:polished_blackstone_bricks")
    c(f"setblock {px} {Y} {pz} minecraft:gilded_blackstone")
    for dx, dz in ((-2, -2), (2, 2)):
        c(f"setblock {px + dx} {Y + 1} {pz + dz} minecraft:polished_blackstone_wall")
        c(f"setblock {px + dx} {Y + 2} {pz + dz} minecraft:soul_lantern")
    holo(px + 0.5, Y + 3.4, pz + 0.5, label, 1.0)

# ===================================================================== огненные врата на выходах дорог
for sgn in (-1, 1):
    for axis in ("z", "x"):
        a = 44 * sgn

        def at(w):
            return (w, a) if axis == "z" else (a, w)
        for w in (-4, 4):
            x, z = at(w)
            c(f"fill {x} {Y + 1} {z} {x} {Y + 8} {z} minecraft:polished_blackstone_bricks")
            c(f"setblock {x} {Y + 1} {z} minecraft:chiseled_polished_blackstone")
            c(f"setblock {x} {Y + 8} {z} minecraft:chiseled_polished_blackstone")
            c(f"setblock {x} {Y + 9} {z} minecraft:polished_blackstone_bricks")
            c(f"setblock {x} {Y + 10} {z} minecraft:campfire[lit=true]")
        for w in range(-3, 4):
            x, z = at(w)
            c(f"setblock {x} {Y + 9} {z} minecraft:{'gilded_blackstone' if w == 0 else 'polished_blackstone_bricks'}")
        for w in (-2, 0, 2):
            x, z = at(w)
            c(f"setblock {x} {Y + 8} {z} minecraft:soul_lantern[hanging=true]")
        hx_, hz_ = at(0)
        holo(hx_ + 0.5, Y + 3.2, hz_ + 0.5, [("Выход в мир", ["#d18cff", "#ff6bd5"], True), ("/tpr — случайная телепортация", "white", False)], 0.9)

# ===================================================================== фонари вдоль дорог
for a in (-38, -30, -22, 22, 30, 38):
    for w in (-4, 4):
        for (x, z) in ((w, a), (a, w)):
            if dist(x, z) < R - 2:
                c(f"setblock {x} {Y + 1} {z} minecraft:polished_blackstone_wall")
                c(f"setblock {x} {Y + 2} {z} minecraft:soul_lantern")

# ===================================================================== деревья, кусты, цветы, светлячки
for (tx, tz) in ((-38, 10), (38, -10), (10, 38), (-10, -38), (38, 12), (-38, -12), (12, -38), (-12, 38)):
    c(f"place feature minecraft:fancy_oak {tx} {Y + 1} {tz}")
for (tx, tz) in ((-24, -36), (24, 36), (-36, 24), (36, -24)):
    c(f"place feature minecraft:cherry {tx} {Y + 1} {tz}")
flowers = ["minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:oxeye_daisy",
           "minecraft:azure_bluet", "minecraft:allium", "minecraft:short_grass", "minecraft:short_grass",
           "minecraft:short_grass", "minecraft:firefly_bush"]
for _ in range(700):
    x, z = rnd.randint(-R, R), rnd.randint(-R, R)
    d = dist(x, z)
    if d > R - 2.5 or d < 16 or (x, z) in road_cells or (x, z) in pav_cells or abs(x) <= 4 or abs(z) <= 4:
        continue
    c(f"setblock {x} {Y + 1} {z} {rnd.choice(flowers)} keep")
for (bx, bz) in ((-10, 17), (10, 17), (-17, 10), (17, 10), (-10, -17), (10, -17), (-17, -10), (17, -10)):
    c(f"setblock {bx} {Y + 1} {bz} minecraft:flowering_azalea keep")

# скамейки у точки спавна
for x in (-7, -6, 6, 7):
    c(f"setblock {x} {Y + 1} 17 minecraft:spruce_stairs[facing=north]")

# ===================================================================== ГИГАНТСКАЯ НАДПИСЬ ClinderGrief
font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22)
title = "ClinderGrief"
bb = font.getbbox(title)
W, H = bb[2] - bb[0] + 2, bb[3] - bb[1] + 2
img = Image.new("1", (W, H), 0)
ImageDraw.Draw(img).text((1 - bb[0], 1 - bb[1]), title, font=font, fill=1)
px = {(x, y) for x in range(W) for y in range(H) if img.getpixel((x, y))}
TZ, TY, X0 = -74, 114, -(W // 2)
PALETTE = ["red_concrete", "red_concrete", "orange_concrete", "orange_concrete", "yellow_concrete",
           "orange_concrete", "orange_concrete", "red_concrete", "red_concrete"]
outline = set()
for (x, y) in px:
    for dx in (-1, 0, 1):
        for dy in (-1, 0, 1):
            if (x + dx, y + dy) not in px:
                outline.add((x + dx, y + dy))
# лицевой слой (Z=TZ) — градиент по горизонтали, задний слой (Z=TZ-1) — чёрная обводка и «толщина»
for y in range(-1, H + 1):
    wy = TY + (H - 1 - y)
    runs = {}
    for x in range(-1, W + 1):
        if (x, y) in px:
            mat = PALETTE[min(len(PALETTE) - 1, int(x / W * len(PALETTE)))]
            if y >= H - 4:
                mat = "magma_block" if mat != "yellow_concrete" else "shroomlight"
            runs.setdefault(("f", mat), []).append(x)
        if (x, y) in px or (x, y) in outline:
            runs.setdefault(("b", "black_concrete"), []).append(x)
    for (layer, mat), xs in runs.items():
        xs.sort()
        start = prev = xs[0]
        for x in xs[1:] + [None]:
            if x is not None and x == prev + 1:
                prev = x
                continue
            z = TZ if layer == "f" else TZ - 1
            c(f"fill {X0 + start} {wy} {z} {X0 + prev} {wy} {z} minecraft:{mat}")
            if x is not None:
                start = prev = x
# подсветка под надписью
for x in range(X0, X0 + W, 6):
    c(f"setblock {x} {TY - 2} {TZ - 1} minecraft:shroomlight")
holo(0.5, TY - 4, TZ + 0.5, [("Выживание • Экономика • Кланы • Ивенты", ["#ffd36b", "#ff8a00"], True)], 3.0)

# ===================================================================== голограммы на площади
holo(0.5, Y + 11.5, 0.5, [("✦ ClinderGrief ✦", FIRE, True), ("Добро пожаловать на сервер!", "#ffd36b", False)], 2.2)
holo(0.5, Y + 3.2, 16.5, [("Меню сервера: /menu", ["#a0ff6b", "#3ddc84"], True), ("Магазин, аукцион, кланы, телепорты", "gray", False),
                          ("Награда дня: /daily", "#ffd36b", False)], 1.0)
holo(-9.5, Y + 2.8, 21.5, [("Приваты", "dark_green", True), ("Золотая лопата: ПКМ по 2 углам", "white", False), ("Палка: показать границы", "gray", False)], 0.9)
holo(10.5, Y + 2.8, 21.5, [("В дикий мир", "light_purple", True), ("/tpr или /rtp", "white", False), ("Дом: /sethome и /home", "gray", False)], 0.9)

# ===================================================================== остров шахты (0, 100, 120)
clear_above(-22, 98, 22, 142, Y + 1, 140)
island(0, 120, 20, Y, 70, "minecraft:stone_bricks")
railing(0, 120, 20, Y + 1)
c(f"fill -11 84 109 11 {Y} 131 minecraft:polished_deepslate")   # стенки и дно ямы
c("fill -10 85 110 10 99 130 minecraft:stone")                   # объём руды (заполнит ClinderCore)
c(f"fill -10 {Y} 110 10 {Y} 130 minecraft:air")                  # яма открыта сверху
c(f"fill -12 {Y} 108 12 {Y} 108 minecraft:polished_blackstone_bricks")
c(f"fill -12 {Y} 132 12 {Y} 132 minecraft:polished_blackstone_bricks")
c(f"fill -12 {Y} 108 -12 {Y} 132 minecraft:polished_blackstone_bricks")
c(f"fill 12 {Y} 108 12 {Y} 132 minecraft:polished_blackstone_bricks")
for (x, z) in ((-12, 108), (12, 108), (-12, 132), (12, 132)):
    c(f"setblock {x} {Y + 1} {z} minecraft:polished_blackstone_wall")
    c(f"setblock {x} {Y + 2} {z} minecraft:lantern")
holo(0.5, Y + 4, 104.5, [("⛏ Шахта", ["#c0c0c0", "#6bcbff"], True), ("Руда обновляется каждые 10 минут", "white", False),
                         ("Назад: /spawn", "gray", False)], 1.3)

# ===================================================================== остров арены (-120, 100, 0)
clear_above(-144, -24, -96, 24, Y + 1, 140)
island(-120, 0, 22, Y, 72, "minecraft:polished_blackstone_bricks")
for x in range(-142, -97):
    for z in range(-22, 23):
        d = dist(x + 120, z)
        if d <= 22 and (int(d) % 4 == 0):
            c(f"setblock {x} {Y} {z} minecraft:red_nether_bricks")
arena_ring = set()
for x in range(-144, -95):
    for z in range(-24, 25):
        if 20.8 < dist(x + 120, z) <= 22.5:
            arena_ring.add((x, z))
for x, z in sorted(arena_ring):
    c(f"fill {x} {Y + 1} {z} {x} {Y + 3} {z} minecraft:polished_blackstone_bricks")
    c(f"setblock {x} {Y + 4} {z} minecraft:red_stained_glass")
for (ox, oz) in ((-128, -6), (-112, 6), (-126, 9), (-114, -9), (-120, 0)):
    c(f"fill {ox} {Y + 1} {oz} {ox} {Y + 3} {oz} minecraft:chiseled_polished_blackstone")
    c(f"setblock {ox} {Y + 4} {oz} minecraft:campfire[lit=true]")
holo(-119.5, Y + 6, 0.5, [("⚔ PvP-Арена", ["#ff2a00", "#ff6b6b"], True), ("Здесь PvP включено • Выход: /spawn", "white", False)], 1.6)

# ===================================================================== тюрьма в скале под главным островом
c("fill -2 80 -2 2 84 2 minecraft:polished_deepslate")
c("fill -1 81 -1 1 83 1 minecraft:air")
c("setblock 0 84 0 minecraft:sea_lantern")
c("fill -1 80 -1 1 80 1 minecraft:deepslate_tiles")

c("setworldspawn 0 101 20 180 0")
c("forceload remove all")

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "-"
    data = "\n".join(cmds) + "\n"
    if out == "-":
        sys.stdout.write(data)
    else:
        open(out, "w", encoding="utf-8").write(data)
    print(f"{len(cmds)} commands", file=sys.stderr)
