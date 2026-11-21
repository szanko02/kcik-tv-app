# Чеклист функций ReTwitch для переноса в KCIKTV (Kick Android)

> **Источник:** [`reyohoho/ReTwitch-src`](https://github.com/reyohoho/ReTwitch-src) (`Toggles.java` — 195 переключателей + `SettingsHook.java`, `RecordSettings.java`, `AboutPage.java`, `QuickSetup.java`, `SettingsScreen.java` — слайдеры, пикеры и отдельные подсистемы).
>
> **Как пользоваться:** отмечайте нужные пункты галочкой `- [ ]` → `- [x]`, чтобы спланировать следующие задачи для реализации в форке `szanko02/kcik-tv-app`.

## Обозначения статусов

- `[Сделано]` (`- [x]`) — уже реализовано в нашем форке (`kcik-tv-app`).
- `[Уже есть в KCIKTV]` (`- [ ]`) — нативный KCIKTV уже имеет эквивалент (указаны отличия от ReTwitch; галочку можно поставить, если нужна доработка под поведение ReTwitch).
- `[Kick OK]` (`- [ ]`) — реализуемо и полезно перенести в Kick без критических ограничений API.
- `[Сложно / адаптация под Kick]` (`- [ ]`) — реализуемо, но требует серьёзной доработки, фоновых сервисов или адаптации под API/плеер Kick.
- `[Только Twitch]` (`- [ ]`) — специфично исключительно для архитектуры, рекламы, монетизации или внутренних SDK/RN-хуков Twitch (вынесено в отдельный раздел внизу, чтобы ничего из ReTwitch не потерялось).

---

## Сводка по статусам

| Статус | Количество |
| --- | :---: |
| `[Сделано]` | 7 |
| `[Уже есть в KCIKTV]` | 37 |
| `[Kick OK]` | 72 |
| `[Сложно / адаптация под Kick]` | 15 |
| `[Только Twitch]` | 19 |
| **Всего каталогизировано пунктов** | **150** |

*(Примечание: все 195 тумблеров из `Toggles.java` и все не-тумблерные настройки и подсистемы из `SettingsHook.java`, `RecordSettings.java`, `AboutPage.java`, `QuickSetup.java` и `SettingsScreen.java` сгруппированы в 150 логических пунктов; в скобках приведены точные имена констант из ReTwitch.)*

---

## 1. Плеер и видео

### Уже реализовано в форке
- [x] `[Сделано]` **Поворот в полном экране по датчику (без системного автоповорота)** — настройка `fullscreenSensorRotation` в `SettingsPanelManager.kt` / `FullscreenToggleManager.kt` (в ReTwitch вне плеера управляется `HomeOrientation`).
- [x] `[Сделано]` **Буферизированный повтор чата VOD на 15 секунд вперёд** — `VodChatReplayBuffer.kt` (устраняет лишний поллинг и скачки сообщений в VOD).
- [x] `[Сделано]` **Исправление перезапуска/обновления VOD** — `VodManager.refreshCurrentPlayback()` (сохраняет позицию и состояние при обновлении потока).

### Уже есть в KCIKTV (с отличиями)
- [ ] `[Уже есть в KCIKTV]` **Качество видео при запуске отдельно для Wi-Fi и мобильной сети** (`defaultQualityWifi`, `defaultQualityCellular`: Авто / Максимум / 1080p..160p) — в KCIKTV (`AppPreferences.kt`, `SettingsPanelManager.kt`) есть `mobileQualityLimit`, `qualityLimit` и `dynamicQualityEnabled`, но нет выбора отдельного стартового качества для Wi-Fi и мобильной сети.
- [ ] `[Уже есть в KCIKTV]` **Переключатель «Обычная задержка»** (`NORMAL_LATENCY`: стабильнее на слабой сети, отключает low-latency) — KCIKTV включает `setLiveLowLatencyEnabled(true)` в `PlayerManager.kt` и предлагает `catchUpMode`, но отдельного тумблера отключения низкой задержки нет.
- [ ] `[Уже есть в KCIKTV]` **Свайп для входа/выхода из полного экрана** (`PLAYER_SWIPE_FULLSCREEN`: вверх — развернуть, вниз — выйти) — в `PlayerGestureManager.kt` уже реализовано (в портрете свайп вверх включает fullscreen, в ландшафте свайп вниз выходит; в ReTwitch жест ограничен центральной зоной видео).
- [ ] `[Уже есть в KCIKTV]` **Фильтры звука (Android 9+): усиление, басы, верхние частоты, разборчивость голоса, лимитер** (`AUDIO_FILTERS`, `AudioCompressor` boost/bass/treble/voice/limit) — в KCIKTV (`CustomEqDialogManager.kt`, `PlayerManager.kt`, `AudioNrTap.kt`) уже есть 6-полосный EQ (`DynamicsProcessing`), встроенный `Limiter` и RNNoise-шумоподавление с пресетом «Голос», но в ReTwitch интерфейс упрощён до 5 тематических ползунков (включая порог лимитера громких звуков).
- [ ] `[Уже есть в KCIKTV]` **Перезагрузка плеера вручную** (`RefreshSettings`: кнопка на плеере и действие в меню) — в KCIKTV есть «Обновить» (`refreshCurrentPlayback`) внутри шторки параметров видео (`VideoSettingsDialogManager.kt`), но нет отдельной кнопки прямо на оверлее плеера.
- [ ] `[Уже есть в KCIKTV]` **Статистика плеера** (`STATS_BUTTON`: качество, битрейт, fps, задержка, буфер, сеть, дропы кадров) — в KCIKTV есть `PlayerStatsSheetManager.kt` (разрешение, fps, битрейт, задержка, буфер, потерянные кадры, скорость, статус, громкость), открывается через меню параметров, но нет отдельной кнопки на оверлее и строки типа сети.
- [ ] `[Уже есть в KCIKTV]` **Перемотка в плеере VOD и уведомлении** (`SeekBack`/`SeekForward`, `NOTIFICATION_SEEK`, `NotificationSeekBack`/`Forward`) — в `PlaybackControlManager.kt` есть перемотка ±10 с и `MediaSessionHelper`, но шаг жёстко фиксирован (10 с), в отличие от произвольного ввода секунд в ReTwitch.
- [ ] `[Уже есть в KCIKTV]` **Экран гаснет на паузе и по окончании эфира** (`SCREEN_OFF_STALLED` / `SCREEN_OFF_PAUSED`) — в `PlayerManager.kt` флаг `FLAG_KEEP_SCREEN_ON` включается только при `PLAYING` и снимается при паузе/остановке/ошибке; отсутствует только автоснятие флага при длительном зависании буфера (>30 с).
- [ ] `[Уже есть в KCIKTV]` **Автозапуск и экран «Далее» в конце VOD** (`NO_AUTOPLAY`, `SKIP_UP_NEXT`) — в `VodManager.kt` на Kick нет автозапуска следующего VOD и карточки «Далее» (есть только автопереход в ленте клипов `ClipFeedManager.kt`).
- [ ] `[Уже есть в KCIKTV]` **Звук в режиме «Только звук»** (`AUDIO_ONLY_UNMUTE` — автоснятие беззвучного режима при переходе в Audio-Only) — сам режим Audio-Only уже есть в `VideoSettingsDialogManager.kt` и `PipManager.kt`, можно добавить только авто-анмьют при переключении.
- [ ] `[Уже есть в KCIKTV]` **Только видео в окне PiP (без лишних плашек)** (`PIP_CLEAN_VIDEO`) — в `PipManager.kt` в PiP остаётся чистое видео с 3 системными действиями (Mute / Play-Pause / Audio-Only).
- [ ] `[Уже есть в KCIKTV]` **Всегда свежий поток без кэша манифеста** (`NO_MANIFEST_CACHE`) — KCIKTV каждый раз запрашивает свежий `playback_url` канала при открытии.

### Реализуемо для Kick
- [ ] `[Kick OK]` **Жесты яркости (слева) и громкости (справа) в полном экране** (`PLAYER_GESTURES`) — вертикальный свайп по боковым зонам (35% ширины) с оверлей-бейджем.
- [ ] `[Kick OK]` **Жесты яркости и громкости в вертикальном полноэкранном режиме** (`PLAYER_GESTURES_VERTICAL`).
- [ ] `[Kick OK]` **Автоперезапуск потока при длительном зависании + выбор таймаута** (`AUTO_RELOAD`, `stall_seconds`) — сейчас KCIKTV перезапускает только на `onError`, но не при вечной буферизации.
- [ ] `[Kick OK]` **Гасить экран при зависании загрузки дольше 30 секунд** (`SCREEN_OFF_STALLED`).
- [ ] `[Kick OK]` **Видео под вырезом камеры (display cutout)** (`PlayerSettings.drawUnderCutout`) — разрешать растягивание видео в область чёлки/камеры в ландшафте.
- [ ] `[Kick OK]` **Режим Ambilight + кнопка в плеере** (`AMBILIGHT`) — мягкая подсветка чёрных полос вокруг видео его текущими цветами.
- [ ] `[Kick OK]` **Отражение видео по горизонтали (зеркало)** (`MIRROR_BUTTON`) — кнопка зеркалирования картинки.
- [ ] `[Kick OK]` **Таймер сна** (`TimerSettings`, `TimerController`: кнопка на плеере/в настройках, выбор часов/минут, режим «Пауза вместо закрытия приложения»).
- [ ] `[Kick OK]` **Кнопка аудиокомпрессора в плеере (Android 9+)** (`COMPRESSOR_BUTTON`, `AudioCompressor`) — выравнивание тихой речи и громких криков/алертов в одно нажатие.
- [ ] `[Kick OK]` **Индикатор задержки (latency) прямо на оверлее плеера** (`LATENCY_LABEL`).
- [ ] `[Kick OK]` **Процент заряда батареи на оверлее в полном экране** (`BATTERY_LABEL`).
- [ ] `[Kick OK]` **Число активных зрителей в чате рядом со счётчиком зрителей** (`SHOW_CHATTERS_COUNT`) — в KCIKTV уже есть `ActiveChattersStore`, можно выводить в скобках.
- [ ] `[Kick OK]` **Отображение задержки потока в подсказке поля ввода чата** (`LATENCY_HINT`).
- [ ] `[Kick OK]` **Настройка произвольного шага перемотки в VOD и уведомлении (в секундах)** (`seek_back_seconds`, `seek_forward_seconds`, `notification_seek_back_seconds`, `notification_seek_forward_seconds`).
- [ ] `[Kick OK]` **Отключение мини-плеера («Свернуть» = закрыть плеер)** (`NO_MINI_PLAYER`).
- [ ] `[Kick OK]` **Закрытие мини-плеера смахиванием за край экрана** (`MINI_PLAYER_SWIPE_CLOSE`) — в `MiniPlayerManager.kt` сейчас есть только перетаскивание и кнопка `✕`.
- [ ] `[Kick OK]` **Кнопки перемотки назад/вперёд в окне PiP для VOD** (`PIP_SEEK`).
- [ ] `[Kick OK]` **Отдельная кнопка «Перезагрузить» прямо на оверлее плеера** (`RefreshSettings.button`).
- [ ] `[Kick OK]` **Точные дата и время в списках VOD вместо относительных («1 нед. назад»)** (`VOD_ABSOLUTE_DATES`).
- [ ] `[Kick OK]` **Фильтр VOD по категории/игре на странице канала** (`VOD_CATEGORY_FILTER`).

### Сложно / требует адаптации под Kick
- [ ] `[Сложно / адаптация под Kick]` **Запись прямых трансляций на устройство** (`LIVE_RECORD`, `LIVE_RECORD_LABEL`, `LIVE_RECORD_ARCHIVE`, `LiveRecord`) — кнопка записи в плеере, индикатор `REC` с таймером на видео, сохранение в `Downloads`, запись с начала эфира из архива канала при наличии VOD.
- [ ] `[Сложно / адаптация под Kick]` **Автозапись выбранных каналов в фоне** (`AutoRecord`: список каналов, индивидуальное качество для каждого канала, опция «Только по Wi-Fi», Foreground-мониторинг).
- [ ] `[Сложно / адаптация под Kick]` **Запись чата вместе со стримом/VOD и встроенный оффлайн-плеер записей** (`LIVE_RECORD_CHAT`, `RecordPlayer`, страница «Записи» в настройках с паузой/докачкой/удалением).
- [ ] `[Сложно / адаптация под Kick]` **Скачивание VOD и клипов (с выбором качества / «Только звук» и настройкой папок в `Download/`)** (`DOWNLOAD_BUTTON`, `VodDownload`, `RecordSettings.folder*` для записей/VOD/клипов).
- [ ] `[Сложно / адаптация под Kick]` **Данмаку (чат летит поверх видео в полном экране)** (`DANMAKU`, `DANMAKU_NICKS`, настройки скорости, занимаемой области видео, размера текста и непрозрачности).
- [ ] `[Сложно / адаптация под Kick]` **Главы (Chapters) VOD по категориям с переходом к таймкоду** (`CHAPTERS_BUTTON`, `VodChapters`) — зависит от наличия сегментов категорий в API Kick VOD.
- [ ] `[Сложно / адаптация под Kick]` **Ручная подстройка смещения синхронизации чата VOD (индивидуально для каждого видео)** (`VOD_CHAT_SYNC`) — поверх уже готового `VodChatReplayBuffer`.

---

## 2. Театральный режим и боковой чат

### Уже реализовано в форке
- [x] `[Сделано]` **Перетаскиваемая граница бокового чата + ручка (handle) + двойной тап для сброса + ползунок ширины** (`TheatreSettings.sideChatDivider`, `sideChatWidthPercent`, сброс ширины) — реализовано в `FullscreenToggleManager.kt`, `SideChatWidthUtils.kt` и `ChatSettingsSheetManager.kt`.

### Уже есть в KCIKTV (с отличиями)
- [ ] `[Уже есть в KCIKTV]` **Чат поверх видео в театральном режиме** (`CHAT_OVER_VIDEO`) — в KCIKTV есть портретный Theatre Mode (`FullscreenToggleManager.enterTheatreMode`), где чат лежит поверх видео, но в ландшафте чат всегда сбоку (нет наложения полупрозрачной колонки поверх широкоэкранного видео).

### Реализуемо для Kick
- [ ] `[Kick OK]` **Чат поверх видео полупрозрачной колонкой в ландшафтном режиме** (`CHAT_OVER_VIDEO` для горизонтального экрана).
- [ ] `[Kick OK]` **Регулятор прозрачности фона чата поверх видео (0–100%)** (`TheatreSettings.chatBgOpacity`) — в KCIKTV сейчас прозрачность жёсткая (`#99000000` при клавиатуре).
- [ ] `[Kick OK]` **Автоматическое расширение бокового чата при открытых опросах/прогнозах** (`WIDE_CHAT_PANELS`).
- [ ] `[Kick OK]` **Кнопки переключения к предыдущему/следующему стриму в плеере (`⏮ ⏭`)** (`STREAM_ARROWS`, `StreamArrowsMode`: листать недавние или отслеживаемые каналы).

---

## 3. Чат: внешний вид и поведение

### Уже реализовано в форке
- [x] `[Сделано]` **Масштаб сообщений чата (50–200%)** (`ChatSettings.fontScale` / `chatMessageScale`) — масштабирует шрифт, эмоуты и бейджи пропорционально (плюс нативный `chatTextSize` 10–30sp в KCIKTV; точный числовой ввод `%` при желании можно добавить отдельно).
- [x] `[Сделано]` **Изменение размера текста чата щипком двумя пальцами** (`CHAT_PINCH_TEXT`) — реализовано в `ChatPinchResizeHelper.kt`.
- [x] `[Сделано]` **Отступы между сообщениями чата (0–16 dp)** (`chatMessageSpacingDp`) — реализовано в `ChatAdapter.kt` и `ChatSettingsSheetManager.kt`.

### Уже есть в KCIKTV (с отличиями)
- [ ] `[Уже есть в KCIKTV]` **Формат времени сообщений** (`TimestampFormat`: выкл, без секунд, с секундами) — в KCIKTV есть `chatShowTimestamps` и `chatShowSeconds`.
- [ ] `[Уже есть в KCIKTV]` **Анимация появления новых сообщений** (`smoothMessages`) — в KCIKTV реализован целый набор анимаций в `ChatMessageAnimator.kt` (`chatMessageAnimation`).
- [ ] `[Уже есть в KCIKTV]` **Не сбрасывать автопрокрутку при чтении выше (Live и VOD)** (`stickyScroll`, `VOD_STICKY_SCROLL`) — в `ChatUiManager.kt` автопрокрутка ставится на паузу при скролле вверх.
- [ ] `[Уже есть в KCIKTV]` **Счётчик новых сообщений при прокрутке вверх** (`NEW_MESSAGES_COUNT`) — в `ChatUiManager.kt` ведётся `newMessageCount` и отображается кнопка возврата вниз.
- [ ] `[Уже есть в KCIKTV]` **Показ удалённых сообщений** (`DeletedMode`) — `ChatEventHandler.kt` / `ChatAdapter.markMessageAsDeleted` оставляют удалённые сообщения в чате зачёркнутыми; в отличие от ReTwitch, нет выбора из 5 стилей и настройки фона.
- [ ] `[Уже есть в KCIKTV]` **История сообщений зрителя в его карточке** (`USER_HISTORY`) — `UserActionsSheetManager.kt` показывает сообщения за сессию, а для модераторов загружает полную историю пользователя с пагинацией через API.
- [ ] `[Уже есть в KCIKTV]` **Ответ свайпом по сообщению** (`SWIPE_REPLY`) — реализовано через `ChatAdapter.SwipeToReplyCallback`.
- [ ] `[Уже есть в KCIKTV]` **Переход к сообщению-оригиналу при нажатии на ответ** (`REPLY_THREADS` частично) — `scrollToRepliedMessage()` в `ChatUiManager.kt` прокручивает к исходному сообщению (в отличие от ReTwitch, не открывается отдельное дерево всей ветки диалога).
- [ ] `[Уже есть в KCIKTV]` **Модерация из меню сообщения** (`MOD_ACTIONS`: удалить, таймаут, бан, разбан, закреп) — реализовано в `MessageActionsSheetManager.kt`, `UserActionsSheetManager.kt` и `ChatModerationManager.kt`.
- [ ] `[Уже есть в KCIKTV]` **Защита от очистки чата модератором** (`PREVENT_MOD_CLEAR`) — в `ChatEventHandler.handleChatClear()` сохраняются сообщения и появляется кнопка «Восстановить чат» (`restoreClearedMessages()`); в ReTwitch есть режим полного игнорирования `/clear` без скрытия ленты.
- [ ] `[Уже есть в KCIKTV]` **Мод-логи** (`MOD_LOGS`) — в KCIKTV есть `ModLogManager.kt` (отдельная шторка с фильтрами событий), но нет вывода банов/таймаутов/удалений строкой прямо в ленту чата.
- [ ] `[Уже есть в KCIKTV]` **Недавние эмоуты (включая подсказки и разделение по каналам)** (`FREQUENT_EMOTES`) — `EmotePanelManager.kt` и `QuickEmoteBarManager.kt` сохраняют недавние эмоуты (глобально и на канал).
- [ ] `[Уже есть в KCIKTV]` **Запоминание правил чата** (`SKIP_CHAT_RULES` частично) — `ChatRulesManager.kt` запоминает хэш принятых правил и не показывает окно повторно (но нет тумблера полного пропуска при первом входе).
- [ ] `[Уже есть в KCIKTV]` **Превью ссылок (OG-карточка и доверенные домены)** (`LinkPreviewSettings`) — `DialogUtils.kt` загружает OG-превью и предпросмотр каналов Kick в модальном окне при тапе; в ReTwitch превью рисуются инлайн прямо в чате.

### Реализуемо для Kick
- [ ] `[Kick OK]` **Чередование фона строк чата (через одну темнее)** (`CHAT_ALTERNATING`).
- [ ] `[Kick OK]` **Цветные `/me`-сообщения (весь текст цветом ника)** (`COLORED_ME`).
- [ ] `[Kick OK]` **Подсветка первого сообщения зрителя на канале** (`FIRST_MESSAGE`).
- [ ] `[Kick OK]` **Полное отключение отрисовки чата для слабых устройств** (`CHAT_OFF`) — сейчас в KCIKTV чат ставится на паузу только в PiP (`batterySaverPauseChatInPip`).
- [ ] `[Kick OK]` **Расширенные режимы удалённых сообщений и их фон** (`DeletedMode`: скрывать / по тапу / текст / зачёркнутый / серый + `DELETED_BACKGROUND` с выбором цвета и насыщенности).
- [ ] `[Kick OK]` **Режим полного игнорирования `/clear` без скрытия ленты** (`PREVENT_MOD_CLEAR` — сразу оставлять сообщения и показывать строку уведомления).
- [ ] `[Kick OK]` **Инлайн мод-логи строкой в общем чате** (`MOD_LOGS` — баны, таймауты и удаления прямо в потоке сообщений).
- [ ] `[Kick OK]` **Окно полной ветки ответов по тапу на «В ответ ...»** (`REPLY_THREADS`).
- [ ] `[Kick OK]` **Пункт «Копировать и отправить» в меню долгого нажатия на сообщение** (`COPY_SEND`).
- [ ] `[Kick OK]` **Отключение тоста «Сообщение скопировано»** (`NO_COPY_TOAST`).
- [ ] `[Kick OK]` **Открытие ссылок без окна предупреждения** (`NO_LINK_DISCLAIMER`).
- [ ] `[Kick OK]` **Инлайн-превью медиа-ссылок прямо в ленте чата** (`LinkPreviewSettings`: режимы — только ссылка / скрывать / только превью / превью+ссылка; макс. ширина/высота dp; размытие до тапа `linkPreviewBlur`; увеличение по тапу `linkPreviewZoom`; загрузка через прокси).
- [ ] `[Kick OK]` **Фильтр: скрывать сообщения ботов** (`HIDE_BOTS` — BotRix, KickBot, Nightbot, StreamElements и др.).
- [ ] `[Kick OK]` **Фильтр: скрывать объявления / выделенные анонсы** (`HIDE_ANNOUNCEMENTS`).
- [ ] `[Kick OK]` **Фильтр: скрывать уведомления о подписках и подарках в чате** (`HIDE_SUB_NOTICES`).
- [ ] `[Kick OK]` **Фильтр: скрывать сообщения о наградах за баллы канала** (`HIDE_REDEMPTIONS`).
- [ ] `[Kick OK]` **Фильтр: скрывать сообщения о сериях просмотров** (`HIDE_MILESTONES`).
- [ ] `[Kick OK]` **Фильтр: скрывать служебные сообщения чата** (`HIDE_SERVICE_MESSAGES` — подключение, смена режима).
- [ ] `[Kick OK]` **Чёрный список слов и фраз в чате** (`ChatBlacklist.KEY_WORDS` — построчный редактор).
- [ ] `[Kick OK]` **Чёрный список пользователей (локальный игнор-лист по логину)** (`ChatBlacklist.KEY_USERS`).
- [ ] `[Kick OK]` **Формат отображения ников / логинов** (`NameFormat`: ник и логин / только ник / только логин).
- [ ] `[Kick OK]` **Вывод сообщений в реальном времени без батчинга** (`CHAT_REALTIME` — в KCIKTV `chatRefreshRate` по умолчанию 1500 мс, нужен режим 0–200 мс).
- [ ] `[Kick OK]` **Синхронизация Live-чата с задержкой видеопотока** (`CHAT_SYNC`).
- [ ] `[Kick OK]` **Подсказки эмоутов без двоеточия и картинки эмоутов в поле ввода** (`EmoteSettings.autocompleteNoColon`, `EMOTE_INPUT_IMAGES`).
- [ ] `[Kick OK]` **Кнопка эмоутов только во время набора текста** (`EMOTE_BUTTON_TYPING_ONLY` — расширяет поле ввода в покое).
- [ ] `[Kick OK]` **Избранные эмоуты (по долгому тапу, глобально и отдельно на канал, первыми в подсказках)** (`EMOTE_FAVORITES`, `EMOTE_FAVORITES_PER_CHANNEL`, `FAVORITES_FIRST_AUTOCOMPLETE`).
- [ ] `[Kick OK]` **Доработки панели эмоутов**: режим кнопки стирания — по букве / по слову / стандарт (`pickerBackspace`), отключение липких заголовков разделов (`PICKER_NO_STICKY_HEADERS`), нажатие «Отправить» и поля ввода с первого тапа при открытой панели (`PICKER_OUTSIDE_TAP`), карточка информации об эмоуте по тапу (`emoteCardEnabled`).
- [ ] `[Kick OK]` **Точный числовой ввод масштаба шрифта чата в %** (`SettingsHook.number` в дополнение к слайдеру).
- [ ] `[Kick OK]` **Строка о рейде / хосте в ленте чата** (`RAID_CARD`) и **выравнивание значков по базовой линии текста** (`BADGES_ON_BASELINE`).
- [ ] `[Kick OK]` **Полный пропуск окна «Правила чата» при первом вводе** (`SKIP_CHAT_RULES`).

### Сложно / требует адаптации под Kick
- [ ] `[Сложно / адаптация под Kick]` **Поддержка сторонних эмоутов 7TV, BetterTTV (BTTV), FrankerFaceZ (FFZ) и Homies + личные эмоуты 7TV + режим «Только эмоуты канала»** (`EmoteSettings` 7tv/bttv/ffz/homies, `NO_GLOBAL_EMOTES`, управление дисковым кэшем эмоутов).
- [ ] `[Сложно / адаптация под Kick]` **Модификаторы эмоутов (`w!`, `h!`, `v!`, `c!`, `z!`, `ffzX`), zero-width наложения и гигантские эмоуты** (`EmoteSettings.modifiers`, `FFZ_GIANT_EMOTES`).
- [ ] `[Сложно / адаптация под Kick]` **Качество картинок (1x–4x), размер в чате (12–64dp), BTTV в WEBP и прокси для эмоутов** (`EmoteSettings.quality`, `emoteSizeDp`, `bttvWebp`, `EmoteSettings.proxy`).
- [ ] `[Сложно / адаптация под Kick]` **Раскраска ников (7TV Paints) и цветные `@упоминания` цветом/краской автора** (`ChatSettings.nickPaints`, `COLORED_MENTIONS`).
- [ ] `[Сложно / адаптация под Kick]` **Значки сторонних клиентов в чате (FFZ, Chatterino, DankChat, Chatsen, Homies)** (`THIRD_PARTY_BADGES`: `FFZ_BADGES`, `CHATTERINO_BADGES`, `DANKCHAT_BADGES`, `CHATSEN_BADGES`, `HOMIES_BADGES`).
- [ ] `[Сложно / адаптация под Kick]` **Догрузка расширенной истории чата до момента входа** (аналог `ROBOTTY_HISTORY` для Kick).
- [ ] `[Сложно / адаптация под Kick]` **Инлайн-модерация AutoMod прямо в потоке чата** (`AUTOMOD_CHAT` — задержанные сообщения с кнопками «Разрешить» / «Отклонить»).

---

## 4. Уведомления и выделение сообщений

### Уже есть в KCIKTV (с отличиями)
- [ ] `[Уже есть в KCIKTV]` **Подсветка упоминаний, определение ника без `@`, заливка строки фоном или цветом ника** (`mentionHighlight`, `nickDetectionEnabled`, `highlightFillBackground`, `chatUseNameColorForHighlight` в `ChatSettingsSheetManager.kt`) — нет выбора конкретной палитры цветов из 6 вариантов (`MentionColorIndex`).
- [ ] `[Уже есть в KCIKTV]` **Вибрация при упоминании** (`vibrateOnMentions`) — в `MentionsManager.kt` длительность фиксирована 50 мс, в ReTwitch настраивается 30–400 мс.
- [ ] `[Уже есть в KCIKTV]` **Звук при упоминании** (`soundOnMentions`) — в `MentionsManager.kt` встроенный `mention_pop`, в ReTwitch можно выбрать системную мелодию (`SoundPicker`) и громкость 25–100%.
- [ ] `[Уже есть в KCIKTV]` **Системное push-уведомление об упоминаниях и ответах в фоне/PiP** (`notifyOnMentions` в `MentionsManager.kt`).

### Реализуемо для Kick
- [ ] `[Kick OK]` **Свои слова-триггеры и `@ники` для подсветки + отдельный цвет для своих слов** (`highlightWords`, `wordsColorIndex`).
- [ ] `[Kick OK]` **Внутричатовые алерты на ответы вам (`REPLY_ALERTS`)** — подсветка, звук и вибрация в реальном времени при ответе на ваше сообщение (даже без `@ника` в тексте).
- [ ] `[Kick OK]` **Выбор палитры цвета упоминаний, длительности вибрации (30–400 мс), мелодии и громкости (25–100%)** (`mentionColorIndex`, `mentionVibrateMs`, `SoundPicker`, `mentionSoundVolume`).

---

## 5. Навигация, интерфейс и приложение

### Уже есть в KCIKTV (с отличиями)
- [ ] `[Уже есть в KCIKTV]` **Выбор языка приложения независимо от системы** (`AppPreferences.language`) — реализовано в `SettingsPanelManager.kt` через нативные `values-*/strings.xml`.
- [ ] `[Уже есть в KCIKTV]` **Автопроверка обновлений, выбор канала/источника и «Что нового»** (`UPDATE_CHECK`, `UPDATE_NOTIFY`, `Changelog`) — реализовано в `UpdateRepository.kt` и `WhatsNewSheetManager.kt` (нет только фонового таймера проверки по интервалам от 30 мин до 1 раза в день).
- [ ] `[Уже есть в KCIKTV]` **Базовый сетевой прокси для API** (`ProxyConfig`: HTTP/SOCKS5, логин/пароль, scope `critical`/`all`) — в KCIKTV нет встроенного чекера доступности прокси и проксирования HLS-видеопотоков/WebSocket чата.
- [ ] `[Уже есть в KCIKTV]` **Просмотр без входа в аккаунт** (`WATCH_LOGGED_OUT`) — KCIKTV изначально позволяет смотреть стримы, VOD и читать чат без авторизации.
- [ ] `[Уже есть в KCIKTV]` **История просмотра VOD и недавно просмотренных каналов** (`AppPreferences.vodWatchHistory`, `recentWatchedSlugs`).
- [ ] `[Уже есть в KCIKTV]` **Скрытие нежелательных категорий в ленте** (`blockedCategories`) и **фильтр языков трансляций** (`streamLanguages`).

### Реализуемо для Kick
- [ ] `[Kick OK]` **Управление ориентацией главной и экранов вне плеера** (`HomeOrientation`: всегда портрет / по системному автоповороту / не сбрасывать ландшафт после выхода из стрима).
- [ ] `[Kick OK]` **Выбор стартовой вкладки при запуске приложения** (аналог `LandingSettings.followingDefaultTab` — открывать «Отслеживаемое» по умолчанию).
- [ ] `[Kick OK]` **Избранные каналы** (`FAVORITE_CHANNELS`) — закреплённый раздел любимых стримеров над общим списком «В эфире».
- [ ] `[Kick OK]` **Компактный список каналов в «В эфире»** (`COMPACT_LIVE_TAB`: превью слева, ник и название справа).
- [ ] `[Kick OK]` **Настройка размера превью каналов в «Отслеживаемом»** (`followingPreviewSize`: обычные / средние / крупные).
- [ ] `[Kick OK]` **Алфавитная сортировка отслеживаемых категорий и раздельная сортировка боковой панели (планшеты)** (`SORT_FOLLOWED_CATEGORIES`, `SEPARATE_DRAWER_SORT`).
- [ ] `[Kick OK]` **Тонкая настройка Главной и Отслеживаемого (скрытие элементов)**: скрыть категории (`HIDE_FOLLOWING_CATEGORIES`), «Продолжить просмотр» (`HIDE_FOLLOWING_CONTINUE`), замена досмотренных видео (`HIDE_FINISHED_CONTINUE`), оффлайн-каналы (`HIDE_FOLLOWING_OFFLINE`), пустые заглушки историй/эфиров (`HIDE_STORIES_EMPTY`, `HIDE_LIVE_EMPTY`), теги/категории под каналом (`HIDE_STREAM_TAGS`, `HIDE_FOLLOWING_CATEGORY`), сами истории (`HIDE_HOME_STORIES`), вкладки ленты и свайп между ними (`HIDE_FEED_TABS`, `NO_HOME_SWIPE`), нижние вкладки меню (`HIDE_TAB_BROWSE`, `HIDE_TAB_ACTIVITY`, `HIDE_TAB_CREATE`, `HIDE_TAB_PROFILE`), время аптайма на карточке (`STREAM_UPTIME` — дополнить на все карточки, где нет).
- [ ] `[Kick OK]` **Гибкий конструктор скрытия элементов UI плеера, шторки параметров, шапки и поля ввода чата**: кнопки плеера (`HIDE_PLAYER_CAST`, `HIDE_PLAYER_SHARE`, `HIDE_PLAYER_CLIP`, `HIDE_PLAYER_FULLSCREEN`, `HIDE_PLAYER_ROTATE`, `HIDE_PLAYER_MUTE`, `HIDE_PLAYER_MINIMIZE`, `HIDE_PLAYER_SETTINGS`), кнопки подписки/отслеживания (`HIDE_FOLLOW_SUB`, `HIDE_UNFOLLOW`), предупреждение 18+ (`HIDE_MATURE_WARNING`), пункты шторки параметров (`HIDE_OPTIONS_QUALITY`, `HIDE_OPTIONS_VIEWING`, `HIDE_OPTIONS_SPEED`, `HIDE_OPTIONS_EXTERNAL`, `HIDE_OPTIONS_BROADCASTER`), шапка чата и чат VOD (`ChatHeaderSetting`, `HIDE_CHAT_TITLE`, `HIDE_HEADER_BUTTONS`, `HIDE_GIFT_BUTTON`, `HIDE_VOD_CHAT_HEADER`, `HIDE_VOD_ROW_MENU`), мини-плеер (`HIDE_MINI_CLOSE`, `HIDE_MINI_EXPAND`), скрытие поля ввода в портрете/ландшафте/оверлее и полосы ограничений чата (`HIDE_PORTRAIT_CHAT_INPUT`, `HIDE_LANDSCAPE_CHAT_INPUT`, `HIDE_ONE_CHAT_INPUT`, `HIDE_CHAT_RESTRICTIONS`, `HIDE_CHAT_SETTINGS_BUTTON`, `IDLE_INPUT_CURSOR`, `LANDSCAPE_INPUT_VISIBLE`), полки профиля канала (`HIDE_PROFILE_FOLLOW`, `HIDE_PROFILE_SUBSCRIBE`, `HIDE_PROFILE_GIFT`, `HIDE_PROFILE_RECAPS`, `HIDE_PROFILE_FEATURED_CLIPS`, `HIDE_PROFILE_USER_CLIPS`, `HIDE_PROFILE_CATEGORIES`, `HIDE_PROFILE_STREAMERS`).
- [ ] `[Kick OK]` **Резервная копия настроек (экспорт/импорт в файл JSON и сброс настроек)** (`SettingsBackup`).
- [ ] `[Kick OK]` **Смена иконки приложения** (`AppIcon` — выбор из нескольких вариантов иконки через `<activity-alias>`).
- [ ] `[Kick OK]` **Мастер быстрой настройки при первом запуске** (`QuickSetup`: «Главное за N вопросов»).
- [ ] `[Kick OK]` **Поиск по всем настройкам приложения** (`SettingsScreen` search с подсветкой совпадений и переходом).
- [ ] `[Kick OK]` **Проксирование видеопотоков (LIVE/VOD HLS) и WebSocket чата + проверка работоспособности прокси в диалоге** (`StreamProxy`, `CHAT_PROXY`, `ProxyHealth`).
- [ ] `[Kick OK]` **Диагностические логи для баг-репортов** (`UI_STALL_LOG`, `BAN_DIAG_LOG`, проверка статуса push-разрешений `PushRegistration`).
- [ ] `[Kick OK]` **Блокировка баннеров/рекламы в ленте и расширенное отключение трекеров** (`BLOCK_DISPLAY_ADS`, `BLOCK_TRACKERS`).

### Сложно / требует адаптации под Kick
- [ ] `[Сложно / адаптация под Kick]` **Обход бана на канале (просмотр стрима и чата в гостевом режиме без аккаунта)** (`BAN_BYPASS`) — требует автоматического отката к guest-подключению плеера и WebSocket при обнаружении бана.

---

## 6. Специфичные для Twitch (`[Только Twitch]`)

> Эти функции завязаны на инфраструктуру, закрытый APK, React Native или протоколы самого Twitch и для Kick не применяются (либо имеют смысл только как справка о полном составе ReTwitch).

- [ ] `[Только Twitch]` **Блокировка вшитой в поток рекламы (VAFT / SSAI)** (`AdSettings.blockSsaiAds`, `VAFT_LOW_LATENCY`, `VAFT_BLOCKING_LABEL`, `VAFT_LOG`, выбор `PlayerType` для `usher`-запросов).
- [ ] `[Только Twitch]` **Блокировка клиентских видео- и аудиорекламных роликов** (`AdSettings.blockClientAds`).
- [ ] `[Только Twitch]` **Скрытие предложений Twitch Turbo, скидок на подписки и баннера «Tap Follow!»** (`HIDE_PROMOTIONS`, `HIDE_SOCIAL_GIFT_SUBS`, `HIDE_GIFT_BANNERS`).
- [ ] `[Только Twitch]` **Скрытие окна Twitch про cookie и предпочтения рекламы** (`HIDE_COOKIE_CONSENT`).
- [ ] `[Только Twitch]` **Просмотр записей для подписчиков без подписки (TwitchNoSub)** (`SUB_VODS`).
- [ ] `[Только Twitch]` **Автосбор баллов канала Twitch (нажатие на сундук)** (`ChatSettings.autoClaimPoints`).
- [ ] `[Только Twitch]` **Кулдаун наград за баллы канала (`REWARD_STATUS`), кнопка Bits (`HIDE_BITS_BUTTON`), кнопка и баланс баллов (`HIDE_POINTS_PORTRAIT`, `HIDE_POINTS_LANDSCAPE`, `POINTS_BALANCE_LANDSCAPE`, `POINTS_BALANCE_UNDER_ICON`), платные закрепы Cheers (`HIDE_PINNED_CHEERS`)**.
- [ ] `[Только Twitch]` **Скрытие Twitch-баннеров над чатом**: Hype Train (`HIDE_HYPE_TRAIN`), Shared Chat / Stream Together (`HIDE_SHARED_CHAT`), Drops-баннер (`HIDE_DROPS_BANNER`), Shoutout (`HIDE_SHOUTOUT`), Прогнозы/опросы Twitch (`HIDE_PREDICTIONS`), Рейды (`HIDE_RAID_BANNER`).
- [ ] `[Только Twitch]` **Настройки закреплённых сообщений стримера/модов Twitch и нативных GIF** (`ChatFilters.pinnedMode`, `HIDE_NATIVE_GIFS`).
- [ ] `[Только Twitch]` **Скрытие расширений канала Twitch (`HIDE_EXTENSIONS`) и таблиц лидеров (`HIDE_LEADERBOARDS`)**.
- [ ] `[Только Twitch]` **Значок серии просмотров Twitch в «Отслеживаемом» (`FOLLOWING_STREAK_TAG`)**.
- [ ] `[Только Twitch]` **Выбор типа страницы канала Twitch (Нативная / React Native)** (`LandingSettings.channelPageMode`) и скрытие кнопки управления стримами (`HIDE_PROFILE_STREAM_MANAGER`).
- [ ] `[Только Twitch]` **Патчи и фиксы React Native / вертикального плеера / полок Twitch**: `FEED_TAB_INDICATOR`, `FRESH_RN_PREVIEWS`, `ALL_FOLLOWED_CATEGORIES`, `FOLLOWING_CATEGORY_TAP`, `LAYOUT_PICKER_ICONS`, `HIDE_VERTICAL_BUTTONS`, `HIDE_VERTICAL_PANEL`, `HIDE_LIVE_FOLLOWED_SHELF`, `HIDE_LIVE_CLIPS_SHELF`.
- [ ] `[Только Twitch]` **Сервис истории чата Robotty** (`ROBOTTY_HISTORY` — работает через публичный API Twitch-логов).
- [ ] `[Только Twitch]` **Экосистема RTE (ReYohoho Twitch Extension)**: подписка RTE, друзья RTE (`FRIENDS_HOME`, `RTE_FRIENDS`, `FRIEND_BUTTON`), значок RTE (`rteBadges`), публичные прокси ReYohoho / TTV LOL PRO / PerfProd (`ProxyBackend`, предупреждение `proxyNoDrops`).
- [ ] `[Только Twitch]` **Редактор экспериментов Twitch (флаги Eppo)** (`ExperimentsPage`).
- [ ] `[Только Twitch]` **Вход в изменённом Twitch-приложении (`FIX_LOGIN`), `GLOBAL_BADGES_NOW`, `VOD_CHAT_EMOTES` (фикс кириллицы в токенизаторе Twitch), `HIDE_CHAT_MODE_POPUP`**.
- [ ] `[Только Twitch]` **Экспорт/импорт пользовательских файлов перевода поверх закрытого APK** (`TranslationFiles`) — в KCIKTV открытые `strings.xml`.
- [ ] `[Только Twitch]` **Отключение трекеров Twitch SDK (`BLOCK_TRACKERS`: Comscore, Sentry, Crashlytics, Open Measurement), `DiagProbe` граф компонентов и тест краша AppMetrica (`menuTestCrash`)**.
