# AnimeFlix

**0.8.1:** interruptor **Ocultar al día** arriba a la derecha, en móvil y TV. Filtra Explorar/Mi lista, conserva la preferencia y mantiene las series sin comprobar. Una serie oculta reaparece cuando se actualizan sus datos y tiene capítulos pendientes. Entrega actual: `v0.8.1-tv-preview.1`.

Móvil **0.8.0** y TV **0.8.0-tv-preview1** añaden [pendientes y última publicación](docs/mi-lista-pendientes.md): Mi lista muestra N pendientes/Al día y la fecha del último episodio. Marcar vistos actualiza el contador; las nuevas publicaciones también aparecen en la ficha. Los datos se guardan sin conexión y se conservan favoritos y progreso mediante migración Room v5. Entrega: `v0.8.0-tv-preview.1`.

Móvil 0.7.0 amplía la reproducción nativa de MP4Upload con preparación de enlaces temporales desde los embeds de Voe, HLS, UPNShare y Byse. Voe y UPNShare han reproducido con Media3 en el móvil; HLS devuelve un rechazo del servidor y Byse requiere verificación humana. Los resultados concretos están en [reproducción nativa](docs/reproduccion-nativa.md). Se conservan pantalla completa, pausa, avance y progreso local. La misma ruta se incorpora a Android TV 0.7.0-tv-preview1; su validación física en TV sigue pendiente.

Código: [leosamu/animeflix](https://github.com/leosamu/animeflix). Entregas: [Releases](https://github.com/leosamu/animeflix/releases). Consulta [cómo guardar y publicar versiones](docs/versiones-github.md) y el [historial de cambios](CHANGELOG.md). El repositorio es privado; las descargas requieren acceso a GitHub.

Proyecto Android nativo para móvil y Android TV. Explorar, Mi lista, Historial, búsqueda, fichas, catálogo público de AnimeAV1 y episodios. Portadas, caché, favoritos, historial, vistos y progreso persistentes en Room; marcado de episodios anteriores en bloque. Sin cuentas todavía. Android TV 0.7.0-tv-preview1 incorpora reproducción nativa, panel de servidores con mando y verificación manual de Byse. Pendiente de prueba física en TV; consulta la [guía de instalación y pruebas](docs/android-tv-pruebas.md).

## Estructura

- `app-mobile`: aplicación Kotlin + Jetpack Compose Material 3.
- `app-tv`: aplicación Kotlin + Compose for TV, launcher Leanback y controles enfocables con mando.
- `core/model`: modelos y contratos compartidos, sin dependencia de Android.
- `core/data`: base de datos Room, DAO, repositorio local y adaptador del catálogo público AnimeAV1. Cada aplicación tiene su propia base de datos.
- `core/playback`: reproductor, observación de progreso y ViewModels de historial/episodios compartidos por móvil y TV. Incluye puntero para el reproductor web con mando en TV.
- `core/data/schemas`: esquemas generados por KSP durante la compilación; deben añadirse a Git cuando se generen.

## Herramientas y versiones

Base fijada: JDK 17, Gradle 8.11.1 (Wrapper incluido), Android Gradle Plugin 8.9.2, Kotlin/Compose compiler 2.1.20, KSP 2.1.20-1.0.32, SDK/target 35, Build Tools 35.0.0, minSdk 23, Room 2.7.1 y TV Material 1.0.0. Son versiones estables fijadas para esta base, no una afirmación de que sean las últimas.

Compatibilidad AGP/Gradle/JDK: https://developer.android.com/build/releases/agp-8-9-0-release-notes
Room: https://developer.android.com/jetpack/androidx/releases/room
Compose for TV: https://developer.android.com/jetpack/androidx/releases/tv

No hace falta instalar Gradle globalmente: el Wrapper descarga su distribución en el primer uso.

## Compilar en Windows

Para instalar la versión TV: [guía de instalación y pruebas Android TV](docs/android-tv-pruebas.md). Archivo local `dist/AnimeFlix-TV-0.8.1-preview1.apk`, con firma de desarrollo y Android mínimo 6.0. Vídeo nativo a pantalla completa, controles Media3 y panel de servidores con mando. Etiqueta `v0.8.1-tv-preview.1`; descargar `AnimeFlix-TV.apk` desde su Release. `dist/` está excluido de Git.

En este entorno se ha verificado JDK 17.0.21 y Gradle 8.11.1. El SDK local se instala en `.tools/android-sdk` (excluido de Git). Para reutilizar Java y la caché local incluso desde una terminal antigua, ejecuta `powershell -ExecutionPolicy Bypass -File scripts/build.ps1`. El resultado de la compilación se documenta en `docs/environment.md`.

1. Instalar JDK 17 y Android Studio. Reiniciar la terminal tras instalar Java y comprobar `java -version` y `javac -version`. Si es necesario, configurar `JAVA_HOME` apuntando al directorio del JDK y añadir su carpeta `bin` al PATH.
2. Abrir esta carpeta en Android Studio. Configurar Gradle JDK 17 en Settings > Build Tools > Gradle.
3. Completar el asistente inicial y usar SDK Manager para instalar Android SDK Platform 35, Build Tools 35.0.0, Platform Tools y Android SDK Command-line Tools (latest). Revisar y aceptar las licencias solicitadas. Para emuladores, instalar también Android Emulator y las imágenes móvil y TV apropiadas al procesador.
4. Android Studio genera `local.properties` con `sdk.dir`. Alternativamente, definir `ANDROID_HOME` con la ruta real del SDK. No subir rutas locales ni claves a Git.
5. Ejecutar desde esta carpeta:

```powershell
.\gradlew.bat --version
.\gradlew.bat :app-mobile:assembleDebug :app-tv:assembleDebug
.\gradlew.bat :app-mobile:lintDebug :app-tv:lintDebug
```

En macOS/Linux: `sh ./gradlew` en lugar de `.\gradlew.bat`.

Los APK se generan en `app-mobile/build/outputs/apk/debug/` y `app-tv/build/outputs/apk/debug/`. Seleccionar la aplicación y un dispositivo compatible en Android Studio para ejecutarla; o usar `:app-mobile:installDebug` / `:app-tv:installDebug` con un dispositivo conectado.

Para actualizar un móvil conectado sin reutilizar accidentalmente un APK antiguo: `powershell -ExecutionPolicy Bypass -File scripts/install-mobile.ps1`. El script exige una compilación correcta, instala con conservación de datos y compara versión y código del APK con los que informa Android. Si hay varios dispositivos, añadir `-Serial IDENTIFICADOR`.

Comprobación realizada el 8 de noviembre de 2026: ambas aplicaciones compilan y pasan lint con cero errores. Hay 4 avisos en móvil y 6 en TV, detallados en el informe del entorno. El esquema Room v1 se ha generado y añadido al proyecto. Falta validar la ejecución en dispositivos.

Verificar manualmente: catálogo visible, búsqueda, ficha, favorito persistente, consulta sin conexión, reintento y carga de más páginas. Las pruebas con cruceta/OK y ajustes TV se harán en una fase posterior.

## Catálogo AnimeAV1

La aplicación consulta `https://animeav1.com/catalogo?page=…&search=…` por HTTPS. No se ha encontrado documentación de API pública: el adaptador lee tarjetas `article` del HTML público con Jsoup, sin ejecutar scripts. Es una integración dependiente de la estructura de la web, no una API oficial. Una modificación del sitio o una restricción de acceso puede requerir actualizar el adaptador; no se eluden bloqueos.

Se descargan páginas de hasta 20 títulos, con búsqueda tras una pausa de 600 ms al escribir. `Cargar más` incorpora páginas adicionales. Los resultados quedan en Room; los favoritos se mantienen al actualizar metadatos. La búsqueda filtra también los títulos almacenados y no exige descargar todo el catálogo. Las portadas usan la caché de imágenes de Coil. Se incluyen carga, errores, reintento y enlace a la ficha original en el navegador.

Room v2 añade `posterUrl` y `sourceUrl` mediante una migración explícita desde v1, sin borrar la base de datos. Los ejemplos antiguos se conservan en la base pero no se muestran en el catálogo móvil conectado.

## Episodios y reproductores

Las fichas consultan los episodios publicados por AnimeAV1 y los presentan en grupos de 50, con orden numérico (incluidos números decimales). Se leen los enlaces y el listado incluido en el HTML público, sin ejecutar código del servidor. Los estrenos sin episodios muestran un estado vacío.

El 8 de noviembre de 2026, el usuario confirmó reproducción efectiva en su móvil con la versión 0.3.3 usando `Reproductor de la web`. No se considera validada la reproducción mediante los iframes directos de `Elegir servidor` ni en Android TV.

En móvil 0.6.1 se usa por defecto reproducción nativa si se publica un MP4Upload compatible. Se lee la configuración MP4 que expone su página de reproducción y se entrega a Android Media3, sin eludir DRM, desafíos ni bloqueos. La URL de vídeo es temporal y se obtiene de nuevo al reintentar. No se guarda en Room ni se usan claves de servidor.

`Usar reproductor web` conserva la página original completa y los iframes como alternativa. La versión TV publicada mantiene esa ruta web; el usuario ha comunicado que no resulta efectiva en su dispositivo, por lo que no se considera validada. Se trabajará en la adaptación nativa a TV después de móvil.

Solo se aceptan enlaces HTTPS; la actividad no está exportada, no se permiten archivos locales ni interfaces JavaScript nativas, y se bloquean ventanas emergentes, descargas e intentos de abrir aplicaciones externas desde el reproductor. La disponibilidad, controles y compatibilidad dependen de cada servidor y de Android System WebView. Un servidor que responde 403 puede no ser utilizable dentro de la aplicación; debe usarse otro o la página original.

## Historial y episodios vistos

`Historial` muestra los episodios abiertos, del más reciente al más antiguo, con fecha, título y acceso para volver a abrirlos. La ficha ofrece `Retomar episodio` para regresar al último abierto de ese anime. La navegación a otros episodios desde la página original actualiza el historial cuando WebView termina de cargar el nuevo episodio.

Abrir un episodio no significa que se haya reproducido ni terminado. Los vistos son explícitos: se pueden marcar y desmarcar desde el listado de episodios, el historial y la pantalla de reproducción. Cambiar ese estado no altera la fecha de apertura; reabrir un episodio no elimina su marca de visto. Un episodio marcado como visto sin abrirlo todavía no aparece en el historial de aperturas.

Estos datos se guardan localmente en Room v3. La migración v2→v3 crea `watch_history` sin borrar catálogo ni favoritos; se conserva también la ruta de migración desde v1. No hay sincronización entre móvil y TV ni con Supabase.

Desde 0.5, se intenta guardar y restaurar el minuto en reproductores compatibles mediante las funciones oficiales de AndroidX WebKit para ejecutar un observador en el documento del reproductor. No se accede al DOM del iframe desde otro origen ni se desactivan las protecciones del navegador. Véase [WebViewCompat](https://developer.android.com/reference/androidx/webkit/WebViewCompat#addDocumentStartJavaScript(android.webkit.WebView,java.lang.String,java.util.Set%3Cjava.lang.String%3E)).

El observador se limita a los orígenes exactos de Zilla, MP4Upload y AnimeAV1 UPNShare configurados en `WebPlaybackProgress`. No hay comodines. Solo comunica tiempo/duración validados, sin acceso a archivos, navegación o funciones privilegiadas. Guarda aproximadamente cada cinco segundos y al pausar; solicita restaurar después de que el vídeo tenga metadatos. No inicia la reproducción por sí solo. Se rechazan datos fuera de rango y no se busca al minuto guardado si la duración difiere o está prácticamente al final.

La compatibilidad depende de Android System WebView y del origen final del reproductor; una redirección a otro dominio no habilita el observador automáticamente. Cuando no hay progreso real, no se muestra un minuto inventado. No se marca automáticamente un episodio como visto. Hay que validar el resultado con vídeo real para cada servidor.

El 8 de noviembre de 2026, el usuario confirmó en su móvil que 0.5.0 guarda y restaura el minuto con vídeo real en `Reproductor de la web`. Esta validación corresponde al reproductor usado, no a todos los servidores ni a Android TV.

## Marcar anteriores como vistos

Cada episodio salvo el primero ofrece `Marcar anteriores al N como vistos`. La confirmación indica cuántas marcas nuevas se añadirán. Se usan los episodios publicados del mismo anime, ordenados numéricamente, incluidos especiales decimales y los de otras páginas del listado. **El episodio elegido y los posteriores quedan fuera**. La operación es una transacción: conserva aperturas, progreso y otras marcas existentes. No añade aperturas ficticias al historial. Las marcas se pueden deshacer individualmente.

Room v4 añade tiempo y duración con migración v3→v4, preservando los datos anteriores.

## Pruebas móviles separadas

Las pruebas instrumentadas usan el tipo de compilación `qa`, identificador `com.animeflix.mobile.qa`, con catálogo ficticio determinista. Nunca ejecutarlas sobre la instalación principal: la herramienta puede desinstalar la aplicación que prueba al terminar.

```powershell
.\gradlew.bat :app-mobile:connectedQaAndroidTest
```

Cubren búsqueda vacía/sin resultados, conservación de pantalla al recrear la actividad, navegación a fichas y actualización de metadatos sin perder favoritos. La prueba de integración con AnimeAV1 real se documenta por separado en el informe del entorno.

`HistoryMigrationTest` crea una base v2 temporal dentro de QA, comprueba la migración v3 con favoritos conservados y verifica aperturas/vistos tras cerrar y reabrir la base. `HistoryScreenTest` comprueba el historial y la marca de visto tras recrear la actividad.

Para las pruebas locales sin depender de la web (diez desde 0.8.1):

```powershell
.\gradlew.bat :app-mobile:connectedQaAndroidTest '-Pandroid.testInstrumentationRunnerArguments.notClass=com.animeflix.mobile.OriginalEpisodeWebViewTest,com.animeflix.mobile.RemoteEpisodeSmokeTest,com.animeflix.mobile.NativePlaybackSmokeTest,com.animeflix.mobile.OmaeServerProbeTest,com.animeflix.mobile.IntegratedProviderTest,com.animeflix.mobile.TvNativePlaybackTest,com.animeflix.mobile.RemoteInventorySmokeTest'
```

`RemoteEpisodeSmokeTest` consulta la ficha y el episodio 1 de One Piece para comprobar desde Android la lectura real de episodios y servidores; requiere conexión y disponibilidad de AnimeAV1. `OriginalEpisodeWebViewTest` comprueba que la página original carga su iframe dentro de la pantalla de reproducción. `:core:data:testDebugUnitTest` prueba también ordenación, deduplicación, idioma, esquema HTTPS y aislamiento de los enlaces de descarga. Estas pruebas no acreditan que un vídeo llegue a reproducirse: eso se valida manualmente en el dispositivo.

## Supabase, fase siguiente

`RemoteAnimeSource` define el punto de entrada futuro. No hay SDK Supabase, URL, claves ni llamadas de red configuradas. Añadir un adaptador remoto, autenticación y sincronización conservando Room como fuente local. Antes de habilitar acceso, diseñar tablas, migraciones y políticas RLS. En clientes solo usar la clave pública/publishable; nunca una clave service_role. Los favoritos ahora son locales y no se comparten entre dispositivos.

## Próximos pasos

1. Validar reproducción efectiva y pantalla completa por servidor en móvil; ajustar errores y compatibilidad.
2. Ampliar pruebas de caché, migraciones y conexión interrumpida; mejorar filtros y paginación.
3. Integrar Supabase y sincronización por usuario cuando se disponga de un proyecto configurado.
4. Ejecutar la guía de pruebas Android TV en el dispositivo del usuario y ajustar foco, puntero y reproducción según los resultados.
5. Configurar CI, firma y distribución de versiones.

## Git

Repositorio en la rama `main`, vinculado a `https://github.com/leosamu/animeflix.git`. Para registrar futuras modificaciones:

```powershell
git add .
git commit -m "Describe el cambio realizado"
git push origin main
```

Consulta `docs/environment.md` para el resultado real de la comprobación de este entorno.
