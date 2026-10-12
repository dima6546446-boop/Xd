#!/usr/bin/env python3
"""Генерирует команды постройки спавна ClinderGrief (летающий остров над точкой 0,0).

Вывод: список команд для консоли сервера (по одной в строке). Координаты:
  центр острова — X=0 Z=0, пол — Y=100, игроки ходят на Y=101;
  точка спавна — 0.5 101 14.5, взгляд на север (на фонтан);
  павильоны: Торговец (-14,-14), Скупщик (14,-14), Привилегии (-14,14), Гид (14,14);
  тюрьма — 0.5 81 0.5 (внутри скалы под островом).
"""
import json
import math
import random
import sys

R = 28
Y = 100
BOTTOM = 70
rnd = random.Random(1211)
cmds = []


def c(s):
    cmds.append(s)


def disc_rows(r):
    """Строки круга радиуса r: (x, zmin, zmax)."""
    rows = []
    for x in range(-int(r), int(r) + 1):
        h = math.sqrt(max(r * r - x * x, 0))
        zm = int(math.floor(h))
        rows.append((x, -zm, zm))
    return rows


def dist(x, z):
    return math.sqrt(x * x + z * z)


def text(s, color="white", bold=False):
    d = {"text": s, "color": color}
    if bold:
        d["bold"] = True
    return d


def snbt_component(parts):
    # Компонент текста в SNBT (1.21.5+): список/словарь как JSON-подобный SNBT без кавычек у ключей.
    def conv(o):
        if isinstance(o, dict):
            return "{" + ",".join(f"{k}:{conv(v)}" for k, v in o.items()) + "}"
        if isinstance(o, list):
            return "[" + ",".join(conv(v) for v in o) + "]"
        if isinstance(o, bool):
            return "true" if o else "false"
        return json.dumps(o, ensure_ascii=False)
    return conv(parts)


def grad(s, colors, bold=True):
    """Посимвольный градиент для text_display: список компонентов с #rrggbb."""
    def hx(c):
        return [int(c[i:i + 2], 16) for i in (1, 3, 5)]
    out = []
    n = max(1, len(s) - 1)
    for i, ch in enumerate(s):
        pos = i / n * (len(colors) - 1)
        k = min(int(pos), len(colors) - 2)
        f = pos - k
        a, b = hx(colors[k]), hx(colors[k + 1])
        rgb = "#%02x%02x%02x" % tuple(round(a[j] + (b[j] - a[j]) * f) for j in range(3))
        out.append(text(ch, rgb, bold))
    return out


FIRE = ["#ff2a00", "#ff8a00", "#ffd000", "#ff8a00", "#ff2a00"]


def holo(x, y, z, lines, scale=1.0, tag="minerus_holo"):
    comp = {"text": "", "extra": []}
    for i, (s, color, bold) in enumerate(lines):
        if i:
            comp["extra"].append(text("\n", "white"))
        if isinstance(color, list):
            comp["extra"].extend(grad(s, color, bold))
        else:
            comp["extra"].append(text(s, color, bold))
    c(f"summon minecraft:text_display {x} {y} {z} {{billboard:\"center\",alignment:\"center\",shadow:1b,"
      f"background:1342177280,line_width:260,text:{snbt_component(comp)},"
      f"transformation:{{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[{scale}f,{scale}f,{scale}f]}},"
      f"Tags:[\"{tag}\"]}}")


# --- подготовка: прогрузить чанки, убрать старые голограммы
c("forceload add -48 -48 47 47")
c("minecraft:kill @e[type=minecraft:text_display,tag=minerus_holo]")

# --- очистить пространство над островом (горы/деревья)
for y0 in range(Y + 1, 200, 8):
    for x0 in range(-R - 3, R + 4, 31):
        c(f"fill {x0} {y0} {-R - 3} {min(x0 + 30, R + 3)} {min(y0 + 7, 199)} {R + 3} minecraft:air")

# --- нижняя часть острова: перевёрнутый конус из камня
for y in range(Y - 1, BOTTOM - 1, -1):
    k = (y - BOTTOM) / (Y - BOTTOM)
    r = max(1.5, R * (k ** 0.55))
    mat = "minecraft:dirt" if y >= Y - 3 else ("minecraft:stone" if y % 5 else "minecraft:andesite")
    for x, z1, z2 in disc_rows(r):
        c(f"fill {x} {y} {z1} {x} {y} {z2} {mat}")
    if y < Y - 3:
        # вкрапления руды и туфа по краю
        for _ in range(int(r)):
            a = rnd.random() * 2 * math.pi
            rr = r - 0.6
            bx, bz = round(rr * math.cos(a)), round(rr * math.sin(a))
            m = rnd.choice(["minecraft:tuff", "minecraft:cobblestone", "minecraft:mossy_cobblestone", "minecraft:coal_ore", "minecraft:copper_ore", "minecraft:gravel"])
            c(f"setblock {bx} {y} {bz} {m}")

# --- верх: трава
for x, z1, z2 in disc_rows(R):
    c(f"fill {x} {Y} {z1} {x} {Y} {z2} minecraft:grass_block")

# --- бордюр и ограждение по краю (камень + полублок: не перепрыгнуть)
ring = set()
for x in range(-R, R + 1):
    for z in range(-R, R + 1):
        d = dist(x, z)
        if R - 1.2 < d <= R + 0.5:
            ring.add((x, z))
for x, z in sorted(ring):
    c(f"setblock {x} {Y} {z} minecraft:stone_bricks")
    c(f"setblock {x} {Y + 1} {z} minecraft:stone_bricks")
    c(f"setblock {x} {Y + 2} {z} minecraft:stone_brick_slab[type=bottom]")

# --- дороги (крест шириной 5) и площадь
for a in range(-R + 1, R):
    for w in range(-2, 3):
        mat = "minecraft:stone_bricks" if abs(w) == 2 else "minecraft:polished_andesite"
        for (x, z) in ((w, a), (a, w)):
            if (x, z) not in ring and dist(x, z) < R - 1:
                c(f"setblock {x} {Y} {z} {mat}")
for x, z1, z2 in disc_rows(10.5):
    c(f"fill {x} {Y} {z1} {x} {Y} {z2} minecraft:polished_andesite")
for x in range(-11, 12):
    for z in range(-11, 12):
        if 9.6 < dist(x, z) <= 10.6:
            c(f"setblock {x} {Y} {z} minecraft:polished_deepslate")
for x in range(-11, 12):
    for z in range(-11, 12):
        if 6.6 < dist(x, z) <= 7.5:
            c(f"setblock {x} {Y} {z} minecraft:smooth_stone")

# --- фонтан
for x, z1, z2 in disc_rows(4.5):
    c(f"fill {x} {Y - 1} {z1} {x} {Y - 1} {z2} minecraft:prismarine_bricks")
for x in range(-5, 6):
    for z in range(-5, 6):
        d = dist(x, z)
        if 3.5 < d <= 4.6:
            c(f"setblock {x} {Y} {z} minecraft:smooth_quartz")
            c(f"setblock {x} {Y + 1} {z} minecraft:smooth_quartz_slab[type=bottom]")
        elif d <= 3.5:
            c(f"setblock {x} {Y} {z} minecraft:water")
for y in range(Y, Y + 4):
    c(f"setblock 0 {y} 0 minecraft:quartz_pillar")
c(f"setblock 0 {Y + 4} 0 minecraft:sea_lantern")
for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
    c(f"setblock {dx} {Y + 4} {dz} minecraft:water")

# --- павильоны
PAV = {
    "shop": (-14, -14, "minecraft:dark_oak_log", "minecraft:spruce_planks", "minecraft:spruce_slab", "minecraft:spruce_stairs",
             [("Магазин", "green", True), ("Блоки, еда и ресурсы", "white", False), ("/shop", "yellow", False)]),
    "buyer": (14, -14, "minecraft:dark_oak_log", "minecraft:spruce_planks", "minecraft:spruce_slab", "minecraft:spruce_stairs",
              [("Скупщик", "gold", True), ("Продайте руду и урожай", "white", False), ("/buyer", "yellow", False)]),
    "donate": (-14, 14, "minecraft:stripped_cherry_log", "minecraft:cherry_planks", "minecraft:cherry_slab", "minecraft:cherry_stairs",
               [("Привилегии", "light_purple", True), ("Только косметика", "white", False), ("/donate", "yellow", False)]),
    "guide": (14, 14, "minecraft:stripped_birch_log", "minecraft:birch_planks", "minecraft:birch_slab", "minecraft:birch_stairs",
              [("Гид", "aqua", True), ("Правила и помощь", "white", False), ("/menu", "yellow", False)]),
}
for key, (px, pz, log, planks, slab, stairs, label) in PAV.items():
    c(f"fill {px - 3} {Y} {pz - 3} {px + 3} {Y} {pz + 3} {planks}")
    c(f"fill {px - 3} {Y + 1} {pz - 3} {px + 3} {Y + 4} {pz + 3} minecraft:air")
    for dx in (-3, 3):
        for dz in (-3, 3):
            c(f"fill {px + dx} {Y + 1} {pz + dz} {px + dx} {Y + 4} {pz + dz} {log}")
    c(f"fill {px - 4} {Y + 5} {pz - 4} {px + 4} {Y + 5} {pz + 4} {planks}")
    c(f"fill {px - 3} {Y + 6} {pz - 3} {px + 3} {Y + 6} {pz + 3} {slab}[type=bottom]")
    c(f"fill {px - 2} {Y + 6} {pz - 2} {px + 2} {Y + 6} {pz + 2} {planks}")
    c(f"fill {px - 1} {Y + 7} {pz - 1} {px + 1} {Y + 7} {pz + 1} {slab}[type=bottom]")
    c(f"setblock {px} {Y + 7} {pz} {planks}")
    c(f"setblock {px} {Y + 8} {pz} {slab}[type=bottom]")
    for dx, dz in ((-2, -2), (2, -2), (-2, 2), (2, 2)):
        c(f"setblock {px + dx} {Y + 4} {pz + dz} minecraft:lantern[hanging=true]")
    # прилавок (сторона к центру)
    sx = 1 if px < 0 else -1
    sz = 1 if pz < 0 else -1
    c(f"setblock {px + 2 * sx} {Y + 1} {pz} minecraft:barrel[facing=up]")
    c(f"setblock {px} {Y + 1} {pz + 2 * sz} minecraft:barrel[facing=up]")
    c(f"setblock {px + 2 * sx} {Y + 2} {pz} minecraft:potted_red_tulip" if key != "donate" else f"setblock {px + 2 * sx} {Y + 2} {pz} minecraft:potted_pink_tulip")
    holo(px + 0.5, Y + 9.6, pz + 0.5, label, 1.3)

# --- фонари вдоль дорог
for a in (-22, -16, 16, 22):
    for w in (-3, 3):
        for (x, z) in ((w, a), (a, w)):
            if dist(x, z) < R - 1.5:
                c(f"setblock {x} {Y + 1} {z} minecraft:oak_fence")
                c(f"setblock {x} {Y + 2} {z} minecraft:lantern")

# --- деревья и цветы
for (tx, tz) in ((-22, 7), (22, -7), (7, 22), (-7, -22), (22, 8), (-22, -8)):
    c(f"place feature minecraft:fancy_oak {tx} {Y + 1} {tz}")
flowers = ["minecraft:poppy", "minecraft:dandelion", "minecraft:cornflower", "minecraft:oxeye_daisy",
           "minecraft:azure_bluet", "minecraft:allium", "minecraft:short_grass", "minecraft:short_grass", "minecraft:short_grass"]
pav_cells = {(px + dx, pz + dz) for (px, pz, *_r) in PAV.values() for dx in range(-4, 5) for dz in range(-4, 5)}
for _ in range(220):
    x, z = rnd.randint(-R, R), rnd.randint(-R, R)
    d = dist(x, z)
    if d > R - 2.5 or d < 11.5 or abs(x) <= 3 or abs(z) <= 3 or (x, z) in pav_cells:
        continue
    c(f"setblock {x} {Y + 1} {z} {rnd.choice(flowers)} keep")
for (bx, bz) in ((-5, 9), (5, 9), (-9, 5), (9, 5), (-5, -9), (5, -9), (-9, -5), (9, -5)):
    c(f"setblock {bx} {Y + 1} {bz} minecraft:flowering_azalea")

# --- скамейки у площади
for (x, z, f) in ((-4, 12, "north"), (4, 12, "north"), (-4, -12, "south"), (4, -12, "south")):
    pass
for z, facing in ((12, "north"), (-12, "south")):
    for x in (-6, -5, 5, 6):
        c(f"setblock {x} {Y + 1} {z} minecraft:spruce_stairs[facing={facing}]")

# --- голограммы
holo(0.5, Y + 8.6, 0.5, [("✦ ClinderGrief ✦", FIRE, True), ("Добро пожаловать на сервер!", "#ffd36b", False),
                         ("Выживание • Экономика • Приваты • Титулы", "gray", False)], 2.0)
holo(0.5, Y + 3.2, 10.5, [("Меню сервера: /menu", ["#a0ff6b", "#3ddc84"], True), ("Магазин, скупщик, телепорты, помощь", "gray", False),
                          ("Награда дня: /daily", "#ffd36b", False)], 1.0)
holo(-6.5, Y + 2.8, 15.5, [("Приваты", "dark_green", True), ("Золотая лопата: ПКМ по 2 углам", "white", False), ("Палка: показать границы", "gray", False)], 0.9)
holo(7.5, Y + 2.8, 15.5, [("В дикий мир", "light_purple", True), ("/tpr или /rtp", "white", False), ("Дом: /sethome и /home", "gray", False)], 0.9)
for (x, z) in ((0.5, -25.5), (0.5, 26.5), (-25.5, 0.5), (26.5, 0.5)):
    holo(x, Y + 3.0, z, [("Выход в мир", "light_purple", True), ("/tpr — случайная телепортация", "white", False)], 0.8)

# --- огненные врата на выходах дорог
for sgn in (-1, 1):
    for axis in ("z", "x"):
        a = 25 * sgn
        def at(w, y):
            return (w, y, a) if axis == "z" else (a, y, w)
        for w in (-3, 3):
            x, _, z = at(w, 0)
            c(f"fill {x} {Y + 1} {z} {x} {Y + 6} {z} minecraft:polished_blackstone_bricks")
            c(f"setblock {x} {Y + 1} {z} minecraft:chiseled_polished_blackstone")
            c(f"setblock {x} {Y + 6} {z} minecraft:chiseled_polished_blackstone")
            c(f"setblock {x} {Y + 7} {z} minecraft:polished_blackstone_bricks")
            c(f"setblock {x} {Y + 8} {z} minecraft:campfire[lit=true]")
        for w in range(-2, 3):
            x, _, z = at(w, 0)
            c(f"setblock {x} {Y + 7} {z} minecraft:{'gilded_blackstone' if w == 0 else 'polished_blackstone_bricks'}")
        for w in (-1, 1):
            x, _, z = at(w, 0)
            c(f"setblock {x} {Y + 6} {z} minecraft:soul_lantern[hanging=true]")

# --- жаровни с дымом вокруг фонтана
for bx, bz in ((6, 6), (-6, 6), (6, -6), (-6, -6)):
    c(f"setblock {bx} {Y + 1} {bz} minecraft:polished_blackstone_bricks")
    c(f"setblock {bx} {Y + 2} {bz} minecraft:campfire[lit=true]")

# --- светлячки (кусты светлячков 1.21.5+) и кольцо из чернита у площади
for _ in range(60):
    x, z = rnd.randint(-R, R), rnd.randint(-R, R)
    d = dist(x, z)
    if d > R - 2.5 or d < 11.5 or abs(x) <= 3 or abs(z) <= 3 or (x, z) in pav_cells:
        continue
    c(f"setblock {x} {Y + 1} {z} minecraft:firefly_bush keep")
for x in range(-12, 13):
    for z in range(-12, 13):
        if 10.6 < dist(x, z) <= 11.4 and abs(x) > 2 and abs(z) > 2:
            c(f"setblock {x} {Y} {z} minecraft:polished_blackstone_bricks")

# --- тюрьма в скале под островом (Essentials /jail)
c("fill -2 80 -2 2 84 2 minecraft:polished_deepslate")
c("fill -1 81 -1 1 83 1 minecraft:air")
c("setblock 0 84 0 minecraft:sea_lantern")
c("fill -1 80 -1 1 80 1 minecraft:deepslate_tiles")

# --- точка спавна мира и завершение
c("setworldspawn 0 101 14 180 0")
c("forceload remove all")

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "-"
    data = "\n".join(cmds) + "\n"
    if out == "-":
        sys.stdout.write(data)
    else:
        open(out, "w", encoding="utf-8").write(data)
    print(f"{len(cmds)} commands", file=sys.stderr)
