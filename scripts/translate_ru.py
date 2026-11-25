#!/usr/bin/env python3
"""Reusable English -> Russian translator and validator for KCIKTV Android strings.xml.

Features:
- Line-preserving parser/writer matching app/src/main/res/shared/values/strings.xml
  line-for-line (XML declaration, comments, blank lines, indentation, trailing newline).
- Protects Android format specifiers, escapes, XML entities, and brand/tech terms.
- Batched free Google Translate (gtx) with numeric delimiters, automatic single-string
  fallback on delimiter mismatch, and MyMemory fallback on HTTP errors.
- Incremental mode (default), --force, --apply-glossary, and --validate-only.
- Built-in validator (XML validity, format-specifier parity, escaped apostrophes,
  zero leftover sentinels, and structural parity).
"""

from __future__ import annotations

import argparse
from collections import Counter
from concurrent.futures import ThreadPoolExecutor, as_completed
import html
import json
import pathlib
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET


REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
RES_DIR = REPO_ROOT / "app" / "src" / "main" / "res" / "shared"
BASE_XML = RES_DIR / "values" / "strings.xml"
OUT_XML = RES_DIR / "values-ru" / "strings.xml"

BATCH_SIZE = 18
MAX_BATCH_CHARS = 1600
MAX_WORKERS = 4

FMT_RE = re.compile(r"%(?:\d+\$)?[-+# 0,(]*(?:\d+|\*)?(?:\.(?:\d+|\*))?[a-zA-Z%]")

BRAND_TERMS = [
    "Kick.com",
    "ReTwitch",
    "Instagram",
    "YouTube",
    "Twitter",
    "Discord",
    "Android",
    "HyperOS",
    "RNNoise",
    "KCIKTV",
    "GitHub",
    "TikTok",
    "Twitch",
    "SOCKS5",
    "HTTPS",
    "Blerp",
    "BTTV",
    "HTTP",
    "MIUI",
    "7TV",
    "FFZ",
    "PiP",
    "PIP",
    "VOD",
    "HLS",
    "AV1",
    "URL",
    "FPS",
]

BRAND_RE = re.compile(
    r"(?<![A-Za-z0-9_])("
    + "|".join(re.escape(t) for t in sorted(BRAND_TERMS, key=len, reverse=True))
    + r")(?![A-Za-z0-9_])"
)

STRING_LINE_RE = re.compile(r'^(\s*<string\s+name="([^"]+)">)(.*)(</string>\s*)$')
ARRAY_START_RE = re.compile(r'^\s*<string-array\s+name="([^"]+)">\s*$')
ARRAY_END_RE = re.compile(r"^\s*</string-array>\s*$")
ITEM_LINE_RE = re.compile(r"^(\s*<item>)(.*)(</item>\s*)$")

SENTINEL_LEFTOVER_RE = re.compile(r"(?:ZX[PBNT]\d+XZ|@@SEP|@@\d{4,}@@)")
UNESCAPED_APOS_RE = re.compile(r"(?<!\\)'")

# Idiomatic Russian overrides keyed by <string name="..."> (raw Android XML inner text).
GLOSSARY_BY_KEY: dict[str, str] = {
    # General / buttons / tabs
    "preview_label": "Предпросмотр",
    "retry": "Повторить",
    "email": "Email",
    "email_label": "Email",
    "email_or_username_label": "Email или логин",
    "live": "В ЭФИРЕ",
    "live_badge": "В ЭФИРЕ",
    "offline": "Офлайн",
    "offline_badge": "ОФЛАЙН",
    "play": "Воспроизвести",
    "logout": "Выйти",
    "system_default": "Как в системе",
    "im_plus_18": "Мне есть 18+",
    "my_list": "ЛИЧНОЕ",
    "recommended_streams": "Рекомендуемые",
    "sort_title_featured": "Рекомендуемые",
    "viewers_high_to_low": "Зрители (по убыв.)",
    "viewers_low_to_high": "Зрители (по возр.)",
    "nav_following": "Подписки",
    "following": "Подписки",
    "following_status": "Отслеживается",
    "following_channels_header": "Отслеживаемые каналы",
    "login_required_following": "Войдите в аккаунт, чтобы видеть отслеживаемые каналы",
    "follow": "Отслеживать",
    "action_follow": "Отслеживать",
    "unfollow": "Отписаться",
    "followed": "Отслеживается",
    "unfollowed": "Отменено",
    "follow_date_format": "Отслеживает с: %s",
    "followers_millions": "%.1f млн фолловеров",
    "followers_thousands": "%.1f тыс. фолловеров",
    "followers_count": "%d фолловеров",
    "viewer_count_label": "%s зрителей",
    "viewer_follower_format": "%1$s зрителей • %2$s фолловеров",
    "tab_live": "Эфир",
    "tab_about": "О канале",
    "live_now": "В эфире",
    "currently_live": "Сейчас в эфире",
    "stream_ended": "Стрим офлайн",
    "stream_offline": "Стрим офлайн",
    "home_featured_title": "Каналы в эфире",
    "home_top_categories_title": "Популярные категории",
    "chat_title": "Онлайн-чат",
    "returning_to_live": "Возврат к эфиру…",
    "is_live_on_kick": "в прямом эфире на Kick!",

    # Categories
    "cat_art": "Искусство",
    "cat_chess": "Шахматы",
    "cat_just_chatting": "Общение",
    "clip_category_just_chatting": "Общение",
    "cat_just_sleeping": "Просто сон",
    "cat_music_production": "Создание музыки",
    "category_unhidden_toast": "Категория снова отображается",

    # Settings & Sidebar
    "settings_category_general": "Общие",
    "battery_general_header": "Общие",
    "settings_category_player": "Плеер",
    "settings_category_advanced": "Дополнительно",
    "chat_section_advanced": "Дополнительно",
    "chat_section_features": "Функции",
    "setting_about": "О приложении",
    "about_title": "О приложении",
    "setting_update_channel": "Канал обновлений",
    "sidebar_title_update_channel": "Канал обновлений",
    "setting_catch_up": "Автодогон эфира",
    "sidebar_title_catch_up": "Автодогон эфира",
    "only_manual": "Только вручную",
    "sidebar_title_delay": "Длительность инфо",
    "sidebar_title_zap": "Скорость переключения",
    "settings_kick_webview": "Kick WebView",
    "brand_green": "Зелёный Kick",
    "apk_not_found": "APK не найден",
    "font_category_serif": "С засечками",
    "font_category_display": "Декоративные и рукописные",
    "font_section_google": "Google Fonts",
    "font_source_google": "Google Fonts",

    # Animations
    "animation_fade_in": "Появление",
    "animation_flip": "Переворот",
    "animation_scale": "Увеличение",
    "animation_zoom_out": "Отдаление",
    "animation_swing": "Покачивание",

    # Proxy & Domains
    "proxy_password_set": "Задан",
    "proxy_not_set": "Не задан",
    "proxy_test": "Проверить прокси",
    "proxy_test_success": "Успешно! Подключено за %1$d мс",
    "trusted_domains_count": "Доменов: %d",

    # Player, Scaling, PiP, Audio, Stats
    "video_scale_fit": "Вписать",
    "video_scale_zoom": "Заполнение (без рамок)",
    "video_scale_fill": "Растянуть",
    "pip_action_mute": "Без звука",
    "pip_action_audio_only": "Только аудио",
    "pip_action_audio_only_desc": "Фоновое воспроизведение аудио",
    "battery_effect_pause_chat_pip": "Пауза чата в мини-плеере / PiP",
    "stats_player_state_label": "Состояние плеера:",
    "player_statistics": "Статистика плеера",
    "stats_volume_label": "Громкость:",
    "stat_volume": "Громкость",
    "stat_buffer_health": "Состояние буфера",
    "stat_ready_state": "Готовность",
    "eq_preset_flat": "Ровный",
    "nr_low": "Низкое",
    "nr_medium": "Среднее",
    "nr_high": "Высокое",
    "nr_light": "Лёгкое",
    "nr_balanced": "Сбалансированное",
    "nr_strong": "Сильное",
    "nr_max": "Максимальное",

    # Blerp
    "blerp_overlay": "Оверлей Blerp",
    "blerp_overlay_disabled_message": "Оверлей Blerp отключён. Включить его для корректного отображения этого контента (аудио и эффектов)?",

    # Chat, Emotes, Badges, Active Chatters
    "chat_identity_preview": "Предпросмотр",
    "chat_identity_color": "Цвет имени",
    "chat_identity_channel_badges": "Значки канала: видны на канале стримера",
    "chat_settings_nick_detection": "Распознавание ника",
    "chat_jump_to_bottom": "Вниз",
    "chat_restore_messages": "Восстановить %d сообщ.",
    "max_badges_reached": "Можно выбрать не более %d значков",
    "chat_reward_used": "использовал",
    "chat_error_followers_only": "Чат только для фолловеров.",
    "chat_hint_followers_only": "Чат только для фолловеров",
    "chat_hint_follower_remaining": "Осталось %d мин до доступа к чату",
    "chat_status_followers_mode_on": "Включён режим только для фолловеров",
    "chat_status_followers_mode_on_by": "Режим только для фолловеров включил &lt;b&gt;%1$s&lt;/b&gt;",
    "chat_status_followers_mode_off": "Режим только для фолловеров отключён",
    "chat_status_followers_mode_off_by": "Режим только для фолловеров отключил &lt;b&gt;%1$s&lt;/b&gt;",
    "mod_followers_only": "Чат только для фолловеров",
    "followers_only_mode": "Только фолловеры",
    "followers_only_mode_min": "Только фолловеры (%d мин+)",
    "slow_mode_active": "Медленный режим: %d с",
    "slow_mode_exempt": "Медленный режим: %d с (без ограничений)",
    "chat_hint_emotes_only": "Только смайлы...",
    "chat_status_emotes_mode_on": "Включён режим только смайлов",
    "chat_status_emotes_mode_on_by": "Режим только смайлов включил &lt;b&gt;%1$s&lt;/b&gt;",
    "chat_status_emotes_mode_off": "Режим только смайлов отключён",
    "chat_status_emotes_mode_off_by": "Режим только смайлов отключил &lt;b&gt;%1$s&lt;/b&gt;",
    "mod_emotes_only": "Только смайлы",
    "emotes_only_mode": "Только смайлы",
    "emote_category_subscribers": "Смайлы подписчиков",
    "emote_section_subscribers_only": "Только для подписчиков",
    "chat_emote_size": "Размер смайлов",
    "chat_emote_combo": "Комбо смайлов",
    "chat_emote_combo_subtitle": "Подсвечивать серию одинаковых смайлов подряд",
    "chat_settings_floating_emotes": "Плавающие смайлы",
    "chat_floating_emotes_subtitle": "Анимированные смайлы пролетают по экрану",
    "battery_chat_emote_fps": "Частота кадров смайлов (FPS)",
    "battery_animated_quick_panel": "Анимированные смайлы (быстрая панель)",
    "collectibles_type_emote": "Смайлы",
    "chat_gifted_sub_single": "%1$s подарил подписку сообществу! Всего подарено подписок на этом канале: %2$d.",
    "chat_gifted_sub_individual": "%1$s подарил подписку %2$s!",
    "chat_stream_hosted": "&lt;b&gt;%1$s&lt;/b&gt; хостит стрим с %2$d зрителями!",
    "chat_stream_hosted_no_count": "&lt;b&gt;%1$s&lt;/b&gt; хостит стрим!",
    "celebration_action_dismiss": "Скрыть",
    "celebration_context_title": "Поделиться юбилеем подписки",
    "active_chatters_title": "Участники чата",
    "active_chatters_message_count": "%d сообщ.",
    "mentions_count_format": "%d сообщ.",
    "active_chatters_group_vips": "VIP",
    "active_chatters_group_ogs": "OG",
    "active_chatters_sort_recent": "Недавние",
    "active_chatters_activity_medium": "Активный (3–9 сообщ.)",
    "active_chatters_activity_low": "Тихий (1–2 сообщ.)",
    "active_chatters_activity_silent": "Без сообщений",
    "badge_name_broadcaster": "Стример",
    "badge_name_og": "OG",
    "badge_name_subscriber": "Подписчик",
    "badge_name_sub_gifter": "Даритель подписок",
    "badge_name_sidekick": "Помощник",
    "chat_setting_og_messages": "Сообщения OG",
    "chat_pinch_to_resize": "Жест масштабирования чата",
    "chat_pinch_to_resize_subtitle": "Масштабировать чат сведением и разведением двух пальцев",
    "side_chat_resize_handle": "Ручка ширины бокового чата",
    "side_chat_resize_handle_subtitle": "Показывать перетаскиваемый разделитель на границе бокового чата",
    "setting_fullscreen_sensor_rotation_desc": "Поворачивать экран в полноэкранном режиме даже при выключенном автоповороте",
    "setting_draw_under_cutout": "Видео под вырезом камеры",
    "setting_draw_under_cutout_desc": "Растягивать полноэкранное видео на область выреза камеры в альбомной ориентации",
    "setting_transparent_chat_overlay": "Прозрачный чат поверх видео",
    "setting_transparent_chat_overlay_desc": "Показывать полупрозрачный чат поверх полноэкранного видео в альбомной ориентации",
    "setting_chat_overlay_opacity": "Непрозрачность фона чата",
    "setting_chat_overlay_edge_margin": "Отступ чата от края",
    "video_settings_chat_settings": "Настройки чата",
    "video_settings_chat_settings_desc": "Внешний вид, прозрачность, отступ и сброс",
    "chat_reset_defaults": "Сбросить чат по умолчанию",
    "chat_reset_defaults_desc": "Вернуть стандартные настройки вида, разметки и оверлея чата",
    "chat_reset_defaults_dialog_title": "Сбросить настройки чата?",
    "chat_reset_defaults_dialog_message": "Все настройки внешнего вида, размера, прозрачности и отступа чата будут сброшены к значениям по умолчанию. Аккаунт, список игнорируемых и настройки видео не изменятся.",
    "chat_reset_defaults_confirm": "Сбросить",
    "chat_reset_defaults_toast": "Настройки чата сброшены по умолчанию",
    "tv_quick_mobile_ui": "Мобильный",
    "tv_exit_dialog_title": "Выйти из ТВ-режима?",
    "tv_exit_dialog_message": "Вы можете вернуться в мобильный интерфейс или закрыть приложение.",
    "tv_exit_app_action": "Закрыть приложение",

    # Moderation, Activity Feed, Polls, Predictions, Clips, Rewards, Drops
    "activity_feed_redeemed": "активировал награду",
    "activity_feed_celebrates": "празднует %d мес. подписки!",
    "activity_feed_celebrates_plural": "празднует %d мес. подписки!",
    "activity_filter_followers": "Фолловеры",
    "activity_filter_gifted_subs": "Подаренные подписки",
    "activity_filter_hosts": "Хосты",
    "mod_log_title": "Действия модератора",
    "mod_view_title": "Действия модератора",
    "mod_emote_channels_title": "Эмоуты мод-каналов",
    "mod_log_banned": "забанен",
    "mod_log_banned_by": "забанен модератором",
    "mod_log_timed_out": "отстранён (тайм-аут)",
    "mod_pin_confirm_title": "Закрепить сообщение",
    "action_pin": "Закрепить сообщение",
    "pin_removed": "Закрепление снято",
    "unpin_title": "Открепить сообщение",
    "mod_ban_confirm_title": "Забанить",
    "action_ban_with_label": "Забанить",
    "mod_custom_duration": "Своя длительность",
    "action_redeem": "Получить",
    "loyalty_redeem_action": "Получить",
    "reward_queue_pause_redemptions": "Приостановить награды",
    "reward_queue_paused_badge": "на паузе",
    "prediction_header": "ПРОГНОЗ",
    "prediction_label": "ПРОГНОЗ",
    "prediction_default_title": "Прогноз",
    "prediction_control": "Управление прогнозом",
    "start_prediction": "Начать прогноз",
    "lock_submissions": "Закрыть приём ставок",
    "resolve_failed": "Не удалось завершить прогноз",
    "prediction_resolved": "Прогноз завершён",
    "prediction_max_bet": "ВА-БАНК",
    "gift_shop_title": "Магазин подарков",
    "trim_label": "ОБРЕЗКА: ",
    "clip_url_label": "URL клипа",
    "clip_url_missing": "URL клипа отсутствует",
    "clip_url_not_found": "URL клипа не найден",
    "clip_url_copied": "Ссылка на клип скопирована",
    "download_getting_url": "Получение ссылки…",
    "live_url_not_found": "URL трансляции не найден",
    "daily_reward_claim_button": "Забрать",
    "daily_reward_claimed_button": "Получено",
    "daily_reward_claiming_button": "Получение…",
    "collectibles_title": "Коллекция",
    "collectibles_load_error": "Не удалось загрузить коллекцию",
    "streak_label": "Серия",
    "drops_title": "Drops",
    "drops_tab_ended": "Завершены",
    "drops_join": "Участвовать",
    "drops_claim": "Забрать",
    "drops_reward_claimed": "Получено",
    "drops_rule_watch": "Смотрите, чтобы получить",
    "drops_load_error": "Не удалось загрузить Drops. Нажмите, чтобы повторить.",

    # Compact time & watch-time formats
    "time_24h": "24 ч",
    "uptime_hours_minutes": "%1$d ч %2$d мин",
    "uptime_minutes": "%d мин",
    "watch_time_hours_minutes": "Просмотрено: %1$d ч %2$d мин",
    "watch_time_minutes": "Просмотрено: %d мин",
    "time_to_level_up_hours_minutes": "%1$d ч %2$d мин до уровня",
    "time_to_level_up_minutes": "%d мин до уровня",
    "daily_reward_consolation_hours_minutes": "+%1$d ч %2$d мин к уровню",
    "daily_reward_consolation_minutes": "+%1$d мин к уровню",
    "daily_reward_locked_progress_hours_minutes": "Осталось %1$d ч %2$d мин",
    "daily_reward_locked_progress_minutes": "Осталось %d мин",
}

# Idiomatic Russian overrides for <string-array> items keyed by (array_name, 0-based index).
ARRAY_ITEM_OVERRIDES: dict[tuple[str, int], str] = {
    ("stream_language_names", 0): "Турецкий",
    ("stream_language_names", 1): "Английский",
    ("stream_language_names", 2): "Африкаанс",
    ("stream_language_names", 3): "Немецкий",
    ("stream_language_names", 4): "Арабский",
    ("stream_language_names", 5): "Албанский",
    ("stream_language_names", 6): "Азербайджанский",
    ("stream_language_names", 7): "Белорусский",
    ("stream_language_names", 8): "Бенгальский",
    ("stream_language_names", 9): "Боснийский",
    ("stream_language_names", 10): "Болгарский",
    ("stream_language_names", 11): "Чешский",
    ("stream_language_names", 12): "Китайский",
    ("stream_language_names", 13): "Датский",
    ("stream_language_names", 14): "Индонезийский",
    ("stream_language_names", 15): "Эсперанто",
    ("stream_language_names", 16): "Эстонский",
    ("stream_language_names", 17): "Фарерский",
    ("stream_language_names", 18): "Персидский",
    ("stream_language_names", 19): "Нидерландский",
    ("stream_language_names", 20): "Филиппинский",
    ("stream_language_names", 21): "Финский",
    ("stream_language_names", 22): "Французский",
    ("stream_language_names", 23): "Грузинский",
    ("stream_language_names", 24): "Гавайский",
    ("stream_language_names", 25): "Хорватский",
    ("stream_language_names", 26): "Хинди",
    ("stream_language_names", 27): "Иврит",
    ("stream_language_names", 28): "Интерлингва",
    ("stream_language_names", 29): "Испанский",
    ("stream_language_names", 30): "Шведский",
    ("stream_language_names", 31): "Итальянский",
    ("stream_language_names", 32): "Японский",
    ("stream_language_names", 33): "Каталанский",
    ("stream_language_names", 34): "Корейский",
    ("stream_language_names", 35): "Латынь",
    ("stream_language_names", 36): "Польский",
    ("stream_language_names", 37): "Латышский",
    ("stream_language_names", 38): "Литовский",
    ("stream_language_names", 39): "Венгерский",
    ("stream_language_names", 40): "Македонский",
    ("stream_language_names", 41): "Малаялам",
    ("stream_language_names", 42): "Малайский",
    ("stream_language_names", 43): "Монгольский",
    ("stream_language_names", 44): "Норвежский",
    ("stream_language_names", 45): "Норвежский букмол",
    ("stream_language_names", 46): "Португальский",
    ("stream_language_names", 47): "Румынский",
    ("stream_language_names", 48): "Молдавский",
    ("stream_language_names", 49): "Русский",
    ("stream_language_names", 50): "Сербский",
    ("stream_language_names", 51): "Словацкий",
    ("stream_language_names", 52): "Словенский",
    ("stream_language_names", 53): "Сомалийский",
    ("stream_language_names", 54): "Сунданский",
    ("stream_language_names", 55): "Татарский",
    ("stream_language_names", 56): "Тайский",
    ("stream_language_names", 57): "Украинский",
    ("stream_language_names", 58): "Урду",
    ("stream_language_names", 59): "Вьетнамский",
    ("stream_language_names", 60): "Греческий",
    ("stream_language_names", 61): "Зулу",
}


def unescape_android_xml(raw: str) -> str:
    """Convert Android XML inner string to plain text for translation."""
    text = raw.replace(r"\'", "'").replace(r'\"', '"')
    text = text.replace(r"\n", "\n").replace(r"\t", "\t")
    text = html.unescape(text)
    return text


def escape_android_xml(text: str) -> str:
    """Convert translated plain text back to valid Android XML inner string."""
    # Normalize any pre-escaped apostrophes before escaping all unescaped ones.
    text = text.replace(r"\'", "'")
    text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    text = text.replace("\n", r"\n").replace("\t", r"\t")
    text = text.replace("'", r"\'")
    return text


def protect_tokens(raw_xml_value: str) -> tuple[str, list[tuple[str, str]]]:
    """Decode XML/escapes and replace format specifiers, newlines, tabs, brands with sentinels."""
    plain = unescape_android_xml(raw_xml_value)
    tokens: list[tuple[str, str]] = []

    def add_token(prefix: str, original: str) -> str:
        sentinel = f"{prefix}{len(tokens)}XZ"
        tokens.append((sentinel, original))
        return sentinel

    # Protect format specifiers first.
    plain = FMT_RE.sub(lambda m: add_token("ZXP", m.group(0)), plain)
    # Protect newlines and tabs so paragraph structure survives batching cleanly.
    plain = plain.replace("\n", " " + add_token("ZXN", "\n") + " ")
    plain = plain.replace("\t", " " + add_token("ZXT", "\t") + " ")
    # Protect brand and tech terms.
    plain = BRAND_RE.sub(lambda m: add_token("ZXB", m.group(0)), plain)
    return plain, tokens


def restore_tokens(translated: str, tokens: list[tuple[str, str]]) -> str:
    """Restore sentinel tokens and re-escape for Android strings.xml."""
    out = translated
    # First restore newlines/tabs, trimming spaces inserted around ZXN/ZXT sentinels.
    for sentinel, original in tokens:
        if sentinel.startswith("ZXN") or sentinel.startswith("ZXT"):
            out = re.sub(rf" ?{re.escape(sentinel)} ?", lambda _: original, out)
        else:
            out = out.replace(sentinel, original)
    # Clean stray spaces right after a newline introduced by translation reordering.
    out = re.sub(r"\n +", "\n", out)
    out = re.sub(r" +\n", "\n", out)
    return escape_android_xml(out)


def is_translatable_content(raw_xml_value: str) -> bool:
    """Return True if the string contains English letters worth translating."""
    stripped = FMT_RE.sub("", unescape_android_xml(raw_xml_value))
    return bool(re.search(r"[A-Za-z]", stripped))


def http_get_json(url: str, timeout: float = 15.0) -> object:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        },
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return json.loads(resp.read().decode("utf-8"))


def translate_google_single(text: str, retries: int = 3) -> str:
    url = (
        "https://translate.googleapis.com/translate_a/single"
        "?client=gtx&sl=en&tl=ru&dt=t&q=" + urllib.parse.quote(text)
    )
    last_err: Exception | None = None
    for attempt in range(retries):
        try:
            data = http_get_json(url)
            return "".join(seg[0] for seg in data[0] if seg and seg[0])
        except Exception as exc:  # noqa: BLE001
            last_err = exc
            time.sleep(0.7 * (attempt + 1))
    raise RuntimeError(f"Google Translate failed: {last_err}")


def translate_mymemory_single(text: str, retries: int = 2) -> str:
    url = (
        "https://api.mymemory.translated.net/get?langpair=en|ru&q="
        + urllib.parse.quote(text)
    )
    last_err: Exception | None = None
    for attempt in range(retries):
        try:
            data = http_get_json(url)
            if isinstance(data, dict) and data.get("responseData", {}).get("translatedText"):
                return str(data["responseData"]["translatedText"])
            raise RuntimeError(f"Unexpected MyMemory payload: {data}")
        except Exception as exc:  # noqa: BLE001
            last_err = exc
            time.sleep(0.8 * (attempt + 1))
    raise RuntimeError(f"MyMemory failed: {last_err}")


def translate_single_with_fallback(text: str) -> str:
    lead = text[: len(text) - len(text.lstrip(" "))]
    trail = text[len(text.rstrip(" ")) :]
    core = text.strip(" ")
    if not core:
        return text
    try:
        res = translate_google_single(core)
    except Exception:  # noqa: BLE001
        res = translate_mymemory_single(core)
    return f"{lead}{res.strip()}{trail}"


def translate_batch(texts: list[str]) -> list[str]:
    """Translate a batch via Google Translate with numeric sentinel delimiters."""
    if len(texts) == 1:
        return [translate_single_with_fallback(texts[0])]

    delims = [f"@@{9000 + i}@@" for i in range(len(texts) - 1)]
    pieces: list[str] = []
    for idx, t in enumerate(texts):
        pieces.append(t)
        if idx < len(delims):
            pieces.append(delims[idx])
    joined = "\n".join(pieces)

    try:
        translated = translate_google_single(joined)
        # Verify exact delimiter sequence survived untouched.
        parts = re.split(r"\s*@@\d{4,}@@\s*", translated.strip())
        if len(parts) == len(texts) and not any(re.search(r"@@\d{4,}@@", p) for p in parts):
            out: list[str] = []
            for orig, part in zip(texts, parts):
                lead = orig[: len(orig) - len(orig.lstrip(" "))]
                trail = orig[len(orig.rstrip(" ")) :]
                out.append(f"{lead}{part.strip()}{trail}")
            return out
    except Exception:  # noqa: BLE001
        pass

    # Fallback: translate items individually if batch delimiter mismatched or failed.
    return [translate_single_with_fallback(t) for t in texts]


def load_existing_translations(
    path: pathlib.Path,
) -> tuple[dict[str, str], dict[tuple[str, int], str]]:
    """Parse existing values-ru/strings.xml into key and array-item maps."""
    strings_map: dict[str, str] = {}
    items_map: dict[tuple[str, int], str] = {}
    if not path.exists():
        return strings_map, items_map

    current_array: str | None = None
    array_idx = 0
    for line in path.read_text(encoding="utf-8").splitlines():
        m_arr = ARRAY_START_RE.match(line)
        if m_arr:
            current_array = m_arr.group(1)
            array_idx = 0
            continue
        if ARRAY_END_RE.match(line):
            current_array = None
            continue
        m_str = STRING_LINE_RE.match(line)
        if m_str:
            strings_map[m_str.group(2)] = m_str.group(3)
            continue
        m_item = ITEM_LINE_RE.match(line)
        if m_item and current_array is not None:
            items_map[(current_array, array_idx)] = m_item.group(2)
            array_idx += 1
    return strings_map, items_map


def validate_output(base_path: pathlib.Path, out_path: pathlib.Path) -> list[str]:
    """Validate XML, format specifiers, apostrophes, sentinels, and structural parity."""
    errors: list[str] = []

    try:
        ET.parse(out_path)
    except ET.ParseError as exc:
        errors.append(f"XML parse error: {exc}")

    base_lines = base_path.read_text(encoding="utf-8").splitlines(keepends=True)
    out_lines = out_path.read_text(encoding="utf-8").splitlines(keepends=True)

    if len(base_lines) != len(out_lines):
        errors.append(f"Line count mismatch: base={len(base_lines)}, out={len(out_lines)}")

    base_trailing = base_lines[-1].endswith("\n") if base_lines else False
    out_trailing = out_lines[-1].endswith("\n") if out_lines else False
    if base_trailing != out_trailing:
        errors.append(
            f"Trailing newline mismatch: base={base_trailing}, out={out_trailing}"
        )

    current_array: str | None = None
    array_idx = 0
    for line_no, (b_line, o_line) in enumerate(zip(base_lines, out_lines), 1):
        m_arr = ARRAY_START_RE.match(b_line)
        if m_arr:
            current_array = m_arr.group(1)
            array_idx = 0
            if b_line != o_line:
                errors.append(f"Line {line_no}: array header mismatch")
            continue
        if ARRAY_END_RE.match(b_line):
            current_array = None
            if b_line != o_line:
                errors.append(f"Line {line_no}: array end mismatch")
            continue

        b_str = STRING_LINE_RE.match(b_line)
        o_str = STRING_LINE_RE.match(o_line)
        if b_str:
            if not o_str:
                errors.append(f"Line {line_no}: expected <string> line")
                continue
            b_prefix, b_key, b_val, b_suffix = b_str.groups()
            o_prefix, o_key, o_val, o_suffix = o_str.groups()
            if b_key != o_key or b_prefix != o_prefix or b_suffix != o_suffix:
                errors.append(f"Line {line_no}: <string> wrapper/key mismatch for '{b_key}'")
            if Counter(FMT_RE.findall(b_val)) != Counter(FMT_RE.findall(o_val)):
                errors.append(
                    f"Line {line_no} ({b_key}): format specifier mismatch "
                    f"base={FMT_RE.findall(b_val)} out={FMT_RE.findall(o_val)}"
                )
            if UNESCAPED_APOS_RE.search(o_val):
                errors.append(f"Line {line_no} ({b_key}): unescaped apostrophe in '{o_val}'")
            if SENTINEL_LEFTOVER_RE.search(o_val):
                errors.append(f"Line {line_no} ({b_key}): leftover sentinel in '{o_val}'")
            continue

        b_item = ITEM_LINE_RE.match(b_line)
        o_item = ITEM_LINE_RE.match(o_line)
        if b_item:
            if not o_item:
                errors.append(f"Line {line_no}: expected <item> line")
                continue
            b_prefix, b_val, b_suffix = b_item.groups()
            o_prefix, o_val, o_suffix = o_item.groups()
            if b_prefix != o_prefix or b_suffix != o_suffix:
                errors.append(f"Line {line_no}: <item> wrapper mismatch")
            if Counter(FMT_RE.findall(b_val)) != Counter(FMT_RE.findall(o_val)):
                errors.append(f"Line {line_no} ({current_array}[{array_idx}]): format mismatch")
            if UNESCAPED_APOS_RE.search(o_val):
                errors.append(f"Line {line_no} ({current_array}[{array_idx}]): unescaped apostrophe")
            if SENTINEL_LEFTOVER_RE.search(o_val):
                errors.append(f"Line {line_no} ({current_array}[{array_idx}]): leftover sentinel")
            array_idx += 1
            continue

        if b_line != o_line:
            errors.append(f"Line {line_no}: structural/comment line mismatch")

    return errors


def build_batches(items: list[tuple[int, str]]) -> list[list[tuple[int, str]]]:
    batches: list[list[tuple[int, str]]] = []
    current: list[tuple[int, str]] = []
    current_chars = 0
    for idx, text in items:
        if current and (len(current) >= BATCH_SIZE or current_chars + len(text) > MAX_BATCH_CHARS):
            batches.append(current)
            current = []
            current_chars = 0
        current.append((idx, text))
        current_chars += len(text) + 12
    if current:
        batches.append(current)
    return batches


def main() -> int:
    parser = argparse.ArgumentParser(description="Translate KCIKTV strings.xml to Russian.")
    parser.add_argument("--force", action="store_true", help="Re-translate all entries.")
    parser.add_argument(
        "--apply-glossary",
        action="store_true",
        help="Apply GLOSSARY_BY_KEY and ARRAY_ITEM_OVERRIDES without hitting network.",
    )
    parser.add_argument(
        "--validate-only",
        action="store_true",
        help="Validate existing values-ru/strings.xml without writing.",
    )
    args = parser.parse_args()

    if args.validate_only:
        if not OUT_XML.exists():
            print(f"ERROR: {OUT_XML} does not exist.", file=sys.stderr)
            return 1
        errs = validate_output(BASE_XML, OUT_XML)
        if errs:
            for e in errs[:50]:
                print(f"VALIDATION ERROR: {e}", file=sys.stderr)
            print(f"Total validation errors: {len(errs)}", file=sys.stderr)
            return 1
        print(f"Validation passed: {OUT_XML} (0 errors)")
        return 0

    base_lines = BASE_XML.read_text(encoding="utf-8").splitlines(keepends=True)
    existing_strings, existing_items = (
        ({}, {}) if args.force else load_existing_translations(OUT_XML)
    )

    if args.apply_glossary and not OUT_XML.exists():
        print(f"ERROR: {OUT_XML} does not exist for --apply-glossary.", file=sys.stderr)
        return 1

    # Collect entries and determine which ones need translation.
    # Each entry: (line_idx, kind, id_or_tuple, prefix, raw_en_val, suffix)
    entries: list[tuple[int, str, object, str, str, str]] = []
    pending_translation: list[tuple[int, str, list[tuple[str, str]]]] = []
    resolved_values: dict[int, str] = {}

    current_array: str | None = None
    array_idx = 0
    reused_count = 0
    glossary_count = 0
    verbatim_count = 0

    for line_idx, line in enumerate(base_lines):
        m_arr = ARRAY_START_RE.match(line)
        if m_arr:
            current_array = m_arr.group(1)
            array_idx = 0
            continue
        if ARRAY_END_RE.match(line):
            current_array = None
            continue

        m_str = STRING_LINE_RE.match(line)
        if m_str:
            prefix, key, val, suffix = m_str.groups()
            entries.append((line_idx, "string", key, prefix, val, suffix))
            if key in GLOSSARY_BY_KEY:
                resolved_values[line_idx] = GLOSSARY_BY_KEY[key]
                glossary_count += 1
            elif not is_translatable_content(val):
                resolved_values[line_idx] = val
                verbatim_count += 1
            elif not args.force and key in existing_strings:
                resolved_values[line_idx] = existing_strings[key]
                reused_count += 1
            elif args.apply_glossary:
                print(f"ERROR: missing key '{key}' in {OUT_XML}", file=sys.stderr)
                return 1
            else:
                protected, tokens = protect_tokens(val)
                pending_translation.append((line_idx, protected, tokens))
            continue

        m_item = ITEM_LINE_RE.match(line)
        if m_item and current_array is not None:
            prefix, val, suffix = m_item.groups()
            item_key = (current_array, array_idx)
            array_idx += 1
            entries.append((line_idx, "item", item_key, prefix, val, suffix))
            if item_key in ARRAY_ITEM_OVERRIDES:
                resolved_values[line_idx] = ARRAY_ITEM_OVERRIDES[item_key]
                glossary_count += 1
            elif not is_translatable_content(val):
                resolved_values[line_idx] = val
                verbatim_count += 1
            elif not args.force and item_key in existing_items:
                resolved_values[line_idx] = existing_items[item_key]
                reused_count += 1
            elif args.apply_glossary:
                print(f"ERROR: missing array item {item_key} in {OUT_XML}", file=sys.stderr)
                return 1
            else:
                protected, tokens = protect_tokens(val)
                pending_translation.append((line_idx, protected, tokens))

    translated_count = 0
    if pending_translation:
        token_map = {line_idx: tokens for line_idx, _, tokens in pending_translation}
        raw_en_map = {e[0]: e[4] for e in entries}
        batch_inputs = [(line_idx, text) for line_idx, text, _ in pending_translation]
        batches = build_batches(batch_inputs)
        print(
            f"Translating {len(pending_translation)} entries in {len(batches)} batches "
            f"(reused={reused_count}, glossary={glossary_count}, verbatim={verbatim_count})..."
        )

        with ThreadPoolExecutor(max_workers=MAX_WORKERS) as pool:
            future_to_batch = {
                pool.submit(translate_batch, [t for _, t in b]): b for b in batches
            }
            for fut in as_completed(future_to_batch):
                batch = future_to_batch[fut]
                results = fut.result()
                for (line_idx, _), ru_protected in zip(batch, results):
                    ru_xml = restore_tokens(ru_protected, token_map[line_idx])
                    # Fix any rare format-specifier loss by falling back to raw English if corrupted.
                    if Counter(FMT_RE.findall(raw_en_map[line_idx])) != Counter(
                        FMT_RE.findall(ru_xml)
                    ):
                        ru_xml = raw_en_map[line_idx]
                    resolved_values[line_idx] = ru_xml
                    translated_count += 1
    else:
        print(
            f"No network translation needed "
            f"(reused={reused_count}, glossary={glossary_count}, verbatim={verbatim_count})."
        )

    out_lines = list(base_lines)
    for line_idx, _, _, prefix, _, suffix in entries:
        out_lines[line_idx] = f"{prefix}{resolved_values[line_idx]}{suffix}"

    OUT_XML.parent.mkdir(parents=True, exist_ok=True)
    OUT_XML.write_text("".join(out_lines), encoding="utf-8")

    errs = validate_output(BASE_XML, OUT_XML)
    if errs:
        for e in errs[:50]:
            print(f"VALIDATION ERROR: {e}", file=sys.stderr)
        print(f"Total validation errors: {len(errs)}", file=sys.stderr)
        return 1

    print(
        f"Wrote {OUT_XML} ({len(entries)} entries: translated={translated_count}, "
        f"reused={reused_count}, glossary={glossary_count}, verbatim={verbatim_count}). "
        "Validation passed (0 errors)."
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
