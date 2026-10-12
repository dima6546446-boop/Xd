# Генерация меню DeluxeMenus (главное меню, магазин, скупщик, привилегии, помощь).
import os
import yaml
from economy import SHOP, BUYER

INNER = [r * 9 + c for r in range(1, 5) for c in range(1, 8)]          # 28 слотов внутри рамки
BORDER = [s for s in range(54) if s not in INNER and s < 45]
BOTTOM = list(range(45, 54))
CUR = "$"


def fmt(v):
    return f"{v:g}"


def filler():
    return {"material": "GRAY_STAINED_GLASS_PANE", "slots": BORDER + BOTTOM, "display_name": "&r", "priority": 99}


def balance_item(slot=45):
    return {"material": "SUNFLOWER", "slot": slot, "update": True,
            "display_name": "&eВаш баланс",
            "lore": ["&f%vault_eco_balance_formatted%" + CUR]}


def back_item(target, slot=49):
    return {"material": "ARROW", "slot": slot, "display_name": "&cНазад",
            "left_click_commands": [f"[openguimenu] {target}"],
            "right_click_commands": [f"[openguimenu] {target}"]}


def close_item(slot=53):
    cmds = ["[close]"]
    return {"material": "BARRIER", "slot": slot, "display_name": "&cЗакрыть",
            "left_click_commands": cmds, "right_click_commands": cmds}


def shop_category(key):
    title, _, items = SHOP[key]
    menu = {"menu_title": f"&8Магазин » {title}", "size": 54, "update_interval": 1, "items": {}}
    menu["items"]["filler"] = filler()
    for slot, (mat, amount, price, name) in zip(INNER, items):
        buy = [
            f"[takemoney] {price}",
            f"[console] minecraft:give %player_name% minecraft:{mat.lower()} {amount}",
            f"[message] &aВы купили &f{name} x{amount} &aза &e{fmt(price)}{CUR}",
            "[sound] ENTITY_EXPERIENCE_ORB_PICKUP",
        ]
        menu["items"][mat.lower()] = {
            "material": mat, "amount": amount, "slot": slot,
            "display_name": f"&f{name} &7x{amount}",
            "lore": ["", f"&7Цена: &e{fmt(price)}{CUR}", "", "&aНажмите, чтобы купить"],
            "left_click_requirement": buy_requirement(price),
            "left_click_commands": buy,
            "right_click_requirement": buy_requirement(price),
            "right_click_commands": buy,
        }
    menu["items"]["balance"] = balance_item()
    menu["items"]["back"] = back_item("shop")
    menu["items"]["close"] = close_item()
    return menu


def buy_requirement(price):
    return {"requirements": {
        "money": {"type": "has money", "amount": price,
                  "deny_commands": ["[message] &cНедостаточно денег! Нужно &e" + fmt(price) + CUR,
                                    "[sound] ENTITY_VILLAGER_NO"]},
        "space": {"type": ">=", "input": "%player_empty_slots%", "output": 1,
                  "deny_commands": ["[message] &cОсвободите хотя бы один слот в инвентаре!",
                                    "[sound] ENTITY_VILLAGER_NO"]},
    }}


def buyer_category(key):
    title, _, items = BUYER[key]
    menu = {"menu_title": f"&8Скупщик » {title}", "size": 54, "update_interval": 1, "items": {}}
    menu["items"]["filler"] = filler()
    for slot, (mat, price, name) in zip(INNER, items):
        m = mat.lower()
        menu["items"][m] = {
            "material": mat, "slot": slot,
            "display_name": f"&f{name}",
            "lore": ["", f"&7Цена за 1 шт.: &e{fmt(price)}{CUR}", f"&7Цена за 64 шт.: &e{fmt(round(price * 64, 2))}{CUR}", "",
                     "&aЛКМ &7— продать 1 стак (64 шт.)", "&aПКМ &7— продать все из инвентаря"],
            "left_click_commands": [f"[player] sell {m} 64", "[refresh]"],
            "right_click_commands": [f"[player] sell {m}", "[refresh]"],
        }
    menu["items"]["balance"] = balance_item()
    menu["items"]["back"] = back_item("buyer")
    menu["items"]["close"] = close_item()
    return menu


def hub(name, title, cats, prefix, extra=None, cmds=None):
    menu = {"menu_title": title, "open_command": cmds, "register_command": True, "size": 27, "update_interval": 1, "items": {}}
    menu["items"]["filler"] = {"material": "GRAY_STAINED_GLASS_PANE", "slots": list(range(27)), "display_name": "&r", "priority": 99}
    for slot, (key, (ctitle, icon, items)) in zip([10, 12, 14, 16], cats.items()):
        menu["items"][key] = {"material": icon, "slot": slot, "display_name": ctitle,
                              "lore": ["", f"&7Товаров: &f{len(items)}", "", "&aНажмите, чтобы открыть"],
                              "left_click_commands": [f"[openguimenu] {prefix}_{key}"],
                              "right_click_commands": [f"[openguimenu] {prefix}_{key}"]}
    menu["items"]["balance"] = balance_item(18)
    menu["items"]["back"] = back_item("main", 22)
    menu["items"]["close"] = close_item(26)
    if extra:
        menu["items"].update(extra)
    return menu


def main_menu():
    def btn(mat, slot, name, lore, cmd):
        return {"material": mat, "slot": slot, "display_name": name, "lore": [""] + lore + ["", "&aНажмите"],
                "left_click_commands": cmd, "right_click_commands": cmd}
    items = {
        "filler": {"material": "BLACK_STAINED_GLASS_PANE", "slots": list(range(45)), "display_name": "&r", "priority": 99},
        "head": {"material": "head-%player_name%", "slot": 4, "update": True,
                 "display_name": "&e%player_name%",
                 "lore": ["", "&7Привилегия: &f%luckperms_prefix%", "&7Баланс: &e%vault_eco_balance_formatted%" + CUR,
                          "&7Пинг: &f%player_ping% мс"]},
        "shop": btn("EMERALD", 19, "&a&lМагазин", ["&7Купить блоки, еду и ресурсы", "&7за игровую валюту"], ["[openguimenu] shop"]),
        "buyer": btn("GOLD_INGOT", 20, "&6&lСкупщик", ["&7Продать руду, урожай", "&7и добычу с мобов"], ["[openguimenu] buyer"]),
        "rtp": btn("ENDER_PEARL", 21, "&d&lСлучайная телепортация", ["&7Телепорт в случайное место", "&7в мире выживания"], ["[close]", "[player] tpr"]),
        "spawn": btn("BEACON", 22, "&b&lНа спавн", ["&7Вернуться на спавн"], ["[close]", "[player] spawn"]),
        "home": btn("RED_BED", 23, "&c&lДом", ["&7Телепорт домой", "&7Установить дом: &f/sethome"], ["[close]", "[player] home"]),
        "kits": btn("CHEST", 24, "&e&lНаборы", ["&7Бесплатные наборы", "&7(стартовый и ежедневный)"], ["[close]", "[player] kit"]),
        "donate": btn("NETHER_STAR", 25, "&6&lПривилегии", ["&7Косметические привилегии", "&7и поддержка сервера"], ["[openguimenu] donate"]),
        "claims": btn("GOLDEN_SHOVEL", 29, "&2&lПриваты", ["&7Как защитить свою постройку"], ["[openguimenu] help_claims"]),
        "shops": btn("CHEST_MINECART", 30, "&3&lСвой магазин", ["&7Как открыть магазин", "&7из сундука"], ["[openguimenu] help_shops"]),
        "rules": btn("BOOK", 31, "&f&lПравила", ["&7Правила сервера"], ["[close]", "[player] rules"]),
        "baltop": btn("GOLD_BLOCK", 32, "&e&lТоп богачей", ["&7Самые богатые игроки"], ["[close]", "[player] baltop"]),
        "help": btn("OAK_SIGN", 33, "&7&lКоманды", ["&7Список полезных команд"], ["[openguimenu] help_commands"]),
        "close": close_item(40),
    }
    return {"menu_title": "&8Меню сервера", "open_command": ["menu", "m", "gui"], "register_command": True,
            "size": 45, "update_interval": 2, "items": items}


DONATE_RANKS = [
    ("vip", "LIME_DYE", "&a&lVIP", ["&7Префикс &a[VIP] &7в чате и TAB", "&7Цветной чат (&f&&a-коды&7)", "&7Смена ника: &f/nick", "&7Шляпа из блока: &f/hat"]),
    ("premium", "LIGHT_BLUE_DYE", "&b&lPremium", ["&7Всё из &aVIP", "&7Префикс &b[Premium]", "&7Жирный/курсив в чате", "&7Цветной ник", "&7Личное время и погода: &f/ptime, /pweather"]),
    ("elite", "ORANGE_DYE", "&6&lЭлита", ["&7Всё из &bPremium", "&7Префикс &6[Элита]", "&7HEX-цвета в чате и нике", "&7Цветные таблички"]),
]


def donate_menu():
    items = {"filler": {"material": "GRAY_STAINED_GLASS_PANE", "slots": list(range(27)), "display_name": "&r", "priority": 99}}
    for slot, (key, mat, name, perks) in zip([11, 13, 15], DONATE_RANKS):
        cmds = ["[close]",
                "[message] &6&lПривилегии &8» &7Купить привилегию можно в магазине сервера:",
                "[message] &b&nУКАЖИТЕ-ССЫЛКУ-НА-ВАШ-МАГАЗИН",
                "[message] &7Все привилегии только косметические и не дают игрового преимущества."]
        items[key] = {"material": mat, "slot": slot, "display_name": name,
                      "lore": [""] + perks + ["", "&8Без игровых преимуществ —", "&8по правилам Minecraft EULA.", "", "&eНажмите, чтобы узнать, как купить"],
                      "left_click_commands": cmds, "right_click_commands": cmds}
    items["back"] = back_item("main", 22)
    return {"menu_title": "&8Привилегии", "open_command": ["donate", "privileges"], "register_command": True,
            "size": 27, "items": items}


def text_menu(title, mat, lines, size=27):
    items = {"filler": {"material": "GRAY_STAINED_GLASS_PANE", "slots": list(range(size)), "display_name": "&r", "priority": 99},
             "info": {"material": mat, "slot": 13, "display_name": title, "lore": [""] + lines}}
    items["back"] = back_item("main", 22)
    return {"menu_title": "&8" + title.replace("&l", ""), "size": size, "items": items}


HELP = {
    "help_claims": ("&2&lКак заприватить территорию", "GOLDEN_SHOVEL", [
        "&f1. &7Возьмите &eзолотую лопату&7.",
        "&f2. &7ПКМ по одному углу участка,",
        "&7    затем ПКМ по противоположному.",
        "&f3. &7Готово! Границы покажет &eпалка &7(ПКМ).",
        "",
        "&7Первый сундук автоматически создаёт",
        "&7небольшой приват вокруг себя.",
        "",
        "&e/trust <ник> &7— дать другу строить",
        "&e/untrust <ник> &7— забрать доступ",
        "&e/claimslist &7— список приватов и блоков",
        "&e/abandonclaim &7— удалить приват",
        "&e/buyclaimblocks <кол-во> &7— купить блоки",
        "",
        "&7Блоки привата копятся за время игры.",
    ]),
    "help_shops": ("&3&lКак открыть свой магазин", "CHEST", [
        "&f1. &7Поставьте сундук (в своём привате).",
        "&f2. &7Возьмите товар в руку и ударьте",
        "&7    сундук (ЛКМ).",
        "&f3. &7Введите в чат цену за 1 шт.",
        "&f4. &7Положите товар в сундук.",
        "",
        "&e/qs buy &7— магазин будет скупать товар",
        "&e/qs sell &7— магазин будет продавать",
        "&e/qs price <цена> &7— сменить цену",
        "&e/qs remove &7— удалить магазин",
        "",
        "&7Покупателю достаточно нажать на сундук",
        "&7и ввести количество в чат.",
    ]),
    "help_commands": ("&7&lПолезные команды", "OAK_SIGN", [
        "&e/menu &7— меню сервера",
        "&e/spawn &7— на спавн",
        "&e/tpr &7(или &e/rtp&7) — случайная телепортация",
        "&e/sethome, /home &7— дом (до 3 точек)",
        "&e/tpa <ник> &7— запрос телепорта к игроку",
        "&e/tpaccept, /tpdeny &7— принять/отклонить",
        "&e/back &7— вернуться на прошлую точку",
        "&e/balance, /pay <ник> <сумма> &7— деньги",
        "&e/sell hand &7— продать предмет в руке",
        "&e/worth &7— узнать цену предмета",
        "&e/kit &7— наборы",
        "&e/msg <ник> <текст>, /r &7— личные сообщения",
        "&e/mail &7— почта, &e/ignore &7— игнор",
        "&e/rules &7— правила, &e/helpop &7— вызвать модератора",
    ]),
}


def write_all(dm_dir):
    gm = os.path.join(dm_dir, "gui_menus")
    os.makedirs(gm, exist_ok=True)
    for f in os.listdir(gm):
        os.remove(os.path.join(gm, f))
    menus = {"main": main_menu(),
             "shop": hub("shop", "&8Магазин сервера", SHOP, "shop", cmds=["shop"]),
             "buyer": hub("buyer", "&8Скупщик", BUYER, "buyer", cmds=["buyer", "skupka"],
                          extra={"sellall": {"material": "HOPPER", "slot": 4, "display_name": "&c&lПродать всё",
                                             "lore": ["", "&7Продаёт &fвсе предметы из инвентаря&7,", "&7которые принимает скупщик", "&7(руды, урожай, древесина, добыча).", "", "&cВнимание: &7продаются и алмазы!", "", "&eShift + ЛКМ, чтобы продать всё"],
                                             "shift_left_click_commands": ["[player] sell inventory", "[refresh]"]}}),
             "donate": donate_menu()}
    for k in SHOP:
        menus[f"shop_{k}"] = shop_category(k)
    for k in BUYER:
        menus[f"buyer_{k}"] = buyer_category(k)
    for k, (title, mat, lines) in HELP.items():
        menus[k] = text_menu(title, mat, lines)
    for name, m in menus.items():
        if m.get("open_command") is None:
            m.pop("open_command", None)
            m.pop("register_command", None)
        with open(os.path.join(gm, name + ".yml"), "w", encoding="utf-8") as fh:
            fh.write("# Меню DeluxeMenus. Сгенерировано tools/menus.py — цены берутся из tools/economy.py\n")
            yaml.safe_dump(m, fh, allow_unicode=True, sort_keys=False, width=200)
    return list(menus)
