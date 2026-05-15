# Публикация на GitHub

Локальный репозиторий уже создан и закоммичен. Осталось создать пустой репозиторий на GitHub и отправить код.

## 1. Создайте репозиторий

1. Откройте https://github.com/new  
2. Имя: **remote-trackpad**  
3. **Public** или Private — на ваш выбор  
4. **Не** добавляйте README, .gitignore и license (они уже в проекте)  
5. Нажмите **Create repository**

## 2. Отправьте код

В PowerShell:

```powershell
cd "c:\Users\Дима\Documents\my projects\remote_trackpad"
git remote set-url origin https://github.com/ВАШ_ЛОГИН/remote-trackpad.git
git push -u origin main
```

В приложении указан логин **morty-smith-sunchez**.

## 3. Обновите ссылки в приложении (если логин другой)

Файл: `android_app/app/src/main/res/values/strings.xml` — строки `github_*`.

## 4. Как залить APK на GitHub (Releases)

APK **не хранят в коде** — его прикрепляют к **Release** (отдельная «версия» для скачивания).

### Шаг 1 — собрать APK на ПК

```powershell
cd "c:\Users\Дима\Documents\my projects\remote_trackpad\android_app"
.\gradlew.bat assembleDebug
```

Готовый файл:

`android_app\app\build\outputs\apk\debug\app-debug.apk`

(для публикации в магазин позже нужен **release**-сборка с подписью; для себя и друзей достаточно debug).

### Шаг 2 — создать Release на сайте GitHub

1. Откройте: https://github.com/morty-smith-sunchez/remote-trackpad./releases  
2. **Create a new release** (или **Draft a new release**).  
3. **Choose a tag:** введите, например, `v0.1.0` → **Create new tag: v0.1.0**.  
4. **Release title:** `v0.1.0` (или «Первая версия»).  
5. Описание — по желанию (что умеет приложение).  
6. В блоке **Attach binaries** перетащите файл **`app-debug.apk`**  
   (можно переименовать в `RemoteTrackpad-v0.1.0.apk` — так понятнее).  
7. **Publish release**.

### Шаг 3 — ссылка для скачивания

После публикации на странице релиза появится ссылка на APK.  
В приложении кнопка **«Скачать APK (релизы)»** откроет список релизов.

### Через командную строку (если установлен GitHub CLI)

```powershell
winget install GitHub.cli
gh auth login
cd "c:\Users\Дима\Documents\my projects\remote_trackpad"
gh release create v0.1.0 "android_app/app/build/outputs/apk/debug/app-debug.apk" --title "v0.1.0" --notes "Первая сборка Remote Trackpad"
```
