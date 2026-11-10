# Descargas de episodios: estudio de viabilidad

Fecha: 10 de noviembre de 2026. Alcance: revisión del código y prueba HTTP acotada; funcionalidad todavía no implementada.

## Resultado

Es viable añadir descargas voluntarias para reproducción sin conexión usando Media3, que ya utiliza el reproductor nativo. La compatibilidad depende del episodio y del servidor. El primer alcance razonable es móvil con MP4Upload; ampliar otros proveedores después de comprobarlos individualmente.

## Evidencia real

Se consultó One Piece, episodio 1, y se resolvió su embed MP4Upload con el mismo agente y Referer que usa el resolvedor de producción. Se pidió exclusivamente `Range: bytes=0-65535`, sin seguir redirecciones del recurso multimedia.

| Comprobación | Resultado |
|---|---|
| Respuesta multimedia | HTTP 206 Partial Content |
| Content-Range | bytes 0-65535/196515679 |
| Datos leídos | 65.536 bytes (64 KiB) |
| Cabecera MP4 | Caja `ftyp` presente |
| Tamaño declarado | 196.515.679 bytes, unos 196,5 MB / 187,4 MiB |
| Tipo comunicado por el servidor | application/octet-stream |

Esto acredita acceso parcial al archivo y compatibilidad técnica inicial con una descarga HTTP por tramos. No acredita una descarga completa, reproducción offline, integridad de todo el vídeo, reanudación tras caducar el enlace ni compatibilidad general del catálogo. No se descargó el capítulo completo ni se inspeccionó toda su estructura/posible protección. La prueba se ejecutó desde el PC; queda pendiente la descarga real en Android.

La herramienta y el informe sin enlaces temporales están en `.tools/offline-feasibility-probe.ps1` y `.tools/offline-feasibility-probe.json`, excluidos de Git. No se guardaron URL temporales, cookies ni el fragmento de vídeo.

## Encaje en el código actual

- `Mp4UploadResolver` entrega un MP4 HTTPS a partir de la configuración publicada en el embed; es el candidato más sencillo.
- Voe y UPNShare entregan fuentes temporales usando una WebView y, a veces, cookies/Origin/Referer. Su reproducción nativa está documentada, pero la descarga completa y reanudación siguen sin probarse.
- HLS/Zilla tiene rechazos HTTP 403 documentados. No debe darse por compatible con descargas.
- Byse puede exigir verificación humana. No se puede prometer resolución/reanudación automática en segundo plano.
- `NativeServerScreen` usa una fuente HTTP de Media3. No tiene caché de descargas ni ruta local de reproducción.
- `PlayerActivity` valida la URL del episodio y el flujo actual obtiene servidores por red. Hará falta una entrada de reproducción de descargas que no intente resolver proveedores al estar sin conexión.
- El catálogo, las fichas y el inventario tienen caché Room por perfil, y los vistos/progreso ya tienen cola local. Eso permite integrar metadatos y progreso offline sin depender de Supabase durante la reproducción.
- La aplicación distingue los embeds de los enlaces de descarga del sitio; el analizador actual excluye estos últimos deliberadamente. Este estudio utilizó la fuente MP4 del reproductor ya soportado, no enlaces de otros gestores de archivos.

## Primera implementación propuesta

1. Botón **Descargar episodio** y sección **Descargas**, con estado, progreso, tamaño y acciones de cancelar/eliminar. Mostrar opción solo para fuentes compatibles y distinguir descarga completa de incompleta.
2. Cola persistente mediante `DownloadService`, `DownloadManager`, `DownloadIndex` y caché dedicada de Media3. Configurar Wi-Fi por defecto, espacio libre y notificación de progreso; contemplar las restricciones de trabajo en segundo plano de Android.
3. Guardar vídeo y metadatos en el propio dispositivo, con acceso por cuenta/perfil. No subir capítulos ni cookies a Supabase. La disponibilidad de una descarga es local al dispositivo, no una propiedad sincronizable como un favorito.
4. Índice local con identidad de episodio, servidor/idioma/calidad, perfil propietario e identificador estable de la descarga. El identificador no puede ser la URL temporal, que caduca.
5. Fuente local de reproducción que lea exclusivamente la caché al elegir un capítulo descargado, sin usar la red como alternativa oculta. Conservar el guardado de progreso y vistos del perfil original.
6. Tratamiento explícito de errores: poco espacio, conexión interrumpida y enlace caducado. Antes de reanudar con un enlace renovado, verificar que sigue siendo el mismo archivo; si cambia o no se puede garantizar, reiniciar la descarga en lugar de mezclar bytes.
7. Mantener los encabezados temporales aislados por descarga/origen y protegidos localmente cuando sean necesarios. La implementación actual no los persiste; no basta con copiar sin más un `ResolvedVideo` al índice de descargas.

Para HLS haría falta descargar manifiestos, pistas y segmentos, no solo el archivo `.m3u8`. Las fuentes protegidas requerirían soporte y licencias offline del proveedor; este estudio no acredita su disponibilidad. No se contempla eludir rechazos, desafíos ni protecciones.

## Prueba de aceptación pendiente

Descargar un episodio completo en el móvil, reiniciar la app, activar modo avión y comprobar imagen, sonido, búsqueda dentro del vídeo y restauración del minuto. Cambiar de perfil y verificar aislamiento. Después recuperar la conexión y comprobar sincronización del progreso. Probar además cancelación, eliminación, espacio insuficiente, interrupción de red y URL caducada. No considerar validado el modo offline hasta completar estas pruebas.

Referencia oficial: [descargas y reproducción offline con Media3](https://developer.android.com/media/media3/exoplayer/downloading-media).
