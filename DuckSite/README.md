# Сайт Duck Launcher

`index.html` — готовый сайт одним файлом (картинки внутри), можно сразу выкладывать.

## Как выложить бесплатно

**GitHub Pages:** Settings → Pages → Source: «Deploy from a branch» → ветка и папка `/DuckSite` недоступна, поэтому
скопируй `index.html` в корень отдельной ветки `gh-pages` (или в папку `docs/` основной ветки) и выбери её.
Сайт будет на `https://<ник>.github.io/<репозиторий>/`.

**Netlify / Cloudflare Pages:** перетащи папку `DuckSite` на app.netlify.com/drop — получишь ссылку за минуту.

## Как поменять

- Текст и вёрстка — в `src.html`, картинки — `*.jpg` рядом.
- Пересобрать `index.html` с картинками внутри: `python3 build.py`.
- Ссылка «Скачать» — переменная `dl` в `build.py`. Лучше выложить exe в **GitHub Releases** и поставить ссылку
  `https://github.com/<ник>/<репо>/releases/latest/download/Duck.Launcher.exe` — тогда она всегда ведёт на последнюю версию.
