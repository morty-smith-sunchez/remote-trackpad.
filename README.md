# Remote Trackpad

Телефон как **удалённый трекпад и клавиатура** для Windows и **пульт** для Android TV.

[![GitHub](https://img.shields.io/badge/GitHub-Nolifery%2Fremote--trackpad-1F6FEB)](https://github.com/Nolifery/remote-trackpad)

## Режимы

| Режим | Нужен сервер на ПК? | Описание |
|-------|---------------------|----------|
| **Bluetooth** | Нет | Телефон как BT-мышь/клавиатура (на Windows бывает нестабильно) |
| **USB / Wi‑Fi** | Да (`pc_agent`) | Стабильно через WebSocket |
| **ТВ** | Нет | Пульт + трекпад для Android TV по Bluetooth |

Подробнее про ТВ: [TV.md](TV.md).

## Структура проекта

```
remote-trackpad/
├── android_app/          # Android-приложение (Kotlin)
├── pc_agent/             # Сервер для Windows (Python)
│   ├── start_server.bat
│   ├── install_autostart.bat
│   └── server.py
├── mobile_web/           # Веб-версия трекпада (опционально)
├── TV.md
└── README.md
```

## Установка APK

Соберите в Android Studio или:

```bash
cd android_app
gradlew.bat assembleDebug
```

APK: `android_app/app/build/outputs/apk/debug/app-debug.apk`

В приложении (режим **USB / Wi‑Fi**) кнопка **«Сервер на ПК (скрипты .bat)»** открывает ссылки на файлы в репозитории GitHub.

## Режим USB / Wi‑Fi (ПК)

1. На Windows: [pc_agent/start_server.bat](pc_agent/start_server.bat) или один раз [install_autostart.bat](pc_agent/install_autostart.bat).
2. Телефон и ПК в одной Wi‑Fi **или** USB-модем.
3. В приложении: **USB / Wi‑Fi** → **Подключить**.

```bash
cd pc_agent
python -m pip install -r requirements.txt
python server.py
```

## Режим Bluetooth

1. Сопрягите телефон с ПК в настройках Bluetooth.
2. В приложении: **Bluetooth** → дождитесь «Готово» → **Подключить**.

## Сборка и публикация на GitHub

```bash
cd remote_trackpad
git init
git add .
git commit -m "Initial release: Remote Trackpad"
git branch -M main
git remote add origin https://github.com/Nolifery/remote-trackpad.git
git push -u origin main
```

Создайте репозиторий `remote-trackpad` на GitHub (пустой), затем выполните команды выше.  
Если имя пользователя другое — измените URL в `android_app/app/src/main/res/values/strings.xml`.

## Лицензия

MIT — см. [LICENSE](LICENSE).
