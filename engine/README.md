# OrcaSlicer engine for Android

Сборка ядра OrcaSlicer (`libslic3r`) под Android arm64 так же, как его собирает
сама Orca: зависимости — по её рецептам, исходники — по её списку, проверка —
её собственными тестами на телефоне. Gradle здесь не участвует.

## Состав

| Путь | Что делает |
| --- | --- |
| `cmake/android-arm64.toolchain.cmake` | Единая цель: NDK 28.2, arm64-v8a, API 29, `c++_static`, `-ffp-contract=off`. |
| `deps/CMakeLists.txt` | Суперсборка зависимостей. Подключает рецепты `upstream/OrcaSlicer/deps/<Name>/<Name>.cmake` без изменений. |
| `deps/cmake/AndroidAutotools.cmake` | Сборка autotools-зависимостей (GMP, MPFR) компиляторами NDK через POSIX shell. |
| `deps/recipes/` | Android-рецепты там, где desktop-рецепт неприменим. |
| `CMakeLists.txt` | Аналог верхнего `CMakeLists.txt` Orca: флаги, поиск пакетов, `deps_src`, `libslic3r`, тесты. |
| `cmake/OrcaSourceLists.cmake` | Читает списки исходников из CMake самой Orca. |
| `CMakePresets.json` | Пресет `android-arm64`: один набор настроек для `engine.ps1` и Gradle. |

Сюда же подключается `slicing/native/src/main/cpp`: JNI-библиотека приложения
`liborcinus_engine.so`, её тест `orca_engine_adapter_tests` и консольная
`orca_engine_slice` для сравнения с desktop OrcaSlicer
(`slicing/native/src/test/cpp`, [`docs/golden-comparison.md`](../docs/golden-comparison.md)). Gradle-задача `:slicing:native:buildOrcaEngine`
собирает `orcinus_engine` через пресет и упаковывает его в APK.

Результаты сборки лежат в `engine/build/` (не в git): `deps-arm64/prefix`
(зависимости), `engine-arm64` (ядро и тесты), `tools/msys64`, `downloads`.

## Команды

Нужны CMake ≥ 3.25 и Ninja в `PATH`, Android SDK с NDK 28.2.13676358.

```powershell
# Зависимости (первый раз ~1 ч, дальше инкрементально) и ядро с тестами
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\engine.ps1 -Stage all -Target libslic3r_tests,fff_print_tests

# JNI-библиотека приложения и тест фасада
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\engine.ps1 -Stage engine -Target orcinus_engine,orca_engine_adapter_tests

# Тесты Orca и фасада на подключённом телефоне
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\engine-test.ps1 -Suite fff_print_tests
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\engine-test.ps1 -Suite libslic3r_tests -Filter '~*coFloatsOrPercents*'
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\engine-test.ps1 -Suite orca_engine_adapter_tests

# Сравнение G-code с официальной desktop OrcaSlicer (нужен Python 3)
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\engine.ps1 -Stage engine -Target orca_engine_slice
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\golden.ps1
```

`engine.ps1` сам скачивает портативный MSYS2 в `engine/build/tools` (архив с
закреплённым SHA256) — в систему ничего не устанавливается. `engine-test.ps1`
копирует `upstream/OrcaSlicer/tests/data` и тестовые бинарники без отладочной
информации в `/data/local/tmp/orca-engine-tests`.

## Отличия от desktop-сборки Orca

| Что | Почему |
| --- | --- |
| zlib из NDK | Стабильный API Android, как системный zlib у Orca на Linux. |
| MD5 из LibreSSL вместо OpenSSL 1.1.1w | libslic3r использует OpenSSL только для MD5; сборка OpenSSL 1.1.1 требует Perl+make с Android-обёртками. Кандидат на возврат к OpenSSL. |
| Не собираются `ObjColorUtils.cpp` (OpenCV) и `SLA/Hollowing.cpp` (OpenVDB) | Цветовой диалог OBJ и SLA-hollowing не входят в FFF-слайсинг на Android. |
| Нет `fontconfig` | На Android OpenCASCADE сам находит системные шрифты. |
| `-ffp-contract=off` | Без него Clang на AArch64 объединяет `a*b+c` в FMA и последние знаки координат расходятся с x86-64. |
| PCH через `target_precompile_headers` | Тот же `pchheader.hpp`, стандартный механизм CMake вместо модуля Orca. |
| `liborcinus_engine.so` линкуется с `-Wl,--exclude-libs,ALL` | Наружу видны только JNI-функции; код, недостижимый из JNI, удаляется, как в исполняемых файлах. Например, импорт SVG, реализацию nanosvg для которого собирает desktop GUI. |

Исходники Orca и её рецепты не изменяются. Если при обновлении Orca исчезнет
исключённый файл или изменится формат списка, конфигурация остановится с
сообщением.

## Известная ошибка в тестах Orca v2.4.2

Сценарий `Placeholder parser coFloatsOrPercents vector access`
(`tests/libslic3r/test_placeholder_parser.cpp:244`) обращается к
`small_perimeter_speed` как к вектору, а в `PrintConfig.cpp:2137` этой версии
опция скалярная. `option<>()` возвращает `nullptr`, тест падает с SIGSEGV на любой
платформе. Поэтому он исключается фильтром.
