# Orcinus Privacy Policy

Effective date: 7 October 2026

Orcinus is a 3D printing slicer that slices on your device. This policy
describes what the app does with data.

## Data we collect

None. Orcinus has no accounts, analytics, advertising, or crash reporting, and
its developers receive no data from the app. The app connects to the network
only for the features below.

## Network connections

- **System profile updates.** When the app starts, and when you select a
  printer of another vendor, Orcinus asks the OrcaSlicer project's server
  (`check-version.orcaslicer.com`, not run by Orcinus) whether that vendor's
  printer profiles have a newer version. The request contains the vendor's
  name (for example "Creality") and the OrcaSlicer version the app is built
  on. When the server names a newer bundle, the app downloads it from the
  address the server gives. Like any server, it sees your IP address. No check
  is made before the Setup Wizard is finished, and none when "Stealth mode" or
  "Update built-in presets automatically." in Preferences → Online says so.
- **Printers and print services you add.** When you send G-code to a printer,
  test its connection, or open the Device tab, the app connects to the address
  you entered, usually on your local network. A cloud print service you add
  yourself (Obico, SimplyPrint, 3DPrinterOS, PrusaConnect) receives the G-code
  you send and your sign-in to it; its own privacy policy applies. Sign-ins are
  kept in the app's private storage.
- **Finding printers.** The Browse button looks for printers on your local
  network; these requests do not leave it.
- **Network test.** When you run "Open Network Test" in Help, the app opens
  `https://github.com/OrcaSlicer/OrcaSlicer` and `http://www.bing.com` to
  check the connection, as OrcaSlicer's own network test does. Nothing else is
  sent.

## Data that stays on your device

- **Models you open.** When you choose a model file, Orcinus copies it into the
  app's private storage so the slicing engine can read it.
- **G-code, projects, presets, and settings.** Sliced G-code, project backups,
  your presets, and the app's settings are saved in the app's private storage.

Other apps cannot read this storage. Uninstalling Orcinus or clearing its data
in the system settings deletes these files. Android's own device backup may
include them, depending on your backup settings; that backup is handled by
your device and account provider, not by Orcinus.

## Permissions

- **Internet and local network** — for the connections above.
- **Wi-Fi multicast** — to find printers on the local network.
- **Notifications** — to show slicing progress, with a cancel button, while a
  slice runs in the background. Slicing works if you decline.
- **Foreground service** — to keep a slice, or an upload to a printer, running
  when you leave the app.

## Children

Orcinus is not directed at children and collects no data from anyone.

## Changes

If this policy changes, the new version will be published at the same address
with a new effective date.

## Contact

Questions about this policy can be asked in the issue tracker of the Orcinus
source repository: https://github.com/ArtLoz/-Orcinus/issues.

---

# Политика конфиденциальности Orcinus

Действует с 7 октября 2026 года

Orcinus — слайсер для 3D-печати, который нарезает модели на устройстве. Здесь
описано, что приложение делает с данными.

## Какие данные мы собираем

Никаких. В Orcinus нет учётных записей, аналитики, рекламы и отчётов о сбоях,
и разработчики не получают от приложения никаких данных. В сеть приложение
выходит только для описанного ниже.

## Сетевые подключения

- **Обновление системных профилей.** При запуске и при выборе принтера другого
  производителя Orcinus спрашивает сервер проекта OrcaSlicer
  (`check-version.orcaslicer.com`, его держит не Orcinus), нет ли более новой
  версии профилей этого производителя. В запросе — название производителя
  (например, «Creality») и версия OrcaSlicer, на которой собрано приложение.
  Если сервер называет новый набор профилей, приложение скачивает его по
  указанному сервером адресу. Как и любой сервер, он видит ваш IP-адрес. До
  завершения мастера настройки проверка не выполняется, а также не выполняется,
  если так задано в «Настройки → В сети»: «Режим конфиденциальности» или
  «Update built-in presets automatically.».
- **Принтеры и сервисы печати, которые вы добавили.** При отправке G-code на
  принтер, проверке подключения и на вкладке «Устройство» приложение
  подключается к адресу, который вы указали, обычно в вашей локальной сети.
  Облачный сервис печати, добавленный вами (Obico, SimplyPrint, 3DPrinterOS,
  PrusaConnect), получает отправленный G-code и ваш вход в него; для него
  действует его собственная политика. Данные входа хранятся в личном хранилище
  приложения.
- **Поиск принтеров.** Кнопка обзора ищет принтеры в локальной сети; эти
  запросы её не покидают.
- **Проверка сети.** Когда вы запускаете проверку сети в разделе «Помощь»,
  приложение открывает `https://github.com/OrcaSlicer/OrcaSlicer` и
  `http://www.bing.com`, чтобы проверить подключение, как это делает проверка
  сети OrcaSlicer. Больше ничего не отправляется.

## Данные, которые остаются на устройстве

- **Открытые модели.** Выбранный файл модели Orcinus копирует в личное
  хранилище приложения, чтобы его прочитало ядро нарезки.
- **G-code, проекты, пресеты и настройки.** Результат нарезки, резервные копии
  проекта, ваши пресеты и настройки приложения хранятся в его личном
  хранилище.

Другие приложения это хранилище прочитать не могут. Удаление Orcinus или
очистка его данных в настройках системы удаляет эти файлы. Системная резервная
копия Android может включать их в зависимости от ваших настроек; её создаёт
устройство и поставщик аккаунта, а не Orcinus.

## Разрешения

- **Интернет и локальная сеть** — для подключений, описанных выше.
- **Многоадресная рассылка Wi-Fi** — чтобы находить принтеры в локальной сети.
- **Уведомления** — чтобы показывать ход нарезки с кнопкой отмены, пока она
  идёт в фоне. Без этого разрешения нарезка тоже работает.
- **Фоновая служба** — чтобы нарезка или отправка на принтер продолжалась,
  когда вы выходите из приложения.

## Дети

Orcinus не предназначен для детей и ни у кого не собирает данные.

## Изменения

Новая версия политики будет опубликована по тому же адресу с новой датой.

## Связь

Вопросы о политике можно задать в трекере задач репозитория Orcinus:
https://github.com/ArtLoz/-Orcinus/issues.
