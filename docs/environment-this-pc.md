# Entorno de este PC — 10 de noviembre de 2026

Proyecto: `C:\Users\leosa\Documents\ChatGPT\Aninetflix`.

Herramientas locales en `.tools/` (excluidas de Git):

- Temurin JDK 17.0.21+9, descargado desde Adoptium y verificado con su SHA-256.
- Android Command-line Tools, Platform Tools (ADB), Platform 35 y Build Tools 35.0.0.
- Gradle 8.11.1 mediante el Wrapper; caché en `.tools/gradle`.
- `local.properties` apunta al SDK local y está excluido de Git.

No se necesita Java ni Gradle global para los scripts. `scripts/build.ps1` detecta el JDK local cuando JAVA_HOME no está definido.

## Uso

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build.ps1
& ./.tools/android-sdk/platform-tools/adb.exe devices -l
```

Compilación de móvil y TV completada correctamente. APK en `app-mobile/build/outputs/apk/debug/` y `app-tv/build/outputs/apk/debug/`.

Móvil USB autorizado: modelo 23049PCD8G, Android 15. Tiene instalada AnimeFlix 0.8.1 (código 16).

## Firma de la instalación existente

La firma de la app instalada difiere de la clave debug de este PC. Para actualizar esa instalación conservando sus datos, recuperar del otro equipo el archivo `%USERPROFILE%\.android\debug.keystore` que se usó para compilarla y verificar su certificado antes de configurarlo. No añadirlo al repositorio.

Certificado SHA-256 instalado: `c8b021a867741be6933657086dae7ab42c0ab72c7d16d1016ba4b7d0beb08eba`.

La variante `qa` usa `com.animeflix.mobile.qa` y permite pruebas separadas. No se desinstaló ni reemplazó la aplicación principal.

## Verificación

- `:app-mobile:assembleDebug :app-tv:assembleDebug`: correcto.
- `:core:data:testDebugUnitTest`: 8 pruebas, cero fallos.
- `:app-mobile:connectedQaAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.animeflix.mobile.HistoryMigrationTest`: correcto, una prueba en el móvil físico. Confirma instalación y ejecución de instrumentación desde este PC.
- La ejecución conjunta con `CatalogNavigationTest` se quedó bloqueada en la primera prueba de pantalla. Se detuvo QA manualmente; ese intento quedó registrado como fallido/incompleto. Navegación automática pendiente de investigar; no se considera validada.
