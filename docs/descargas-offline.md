# Episodios sin conexión

## Uso

- En la lista de episodios, pulsa **↓**. La app consulta las fuentes y ofrece la descarga cuando encuentra MP4Upload. También se puede descargar desde el menú del reproductor nativo con ese servidor seleccionado.
- Confirma la descarga. Se espera una red sin coste medido por defecto; puedes permitir datos móviles en el diálogo. No se permiten redes en roaming.
- Abre **Descargas** desde la cabecera de móvil o la navegación principal de TV. Se muestran los capítulos del perfil activo, el servidor, el espacio ocupado, los bytes transferidos y el estado.
- Solo **Disponible sin conexión** permite **Ver offline**. Las descargas incompletas no se abren como vídeos terminados.
- Cancelar/eliminar pide confirmación y borra únicamente el archivo y la entrada local. Conserva el historial, los vistos y el progreso.
- Para acceder sin Internet hay que haber iniciado sesión y seleccionado un perfil previamente en ese dispositivo. Los perfiles guardados siguen disponibles cuando falla su actualización en línea.

## Alcance inicial

Móvil y TV comparten la implementación. Solo se habilitan archivos MP4 publicados por el resolvedor de MP4Upload; no se descargan páginas web, manifiestos HLS, desafíos ni fuentes que requieran licencias offline. La disponibilidad depende de cada episodio y de la respuesta del proveedor. Si hay varios servidores MP4Upload se usa el primero al descargar desde la lista; desde el reproductor se usa el servidor seleccionado. Se conserva una descarga por episodio y perfil.

Los archivos pertenecen al dispositivo y al perfil de la cuenta. No se suben vídeos a Supabase ni se sincroniza su disponibilidad con otros equipos. El progreso y los vistos utilizan la cola local de sincronización existente. Desinstalar la aplicación elimina sus archivos.

## Implementación

Para este alcance progresivo se usa `android.app.DownloadManager`, en lugar de introducir una caché de segmentos de Media3. Android mantiene la cola y realiza las transferencias en segundo plano. Los archivos están en el directorio Movies propio de la aplicación, sin permisos de almacenamiento general. El índice de la app guarda identificadores de cuenta/perfil/episodio, ID de transferencia, nombre de archivo aleatorio y URL pública del embed. Los enlaces multimedia temporales y los encabezados de la transferencia solo se entregan al gestor del sistema; no se añaden al índice de la app ni a Supabase.

Antes de encolar se comprueba la disponibilidad de almacenamiento y un mínimo de 256 MiB libres. Android controla la falta de espacio durante la transferencia. La biblioteca muestra ese error cuando el gestor lo comunica. Antes de considerar un archivo disponible se exige una transferencia terminada, archivo presente, tamaño consistente y cabecera MP4 `ftyp`. Esto detecta archivos faltantes/truncados y respuestas HTML, pero no sustituye una comprobación completa de todos los fotogramas.

La reproducción offline usa exclusivamente `FileDataSource` y una entrada de `PlayerActivity` que retorna antes de consultar servidores. No hay alternativa HTTP automática. Los callbacks de progreso conservan el repositorio del perfil que abrió el reproductor.

Un reintento de una descarga fallida vuelve a resolver el embed y crea una transferencia/archivo nuevos. Al encolarse correctamente se elimina la entrada fallida anterior. No se unen fragmentos descargados de enlaces distintos. Las interrupciones transitorias de una misma transferencia las gestiona Android; una URL caducada requiere un reintento manual.

Referencias: [gestor de descargas de Android](https://developer.android.com/reference/android/app/DownloadManager), [restricciones y destino de una solicitud](https://developer.android.com/reference/android/app/DownloadManager.Request), [alternativa Media3 para medios segmentados](https://developer.android.com/media/media3/exoplayer/downloading-media).

## Validación

`OfflineIndexTest` verifica que los archivos incompletos nunca son reproducibles, que un perfil no puede consultar ni borrar los de otro y que una fuente no compatible se rechaza antes de acceder a la red.

`OfflineDownloadTest` es una aceptación de red explícita, aislada en el paquete QA. Descarga un capítulo entero, comprueba pistas de vídeo/audio y ausencia de duplicados, cambia de perfil, desactiva la conectividad y exige que no haya red activa antes de abrirlo. Comprueba primer fotograma, salto a otro minuto, reapertura con progreso y detección de archivo borrado. Restaura la red y elimina la descarga de prueba en `finally`. Una pantalla QA mantiene la aplicación visible durante la transferencia; Xiaomi bloquea los lanzamientos desde segundo plano si no se hace así.

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
& ./scripts/build.ps1 -Tasks @(':app-mobile:connectedQaAndroidTest', '-Pandroid.testInstrumentationRunnerArguments.class=com.animeflix.mobile.OfflineDownloadTest,com.animeflix.mobile.OfflineIndexTest', '-Pandroid.testInstrumentationRunnerArguments.liveOffline=true')
```

La aceptación live consume aproximadamente 197 MB por ejecución y no se ejecuta por defecto. Para un móvil físico se exige además `-Pandroid.testInstrumentationRunnerArguments.allowPhysical=true` y una red inicial sin coste medido. El código de servicio Ethernet de la variante de emulador corresponde a `IEthernetManager.setEthernetEnabled` en la imagen API 36; no se usa en producción ni en dispositivos físicos.

El 11/11/2026 pasaron **3 pruebas instrumentadas, sin fallos**, en el móvil físico 23049PCD8G / Android 15: aceptación live e índice local. El capítulo completo tenía **196.515.679 bytes**, idénticos al tamaño declarado. Se verificaron las pistas de audio/vídeo, ausencia de red activa, primer fotograma desde URI `file`, avance después de saltar al minuto 1, reapertura con el minuto restaurado, aislamiento, archivo borrado e incompletos no reproducibles. No se certifica una escucha manual de toda la pista de audio ni cada fotograma del capítulo.

La prueba en el emulador API 36 encontró además un cierre nativo de `MediaCodec` (`CHECK(!info->mOwnedByClient)`), antes de completar la aceptación. No se presenta como validación de TV física. Se corrigió un problema independiente del arranque rápido local: el reproductor puede prepararse antes de `ON_RESUME` y ahora arranca al pasar a primer plano, conservando una pausa manual al regresar de segundo plano. También espera el progreso persistido antes de crear el reproductor offline.

Queda la aceptación manual de aspecto/sonido y mando en TV física, la interrupción de una transferencia real, la restricción de datos medidos, el agotamiento real del disco y la sincronización del progreso con un proyecto Supabase después de recuperar conexión. No se simulan resultados positivos de estos casos.
