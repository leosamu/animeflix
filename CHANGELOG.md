# Historial de versiones

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
