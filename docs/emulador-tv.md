# Emulador Android TV en este PC

Preparado el 10 de noviembre de 2026. AVD local **AnimeFlix_TV_API_36**:

- Imagen oficial `system-images;android-36;android-tv;x86_64`.
- Pantalla TV de 1280×720, 4 núcleos virtuales y 3 GB de RAM.
- Android Emulator 37.2.13, con aceleración WHPX confirmada como disponible.
- SDK y emulador en `.tools/android-sdk`; dispositivo virtual en `.tools/avd`. Ambos excluidos de Git.
- Identificador ADB: `emulator-5554`. El móvil USB conserva su identificador separado.
- Teclado físico habilitado en el AVD (`hw.keyboard = yes`, `hw.keyboard.lid = no`). Android configurado con `show_ime_with_hard_keyboard=0` para evitar que el teclado virtual cubra el formulario mientras se utiliza el teclado del PC.

## Abrir

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start-tv-emulator.ps1
```

Para un arranque sin cargar snapshot: añadir `-ColdBoot`. Cerrar la ventana del emulador al terminar; el siguiente arranque podrá reutilizar su estado guardado. El script no inicia otra instancia si este AVD ya está abierto.

## Instalar la versión actual

```powershell
& ./scripts/build.ps1 -Tasks @(':app-tv:assembleDebug')
& ./.tools/android-sdk/platform-tools/adb.exe -s emulator-5554 install -r app-tv/build/outputs/apk/debug/app-tv-debug.apk
& ./.tools/android-sdk/platform-tools/adb.exe -s emulator-5554 shell am start -n com.animeflix.tv/.MainActivity
```

No instalar con un ADB sin selector cuando hay también un teléfono conectado: usar siempre `-s emulator-5554` para estas pruebas.

## Manejo

Usar el mando/cruceta del emulador; también se pueden probar flechas y Enter con el teclado del PC según la captura de teclas de la ventana. El ratón permite seleccionar campos y el teclado físico permite introducir correo y contraseña. El teclado virtual TV tiene una tecla de confirmación. Para Atrás, usar el botón Back de la barra del emulador.

Para enviar eventos de mando desde una terminal:

```powershell
& ./.tools/android-sdk/platform-tools/adb.exe -s emulator-5554 shell input keyevent KEYCODE_DPAD_DOWN
& ./.tools/android-sdk/platform-tools/adb.exe -s emulator-5554 shell input keyevent KEYCODE_DPAD_RIGHT
& ./.tools/android-sdk/platform-tools/adb.exe -s emulator-5554 shell input keyevent KEYCODE_DPAD_CENTER
& ./.tools/android-sdk/platform-tools/adb.exe -s emulator-5554 shell input keyevent KEYCODE_BACK
```

Iniciar sesión con la cuenta ya creada. Para probar registro/recuperación por correo en TV, añadir `com.animeflix.tv://auth-callback` en Redirect URLs de Supabase; el acceso por correo y contraseña a una cuenta existente no necesita ese cambio.

## Estado comprobado

- Instalación de emulador e imagen, creación del AVD y arranque completo (`sys.boot_completed=1`).
- Compilación e instalación correctas de `com.animeflix.tv`, y apertura de la pantalla de acceso.
- No se modificaron componentes de virtualización de Windows ni fue necesario reiniciar el PC.
- Usuario confirmó escritura con el teclado del PC e inicio de sesión correcto.
- Se detectó que el perfil TV original desactivaba el teclado físico. Se corrigió y se verificó el dispositivo de entrada `qwerty2` en Android.
- Navegación comprobada mediante eventos de mando (cruceta y OK): Explorar → Mi lista → Historial → selector de perfiles.
- Comprobado cambio al segundo perfil, carga de una lista de favoritos distinta y preferencia Ocultar al día independiente. Regreso al perfil original y a Explorar. No se añadieron/eliminaron favoritos ni se cambiaron marcas de visto.
- Catálogo e historial muestran portadas; el historial recupera episodios, progreso y marcas de visto de la cuenta. Capturas locales en `screens/tv-favorites.png`, `screens/tv-history.png`, `screens/tv-profiles.png`, `screens/tv-second-profile.png` y `screens/tv-ready.png`, excluidas de Git.
- La reproducción real de servidores, audio y rendimiento debe probarse aparte. El resultado de un emulador no acredita compatibilidad con todas las TV físicas.
- Posteriormente, el usuario observó imagen duplicada y ruido de color al reproducir en el emulador. No se ha determinado si el origen es el emulador, el renderizado, el decodificador o la fuente. Se prepara `v0.9.0-tv-preview.1` para comprobarlo en una TV real; no se considera validada la reproducción TV de esta entrega.
- Corrección 0.9.1: se habilitó temporalmente `show_ime_with_hard_keyboard=1` para reproducir el uso del teclado virtual con mando. Las pruebas `CredentialsFocusTest` verifican Siguiente/Listo y que Atrás permite continuar navegando por los campos y Entrar. La actividad QA no usa credenciales reales ni llama al acceso de Supabase.

Registros del emulador en `.tools/emulator-logs/`. La captura inicial de acceso sin credenciales está en `screens/tv-start.png`. No capturar ni registrar contraseñas o enlaces con tokens.

Referencia oficial: [aceleración del emulador](https://developer.android.com/studio/run/emulator-acceleration).
