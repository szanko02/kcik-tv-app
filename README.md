# KCIKTV (форк `szanko02/kcik-tv-app`)

[![CI](https://github.com/szanko02/kcik-tv-app/actions/workflows/ci.yml/badge.svg)](https://github.com/szanko02/kcik-tv-app/actions/workflows/ci.yml)
[![Build and Release](https://github.com/szanko02/kcik-tv-app/actions/workflows/release.yml/badge.svg)](https://github.com/szanko02/kcik-tv-app/actions/workflows/release.yml)

Мобильный неофициальный Android-клиент для **Kick.com** — форк [`xacnio/kcik-tv-app`](https://github.com/xacnio/kcik-tv-app) с удалённым TV-интерфейсом, доработками чата и плеера по мотивам **ReTwitch**, исправлением чата в VOD и поддержкой обновлений как из форка, так и из upstream.

> **Отказ от ответственности:** неофициальный проект, не связанный с Kick.com или Blerp. Все торговые марки принадлежат их владельцам.

## Основные изменения форка

- **Только мобильный интерфейс:** удалены Leanback / Android TV активности, ресурсы и переключение режимов (`tv/PlayerActivity`, `ChannelSidebarAdapter`, зависимости `androidx.leanback`).
- **Настройки бокового чата и отображения (ReTwitch):**
  - Изменение ширины бокового чата в ландшафте (15%–70%) с перетаскиваемой ручкой на границе, сбросом двойным тапом и адаптивной строкой ввода на узких ширинах.
  - Масштаб сообщений чата (50%–200%), жест масштабирования двумя пальцами (pinch-to-resize) и настройка отступа между сообщениями (0–16 dp).
  - Поворот в полноэкранном режиме по датчику даже при отключённом системном автоповороте.
  - Видео под вырезом камеры (display cutout) в ландшафтном полноэкранном режиме с защитой элементов управления и переключением масштаба `Fit` / `Zoom` / `Stretch` (кнопкой на оверлее, щипком двумя пальцами или в настройках плеера).
- **Исправление VOD и чата:**
  - Переработанный буфер реплея чата VOD (`VodChatReplayBuffer`) со строгим 5-секундным шагом страниц, упреждающим буфером 15 с (до 20 с), защитой от пропусков на пустых страницах, корректной обработкой перемотки и экспоненциальным backoff.
  - Убран лишний пустой отступ снизу в чате VOD и исправлено схлопывание поля ввода при узком боковом чате в лайв-стримах.
  - Исправлено обновление (refresh) при просмотре VOD/клипа: больше не сбрасывается на лайв-стрим (`Stream Offline`), сохраняются позиция и скорость воспроизведения.
- **Двойной источник обновлений:** выбор источника релизов и списка «Что нового» в настройках (`Fork (szanko02)` или `Upstream (xacnio)`).

Подробная история изменений — в [CHANGELOG.md](CHANGELOG.md).  
Каталог всех функций ReTwitch и их статус для порта на Kick — в [RETWITCH_FEATURES.md](RETWITCH_FEATURES.md).

## Стек и структура

- **Язык:** Kotlin, MVVM + менеджеры UI, Coroutines/Flow.
- **Плеер:** Amazon IVS Player SDK (с поддержкой RNNoise через JNI/Pine), фоновое аудио и PiP.
- **Сеть и чат:** Retrofit 2, OkHttp 3, Gson, WebSocket, прокси (HTTP/SOCKS5).

## Сборка и тестирование

1. Клонируйте репозиторий:
   ```bash
   git clone https://github.com/szanko02/kcik-tv-app.git
   cd kcik-tv-app
   ```
2. Подготовьте `app/google-services.json` (для локальной сборки/CI подойдёт копия `app/google-services.json.example`):
   ```bash
   cp app/google-services.json.example app/google-services.json
   ```
3. Проверьте строгую синхронизацию переводов (`values*/strings.xml`):
   ```bash
   python3 scripts/verify_translations_strict.py
   ```
4. Запустите unit-тесты и соберите debug APK:
   ```bash
   # Linux / macOS
   ./gradlew testDebugUnitTest assembleDebug

   # Windows
   .\gradlew.bat testDebugUnitTest assembleDebug
   ```

## Выпуск релизов

Workflow [`.github/workflows/release.yml`](.github/workflows/release.yml) запускается при пуше тега вида `v*` или вручную через `workflow_dispatch`:

```bash
git tag v2.4.5-fork.1
git push --tags
```

При наличии секретов подписи (`SIGNING_KEY`, `ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD`) публикуется подписанный `KCIKTV-${VERSION}.apk`, иначе — `KCIKTV-${VERSION}-debug.apk` для прямой установки и работы встроенного автообновления форка.

## Лицензия и благодарности

- Лицензия: **MIT** ([LICENSE](LICENSE)).
- Оригинальный клиент KCIKTV: **[xacnio](https://github.com/xacnio/kcik-tv-app)**.
- Вдохновение и список доработок плеера/чата: **[ReTwitch](https://github.com/reyohoho/ReTwitch-src)**.
