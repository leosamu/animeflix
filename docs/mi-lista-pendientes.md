# Mi lista: pendientes y última publicación

Disponible en móvil 0.8.0 y TV 0.8.0-tv-preview1.

- Cada favorito muestra **N pendientes**, **Al día**, **Sin episodios** o **Pendientes por comprobar**.
- Pendiente significa episodio publicado que no has marcado como visto. Abrirlo o guardar un minuto no lo marca automáticamente. No se cuentan huecos en la numeración ni se duplican los idiomas de un mismo episodio.
- El marcador cambia al marcar/desmarcar un visto, también al usar **Anteriores vistos**. Esta opción permite ponerse al día con series largas sin marcar uno por uno.
- Se muestra **Último: episodio N · dd/MM/aaaa** en Mi lista, la ficha y las tarjetas con datos guardados. Es la publicación del episodio disponible de número más alto, obtenida del `publishedAt` de su propia página. No se usa la actualización de la ficha, la fecha de emisión ni la hora de comprobación como sustituto.
- Si no se puede obtener la fecha, aparece **Fecha no disponible**. Si aún no se consultaron sus episodios, no se inventa un contador ni se muestra Al día.
- Al entrar o regresar a Mi lista/ficha se comprueba la información si lleva 15 minutos sin actualizarse. **Actualizar episodios** o **Actualizar episodios y fecha** fuerza una consulta nueva. Hasta tres títulos se consultan a la vez.
- La ficha usa la misma lista guardada de episodios: una nueva publicación actualiza también los botones para verla. Sin conexión se conservan los datos anteriores y las marcas de visto siguen actualizando el contador local. Un fallo de red muestra el aviso correspondiente.

Room pasa de v4 a v5 con una tabla adicional para las listas y la fecha. La migración conserva favoritos, historial, vistos y minutos guardados. Móvil y TV continúan teniendo datos independientes; no se añaden cuentas, sincronización ni notificaciones en segundo plano.

## Ocultar al día — 0.8.1

En la barra superior derecha de Explorar y Mi lista hay un interruptor en móvil y una casilla accionable con OK en TV. Oculta solo series con episodios publicados comprobados y cero pendientes. Las series sin comprobar o todavía sin episodios siguen visibles. La preferencia se guarda en cada dispositivo y sobrevive al cierre/recreación de la app.

No cambia favoritos, vistos ni historial. La ficha de una serie abierta no se cierra al ponerse al día. Las series ocultas siguen incluidas en la comprobación de favoritos: una nueva publicación pendiente las devuelve a la lista al actualizar los datos. Si todos los resultados están ocultos, se indica cómo desactivar el filtro; el catálogo conserva Cargar más cuando hay más páginas.

Validación: pruebas de episodios con huecos/especiales, títulos distintos, vistos explícitos y cambios Al día → pendiente; fechas UTC/offset/microsegundos y ausencia de publicación; migraciones v2/v4 a v5 y reapertura; interfaz con contador/fecha, recreación y llegada de un episodio nuevo; consulta real de Omae gotoki y persistencia de su fecha. Las pruebas QA usan una aplicación separada.
