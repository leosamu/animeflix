# Comprobación del entorno — 8 de noviembre de 2026

- Carpeta real: `C:\Users\Leo\Documents\ChatGPT\Aninetflix\AnimeFlix`.
- Git verificado: `2.56.0.windows.4`, proporcionado por el entorno Codex. Repositorio inicializado con rama `main`; sin remoto ni identidad inventada.
- Al inicio no había `java`, `javac`, `gradle`, `adb` ni `sdkmanager` en PATH, ni JDK/SDK declarados mediante JAVA_HOME o ANDROID_HOME. No se encontró Android Studio en su ruta habitual ni SDK en `%LOCALAPPDATA%\Android\Sdk`.
- Se solicitaron mediante winget Temurin JDK 17.0.21.9 y Android Studio 2026.2.2.8. Ver el estado final al pie de este archivo.
- Wrapper oficial de Gradle 8.11.1 descargado de `gradle/gradle`, etiqueta `v8.11.1`. SHA-256 del JAR: `2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.
- Primera ejecución de `gradlew.bat --version`: falló porque no había Java disponible. No se generaron APK ni se validó la aplicación en un dispositivo.
- No se crearon claves ni proyectos de Supabase y no se configuró ningún acceso remoto.

## Intervención posible

Los instaladores del sistema pueden requerir confirmación de Windows. Después de completarlos, abrir una terminal nueva, ejecutar las verificaciones del README y completar SDK Manager/licencias e imágenes de emulador. La presencia de Android Studio por sí sola no acredita la instalación del SDK.

## Estado final observado

La instalación de JDK está esperando una confirmación UAC de Windows: proceso `consent`, título «Eclipse Foundation está solicitando su permiso». Se observaron también `msiexec` y dos procesos `winget`. No se confirmó instalación final de JDK ni Android Studio. Hay que atender ese diálogo localmente; no se ha aceptado ni eludido desde el agente. Android Studio sigue sin resultado final del instalador. Las descargas/inicios de instalación no equivalen a herramientas instaladas.

No se creó un commit porque no había identidad Git configurada. Los archivos están presentes y el repositorio inicializado. No se pudo recuperar el hilo anterior con las herramientas disponibles; esta base sigue los requisitos explícitos de la petición actual.

## Segunda comprobación

- JDK confirmado mediante `java -version` y `javac -version`: Temurin 17.0.21+9, en `C:\Program Files\Eclipse Adoptium\jdk-17.0.21.9-hotspot`.
- Android Studio presente con build `AI-262.10968.63.2622.16730706`.
- Gradle Wrapper ejecutado correctamente: Gradle 8.11.1 con JVM 17.0.21.
- SDK local instalado en `.tools/android-sdk`: Platform 35, Build Tools 35.0.0 y Platform Tools. `android.jar` y `aapt2.exe` comprobados; `adb version` devuelve 1.0.41 / 37.0.1.
- Command-line Tools descargadas de Google, archivo `commandlinetools-win-15859902_latest.zip`; SHA-256 `90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a`, coincidente con la página oficial. Licencias aceptadas durante la instalación autorizada.
- `local.properties` configura el SDK local y está excluido de Git. `scripts/build.ps1` detecta JAVA_HOME del sistema y reutiliza la caché Gradle del proyecto.
- Emuladores e imágenes de sistema todavía no instalados; no hay prueba de ejecución en móvil o TV.

## Compilación verificada

`gradlew.bat :app-mobile:assembleDebug :app-tv:assembleDebug :app-mobile:lintDebug :app-tv:lintDebug --console=plain --info`: **BUILD SUCCESSFUL**, 133 tareas ejecutadas, 5 min 50 s.

- APK móvil: `app-mobile/build/outputs/apk/debug/app-mobile-debug.apk`, 9 598 127 bytes.
- APK TV: `app-tv/build/outputs/apk/debug/app-tv-debug.apk`, 8 189 029 bytes.
- Esquema Room generado: `core/data/schemas/com.animeflix.data.AnimeDatabase/1.json`.
- Lint móvil: 0 errores, 4 avisos. Lint TV: 0 errores, 6 avisos. Avisos: nuevas versiones de dependencias disponibles, reglas de extracción de copias de seguridad en Android 12+, tamaño vectorial del banner TV. No se han ocultado avisos ni actualizado dependencias fuera de esta base fijada.
- Informes completos: `app-mobile/build/reports/lint-results-debug.html` y `app-tv/build/reports/lint-results-debug.html`.
- Comprobación de configuración `gradlew.bat projects` también superada.

Esto verifica compilación y análisis estático, no comportamiento de la interfaz ni persistencia en un dispositivo.

ADB iniciado correctamente y `adb devices` ejecutado: lista vacía. No hay dispositivos autorizados conectados. Para probar, conectar un dispositivo con depuración activada y autorizarlo, o crear un emulador móvil/TV en Android Studio.

## Prueba posterior en dispositivo físico

Tras cambiar el cable USB, ADB detectó y autorizó el dispositivo modelo `23049PCD8G` (`marble_global`). Instalación de `app-mobile-debug.apk` mediante `adb install -r`: `Success`. Apertura de `com.animeflix.mobile/.MainActivity` mediante `am start -W`: `Status: ok`, arranque en frío en 1118 ms. Proceso de la aplicación presente tres segundos después; la consulta posterior de AndroidRuntime no mostró errores. Esto confirma instalación y arranque; no verifica todavía interacción, persistencia de favoritos ni ejecución en Android TV.

## Móvil 0.2 y catálogo AnimeAV1

Se añadieron navegación Explorar/Mi lista, fichas, búsqueda, ViewModel con estado conservado mediante SavedStateHandle, carga/error/reintento y diseño oscuro adaptable. Fuente seleccionada por el usuario: `https://animeav1.com/`.

El adaptador consulta HTML público paginado del catálogo, sin ejecutar JavaScript ni resolver reproductores. Portadas con Coil; metadatos guardados en Room v2 con migración explícita desde v1. Las actualizaciones de metadatos no reemplazan favoritos. Búsqueda remota con pausa de 600 ms y carga de páginas adicionales. La disponibilidad depende de la web; no se ha encontrado documentación de API pública.

Validación final:

- `:app-mobile:assembleDebug :app-mobile:lintDebug :app-mobile:connectedQaAndroidTest`: **BUILD SUCCESSFUL** (27 s), 153 tareas, 3 pruebas ejecutadas, 0 fallos/errores/omitidas.
- Pruebas: búsqueda/sin resultados y recreación de actividad; ficha/recreación/regreso/Mi lista; actualización de metadatos conservando favorito.
- Lint móvil: 0 errores y 8 avisos; consultar informe HTML para el detalle.
- `:app-tv:assembleDebug` superado tras los cambios del modelo y la base compartida. No se ha instalado ni probado en TV.
- Actualización principal instalada con `adb install -r`: Success. Arranque: Status ok, 1171 ms. No aparecieron errores AndroidRuntime en la consulta posterior.
- Captura real de la aplicación examinada: catálogo AnimeAV1 con títulos y portadas descargadas, buscador, ficha mediante tarjetas y botón Cargar más. Captura local excluida de Git: `.tools/av1-mobile.png`.

Durante las primeras pruebas instrumentadas, la herramienta desinstaló la aplicación principal al terminar. Se reinstaló; los favoritos previos pueden haberse perdido. Se informó al usuario y se corrigió el aislamiento: las pruebas posteriores usan `com.animeflix.mobile.qa`, datos ficticios y `connectedQaAndroidTest`. El primer envío QA fue rechazado por el móvil; tras confirmación del usuario se reenvió y las 3 pruebas pasaron.

Pendiente: episodios/reproducción interna, autenticación/Supabase, pruebas de funcionamiento sin red y de migraciones con datos históricos más amplios. Las pruebas y ajustes de Android TV se posponen hasta estabilizar móvil, según indicación del usuario.

## Episodios y reproducción móvil — 0.3.3

Se implementó lectura de episodios publicados, orden numérico, grupos de 50, estados de carga/error/vacío y pantalla de reproducción con selección de idioma/servidor. Actividad interna no exportada, WebView sin interfaces nativas ni acceso a archivos, bloqueo de ventanas emergentes y descargas, ciclo de vida y pantalla completa del proveedor.

Diagnóstico y validación:

- Tres pruebas JVM del lector de episodios/servidores superadas: orden y deduplicación, separación de descargas e idiomas, validación HTTPS y fallback de iframe.
- Se detectó una diferencia entre Java del ordenador y Android: una llave de cierre sin escapar en una expresión regular provocaba `PatternSyntaxException` al leer servidores. Se corrigió y se añadió `RemoteEpisodeSmokeTest` contra una página real desde el dispositivo.
- Cuatro pruebas QA superadas en Android 15: las tres pruebas anteriores y la nueva prueba real de episodios/servidores (0 fallos, errores u omitidas). No equivalen a reproducción efectiva de vídeo.
- Durante una compilación interrumpida por rechazo de la instalación QA, quedó un APK debug antiguo. Se detectó mediante fechas del APK y versión instalada, se informó al usuario y se corrigió la instalación. `scripts/install-mobile.ps1` ahora exige compilación correcta y compara la versión instalada con los metadatos del APK.
- El usuario confirmó la carga de episodios y, tras instalar 0.3.1, la aparición de servidores. La reproducción directa seguía fallando. El proveedor HLS emitió «Content not found» en consola; otros reproductores mostraron pantalla negra.
- 0.3.2 corrigió la integración directa para usar iframe. El usuario confirmó que aún no reproducía, mientras que la web original sí lo hacía.
- 0.3.3 añade por defecto `Reproductor de la web`: la página original completa se carga dentro de la aplicación, conservando su contexto. `Elegir servidor` mantiene la alternativa de iframe directo. No hay extracción de archivos de vídeo ni elusión de bloqueos.
- Compilación y lint 0.3.3 superados. Instalación 0.3.3 (versionCode 6) verificada por el script y arranque correcto.
- Se añadió `OriginalEpisodeWebViewTest` para comprobar la carga del iframe original en QA. El primer intento de ejecutarlo fue bloqueado por el móvil con `INSTALL_FAILED_USER_RESTRICTED`; esta prueba nueva no se considera superada.

El usuario confirmó reproducción efectiva dentro de AnimeFlix 0.3.3 usando `Reproductor de la web`. Ese es el modo validado y predeterminado; la reproducción mediante iframes directos no queda validada. Permanecen pendientes posición de reproducción, vistos/historial, autenticación/Supabase y ajustes TV. No se ha borrado ni modificado la instalación principal durante las pruebas QA de esta fase.

## Historial y vistos — 0.4.0

Se añadieron la pestaña Historial, apertura por fecha, retorno al último episodio abierto desde la ficha y marcas manuales de visto desde ficha, historial y reproductor. El historial sigue las navegaciones a episodios reconocidos dentro de la página original. No se confunde abrir con reproducir o finalizar.

Room v3 incorpora `watch_history` con migración v2→v3, conservando v1→v2. Apertura y visto se actualizan de forma independiente en transacciones, sin reemplazar el otro estado.

Validación del 8 de noviembre de 2026:

- Compilación debug, lint y las tres pruebas JVM del lector: superados. Lint: 0 errores, 15 avisos.
- Cinco pruebas instrumentadas locales QA: **0 fallos, errores u omitidas**, 7,608 s de pruebas; Gradle **BUILD SUCCESSFUL** en 29 s.
- Incluyen búsqueda, ficha/recreación, metadatos/favoritos, migración real de base v2 a v3 con favorito conservado, reapertura de la base con historial/vistos persistentes y pantalla Historial tras recreación de actividad.
- Las dos pruebas de red/WebView se excluyeron de esta ejecución para comprobar la persistencia local sin depender de la web. No se afirma una nueva validación de reproducción para esta versión.
- Instalación principal mediante `scripts/install-mobile.ps1`: **0.4.0 (versionCode 7)** verificada con Android. Arranque correcto en 875 ms, proceso vivo y sin errores AndroidRuntime en la consulta posterior. Instalación con `-r`; no se han borrado datos de la aplicación principal.

La vuelta al episodio está implementada. La vuelta al minuto exacto sigue pendiente: el vídeo reproducible vive en un iframe de otro origen sin interfaz de progreso documentada. No se ha fingido esa capacidad ni eludido el aislamiento del navegador. Permanecen pendientes Supabase/cuentas, sincronización entre dispositivos, CI/distribución y pruebas/ajustes de TV.

## Progreso compatible y marcado de anteriores — 0.5.0

Se añadió marcado masivo de episodios publicados anteriores al seleccionado, en orden numérico y dentro del mismo anime. Incluye episodios de otras páginas y especiales decimales; no incluye el seleccionado ni los posteriores. Confirmación con recuento de nuevas marcas y transacción que conserva fechas de apertura y progreso. No crea aperturas falsas para episodios marcados en bloque.

Para la posición de reproducción se integró AndroidX WebKit 1.12.1: `addDocumentStartJavaScript` ejecuta un observador dentro de los documentos de los orígenes permitidos; `addWebMessageListener` recibe únicamente tiempo y duración. Orígenes exactos: Zilla, MP4Upload y AnimeAV1 UPNShare, sin comodines. No se desactiva el aislamiento de orígenes ni se accede al DOM del iframe desde su padre. El canal no expone navegación, archivos ni operaciones privilegiadas. Mensajes limitados y validados, asociación por episodio/documento/origen y rechazo de duraciones fuera de rango. Compatibilidad comprobada en tiempo de ejecución antes de usar las funciones WebView.

Room v4 añade `positionMs` y `durationMs`, conservando rutas de migración desde las versiones anteriores. Los tiempos se guardan aproximadamente cada cinco segundos y al pausar. La restauración espera a los metadatos, respeta el gesto de reproducción y solo solicita el salto si la duración coincide y el minuto guardado no está al final. En proveedores no compatibles no se inventa progreso.

Verificación:

- Compilación debug y lint superados; tres pruebas JVM del lector superadas.
- Siete pruebas QA locales: **0 fallos, errores u omitidas**, 8,839 s de pruebas, Gradle **BUILD SUCCESSFUL** en 33 s. El primer envío QA fue rechazado antes de iniciar pruebas; tras confirmación del usuario se reenvió y pasó.
- La prueba de migración comprueba conservación de favorito, apertura, marca y progreso tras reabrir la base.
- `PreviousEpisodesTest`: marca 52 episodios reales anteriores al 52 (incluido el 2.5), conserva progreso/fecha del primero y deja intactos el 52, el 53 y otro anime.
- `WebPlaybackProgressTest`: con vídeo simulado y documento HTTPS interceptado localmente, restaura 30 segundos y recibe ese progreso por el canal nativo en el WebView real del dispositivo. No depende de servidores remotos y no acredita todavía el comportamiento con vídeo real de cada proveedor.
- Instalación principal 0.5.0 (versionCode 8) verificada con el script; arranque correcto en 1210 ms, sin borrado de datos.

El usuario confirmó en su móvil que aparece el minuto guardado y que el episodio vuelve a ese punto al salir y retomar, con vídeo real en «Reproductor de la web» de 0.5.0. La confirmación corresponde al reproductor utilizado en esta prueba; no acredita todos los servidores ni Android TV. Android TV continúa aplazado.

## Android TV — 0.5.0-tv-preview1

A petición del usuario se generó un APK instalable para un dispositivo separado, sin conexión del TV al entorno. Se sustituyó la demostración inicial por catálogo público, búsqueda, favoritos, fichas, episodios por grupos, historial, vistos, marcado de anteriores y acceso al reproductor.

El reproductor, el observador de progreso y los ViewModels de historial/episodios se trasladaron a `core/playback`, compartido con móvil. Los import de las pruebas móviles se actualizaron. La interfaz TV usa componentes Compose for TV enfocables, foco inicial y navegación Atrás. El reproductor incluye un puntero controlado con cruceta/OK que despacha gestos solo dentro de su propio WebView; Atrás devuelve el foco a los controles. Estas interacciones están implementadas pero todavía no verificadas en TV físico.

Validación realizada:

- `:app-tv:assembleDebug :app-tv:lintDebug :app-mobile:compileQaAndroidTestKotlin :app-mobile:assembleDebug :app-mobile:lintDebug`: **BUILD SUCCESSFUL**, 197 tareas, 12 s.
- Las tres pruebas JVM de datos/lector también pasaron durante la compilación previa de ambos módulos.
- Lint TV: 0 errores y 8 avisos. Informe: `app-tv/build/reports/lint-results-debug.html`.
- `apksigner verify --verbose --print-certs`: verifica firma v1/v2, certificado Android Debug. No se ha creado firma de producción.
- APK inspeccionado con aapt: paquete `com.animeflix.tv`, versionCode 8, versión `0.5.0-tv-preview1`, SDK mínimo 23 y target 35; actividad Leanback, pantalla táctil no requerida y arquitecturas arm64-v8a, armeabi-v7a, x86 y x86_64.
- Entrega: `dist/AnimeFlix-TV-0.5.0-preview1.apk`, 11 294 455 bytes. SHA-256 en `dist/SHA256SUMS.txt`, instrucciones en `dist/LEEME-ANDROID-TV.md` y copia versionada en `docs/android-tv-pruebas.md`.

No se ha instalado en TV físico ni ejecutado en emulador TV. Tampoco se ha actualizado la instalación móvil del usuario en esta fase. Las pruebas instrumentadas móviles se han compilado, no ejecutado de nuevo. El usuario realizará las pruebas de la guía, especialmente foco del mando, teclado, puntero, vídeo, pantalla completa, regreso al minuto y segundo plano. La sincronización móvil/TV continúa sin implementar.

## Corrección del reproductor TV — preview 2

La fotografía enviada por el usuario muestra que los controles nativos consumían casi toda la altura disponible y el WebView quedaba reducido a una franja. Se añadió una composición específica para TV: WebView a pantalla completa, controles en panel superpuesto mediante Atrás/Menú y retorno al puntero al ocultar el panel. El iframe publicado se dimensiona al viewport manteniendo su fuente y contexto; el panel permite recuperar la página completa. El WebView deja de aceptar foco mientras el panel está abierto. La rama móvil mantiene su interfaz.

APK `0.5.0-tv-preview2`, versionCode 9, 11 330 464 bytes. Compilación móvil/TV, lint, compilación de pruebas QA y pruebas JVM superadas en la comprobación final (22 s, 203 tareas). La prueba local `node scripts/test-tv-video-layout.cjs` verifica expansión, restauración de estilos, iframe insertado dinámicamente y conservación de su URL. Firma de desarrollo verificada y conservada para actualizar sobre preview 1. Pendiente de nueva validación física en la TV del usuario.

## Cambio a reproducción nativa en móvil — 0.6.1

El usuario comunicó que la corrección web de TV seguía sin permitir ver el reproductor y solicitó conseguir primero reproducción por servidor en móvil, conectando el dispositivo. ADB confirmó el móvil autorizado.

Se implementó `Mp4UploadResolver`, que lee la configuración MP4 pública del embed y valida HTTPS, dominio, puerto y extensión. Se integró Media3 1.6.1 como reproductor nativo de móvil con controles, foco de audio, errores/reintento, guardado de progreso y liberación al salir. No se han eludido desafíos, DRM ni rechazos HTTP. La consulta HLS respondió 403 en el entorno de inspección; el resolver Voe no se implementó. UPNShare y Byse siguen pendientes de reproducción nativa.

Validaciones:

- Pruebas JVM del lector y del resolver MP4: superadas. Cubren configuración publicada, puerto del proveedor, rechazo de otros hosts, credenciales y enlaces no MP4.
- `NativePlaybackSmokeTest` ejecutado en QA con One Piece episodio 1: primer fotograma de vídeo real y progreso guardado; 1 prueba, 0 fallos/errores/omitidas.
- El usuario confirmó que había vídeo nativo en 0.6.0 y señaló que faltaba pantalla completa.
- 0.6.1 añade pantalla completa mediante el control Media3 y un botón explícito, orientación horizontal, ocultación de barras y controles externos y salida inicial con Atrás. Se conserva la instancia de ExoPlayer al alternar la vista.
- Prueba real ampliada: vídeo, progreso, entrada a pantalla completa, regreso con Atrás y conservación de la vista con fotograma listo. **BUILD SUCCESSFUL**, 47 s, una prueba ejecutada sin fallos.
- Compilación y lint móvil y pruebas JVM superados. La compilación TV también se comprobó, pero no se cambió su modo a nativo ni se publicó un APK TV nuevo en esta fase.
- Instalación principal **0.6.1 (versionCode 10)** verificada con el script; arranque Status ok en 1277 ms, actualización con conservación de datos.

El usuario confirmó la entrada a pantalla completa y aclaró que, al salir, la pantalla permanecía apaisada y se recortaban los controles. Se corrigió en 0.6.2 fijando vertical para la vista normal y horizontal para pantalla completa; la orientación previa solo se restaura al abandonar la pantalla de reproducción. La prueba real se amplió para comprobar orientación vertical tras Atrás. La integración nativa corresponde a MP4Upload y al episodio utilizado, no a todos los servidores ni a todos los episodios.

La prueba ampliada 0.6.2 pasó en el móvil (una prueba, cero fallos/errores/omitidas; Gradle 29 s). Instalación principal 0.6.2, versionCode 11, verificada con actualización sin borrado de datos y arranque en 1073 ms. El usuario confirmó que vuelve a vertical con todos los controles y sin perder el vídeo. Esta fase móvil queda validada para MP4Upload; los otros proveedores y la reproducción nativa TV siguen pendientes.
