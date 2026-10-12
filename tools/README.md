# Инструменты сборки ClinderGrief

Скрипты, которыми собрана папка `ClinderGrief-1.21.11/` (для повторной сборки/правок; владельцу сервера не нужны).

| Файл | Назначение |
|---|---|
| `configure.py` | применяет все настройки к папке сервера после первого запуска (Essentials, GP, QuickShop, TAB, DeluxeMenus, переводы, server.properties) |
| `economy.py` | единая таблица цен магазина и скупщика (с проверкой, что перепродажа невыгодна) |
| `menus.py` | генерация меню DeluxeMenus из `economy.py` |
| `spawn.py` | генерация консольных команд постройки острова спавна |
| `gp_messages_ru.py` | русский перевод сообщений GriefPrevention |
| `lang/coreprotect_ru.yml` | русский перевод CoreProtect |
| `lang/limboauth_ru.py` | русский перевод LimboAuth (регистрация на Velocity) |
| `luckperms_setup.txt` | команды создания групп и прав LuckPerms |
| `clean.sh` | очистка папки сервера перед упаковкой (логи, кэши, тестовые игроки, лишние чанки, eula=false) |

Собственные плагины: `clindercore/` (ClinderCore — кейсы, задания, аукцион, кланы и др.) и `clinderac/` (античит ClinderAC); сборка — `build.sh <папка с libraries/ сервера, Vault и PlaceholderAPI>`.

Требуется Python 3 с `pyyaml` и `ruamel.yaml`: `python3 tools/configure.py ClinderGrief-1.21.11`.
