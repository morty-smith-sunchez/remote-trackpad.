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

Замените `ВАШ_ЛОГИН` на свой GitHub (сейчас в приложении указан **Nolifery**).

## 3. Обновите ссылки в приложении (если логин другой)

Файл: `android_app/app/src/main/res/values/strings.xml` — строки `github_*`.

## 4. APK в Releases (по желанию)

1. GitHub → репозиторий → **Releases** → **Create a new release**  
2. Прикрепите `android_app/app/build/outputs/apk/debug/app-debug.apk`  
3. В приложении кнопка «Скачать APK» откроет страницу релизов
