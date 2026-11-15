# Inicio

Launcher ligero para Fire TV (Fire OS 6 y 7), con tarjetas del tiempo, Home Assistant y fútbol, horóscopo en la
cabecera y apps por categorías.

- **Instalar o actualizar:** desde Inicio, **Personalizar → Buscar actualizaciones**, o descargando
  [`dist/Inicio.apk`](dist/Inicio.apk). La versión publicada está en [`update.json`](update.json).
- **Configuración:** `config.json` en `/sdcard/Android/data/com.inicio.tv/files/` (plantilla en
  `app/src/main/assets/config.json`).
- **Compilar:** `./build.sh` (herramientas de Ubuntu: `aapt`, `dalvik-exchange`, `apksigner`, `zipalign`,
  `android-sdk-platform-23`) o Android Studio. Las actualizaciones deben firmarse con la misma clave, que no está en
  este repositorio. Para publicar una versión: `./build.sh && ./publicar.sh "novedades"`, y subir `dist/` y
  `update.json`.

El botón Home en Fire OS 6 se basa en `FireHomeWatcher` de [cleanyourtv](https://github.com/Gathin23/cleanyourtv)
(licencia MIT, ver `LICENSE-cleanyourtv.txt`). Tipografía Outfit bajo licencia OFL (`app/src/main/assets/fonts/`).
