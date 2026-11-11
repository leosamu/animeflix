# Cola de descargas y fuentes alternativas

La revisión posterior a 0.10.0 sustituye las solicitudes independientes a Android DownloadManager por una cola persistente de WorkManager. Hay **un capítulo activo por dispositivo**, también cuando se añaden varios o se cambia de perfil. Un capítulo en espera guarda su identidad y el idioma; su enlace multimedia se prepara al llegar el turno, evitando que caduque mientras espera.

## Selección de fuentes

Se rota entre MP4Upload, Voe y UPNShare cuando el episodio publica alternativas compatibles en el idioma elegido. Nunca se pasa automáticamente de SUB a DUB ni se mezclan fragmentos de servidores distintos. Si no se conoce el idioma, se conserva exclusivamente el embed seleccionado. Byse y el servidor HLS/Zilla siguen fuera de la selección automática.

Voe y UPNShare se preparan ejecutando su embed público con las mismas restricciones de origen y observación de vídeo del reproductor. Una verificación humana, un permiso o un origen desconocido no se elude: se abandona ese intento y se prueba el siguiente. Hay como máximo un intento por proveedor y ronda; las fuentes que fallan entran en descanso temporal (diez minutos para HTTP 403/429/503, un minuto para otros fallos).

Ante error se elimina la caché parcial y el siguiente proveedor empieza de cero. Si después de un minuto de transferencia la velocidad estima más de veinte minutos restantes y hay otra fuente disponible, también se intenta esa alternativa. La última fuente no se descarta solo por ir lenta, para no convertir una conexión lenta en un fallo inevitable. Las caídas de conexión vuelven a dejar el capítulo en cola y WorkManager espera la red.

## MP4 y HLS sin red

Las transferencias usan los descargadores de Media3 y una caché dedicada por capítulo. MP4 se valida por su cabecera. Para HLS se descargan la lista, segmentos, claves AES publicadas cuando corresponda y pistas elegidas: una variante de vídeo de hasta 720p si existe, su audio y una pista de subtítulos asociada si se publica. Se exige una lista finita bajo demanda y se rechazan manifiestos con DRM que requieran licencia offline.

El reproductor recupera las mismas claves de pistas y utiliza `CacheDataSource` **sin fuente HTTP alternativa**. Las cabeceras y cookies de resolución son temporales por intento; no se guardan en el índice. El descriptor de reproducción se cifra con AES-GCM y una clave no exportable de Android Keystore. La caché, incluidas las listas con sus URI, permanece en almacenamiento privado sin copia de seguridad; no se sube a Supabase.

Los vídeos completados de 0.10.0 siguen siendo reproducibles. Sus transferencias todavía activas se convierten en entradas de la nueva cola y reinician desde cero en red sin coste medido. Las entradas fallidas anteriores permiten Reintentar con el sistema nuevo. Cancelar un capítulo no cancela los siguientes; eliminar un archivo en reproducción espera a que se cierre el lector para liberar la caché.

## Pruebas

Resultados positivos: cuatro casos instrumentados de aceptación en móvil Android 15 y diecinueve pruebas JVM. HLS real completó reproducción, audio y búsqueda sin conexión; MP4 se comprobó con la muestra pequeña usando ProgressiveDownloader y el reproductor real sin fuente HTTP alternativa. El ensayo de capítulo entero forzando MP4Upload tuvo un resultado negativo de rendimiento: 81.518.282 de 196.515.679 bytes al llegar al límite de 240 segundos; se conserva como limitación, no como prueba superada.

- `DownloadOriginPolicyTest`: cinco elecciones consecutivas, rotación entre proveedores, idioma, descanso de fuentes y rechazo de hosts no compatibles.
- `OfflineQueueBatchTest`: aceptación live optativa con los primeros cinco capítulos de **Ao no Hako Season 2**, aislamiento en QA y Wi-Fi sin coste medido. Comprueba un solo capítulo activo, cinco terminados, más de un proveedor, cancelación de un elemento en espera y reproducción sin red con pistas de audio y salto de minuto por proveedor.
- `OfflineIndexTest`: conserva la comprobación de archivos antiguos incompletos, aislamiento y rechazo de fuentes no compatibles.

El primer lote live completó cinco capítulos con Voe y MP4Upload. La fase de reproducción detectó un identificador de caché interpretado como streaming; el defecto se corrigió. El argumento adicional `-Pandroid.testInstrumentationRunnerArguments.offlinePlaybackRecheck=true` repite la lectura HLS pendiente con los capítulos 1 y 5 forzando Voe, conservando cancelación y aislamiento. `OfflineMp4CacheTest` comprueba la lectura MP4 por separado con una muestra oficial de AndroidX de 101.674 bytes y una identidad HTTPS sin red disponible; no introduce APIs de fixtures en producción. La ejecución por defecto sigue incluyendo los cinco.

```powershell
$env:ANDROID_SERIAL = '3684f098'
& ./scripts/build.ps1 -Tasks @(':app-mobile:connectedQaAndroidTest', '-Pandroid.testInstrumentationRunnerArguments.class=com.animeflix.mobile.OfflineQueueBatchTest,com.animeflix.mobile.OfflineIndexTest', '-Pandroid.testInstrumentationRunnerArguments.liveOfflineBatch=true')
```

La prueba live descarga un lote real, puede consumir alrededor de 1 GB y restaura Wi-Fi/datos al terminar. No forma parte de las pruebas automáticas normales. La rotación solo puede usar fuentes que el episodio publique y que acepten la transferencia; no garantiza saltarse límites del proveedor ni disponibilidad permanente.

Referencias: [trabajo persistente de Android](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work), [descargas de Media3](https://developer.android.com/media/media3/exoplayer/downloading-media).
