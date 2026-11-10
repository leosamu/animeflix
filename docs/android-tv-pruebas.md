# AnimeFlix Android TV — 0.8.1 preview 1

La versión 0.9.0 en desarrollo ya dispone de un [emulador Android TV local](emulador-tv.md), con instalación y pantalla de acceso comprobadas. El resto de esta guía describe la entrega 0.8.1 y sus pruebas físicas pendientes.

Esta versión usa reproducción nativa con Media3 por defecto. Compilación y análisis estático verificados; el modo TV compartido se comprueba en el móvil QA en horizontal, pero no se ha probado en un Android TV físico ni en un emulador TV. No está publicada en Google Play. APK firmado con la misma clave de desarrollo de las entregas TV anteriores.

## Archivo e instalación

Archivo local: `dist/AnimeFlix-TV-0.8.1-preview1.apk`. En GitHub, descargar `AnimeFlix-TV.apk` de la etiqueta `v0.8.1-tv-preview.1`. Android 6.0 o posterior (API 23), Android TV/Google TV y conexión a internet para el catálogo y el vídeo. Dispositivos basados en Android que no sean Android TV/Google TV pueden no mostrar el acceso de TV; Fire TV no está validado.

1. Copia el APK al dispositivo por USB o mediante tu herramienta habitual de transferencia.
2. Ábrelo con un gestor de archivos y permite instalar aplicaciones desde ese gestor cuando Android lo solicite.
3. Instálalo y abre **AnimeFlix TV** en la lista de aplicaciones. En algunos lanzadores hay que añadirlo manualmente a favoritos.
4. Si ya tenías una versión anterior de `com.animeflix.tv`, instala encima para conservar datos. Si Android rechaza la actualización por firma distinta, no desinstales sin valorar que se borrarán los datos locales; anota el mensaje y comunícalo.

Opcional, si usas ADB en tu propio entorno:

```text
adb install -r AnimeFlix-TV.apk
adb shell am start -n com.animeflix.tv/.MainActivity
```

El SHA-256 se incluye en `SHA256SUMS.txt` de esa Release y en `dist/library-0.8.1/`. La firma es de desarrollo: las futuras actualizaciones de prueba deben mantener la misma firma. Una futura distribución con firma de producción puede requerir un procedimiento de migración. El repositorio es privado: los enlaces de descarga requieren acceso a GitHub.

## Cómo usar el mando

- Cruceta y OK: mover el foco y seleccionar los controles de la aplicación.
- Atrás: cerrar un diálogo, volver de una ficha al catálogo o regresar a Explorar desde otras secciones.
- Buscar: abre el campo de texto y el teclado disponible en tu TV. También puedes usar un teclado USB/Bluetooth; no se ha integrado búsqueda por voz propia.
- El episodio selecciona MP4Upload si está disponible. Después de preparar el enlace, el vídeo nativo ocupa toda la pantalla; en TV se mantiene siempre horizontal.
- En el vídeo, usa cruceta y OK para mostrar/recorrer los controles nativos de pausa y avance. Comprueba también Play/Pausa y avance/retroceso si tu mando tiene esas teclas.
- **Atrás o Menú** abre el panel de servidores. **Ver vídeo · cerrar menú** devuelve el foco al vídeo sin reiniciarlo. El elemento enfocado tiene un borde blanco. Atrás con el panel abierto vuelve a los episodios.
- En el panel puedes elegir servidor e idioma, reintentar, marcar visto, cambiar a la alternativa web o volver a los episodios. Seleccionar otro servidor obtiene una fuente nueva conservando el minuto del episodio.
- Byse puede solicitar verificación humana antes de entregar el vídeo. En su preparación elige **Controlar verificación con mando**: cruceta mueve el puntero blanco y OK pulsa el botón bajo el puntero. La verificación debes completarla tú; la aplicación no la automatiza. **Atrás** sale del puntero y recupera el botón de la aplicación, sin abandonar el episodio.
- El puntero solo se activa al solicitarlo, durante la preparación/verificación; no se usa para los controles del vídeo nativo. La alternativa web mantiene el reproductor antiguo y permite regresar a **Usar reproducción nativa** desde su panel; no es la ruta recomendada para TV.

## Pruebas, en este orden

| Prueba | Qué hacer | Resultado esperado |
|---|---|---|
| 1. Instalación y arranque | Instalar y abrir desde el lanzador | Acceso AnimeFlix TV visible, catálogo y portadas cargados, sin cierre |
| 2. Foco | Recorrer botones y títulos solo con cruceta y OK | Se distingue el elemento seleccionado y no queda atrapado el foco |
| 3. Catálogo largo | Bajar por la lista y usar Cargar más títulos | Desplazamiento automático hasta el elemento enfocado; aparecen más títulos |
| 4. Búsqueda | Buscar One Piece, probar un texto sin resultados y borrar búsqueda | Teclado utilizable con mando; resultados y estado vacío correctos |
| 5. Favoritos | Añadir desde ficha, entrar en Mi lista, quitar y volver a añadir | La lista refleja los cambios; persisten al cerrar y abrir la app |
| 6. Episodios | Abrir un anime con muchos episodios y cambiar de grupo de 50 | Se ven números y controles de paginación; un estreno sin episodios muestra ese estado |
| 7. Vistos | Marcar y desmarcar uno; cancelar y confirmar Anteriores vistos en otro | El diálogo cuenta las marcas nuevas; solo se marcan anteriores del mismo anime, no el elegido ni posteriores |
| 8. Reproducción y espacio | Abrir One Piece 1 en MP4Upload | Vídeo nativo a pantalla completa, imagen y sonido, sin cursor ni ratón externo |
| 9. Menú y orientación | Durante el vídeo, pulsar Atrás; cerrar con Ver vídeo | Panel utilizable con mando, foco visible, siempre horizontal y vídeo sin reinicio |
| 10. Servidores | Abrir Omae gotoki 1; probar Voe y UPNShare en SUB/DUB; Byse completando personalmente su verificación | Los primeros deben pasar de preparación a vídeo nativo. Byse debe mostrar Play, permitir volver del puntero con Atrás y entregar vídeo después de tu verificación. HLS sigue experimental y puede rechazar con 403 |
| 11. Minuto guardado | Adelantar a 2:00, dejar 10 segundos, pausar, salir y retomar | Minuto guardado en el panel y retorno aproximadamente al punto anterior; cambiar de servidor conserva el progreso |
| 12. Historial | Abrir varios episodios y volver a Historial | Orden de apertura correcto; se pueden reabrir y cambiar las marcas de visto |
| 13. Segundo plano | Mientras reproduce, pulsar Inicio; regresar después | No queda audio inesperado en segundo plano; al cerrar el reproductor se libera el vídeo |
| 14. Sin conexión | Desconectar la red después de cargar títulos y volver a abrir la app | Se conserva el catálogo local; los fallos de red no borran favoritos, historial, vistos ni progreso |
| 15. Tamaño de pantalla | Revisar textos, botones y márgenes desde la distancia habitual | Contenido legible, sin recortes por los bordes de la TV |
| 16. Pendientes y fecha | Abrir Mi lista, comprobar el último episodio/fecha, marcar un capítulo como visto y regresar; usar Actualizar episodios | El contador baja al marcar visto y sube al desmarcar. Todos vistos muestra Al día. La fecha corresponde al último episodio; si falta, Fecha no disponible. Nuevas publicaciones aparecen también en la ficha |
| 17. Ocultar al día | Activar la casilla de la barra superior derecha con OK, cerrar/reabrir y desactivar | Solo se ocultan series comprobadas con cero pendientes, la preferencia se conserva y desactivarla recupera los títulos. Sin comprobar/sin episodios permanecen visibles |

No marques como superada la reproducción solo porque carga la página: confirma movimiento del vídeo y audio. Tampoco el progreso solo por ver un tiempo: cierra y retoma para comprobar la restauración.

## Límites conocidos

- No se ha validado físicamente esta entrega en Android TV. Las pruebas en un móvil con el modo TV no sustituyen tu prueba de mando, audio, códecs y firmware.
- MP4Upload, Voe, UPNShare y Byse tienen reproducción nativa confirmada en móvil; Byse necesita intervención humana cuando su proveedor la exige. La disponibilidad puede variar por episodio, idioma y dispositivo. Android System WebView actualizado es necesario para preparar Voe/UPNShare/Byse.
- HLS/Zilla entrega un manifiesto pero rechaza la descarga nativa con HTTP 403 en las pruebas. No se presenta como compatible ni se evitan sus controles de acceso.
- Los datos de TV son independientes de los del móvil. No hay cuenta, Supabase ni sincronización todavía.
- No hay descargas de episodios ni reproducción sin conexión.

## Qué enviarme si algo falla

```text
Modelo de TV o dispositivo:
Versión de Android/Google TV:
Versión de Android System WebView (si aparece en Ajustes):
Versión de AnimeFlix: 0.8.1-tv-preview1
Número de prueba:
Anime / episodio / servidor:
Pasos exactos y tecla pulsada:
Resultado observado y mensaje de error:
¿Se reproduce ese episodio desde un navegador en ese mismo dispositivo?:
```

Si se pierde el foco, indica qué control estaba seleccionado y la última dirección pulsada. Si el puntero no llega a un control o desaparece en pantalla completa, indica en qué pantalla ocurrió.
