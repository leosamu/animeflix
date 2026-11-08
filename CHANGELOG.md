# Historial de versiones

## v0.7.0-tv-preview.1 — 2026-11-08

- Android TV usa reproducción nativa por defecto con los mismos resolvedores de MP4Upload, Voe, UPNShare y Byse validados en móvil.
- Vídeo a pantalla completa y siempre horizontal. Atrás/Menú abre el panel de servidores sin desmontar ni reiniciar el vídeo; Ver vídeo devuelve el foco a Media3.
- Panel desplazable con foco de borde blanco para seleccionar servidor/idioma, reintentar y marcar visto.
- Byse conserva su verificación humana: puntero activable con mando durante la preparación y salida con Atrás que recupera el foco de la aplicación. No se automatiza el desafío.
- Alternativa web conservada, con opción para regresar al modo nativo. HLS continúa experimental por rechazo HTTP 403.
- Aplicación TV 0.7.0-tv-preview1, código 13. Firma de desarrollo conservada para actualizar la preview anterior sin borrar datos. Prueba en Android TV físico pendiente del usuario.

## Móvil 0.7.0 — 2026-11-08

- Preparación de fuentes temporales mediante el embed del servidor, con una sola selección e idioma aislados y posterior reproducción con Media3. Primer servidor validado: Voe.
- Motor HLS de Media3 y conservación del agente, Referer, cookies y Origin observados al resolver la fuente.
- Los enlaces permanecen en memoria; reintentar obtiene una fuente nueva. Tiempo límite y mensaje de error cuando el servidor no entrega vídeo compatible.
- Cada cambio de servidor tiene su propio estado para evitar que una petición anterior cancelada oculte el reproductor nuevo.
- Pruebas integradas en paquete QA separado para HLS, UPNShare, Voe y Byse en SUB/DUB, con primer fotograma, progreso y pantalla completa cuando hay vídeo. Audio pendiente de revisión manual.
- Sin activar todavía la ruta nativa en Android TV.
- HLS/Zilla entrega manifiesto pero rechaza la descarga nativa con 403. Byse abre un iframe en n1mwq.org que solicita verificación humana; se presenta el recuadro y se espera la intervención manual, sin automatizarla.
- El usuario confirma que Byse reproduce con controles nativos después de completar personalmente la verificación en 0.7.0.

## Móvil 0.6.2 — 2026-11-08

- Corrige el regreso de pantalla completa: vuelve explícitamente a vertical para evitar que se recorten controles en horizontal.
- La prueba real de reproducción comprueba también la orientación vertical después de pulsar Atrás.

## Móvil 0.6.1 — 2026-11-08

- Primera reproducción nativa por servidor con Android Media3: MP4Upload.
- Selección automática del servidor compatible, lectura validada de su configuración pública y reobtención de enlaces temporales al reintentar.
- Controles nativos de pausa/avance, foco de audio y progreso local.
- Pantalla completa desde el botón o el icono del vídeo y salida mediante Atrás sin reiniciar el reproductor.
- Prueba instrumental con un vídeo real de MP4Upload, en aplicación QA separada.
- HLS, UPNShare, Voe y Byse pendientes de integración nativa. Pruebas de reproducción TV aplazadas hasta validar móvil.

## v0.5.0-tv-preview.2 — 2026-11-08

- Corrige el espacio insuficiente del WebView observado en la prueba real de TV: reproductor a pantalla completa y controles en panel superpuesto.
- Atrás/Menú abre los controles; Ver vídeo los oculta y recupera el puntero.
- El iframe original se ajusta al tamaño completo de la pantalla sin cambiar su fuente; opción para restaurar la página completa.
- El foco del WebView se desactiva mientras se usa el panel; el puntero se limita al área visible al cambiar de tamaño.
- Se mantiene la firma de desarrollo para actualizar sobre preview 1 sin borrar datos.

Pendiente de nueva comprobación física en la TV del usuario.

## v0.5.0-tv-preview.1 — 2026-11-08

Primera versión del proyecto publicada en GitHub. Incluye móvil 0.5.0 y Android TV 0.5.0-tv-preview1.

- Catálogo AnimeAV1, portadas, búsqueda y fichas.
- Favoritos locales, episodios, historial y marcas de visto.
- Marcado de episodios anteriores en bloque.
- Guardado y retorno al minuto en reproductores web compatibles, confirmado en móvil.
- Primera interfaz TV para mando y puntero dentro del reproductor, pendiente de pruebas físicas.
- Datos y reproductor compartidos; Room con migraciones hasta v4.
- Comprobaciones automáticas de compilación, pruebas JVM y lint para móvil y TV.

APK de desarrollo, firmados para pruebas. Android TV no se considera una versión estable. Sin cuentas, Supabase ni sincronización entre dispositivos.

Las etapas anteriores se documentan en `docs/environment.md`; no se han creado commits o etiquetas históricos ficticios para ellas.
