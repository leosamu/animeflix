# Reproducción nativa móvil — 0.6.2

La reproducción nativa sustituye el reproductor web por Android Media3 para los proveedores implementados. No es necesario interactuar con una página ni con un iframe para ver el vídeo.

El 8 de noviembre de 2026 se validó MP4Upload en el móvil conectado: prueba real de primer fotograma y progreso, pantalla completa y retorno a vertical, más confirmación manual del usuario de que los controles vuelven y no se pierde el vídeo.

| Servidor publicado | Estado nativo |
|---|---|
| MP4Upload | Implementado: configuración MP4 de su embed público, controles Media3, progreso y pantalla completa |
| HLS/Zilla | No implementado; la consulta inicial respondió HTTP 403 en el entorno de inspección |
| UPNShare | Pendiente |
| Voe | Pendiente; la página pública redirige a otro dominio y su configuración no está integrada |
| Byse | Pendiente |

La aplicación selecciona automáticamente MP4Upload cuando está disponible. Elegir un proveedor no implementado muestra esa limitación y permite volver a otro o a la alternativa web. No se promete compatibilidad con todos los episodios de MP4Upload: la prueba real se hace con One Piece, episodio 1.

## Funcionamiento

- El enlace MP4 se obtiene de `player.src` en la página pública del proveedor. Se validan el esquema HTTPS, dominio y puerto de su CDN. No se ejecuta código descargado para resolverlo.
- El cliente respeta el Referer del embed original. No desactiva TLS, no cambia de IP ni intenta saltar un rechazo de acceso.
- Cada reintento obtiene una dirección nueva; los enlaces temporales no se almacenan en la base ni en el repositorio.
- Media3 maneja el vídeo, audio, pausa, avance, búsqueda temporal y foco de audio.
- La posición se guarda cada cinco segundos, al pausar y al salir, reutilizando Room v4. No se marca automáticamente como visto.
- Al ir al segundo plano se pausa y al cerrar se libera el reproductor.
- Pantalla completa oculta los controles externos de la aplicación y las barras del sistema, permite horizontal y conserva la misma instancia del reproductor. Se entra desde el icono de los controles o el botón Pantalla completa; Atrás vuelve primero a la vista vertical y recupera los controles. La orientación previa de la actividad se restaura solo al salir de la pantalla de reproducción, no al alternar pantalla completa.

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

TV no se ha cambiado a reproducción nativa todavía y no se ha instalado en el dispositivo TV.
