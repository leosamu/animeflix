# Historial de versiones

## v0.10.2-tv-preview.1 — 2026-11-12

- Descargas pasa a ser la cuarta pestaña inferior del móvil, junto a Explorar, Mi lista e Historial. La biblioteca permanece accesible durante la carga o un fallo del catálogo.
- La acción de cada episodio muestra reproducir sin conexión cuando ya está descargado, progreso/acceso a la cola si está pendiente y descargar si todavía no está disponible. Estado compartido por perfil en móvil/TV; un único observador para toda la lista.


## v0.10.1-tv-preview.1 — 2026-11-12

- Cola persistente de un capítulo activo por dispositivo; enlaces preparados al llegar el turno y cancelación independiente de los capítulos en espera.
- Rotación y alternativas entre MP4Upload, Voe y UPNShare, conservando idioma. Fuentes fallidas descansan temporalmente; cambio de origen lento si hay alternativas y espera cuando todos están en descanso.
- Descarga MP4/HLS mediante Media3, selección de una variante de vídeo/audio/subtítulos y reproducción de la caché sin HTTP alternativo. Descriptor local cifrado y cabeceras temporales por intento.
- Conserva vídeos terminados de 0.10.0 y reinicia sus transferencias activas en la nueva cola.
- Móvil código 22; TV código 23, misma firma de las entregas anteriores.


## v0.10.0-tv-preview.1 — 2026-11-11

- Descargas MP4Upload desde la lista o el reproductor y biblioteca local por perfil. Cola de Android con estado, progreso, tamaño, redes sin coste medido por defecto, cancelar/eliminar y reintentar con enlace renovado. Reproducción exclusivamente local con progreso y vistos. Consulta `docs/descargas-offline.md` para compatibilidad y validación.
- Móvil: código 20; TV: código 21. Se conserva la firma de las versiones anteriores de este PC.
- Lista de episodios móvil/TV con filtro Ocultar vistos antes de paginar, estado sin pendientes y acciones vectoriales para marcar visto y anteriores en una sola fila. Se conserva la confirmación de marcado masivo.
- Reproductor con la paleta compartida, acciones móviles agrupadas, vídeo con esquinas redondeadas fuera de pantalla completa y menús TV compactos con servidor seleccionado y foco visible.


## v0.9.1-tv-preview.1 — 2026-11-10

- Corrige el recorrido de foco del acceso: correo → Siguiente → contraseña → Listo → Entrar.
- Android TV recupera el foco al cerrar el teclado con Atrás. Con el teclado cerrado, cruceta arriba/abajo navega entre campos y OK abre la edición; no se vuelve a abrir el teclado solo por seleccionar un campo.
- Formulario compartido de móvil/TV; no cambia cuentas, perfiles, contraseñas ni datos sincronizados.
- Dos pruebas instrumentadas con formulario ficticio y teclado virtual TV verifican acciones IME y navegación después de Atrás, sin realizar peticiones de autenticación.
- Móvil 0.9.1 (18), TV 0.9.1-tv-preview1 (19). Conserva la firma de 0.9.0 de este PC y la incidencia de vídeo en emulador pendiente de validar en TV real.

## v0.9.0-tv-preview.1 — 2026-11-10

- Rediseño visual previo a Release: selector de perfiles con avatares vectoriales, cuadrícula adaptable, creación en diálogo y estados de foco para mando. Cabecera compacta del perfil activo y paleta oscura/coral compartida.
- Historial móvil con portadas, tarjeta Seguir viendo basada en progreso real, barras de avance, agrupación por fecha y estado vacío. Historial TV con portadas y acciones enfocables. Se conservan las marcas de visto y la separación de perfiles.
- Descargas offline pospuestas a una versión posterior; estudio de viabilidad disponible en `docs/descargas-offline-viabilidad.md`.

- Recuperación de contraseña desde la app, pantalla de nueva contraseña y enlaces de confirmación/recuperación por variante Android. Importación manual de enlaces antiguos de localhost, con validación de origen y token contra Supabase. Botón Mostrar contraseña y paso automático del registro al acceso. Flujo real de recuperación pendiente de confirmar con el usuario.

- Inicio de sesión y registro por correo/contraseña con Supabase Auth, confirmación de correo y renovación de sesión. Tokens cifrados con Android Keystore; no se guardan contraseñas.
- Una cuenta contiene varios perfiles; selector compartido por móvil/TV, creación de perfiles y cambio sin cerrar sesión.
- Favoritos, historial, vistos, progreso y Ocultar al día separados por perfil. Bases locales por cuenta/perfil; los guardados diferidos del reproductor conservan el perfil original.
- Cola persistente y sincronización por campo; reintento manual y al volver a la app. Políticas RLS y claves foráneas para aislar cuentas.
- Room v6 y migración 5→6. Vista previa local `com.animeflix.mobile.preview` con Niño/Niña, instalada como AnimeFlix Perfiles.
- Móvil 0.9.0 (17) y TV 0.9.0-tv-preview1 (18). APK de tipo release, no depurables, con firma local de pruebas activada explícitamente. Entrega GitHub como prerelease con APK, notas y SHA-256; se conservan las entregas anteriores.
- Supabase configurado localmente. Usuario confirmó acceso y perfiles en móvil; emulador TV comprobado con dos perfiles, listas diferentes, historial y progreso. Consulta SQL de RLS entre cuentas y recuperación completa de contraseña pendientes.
- La reproducción en el emulador mostró imagen duplicada con ruido de color. Causa sin determinar; validación en Android TV real pendiente.

## v0.8.1-tv-preview.1 — 2026-11-08

- Interruptor Ocultar al día arriba a la derecha en móvil y casilla con mando en TV, en Explorar y Mi lista.
- Oculta únicamente series comprobadas con episodios publicados y cero pendientes. Mantiene visibles las que no tienen datos o aún no publican capítulos; Historial y fichas no se filtran.
- La preferencia se conserva al recrear/cerrar la aplicación. Cuando llega un capítulo pendiente a una serie oculta, reaparece al actualizar sus datos.
- Mensaje específico cuando el filtro oculta todos los resultados; el catálogo mantiene Cargar más disponible.
- Móvil 0.8.1 (16), TV 0.8.1-tv-preview1 (17), sin cambios adicionales de base de datos.

## v0.8.0-tv-preview.1 — 2026-11-08

- Móvil 0.8.0 (14) y TV 0.8.0-tv-preview1 (15): Mi lista muestra el número de episodios publicados sin marcar como vistos, Al día o estado sin comprobar.
- Último episodio y fecha real de publicación en favoritos, ficha y tarjetas con información guardada. Fecha no disponible cuando falta el dato; no se reutiliza la actualización de la ficha.
- Comprobación al entrar/regresar, caché de 15 minutos, actualización manual y conservación de información sin conexión. Los vistos y Anteriores vistos actualizan el indicador inmediatamente.
- La lista de episodios de la ficha recibe los cambios del mismo inventario, para poder abrir una nueva publicación detectada.
- Migración Room 4→5 sin borrar favoritos, historial, vistos o progreso; migraciones antiguas conservadas. Datos independientes por dispositivo.

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
