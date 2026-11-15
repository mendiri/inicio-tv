# Inicio

Launcher ligero para Fire TV (Fire OS 6 y 7), con tarjetas del tiempo, Home Assistant y fútbol, horóscopo en la
cabecera y apps por categorías.

- **Instalar o actualizar:** descarga `Inicio.apk` de la [última versión](https://github.com/mendiri/inicio-tv/releases/latest),
  o desde Inicio: **Personalizar → Buscar actualizaciones**.
- **Configuración:** `config.json` en `/sdcard/Android/data/com.inicio.tv/files/` (plantilla en
  `app/src/main/assets/config.json`).
- **Compilar:** `./build.sh` (herramientas de Ubuntu: `aapt`, `dalvik-exchange`, `apksigner`, `zipalign`,
  `android-sdk-platform-23`) o Android Studio. Las actualizaciones deben firmarse con la misma clave, que no está en
  este repositorio.

El botón Home en Fire OS 6 se basa en `FireHomeWatcher` de [cleanyourtv](https://github.com/Gathin23/cleanyourtv)
(licencia MIT, ver `LICENSE-cleanyourtv.txt`). Tipografía Outfit bajo licencia OFL (`app/src/main/assets/fonts/`).
