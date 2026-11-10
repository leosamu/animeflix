# Revisión visual previa a Release — 10 de noviembre de 2026

Historial y gestión de perfiles actualizados para seguir la identidad oscura/coral del catálogo, con una presentación más visual inspirada en aplicaciones de streaming.

## Perfiles

- Cuadrícula adaptable con avatares vectoriales, colores diferenciados por posición y nombres de hasta dos líneas.
- Fondo con degradado sutil, título centrado y acciones secundarias separadas de la selección.
- Añadir perfil abre un diálogo con nombre, crear y cancelar; conserva las operaciones existentes de Supabase.
- Foco con borde coral y ampliación discreta para navegación por mando.
- Cabecera compacta del perfil activo con avatar, cambio de perfil y sincronización.
- La paleta se comparte entre catálogo, perfiles y componentes de móvil/TV.

## Historial

- Móvil: portadas, tarjeta Seguir viendo para un episodio con progreso real pendiente, barras de avance, actividad agrupada por fecha y estado vacío diseñado.
- Las aperturas siguen diferenciadas de los vistos. Marcar/desmarcar continúa siendo una acción explícita; no se inventa progreso ni se marca visto al abrir.
- TV: tarjetas con portada, avance y acciones enfocables para continuar/abrir y cambiar la marca de visto.
- No cambian el esquema, la sincronización ni el aislamiento por cuenta/perfil.

## Verificación

- Compilaciones `assembleCloudPreview`, `assembleQa` y `app-tv:assembleDebug` correctas.
- `lintCloudPreview` y `app-tv:lintDebug`: cero errores. Persisten avisos de dependencias/manifest ajenos a esta revisión.
- Revisión visual en el móvil Android 15 de perfiles con nombres largos, historial con progreso y portadas reales, y estado vacío.
- Capturas locales en `.tools/ui-review/` (excluidas de Git): `profiles.png`, `history.png`, `history-empty.png`.
- La vista `UiReviewActivity` pertenece solo a la variante QA. Usa datos ficticios y no lee/escribe la cuenta del usuario ni accede a Supabase. No se empaqueta en la vista previa ni en Release.
- Revisión adicional en emulador Android TV API 36: perfiles, catálogo, favoritos e historial visibles; navegación con cruceta/OK y cambio entre perfiles comprobados. La revisión en una TV física queda pendiente; la reproducción en emulador no se ha probado en esta revisión.

No se ha publicado una Release con esta revisión. Las descargas offline quedan pospuestas a la siguiente versión.
