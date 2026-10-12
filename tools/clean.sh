#!/usr/bin/env bash
# Приводит папку сервера к состоянию «для раздачи»: удаляет логи, кэши, данные тестовых игроков,
# лишние сгенерированные чанки (оставляет только остров спавна: регионы r.-1.-1, r.-1.0, r.0.-1, r.0.0).
set -e
S="${1:?папка сервера}"
cd "$S"
rm -rf logs cache libraries versions crash-reports plugins/.paper-remapped hs_err_pid*.log runtime-download.tmp
for f in usercache.json ops.json whitelist.json banned-players.json banned-ips.json; do echo "[]" > "$f"; done
rm -rf world/playerdata world/stats world/advancements world/session.lock world/level.dat_old
for d in region entities poi; do
  [ -d "world/$d" ] && find "world/$d" -type f ! -name 'r.-1.-1.mca' ! -name 'r.-1.0.mca' ! -name 'r.0.-1.mca' ! -name 'r.0.0.mca' -delete
done
for w in world_nether world_the_end; do
  rm -rf "$w/region" "$w/entities" "$w/poi" "$w/DIM-1/region" "$w/DIM-1/entities" "$w/DIM-1/poi" "$w/DIM1/region" "$w/DIM1/entities" "$w/DIM1/poi" "$w/session.lock" "$w/level.dat_old" "$w/playerdata" "$w/stats" "$w/advancements"
done
rm -rf plugins/Essentials/userdata plugins/Essentials/userdata-npc-backup plugins/Essentials/usermap.bin plugins/Essentials/uuids.bin
mkdir -p plugins/Essentials/userdata
rm -rf plugins/GriefPreventionData/PlayerData/* plugins/GriefPreventionData/ClaimData/* plugins/GriefPreventionData/Logs/*
rm -f plugins/QuickShop-Hikari/shops.mv.db plugins/QuickShop-Hikari/shops.trace.db plugins/QuickShop-Hikari/qs.log
rm -rf plugins/QuickShop-Hikari/cache
rm -f plugins/CoreProtect/database.db
: > plugins/TAB/playerdata.yml; : > plugins/TAB/users.yml
rm -rf plugins/FancyNpcs/logs plugins/WorldEdit/sessions/* plugins/spark/tmp
sed -i 's/^online-mode=.*/online-mode=true/' server.properties
sed -i '/^management-server-secret=/d' server.properties
printf '# Чтобы запустить сервер, владелец должен принять EULA Minecraft: https://aka.ms/MinecraftEULA\n# start.bat спросит об этом при первом запуске. Вручную: замените false на true.\neula=false\n' > eula.txt
echo "cleaned $S"
