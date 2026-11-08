# Reproducción nativa móvil — 0.7.0

La reproducción nativa usa Android Media3 para el vídeo y sus controles. Algunos proveedores necesitan preparar el enlace en su embed y Byse solicita una verificación humana antes de entregar la fuente.

El 8 de noviembre de 2026 se validó MP4Upload en el móvil conectado: prueba real de primer fotograma y progreso, pantalla completa y retorno a vertical, más confirmación manual del usuario de que los controles vuelven y no se pierde el vídeo.

| Servidor publicado | Estado nativo |
|---|---|
| MP4Upload | Implementado: configuración MP4 de su embed público, controles Media3, progreso y pantalla completa |
| HLS/Zilla | Preparación implementada; el servidor rechaza peticiones nativas con HTTP 403. En pruebas |
| UPNShare | Integrado mediante su embed y Turboembed; vídeo nativo confirmado en SUB/DUB del episodio de prueba |
| Voe | Integrado mediante su embed y redirección pública; vídeo nativo confirmado en SUB/DUB del episodio de prueba |
| Byse | Reproducción con controles nativos confirmada manualmente por el usuario en 0.7.0, después de completar la verificación humana en su iframe n1mwq.org |

La aplicación selecciona automáticamente MP4Upload cuando está disponible; si no, intenta el primer proveedor reconocido. La compatibilidad se comprueba por episodio y no se garantiza para todo el catálogo. La prueba MP4Upload usa One Piece, episodio 1; la matriz de otros proveedores usa el episodio 1 de Omae gotoki.

## Funcionamiento

- El enlace MP4 se obtiene de `player.src` en la página pública del proveedor. Se validan el esquema HTTPS, dominio y puerto de su CDN. No se ejecuta código descargado para resolverlo.
- El cliente respeta el Referer del embed original. No desactiva TLS, no cambia de IP ni intenta saltar un rechazo de acceso.
- Cada reintento obtiene una dirección nueva; los enlaces temporales no se almacenan en la base ni en el repositorio.
- Media3 maneja el vídeo, audio, pausa, avance, búsqueda temporal y foco de audio.
- La posición se guarda cada cinco segundos, al pausar y al salir, reutilizando Room v4. No se marca automáticamente como visto.
- Al ir al segundo plano se pausa y al cerrar se libera el reproductor.
- Pantalla completa oculta los controles externos de la aplicación y las barras del sistema, permite horizontal y conserva la misma instancia del reproductor. Se entra desde el icono de los controles o el botón Pantalla completa; Atrás vuelve primero a la vista vertical y recupera los controles. La orientación previa de la actividad se restaura solo al salir de la pantalla de reproducción, no al alternar pantalla completa.

Para Voe, UPNShare, HLS y Byse se abre únicamente el embed seleccionado dentro de una página mínima con el contexto del episodio original. Una WebView ejecuta el reproductor del proveedor, con popups, acceso a archivos, contenido mixto y permisos de dispositivo deshabilitados. El vídeo de preparación permanece silenciado y el usuario puede pulsar el Play normal si hace falta. No se desactivan comprobaciones TLS ni se resuelven desafíos de acceso.

El observador se limita a orígenes concretos de cada proveedor y acepta manifiestos HLS recibidos correctamente o vídeos reales con metadatos de duración/dimensiones. No extrae cualquier cadena MP4 de la página: Voe contiene un vídeo corto de muestra que no es el episodio. Al resolver se cierra esa WebView y Media3 recibe el enlace temporal, su agente, Referer y, cuando existen, cookies y Origin observados. Los enlaces/cookies no se guardan en Room, Git ni registros. Android System WebView debe soportar los mensajes acotados por origen y los scripts de inicio; si no, se solicita actualizar ese componente.

El intento tiene un límite de 45 segundos, ampliado a tres minutos si el proveedor muestra una verificación humana. Esta se presenta al usuario y no se pulsa ni se resuelve automáticamente. Byse necesitó además ajustar la altura de sus contenedores anidados para que ese recuadro pueda verse. Se puede reintentar o cambiar de servidor. Cada selección mantiene su propio estado para que cancelar la anterior no borre el resultado nuevo. HLS y Byse se identifican en la interfaz como servidores en pruebas.

## Comprobación en móvil

1. Abrir One Piece, episodio 1, en Reproducción nativa → MP4Upload.
2. Confirmar movimiento de imagen, sonido y controles de pausa/avance.
3. Entrar en pantalla completa y verificar que no reinicia ni vuelve al principio.
4. Pulsar Atrás y confirmar que vuelve a la vista normal conservando la posición.
5. Adelantar, dejar avanzar al menos diez segundos, salir y retomar; comprobar el minuto.
6. Pulsar Inicio mientras reproduce y comprobar que no queda audio inesperado de fondo.

`NativePlaybackSmokeTest` usa la aplicación QA separada y un vídeo real: espera el primer fotograma y un registro de progreso. La prueba requiere red y disponibilidad de AnimeAV1 y MP4Upload; no sustituye la comprobación humana del audio.

```powershell
.\gradlew.bat :app-mobile:connectedQaAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.animeflix.mobile.NativePlaybackSmokeTest'
```

TV 0.7.0-tv-preview1 usa los mismos resolvedores y Media3 por defecto, con pantalla completa horizontal, panel de servidores y puntero explícito para la verificación de Byse. `TvNativePlaybackTest` comprueba ese modo compartido en QA del móvil; no equivale a una prueba en hardware Android TV. La instalación y revisión física en TV corresponden a la [guía TV](android-tv-pruebas.md).

## Diagnóstico de otros servidores

`OmaeServerProbeTest` comprueba el episodio 1 de «Omae gotoki ga Maou ni kateru to omouna…» en SUB y DUB, con los enlaces publicados por AnimeAV1. Se ejecuta únicamente en el paquete QA y no cambia los proveedores habilitados en la aplicación principal.

```powershell
.\gradlew.bat :app-mobile:connectedQaAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.animeflix.mobile.OmaeServerProbeTest'
```

La prueba aísla el embed seleccionado del HLS que carga inicialmente la página. Solo registra reproducción web cuando observa vídeo con duración mayor de un minuto, dimensiones y tiempo avanzando. Cuando observa una petición multimedia HTTPS, comprueba por separado si Media3 decodifica su primer fotograma, conservando el Referer y agente de esa petición. No registra las direcciones temporales completas.

Es una herramienta de diagnóstico: que termine correctamente significa que completó las observaciones, no que todos los servidores funcionen. La ausencia de vídeo durante 25 segundos tampoco demuestra que un servicio esté caído. No comprueba sonido, pausa, avance ni pantalla completa de estos proveedores.

Resultados del 8 de noviembre de 2026, Android 15 físico, episodio 1, diagnóstico con aislamiento de servidor e idioma (versión 3):

| Servidor | DUB | SUB | Reproducción nativa en la aplicación principal |
|---|---|---|---|
| HLS | Vídeo web observado, 1920 px, unos 23:40 | No confirmada en la prueba aislada | Pendiente: no se obtuvo fuente para Media3 |
| UPNShare | No confirmada | No confirmada | Pendiente |
| Voe | Primer fotograma Media3, duración 1.420.132 ms | Primer fotograma Media3, duración 1.420.119 ms | Pendiente de integración; resultado positivo solo en QA |
| Byse | No confirmada | No confirmada | Pendiente |

Para UPNShare la resolución pública redirigió a Turboembed; cargar esa página no acreditó reproducción. Byse cargó su aplicación JavaScript sin que la prueba observara vídeo ni una petición multimedia compatible. Estas observaciones no permiten concluir que los proveedores fallen en todos los navegadores. Los enlaces temporales y las respuestas completas de proveedores se conservan fuera de Git, en `.tools`.

La tabla anterior describe el diagnóstico inicial, anterior a la integración 0.7.0. `IntegratedProviderTest` prueba ahora la pantalla real y su resolvedor de producción, en la aplicación QA separada. Voe y UPNShare exigen primer fotograma nativo; para Byse se exige vídeo o el aviso accionable de verificación manual. HLS se observa y registra sin afirmar compatibilidad por el mero éxito del test. Cuando hay vídeo se comprueban el registro de progreso, pantalla completa y retorno a vertical. La escucha del audio y los controles de pausa/avance requieren comprobación manual.

```powershell
.\gradlew.bat :app-mobile:connectedQaAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=com.animeflix.mobile.IntegratedProviderTest'
```

Validación final de 0.7.0 el 8 de noviembre de 2026: nueve comprobaciones en el móvil físico completadas correctamente, con estos resultados diferenciados:

| Caso | Resultado |
|---|---|
| Voe SUB y DUB (2) | Vídeo nativo, progreso y entrada/salida de pantalla completa |
| UPNShare SUB y DUB (2) | Vídeo nativo, progreso y entrada/salida de pantalla completa |
| MP4Upload One Piece 1 (1) | Regresión de vídeo/progreso/pantalla completa superada |
| Byse SUB y DUB (2) | Detecta la verificación humana y muestra el aviso; reproducción pendiente de completarla manualmente |
| HLS SUB y DUB (2) | Rechazo HTTP 403 de player.zilla-networks.com, con mensaje de error y cambio de servidor disponible |

El recuadro de Byse se revisó además visualmente y por medidas: el iframe y la verificación tienen 220 px de altura, y el botón Play aparece centrado y dentro del área visible. No se pulsó el desafío. Compilación y lint móvil/TV, pruebas de datos, regresión del diseño TV y selección de fuentes JavaScript completados sin errores. `scripts/test-provider-source.cjs` comprueba rechazo de muestras/anuncios cortos, conservación del manifiesto maestro y su audio, rechazo de enlaces blob y ausencia de clics automáticos en la verificación humana.

Para la revisión manual, abrir Omae gotoki, episodio 1, seleccionar Voe o UPNShare en SUB/DUB y comprobar sonido, pausa/avance, pantalla completa y retorno al minuto. En Byse, completar personalmente la verificación del recuadro y comprobar si después se entrega vídeo al reproductor nativo. HLS continúa experimental y no se da por compatible.

Confirmación posterior del usuario, 8 de noviembre de 2026: Byse entrega el vídeo a los controles nativos tras completar personalmente la verificación en 0.7.0, siguiendo la prueba de Omae gotoki, episodio 1. No se indicó el idioma seleccionado ni se confirmó por separado sonido, búsqueda temporal o reanudación; no se extiende este resultado a todos los episodios o idiomas.
