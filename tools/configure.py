#!/usr/bin/env python3
"""Применяет настройки сборки ClinderGrief к папке сервера (после первого запуска, когда конфиги уже созданы).

Использование: python3 tools/configure.py <папка сервера>
"""
import os
import sys
import re
import shutil
import yaml
from ruamel.yaml import YAML

sys.path.insert(0, os.path.dirname(__file__))
from economy import BUYER  # noqa: E402
from gp_messages_ru import RU as GP_RU  # noqa: E402
import menus  # noqa: E402

S = sys.argv[1]
P = os.path.join(S, "plugins")
ry = YAML()
ry.preserve_quotes = True
ry.width = 4096


def edit(path, fn):
    path = os.path.join(S, path)
    with open(path, encoding="utf-8") as f:
        data = ry.load(f)
    fn(data)
    with open(path, "w", encoding="utf-8") as f:
        ry.dump(data, f)


def write(path, text):
    path = os.path.join(S, path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text.lstrip("\n"))


def set_in(d, dotted, value):
    keys = dotted.split(".")
    for k in keys[:-1]:
        d = d[k]
    d[keys[-1]] = value


def uesc(s):
    """\\uXXXX-экранирование для server.properties (надёжно в любой кодировке)."""
    return "".join(c if ord(c) < 128 else "\\u%04x" % ord(c) for c in s)


# ---------------------------------------------------------------- server.properties
props_path = os.path.join(S, "server.properties")
lines = open(props_path, encoding="utf-8").read().splitlines()
want = {
    "motd": uesc("§8§l✦ §4§lC§c§ll§c§li§6§ln§6§ld§e§le§e§lr§6§lG§6§lr§c§li§c§le§4§lf §8§l✦ §7Выживание §e1.21.11\\n§6⚡ §eЕжедневные награды §8• §cОхота за головами §8• §bТитулы"),
    "difficulty": "normal",
    "max-players": "50",
    "spawn-protection": "0",
    "view-distance": "8",
    "simulation-distance": "6",
    "enable-command-block": "false",
    "online-mode": "true",
    "white-list": "false",
    "allow-flight": "false",
    "server-port": "25565",
}
out = []
for ln in lines:
    if ln.startswith("management-server-secret="):
        continue  # сгенерируется заново на компьютере владельца
    k = ln.split("=", 1)[0]
    if k in want:
        ln = f"{k}={want.pop(k)}"
    out.append(ln)
out += [f"{k}={v}" for k, v in want.items()]
open(props_path, "w", encoding="utf-8", newline="\n").write("\n".join(out) + "\n")

# ---------------------------------------------------------------- spigot / paper
edit("spigot.yml", lambda d: d["messages"].update({
    "whitelist": "Вас нет в белом списке этого сервера!",
    "unknown-command": "Неизвестная команда. Введите /help или откройте /menu.",
    "server-full": "Сервер переполнен! Попробуйте зайти позже.",
    "outdated-client": "Устаревший клиент! Используйте версию {0}",
    "outdated-server": "Устаревший сервер! Сервер работает на версии {0}",
    "restart": "Сервер перезагружается",
}))


def paper_global(d):
    d["messages"]["no-permission"] = "<red>У вас нет прав на эту команду. Если это ошибка — обратитесь к администрации."
    d["messages"]["kick"]["connection-throttle"] = "Слишком частые подключения! Подождите немного и попробуйте снова."


edit("config/paper-global.yml", paper_global)


def paper_world(d):
    d["anticheat"]["anti-xray"]["enabled"] = True


edit("config/paper-world-defaults.yml", paper_world)

# ---------------------------------------------------------------- Essentials
ESS_CFG = {
    "locale": "ru",
    "ops-name-color": "none",
    "teleport-delay": 3,
    "teleport-cooldown": 5,
    "starting-balance": 500,
    "max-tempban-time": 604800,
    "custom-join-message": "&8[&a+&8] &7{USERNAME}",
    "custom-quit-message": "&8[&c-&8] &7{USERNAME}",
    "respawn-at-home": False,
    "spawn-on-join": False,
    "currency-symbol": "$",
}


def essentials(d):
    for k, v in ESS_CFG.items():
        d[k] = v
    d["sethome-multiple"] = {"default": 3, "staff": 10}
    # EssentialsChat уже добавляет префикс LuckPerms в {DISPLAYNAME}
    d["chat"]["format"] = "{DISPLAYNAME}&8 » &f{MESSAGE}"
    d["newbies"]["announce-format"] = "&eПоприветствуйте нового игрока &6{DISPLAYNAME}&e!"
    d["newbies"]["spawnpoint"] = "default"
    d["newbies"]["kit"] = "start"


edit("plugins/Essentials/config.yml", essentials)

write("plugins/Essentials/kits.yml", """
# Наборы (Essentials). Выдать себе: /kit <название>. Право: essentials.kits.<название>
kits:
  start:
    # Стартовый набор: выдаётся автоматически при первом входе. delay: -1 — только один раз.
    delay: -1
    items:
      - stone_sword 1 name:&fМеч_новичка
      - stone_pickaxe 1 name:&fКирка_новичка
      - stone_axe 1 name:&fТопор_новичка
      - stone_shovel 1
      - bread 16
      - torch 16
      - oak_log 16
      - golden_shovel 1 name:&eЛопата_привата lore:&7ПКМ_по_двум_углам_—_создать_приват
      - stick 1 name:&eПросмотр_приватов lore:&7ПКМ_—_показать_границы_приватов
  food:
    # Ежедневная еда (раз в 24 часа)
    delay: 86400
    items:
      - bread 16
      - cooked_beef 8
      - apple 8
""")


def worth_key(mat):
    return mat.lower().replace("_", "")


worth = {}
for _, _, items in BUYER.values():
    for mat, price, _ in items:
        worth[worth_key(mat)] = float(price)
with open(os.path.join(P, "Essentials/worth.yml"), "w", encoding="utf-8") as f:
    f.write("# Цены скупщика (команда /sell и меню /buyer). Сгенерировано из tools/economy.py.\n"
            "# Предметы, которых нет в списке, продать нельзя. Изменить цену: /setworth <предмет> <цена>\n")
    yaml.safe_dump({"worth": worth}, f, sort_keys=True)

write("plugins/Essentials/motd.txt", """
&#ff5e00&l✦ &fДобро пожаловать на &#ff8a00&lClinderGrief&f, &#ffd000{PLAYER}&f! &#ff5e00&l✦
&7Сейчас онлайн: &f{ONLINE}
&7Главное меню сервера: &a/menu  &8•  &7Награда дня: &e/daily
&7Случайная телепортация в дикий мир: &a/tpr &7(или &a/rtp&7)
&7Защитить постройку: &eзолотая лопата &7(подробно в &a/menu&7)
&7Правила сервера: &a/rules
""")

write("plugins/Essentials/rules.txt", """
&6&lПравила сервера
&e1. &fУважайте других игроков. Оскорбления, травля, дискриминация запрещены.
&e2. &fЗапрещены читы, X-Ray, автокликеры и иные моды, дающие преимущество.
&e3. &fЗапрещён гриферинг и воровство, в том числе на незаприваченной территории.
&e4. &fЗапрещены спам, флуд, реклама других серверов и ссылки на сторонние ресурсы.
&e5. &fЗапрещены баги, дюпы и лаг-машины. О найденных ошибках сообщайте администрации.
&e6. &fНе выдавайте себя за администрацию и не передавайте свой аккаунт.
&e7. &fРешение администрации окончательное. Незнание правил не освобождает от ответственности.
&7Нарушения: предупреждение → мут → временный бан → бан. Вызвать модератора: &a/helpop <сообщение>
""")

write("plugins/Essentials/info.txt", """
&6&lClinderGrief &7— сервер выживания с экономикой
&e/menu &7— главное меню (магазин, скупщик, телепорты, помощь)
&e/shop &7— магазин сервера  &e/buyer &7— скупщик
&e/donate &7— привилегии  &e/rules &7— правила
""")


# ---------------------------------------------------------------- GriefPrevention
gp_path = os.path.join(P, "GriefPreventionData/messages.yml")
with open(gp_path, encoding="utf-8") as f:
    gp = ry.load(f)
for k, v in gp["Messages"].items():
    v["Text"] = GP_RU[k]
with open(gp_path, "w", encoding="utf-8") as f:
    f.write("# Русский перевод сообщений GriefPrevention (сборка ClinderGrief). Цвета задаются символом $ (например $a).\n")
    ry.dump(gp, f)


def gp_cfg(d):
    g = d["GriefPrevention"]
    g["Claims"]["InitialBlocks"] = 400
    g["Claims"]["Claim Blocks Accrued Per Hour"]["Default"] = 150
    g["Economy"]["ClaimBlocksPurchaseCost"] = 5.0
    g["Economy"]["ClaimBlocksSellValue"] = 2.0
    g["Economy"]["ClaimBlocksMaxBonus"] = 20000
    g["Spam"]["WarningMessage"] = "Пожалуйста, не спамьте. Спамеров блокируют."
    g["Spam"]["BanMessage"] = "Заблокирован за спам."
    # Антиспам GP несовместим с кириллицей: проверка использует \p{Alpha} (только латиница), из-за чего
    # любые русские сообщения переводятся в нижний регистр и считаются «похожими» (спам). Отключаем.
    g["Spam"]["Enabled"] = False
    g["Spam"]["BanOffenders"] = False
    # Кик «You must wait N seconds before logging-in again» зашит в код плагина по-английски;
    # защиту от частых перезаходов выполняет Paper (connection-throttle, сообщение переведено).
    g["Spam"]["LoginCooldownSeconds"] = 0
    g["Spam"]["AllowedIpAddresses"] = ""
    g["PvP"]["BlockedSlashCommands"] = "/home;/vanish;/spawn;/tpa;/tpr;/rtp;/warp;/back"


edit("plugins/GriefPreventionData/config.yml", gp_cfg)

# ---------------------------------------------------------------- PlayerParticles (донат-косметика)
edit("plugins/PlayerParticles/config.yml", lambda d: d.__setitem__("locale", "ru_RU"))

# ---------------------------------------------------------------- ClinderCore (собственный плагин)
cc_jar = os.path.join(os.path.dirname(__file__), "..", "clindercore", "build", "ClinderCore-1.0.0.jar")
if os.path.exists(cc_jar):
    shutil.copyfile(cc_jar, os.path.join(P, "ClinderCore-1.0.0.jar"))

# ---------------------------------------------------------------- DecentHolograms: живые топы у фонтана
TOPS = {
    "top_money": ("money", "&#ffd000&l⛁ Топ богачей", "&a", "world:-8.5:105.6:0.5", 90.0),
    "top_playtime": ("playtime", "&#6bcbff&l⌚ Топ по времени в игре", "&b", "world:9.5:105.6:0.5", -90.0),
    "top_kills": ("kills", "&#ff6b6b&l⚔ Топ убийц", "&c", "world:0.5:105.6:-8.5", 0.0),
}
os.makedirs(os.path.join(P, "DecentHolograms/holograms"), exist_ok=True)
for name, (typ, title, col, loc, facing) in TOPS.items():
    lines = [{"content": title, "height": 0.35}, {"content": "&8&m                    ", "height": 0.3}]
    medals = {1: "&#ffd700", 2: "&#c0c0c0", 3: "&#cd7f32"}
    for n in range(1, 11):
        lines.append({"content": f"{medals.get(n, '&7')}{n}. &f%clinder_top_{typ}_name_{n}% &8— {col}%clinder_top_{typ}_value_{n}%",
                      "height": 0.27})
    lines.append({"content": "&8&m                    ", "height": 0.3})
    lines.append({"content": "&7Обновление каждые 5 мин &8• &7/top", "height": 0.3})
    holo = {"location": loc, "enabled": True, "display-range": 32, "update-range": 32, "update-interval": 100,
            "facing": facing, "down-origin": False, "pages": [{"lines": lines, "actions": {}}]}
    with open(os.path.join(P, "DecentHolograms/holograms", name + ".yml"), "w", encoding="utf-8") as fh:
        yaml.safe_dump(holo, fh, allow_unicode=True, sort_keys=False, width=400)

# ---------------------------------------------------------------- QuickShop-Hikari
def qs(d):
    d["game-language"] = "ru_ru"
    d["enabled-languages"] = ["ru_ru"]
    d["shop"]["display-type"] = 3  # витрина через Display Entity, без ProtocolLib/packetevents
    d["shop-tax"]["basic"]["rate"] = 0.0  # налог скрыт от игроков (show: false), поэтому отключаем


edit("plugins/QuickShop-Hikari/config.yml", qs)

# ---------------------------------------------------------------- CoreProtect / Chunky / FancyNpcs
# Автоперевод CoreProtect требует платный donation key, поэтому кладём собственный перевод language.yml
edit("plugins/CoreProtect/config.yml", lambda d: d.__setitem__("language", "ru"))
shutil.copyfile(os.path.join(os.path.dirname(__file__), "lang/coreprotect_ru.yml"), os.path.join(P, "CoreProtect/language.yml"))
edit("plugins/Chunky/config.yml", lambda d: d.__setitem__("language", "ru"))
# FancyNpcs 2.9.2 выбрана намеренно (последняя версия под Java 21) — уведомление об обновлении не нужно
edit("plugins/FancyNpcs/config.yml", lambda d: d.__setitem__("mute_version_notification", True))

# ---------------------------------------------------------------- DeluxeMenus
names = menus.write_all(os.path.join(P, "DeluxeMenus"))
dm_cfg = {"check_updates": True, "debug": "LOW", "use_admin_commands_in_menus_list": False,
          "menus_list_page_size": 10, "metas_list_page_size": 15,
          "gui_menus": {n: {"file": n + ".yml"} for n in names}}
with open(os.path.join(P, "DeluxeMenus/config.yml"), "w", encoding="utf-8") as f:
    f.write("# DeluxeMenus — меню сервера ClinderGrief. Файлы меню лежат в gui_menus/.\n")
    yaml.safe_dump(dm_cfg, f, allow_unicode=True, sort_keys=False)

# ---------------------------------------------------------------- TAB
def gradient_frames(text, colors, frames=20):
    """Кадры анимации MiniMessage-градиента с бегущей фазой (TAB поддерживает MiniMessage)."""
    out = []
    for i in range(frames):
        phase = round(-1 + 2 * i / frames, 2)
        out.append(f"<bold><gradient:{':'.join(colors)}:{phase}>{text}</gradient></bold>")
    return out


FIRE = ["#ff2a00", "#ff8a00", "#ffd000", "#ff8a00", "#ff2a00"]


def tab(d):
    d["header-footer"]["designs"] = {"default": {
        "header": [
            "",
            "%animation:CGTitle%",
            "&7Выживание &8• &7Экономика &8• &7Приваты &8• &7Титулы",
            "",
            "&fИгроков онлайн: &a%online% &8• &fСерия наград: &e%clinder_streak% дн.",
            "",
        ],
        "footer": [
            "",
            "&7Пинг: &#a0ff6b%ping% мс &8• &7TPS: &#a0ff6b%tps%",
            "%animation:CGTips%",
            "&8Соцсети и сайт: &7/links",
            "",
        ],
    }}
    d["scoreboard-teams"]["sorting-types"] = [
        "GROUPS:admin,moder,helper,god,imperator,titan,legend,elite,premium,vip,default", "PLACEHOLDER_A_TO_Z:%player%"]
    d["belowname-objective"]["enabled"] = True
    d["belowname-objective"]["value"] = "%health%"
    d["belowname-objective"]["fancy-value"] = "&c%health% ❤"
    d["belowname-objective"]["title"] = "&c❤"
    d["scoreboard"]["enabled"] = True
    d["scoreboard"]["scoreboards"] = {"main": {
        "title": "%animation:CGTitle%",
        "lines": [
            "&8%date% &8• &8%time%",
            "",
            "&#ffb347▶ &fНик: &#ffb347%player%",
            "&#ffb347▶ &fРанг: %luckperms_meta_rank%",
            "&#ffb347▶ &fТитул: %clinder_tag%",
            "",
            "&#a0ff6b$ &fБаланс: &#a0ff6b%vault_eco_balance_formatted%$",
            "&#ffd36b✦ &fСерия наград: &#ffd36b%clinder_streak% дн.",
            "",
            "&#ff6b6b⚔ &fУбийств: &#ff6b6b%clinder_kills%",
            "&#9e9e9e☠ &fСмертей: &#9e9e9e%clinder_deaths%",
            "&#6bcbff⌚ &fВ игре: &#6bcbff%clinder_playtime%",
            "",
            "&#a0ff6b● &fОнлайн: &#a0ff6b%online%",
            "%animation:CGSbFooter%",
        ],
    }}
    d["placeholders"]["locale"] = "ru-RU"
    d["placeholders"]["time-format"] = "HH:mm"
    d["placeholder-output-replacements"] = {"%clinder_tag%": {"": "&7нет &8(/tags)"}}
    d["primary-group-finding-list"] = ["admin", "moder", "helper", "god", "imperator", "titan", "legend", "elite", "premium", "vip", "default"]


edit("plugins/TAB/config.yml", tab)
tab_anim = {
    "CGTitle": {"change-interval": 100, "texts": gradient_frames("✦ ClinderGrief ✦", FIRE)},
    "CGTips": {"change-interval": 4000, "texts": [
        "&7Меню сервера: &#ffb347/menu",
        "&7Ежедневная награда: &#ffd36b/daily",
        "&7Титулы: &#d18cff/tags &8• &7Частицы: &#ff6b6b/pp",
        "&7Привилегии: &#ff8800/donate",
        "&7Охота за головами: &#ff6b6b/bounty",
    ]},
    "CGSbFooter": {"change-interval": 3000, "texts": [
        "&#ff8a00ClinderGrief &8• &7/menu",
        "&#ffd000/daily &7— забери награду!",
        "&#ff5e00/donate &7— привилегии",
    ]},
}
with open(os.path.join(P, "TAB/animations.yml"), "w", encoding="utf-8") as fh:
    fh.write("# Анимации ClinderGrief для TAB (сгенерировано tools/configure.py)\n")
    yaml.safe_dump(tab_anim, fh, allow_unicode=True, sort_keys=False, width=400)
write("plugins/TAB/groups.yml", """
# Префиксы берутся из LuckPerms (команды /lp group <группа> meta setprefix ...)
_DEFAULT_:
  tabprefix: "%luckperms-prefix%"
  tagprefix: "%luckperms-prefix%"
  customtabname: "%player%"
  tabsuffix: "%luckperms-suffix%"
  tagsuffix: "%luckperms-suffix%"
""")
TAB_RU = {
    "bossbar-feature-not-enabled": "&cДля этой команды нужно включить функцию боссбара.",
    "bossbar-announce-command-usage": "Использование: /tab bossbar announce <боссбар> <длительность>",
    "bossbar-not-found": "&cБоссбар с названием \"%name%\" не найден",
    "bossbar-already-announced": "&cЭтот боссбар уже показывается",
    "group-data-removed": "&3[TAB] Все данные группы &e%group% &3удалены",
    "group-value-assigned": "&3[TAB] Параметр %property% '&r%value%&r&3' назначен группе &e%group%",
    "group-value-removed": "&3[TAB] Параметр %property% удалён у группы &e%group%",
    "user-data-removed": "&3[TAB] Все данные игрока &e%player% &3удалены",
    "user-value-assigned": "&3[TAB] Параметр %property% '&r%value%&r&3' назначен игроку &e%player%",
    "user-value-removed": "&3[TAB] Параметр %property% удалён у игрока &e%player%",
    "parse-command-usage": "Использование: /tab parse <игрок> <плейсхолдер>",
    "send-command-usage": "Использование: /tab send <тип> <игрок> <боссбар> <длительность>\nДоступные типы: &lbar",
    "send-bar-command-usage": "Использование: /tab send bar <игрок> <боссбар> <длительность>",
    "team-feature-required": "Для этой команды нужно включить функцию scoreboard-teams",
    "collision-command-usage": "Использование: /tab setcollision <игрок> <true/false>",
    "no-permission": "&cУ вас нет прав на эту команду. Если это ошибка — обратитесь к администрации.",
    "command-only-from-game": "&cЭту команду можно выполнить только в игре",
    "player-not-online": "&cИгрок \"%player%\" не в сети",
    "invalid-number": "\"%input%\" — это не число!",
    "scoreboard-feature-not-enabled": "&4Для этой команды нужно включить функцию скорборда.",
    "scoreboard-announce-command-usage": "Использование: /tab scoreboard announce <скорборд> <длительность>",
    "scoreboard-not-found": "&cСкорборд с названием \"%name%\" не найден",
    "reload-success": "&3[TAB] Конфигурация перезагружена",
    "reload-fail-file": "&3[TAB] &4Ошибка перезагрузки: в файле %file% ошибка синтаксиса. Подробности в консоли.",
    "scoreboard-toggle-on": "&2Скорборд включён",
    "scoreboard-toggle-off": "&7Скорборд выключен",
    "bossbar-toggle-on": "&2Боссбар теперь виден",
    "bossbar-toggle-off": "&7Боссбар скрыт",
    "scoreboard-show-usage": "Использование: /tab scoreboard show <скорборд> [игрок]",
    "bossbar-not-marked-as-announcement": "&cЭтот боссбар не помечен как объявление и уже показывается постоянно (если выполнено условие показа)",
    "bossbar-announcement-success": "&aБоссбар &6%bossbar% &aпоказывается всем на %length% сек.",
    "bossbar-send-success": "&aБоссбар &6%bossbar% &aотправлен игроку &6%player% &aна %length% сек.",
    "help-menu": [
        "&m                                                                                ",
        " &8>> &3&l/tab reload",
        "      - &7Перезагрузить плагин и конфигурацию",
        " &8>> &3&l/tab &9group&3/&9player &3<имя> &9<параметр> &3<значение...>",
        "      - &7Введите &8/tab group/player&7, чтобы увидеть список параметров",
        " &8>> &3&l/tab parse <игрок> <плейсхолдер> ",
        "      - &7Проверить работу плейсхолдера",
        " &8>> &3&l/tab dump <игрок>",
        "      - &7Сводка информации об игроке и сервере",
        " &8>> &3&l/tab cpu",
        "      - &7Нагрузка плагина на процессор",
        " &8>> &3&l/tab group/player <имя> remove",
        "      - &7Удалить все данные игрока/группы",
        "&m                                                                                ",
    ],
    "mysql-help-menu": ["&6/tab mysql upload - выгрузить данные из файлов в MySQL",
                        "&6/tab mysql download - загрузить данные из MySQL в файлы"],
    "mysql-fail-not-enabled": "&cНельзя выгрузить/загрузить данные MySQL: MySQL отключён.",
    "mysql-fail-error": "Ошибка выгрузки/загрузки MySQL. Подробности в консоли.",
    "mysql-download-success": "&aДанные MySQL успешно загружены.",
    "mysql-upload-success": "&aДанные успешно выгружены в MySQL.",
    "scoreboard-help-menu": ["/tab scoreboard [on/off/toggle] [игрок] [опции]",
                             "/tab scoreboard show <скорборд> [игрок]",
                             "/tab scoreboard announce <скорборд> <длительность>"],
    "bossbar-help-menu": ["/tab bossbar [on/off/toggle] [игрок] [опции]",
                          "/tab bossbar send <боссбар> [игрок]",
                          "/tab bossbar announce <боссбар> <длительность>"],
}
TAB_NAMETAG_RU = {
    "help-menu": ["/tab nametag <show/opaque/hide/toggle> [игрок] [зритель] [-s] - Переключить ник над головой игрока",
                  "/tab nametag <showview/opaqueview/hideview/toggleview> [зритель] [-s] - Переключить видимость ников для зрителя"],
    "feature-not-enabled": "&cДля этой команды нужно включить функцию ников над головой.",
    "view-hidden": "&aНики всех игроков скрыты для вас",
    "view-shown": "&aНики всех игроков снова видны вам",
    "player-hidden": "&aВаш ник скрыт",
    "player-shown": "&aВаш ник снова виден",
    "opaque-player-enabled": "&aВаш ник больше не виден сквозь стены",
    "opaque-view-enabled": "&aВы больше не видите ники игроков за стенами",
    "opaque-player-disabled": "&aВаш ник снова виден сквозь стены",
    "opaque-view-disabled": "&aВы снова видите ники игроков за стенами",
    "no-arg-from-console": "&cПри запуске из консоли укажите игрока",
}


def tab_msgs(d):
    for k, v in TAB_RU.items():
        if k in d:
            d[k] = v
    if "nametag" in d:
        for k, v in TAB_NAMETAG_RU.items():
            if k in d["nametag"]:
                d["nametag"][k] = v


edit("plugins/TAB/messages.yml", tab_msgs)

# ---------------------------------------------------------------- bukkit commands.yml: короткие алиасы
def cmds(d):
    d["aliases"] = {"rtp": ["tpr $1-"], "wild": ["tpr $1-"], "skupshik": ["buyer"], "magazin": ["shop"]}


edit("commands.yml", cmds)
print("configured", S)
