# Рабочий план: Android-порт OrcaSlicer

Актуально на 16 сентября 2026, OrcaSlicer **v2.4.2**
(`8500fcdccaa10b5099ac20d252af3a7c560046f1`).

## 1. Цель

На Android-устройстве запускать оригинальный FFF-конвейер OrcaSlicer:
STL + профили принтера/пластика/процесса → `Print::apply()` → `process()` →
`export_gcode()`. Первый набор профилей: Creality K2 Plus 0.4, Generic PLA @K2
Plus-all, 0.20mm Standard @Creality K2 Plus 0.4. Обновление Orca = обновить
submodule, пересобрать, прогнать проверки; без ручного переноса кода.

## 2. Уровни проверки

| Уровень | Что доказано |
| --- | --- |
| C — компиляция | Код собирается NDK под arm64. Ничего не говорит о работе. |
| R — исполнение | Код выполняется на телефоне и проходит проверки. |
| G — эталон | Результат совпадает с desktop Orca того же commit. |

В отчётах всегда называем уровень. «Собралось» ≠ «работает».

## 3. Состояние

| Что | Уровень | Подтверждение |
| --- | --- | --- |
| Зависимости Orca под Android (17 шт.) | C | `engine/deps`, рецепты Orca без изменений. |
| Всё ядро `libslic3r` (201 из 203 файлов, 2 исключены) + `libslic3r_cgal` | C | `engine/CMakeLists.txt`, список исходников из CMake Orca. |
| Тесты Orca `fff_print_tests` на Pixel 8 Pro | **R** | 37/37 тестовых случаев, 565 проверок: `Print` → периметры, заполнение, поддержки, юбка/кайма, `export_gcode`. |
| Тесты Orca `libslic3r_tests` на Pixel 8 Pro | **R** | 114 тестовых случаев, 48 537 проверок: конфиг, `PresetBundle`, 3MF, STL, Arachne, Voronoi, MeshBoolean, placeholder parser. 1 сценарий исключён (ошибка теста в Orca, см. `engine/README.md`). |
| Фасад приложения над ядром (`orca_engine_adapter`) на Pixel 8 Pro | **R** | `orca_engine_adapter_tests`: профили K2 Plus через `PresetBundle`, куб 20 мм → 100 слоёв, STL из тестовых данных Orca, неизвестный профиль, отсутствующий файл, отмена. |
| APK со встроенным ядром Orca | **R** | `liborcinus_engine.so` 13,9 МБ, 1566 файлов профилей. На Pixel 8 Pro через интерфейс: куб 20 мм и выбранный STL → 100 слоёв, 12 мин, 1,21 м; сфера 120 мм (638 400 треугольников) → 600 слоёв, 3 ч 54 мин, 24 с, пик памяти ~394 МБ; отмена посреди нарезки без `.part` и падений. |
| Ядро в отдельном процессе `:slicer` | **R** | Библиотека Orca загружается только в `:slicer`. Сфера 120 мм нарезана со свёрнутым приложением (foreground-сервис `specialUse`, уведомление с прогрессом); отмена из уведомления; `SIGSEGV` процессу ядра посреди нарезки — интерфейс жив, показывает ошибку, следующая нарезка проходит. |
| Релизная сборка (R8, подпись, AAB) | **R** | На Pixel 8 Pro релизный APK (18,2 МБ): куб 20 мм → 100 слоёв, 12 мин, 1,21 м; «О программе», список из 111 компонентов, тексты лицензий, возврат назад. Нативные библиотеки выровнены под страницы 16 КБ (`LOAD` 0x4000, `zipalign -P 16`). Проверка нашла, что R8 переименовывал `onProgress`, который JNI ищет по имени, — исправлено в `consumer-rules.pro`. |
| Сравнение с desktop Orca | **G** (с допусками) | `scripts/golden.ps1`: 10 моделей из тестов Orca, телефон против официальной OrcaSlicer 2.4.2 для Windows. Настройки совпадают, слои и типы путей одинаковые, выдавливание по слою расходится не больше 0,24 %, филамент — 0,022 %, время — 2 с. Побайтно не совпадает из-за математической библиотеки Windows/Android, см. [`golden-comparison.md`](golden-comparison.md). |

## 4. Правила

1. `upstream/OrcaSlicer` — submodule, не редактируется. Версия дублируется в
   `upstream/orca.lock.json`.
2. Зависимости берутся из рецептов `upstream/OrcaSlicer/deps`. Android-рецепт
   пишется только там, где desktop-рецепт неприменим, и повторяет его флаги.
3. Исходники берутся из CMake Orca. Исключение файла — только с причиной в
   `engine/CMakeLists.txt` и в таблице отличий `engine/README.md`.
4. Никаких заглушек и переписанных копий кода Orca. Если чего-то не хватает для
   линковки — собираем настоящую зависимость.
5. Проверка ядра — тесты самой Orca на устройстве. Своя проверка добавляется
   только там, где у Orca теста нет.
6. Kotlin-архитектура: `:feature` → `:domain` → `:slicing:api`/`:storage:api` →
   адаптеры. Типы Orca, `JNIEnv`, `Uri` не пересекают границ портов.

## 5. Этапы

### Этап 1 — ядро на устройстве (выполнен)

Зависимости и `libslic3r` собираются по правилам Orca, её тесты проходят на
телефоне.

### Этап 2 — ядро в приложении (выполнен)

Сделано:

1. `slicing/native` больше не собирает Orca выборочно: `liborcinus_engine.so`
   собирается в `engine/` тем же тулчейном, что и `libslic3r`; Gradle вызывает
   `engine/CMakePresets.json` и упаковывает результат. Старая сборка Codex,
   compat-заглушки, переписанные копии кода Orca, самописный генератор G-code и
   self-test на старте удалены.
2. Профили загружаются `PresetBundle` Orca из бандлов Creality и
   OrcaFilamentLibrary; выбор — как у desktop (`AppConfig` + `load_selections` +
   `full_config`).
3. Фасад: STL или куб → `Model` → `Print::apply/validate/process/export_gcode`,
   прогресс Orca, отмена через `Print::cancel()`, запись через `.part`.
4. Приложение: движок живёт в `Application`, инициализация вне главного потока,
   кнопки «Нарезать» для STL и «Нарезать куб 20 мм», прогресс с текстом Orca.
5. Нарезка через интерфейс проверена на телефоне: куб, выбранный через системный
   диалог STL, тяжёлая модель, отмена.
6. Ядро работает в отдельном процессе `:slicer` (`:slicing:service`,
   AIDL). Во время нарезки сервис — foreground `specialUse` с уведомлением
   (прогресс, «Отменить»), нарезка продолжается в фоне. Падение ядра приходит в
   интерфейс как `ENGINE_CRASHED`, новый процесс ядра поднимается сразу.

Критерий этапа: импортированный STL на телефоне даёт G-code Orca с прогрессом и
отменой, а падение ядра не закрывает приложение.

### Этап 3 — эталонное сравнение (выполнен)

1. Эталон: официальная OrcaSlicer 2.4.2 для Windows (тег = закреплённый commit,
   SHA256 архива закреплён), нарезка через её командную строку.
2. Телефон: `orca_engine_slice` поверх того же фасада, что в APK.
3. 10 моделей из `upstream/OrcaSlicer/tests/data`: куб, куб с отверстием,
   наклонное отверстие, нависание, мост, V, A, Prusa, деталь экструдера,
   frog_legs.
4. Критерии: совпадение настроек, слоёв (Z, высота, типы путей), выдавливание
   слоя ±1 %, филамент ±0,1 %, время ±1 %. Все 10 моделей проходят.
5. По ходу найдено и исправлено: фасад размещал модель не теми шагами, что
   desktop GUI, а импорт называл объект `selected-model.stl` вместо имени
   файла.

Подробности и причины расхождений — [`golden-comparison.md`](golden-comparison.md).
Не покрыто: поддержки, другие профили, поведение GUI сверх нарезки.

### Этап 4 — продукт (идёт)

Сделано:

1. Архитектура интерфейса: каждый экран — отдельный feature-модуль
   (`:feature:prepare`, `:feature:preview`), навигация Navigation 3 в оболочке
   `:app`, общее состояние стола в `:data:plate` через use case'ы `:domain`
   (подробно — [`architecture.md`](architecture.md)).
2. Полная тема OrcaSlicer в `:core:designsystem`: цвета светлые и тёмные из
   таблицы Orca, шкала шрифтов Orca на Inter (урезанном
   `scripts/subset-fonts.ps1`; HarmonyOS Sans из Orca нельзя изменять и
   выпускать под свободной лицензией),
   формы, компоненты с превью, иконки Orca со светлыми и тёмными вариантами.
   Раскладка адаптивная, холст на весь экран под системными панелями: от 600 dp
   боковая панель под вкладками рядом с холстом и сворачивается, на телефоне это
   нативная выдвижная панель поверх всего экрана: кнопка сворачивания Orca или
   свайп. Информация о нарезке —
   панелью внизу, как блок Sliced Info.
3. Подготовка к открытому коду и публикации: имя Orcinus, пакет
   `app.orcinus.shadow`, лицензия AGPL-3.0 в `LICENSE`, страница «О программе»
   (`:feature:about`) с юридическими уведомлениями AGPL, ссылкой на исходный код
   и текстами лицензий всех 111 компонентов (Maven-библиотеки собирает плагин
   AboutLibraries, нативные — `scripts/notices/update_notices.py` из их
   исходников), новая иконка, релизная сборка с R8, подписью из
   `keystore.properties` и проверкой ссылки на исходники, тексты и графика для
   магазина в `fastlane/`. Порядок публикации —
   [`publishing.md`](publishing.md), политика конфиденциальности —
   [`privacy-policy.md`](privacy-policy.md).

Дальше:

1. 3D на «Подготовке»: стол из профиля принтера, модель после расстановки Orca,
   жесты камеры.
2. «Просмотр нарезки» на `libvgcode`: линии G-code, ползунки слоёв и движений,
   легенда.
3. Каталог и выбор профилей, редактирование настроек, 3MF, отправка на
   принтер, STEP (OpenCASCADE уже собран), другие ABI.

## 6. Окружение

- Android Studio (JDK из неё), Android SDK, NDK 28.2.13676358.
- CMake ≥ 3.25 и Ninja в `PATH`: их использует `engine/`, и через него Gradle собирает JNI-библиотеку.
- MSYS2 для GMP/MPFR скачивается скриптом в `engine/build/tools` автоматически.
- Телефон arm64 с включённой отладкой по USB/Wi-Fi.

Переменные для Gradle на этой машине:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
$env:GRADLE_USER_HOME = "$PWD\.gradle-local"
$env:ANDROID_USER_HOME = "$PWD\.android-user"
```

## 7. Команды

```powershell
# Проверка закреплённой Orca
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-upstream.ps1

# Ядро: зависимости + libslic3r + тесты Orca
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\engine.ps1 -Stage all -Target libslic3r_tests,fff_print_tests

# Только ядро после правок engine/CMakeLists.txt
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\engine.ps1 -Stage engine -Target libslic3r

# Тесты Orca на телефоне (можно Catch2-фильтр: -Filter '[GCode]')
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\engine-test.ps1 -Suite fff_print_tests
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\engine-test.ps1 -Suite libslic3r_tests -Filter '~*coFloatsOrPercents*'

# Фасад приложения на телефоне (скрипт кладёт туда профили Creality)
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\engine.ps1 -Stage engine -Target orca_engine_adapter_tests
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\engine-test.ps1 -Suite orca_engine_adapter_tests

# Сравнение с desktop OrcaSlicer (нужен Python 3)
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\engine.ps1 -Stage engine -Target orca_engine_slice
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\golden.ps1

# Приложение: Gradle сам собирает liborcinus_engine.so через engine/CMakePresets.json,
# зависимости engine/deps должны быть собраны заранее
.\gradlew.bat :domain:test :data:notices:test :feature:about:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon

# Уведомления о лицензиях нативных библиотек и шрифта (после сборки engine/deps и шрифта)
python scripts/notices/update_notices.py

# Релиз для Google Play (подпись и адрес исходников — в publishing.md)
.\gradlew.bat :app:bundleRelease
```

Сборка первой копии зависимостей занимает около часа, дальше всё
инкрементально. Логи удобно писать в `engine/build/*.log`.

## 8. Какие проверки запускать

| Что изменили | Минимум |
| --- | --- |
| `engine/deps` | `engine.ps1 -Stage all`, оба набора тестов на устройстве, `update_notices.py`. |
| `engine/CMakeLists.txt`, toolchain | `engine.ps1 -Stage engine`, оба набора тестов. |
| Kotlin: domain, API, UI | `:domain:test :app:assembleDebug :app:lintDebug`. |
| `slicing/service` | Сборка APK; на телефоне: нарезка со свёрнутым приложением, отмена из уведомления, `run-as <пакет> kill -SEGV <pid :slicer>` посреди нарезки дважды подряд, затем новая нарезка. |
| `slicing/native/src/main/cpp` | `orca_engine_adapter_tests` на устройстве, `golden.ps1`, сборка APK, запуск, `logcat`, сценарий в интерфейсе. |
| Submodule Orca | `verify-upstream.ps1`, всё выше, затем `golden.ps1` (сначала обновить в нём эталонный релиз) и `update_notices.py`. |
| Зависимости Gradle | Сборка APK; на странице «О программе» → «Сторонние компоненты» у новых библиотек есть текст лицензии. |

## 9. Обновление OrcaSlicer

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\update-orca.ps1 -Ref <тег-или-commit>
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\engine.ps1 -Stage all -Target libslic3r_tests,fff_print_tests
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\engine-test.ps1 -Suite fff_print_tests
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\engine-test.ps1 -Suite libslic3r_tests
# после замены эталонного релиза в scripts/golden.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\engine.ps1 -Stage engine -Target orca_engine_slice
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\golden.ps1
```

Новые версии зависимостей и новые файлы ядра подхватываются автоматически.
Вручную проверяются только сообщения конфигурации (исчезнувшее исключение,
изменившийся формат рецепта) и Android-рецепты в `engine/deps/recipes`.

## 10. Частые проблемы

| Симптом | Причина / что делать |
| --- | --- |
| `corrupt patch at line N` у патча Orca | Checkout на Windows с `core.autocrlf` хранит патчи в CRLF. Autotools-рецепты уже переводят патч в LF. |
| `archive member ... is neither ET_REL nor LLVM bitcode` | Объекты, обнулённые при оборванной сборке. Найти `.o` без заголовка ELF, удалить, удалить stamp-файлы `build/install/done` зависимости, пересобрать. |
| `detected dubious ownership` у git | `.git` создан пользователем песочницы Codex; команды запускать с `-c safe.directory=<путь>` или сменить владельца каталога. |
| Зеркало MSYS2 отвечает таймаутом | `engine.ps1` повторяет установку пакетов до трёх раз; при повторе просто перезапустить. |
| Бинарник тестов ~900 МБ | Отладочная информация; `engine-test.ps1` отправляет на телефон копию без неё (~20 МБ). |
