# PC agent (Windows)

Сервер для режима **USB / Wi‑Fi**: принимает команды с телефона и двигает мышь/клавиатуру на ПК.

## Быстрый старт

1. Установите [Python 3.10+](https://www.python.org/downloads/).
2. Дважды щёлкните **`start_server.bat`** (или в терминале: `python server.py`).
3. На телефоне: режим **USB / Wi‑Fi** → **Подключить**.

## Скрипты

| Файл | Назначение |
|------|------------|
| `start_server.bat` | Запуск сервера в окне (удобно для проверки) |
| `install_autostart.bat` | Добавить автозапуск при входе в Windows |
| `run_hidden.vbs` | Запуск сервера в фоне без окна |
| `uninstall_autostart.bat` | Убрать из автозагрузки |

Порты: WebSocket **8765**, UDP-поиск **8766**.

## Установка зависимостей вручную

```bash
python -m pip install -r requirements.txt
python server.py --host 0.0.0.0 --port 8765
```

Разрешите Python в брандмауэре Windows (частная сеть).
