# AnimeFlix Android TV — 0.5.0 preview 1

Esta versión es para pruebas mediante instalación manual. Compilación y análisis estático verificados; no se ha probado en un Android TV físico ni en un emulador TV. No está publicada en Google Play. APK firmado con clave de desarrollo, sin claves de producción.

## Archivo e instalación

Archivo entregado: `dist/AnimeFlix-TV-0.5.0-preview1.apk`. Android 6.0 o posterior (API 23), Android TV/Google TV y conexión a internet para el catálogo y el vídeo. Dispositivos basados en Android que no sean Android TV/Google TV pueden no mostrar el acceso de TV; Fire TV no está validado.

1. Copia el APK al dispositivo por USB o mediante tu herramienta habitual de transferencia.
2. Ábrelo con un gestor de archivos y permite instalar aplicaciones desde ese gestor cuando Android lo solicite.
3. Instálalo y abre **AnimeFlix TV** en la lista de aplicaciones. En algunos lanzadores hay que añadirlo manualmente a favoritos.
4. Si ya tenías una versión anterior de `com.animeflix.tv`, instala encima para conservar datos. Si Android rechaza la actualización por firma distinta, no desinstales sin valorar que se borrarán los datos locales; anota el mensaje y comunícalo.

Opcional, si usas ADB en tu propio entorno:

```text
adb install -r AnimeFlix-TV-0.5.0-preview1.apk
adb shell am start -n com.animeflix.tv/.MainActivity
```

El SHA-256 del archivo se incluye en `dist/SHA256SUMS.txt`. La firma es de desarrollo: las futuras actualizaciones de prueba deben mantener la misma firma. Una futura distribución con firma de producción puede requerir un procedimiento de migración.

## Cómo usar el mando

- Cruceta y OK: mover el foco y seleccionar los controles de la aplicación.
- Atrás: cerrar un diálogo, volver de una ficha al catálogo o regresar a Explorar desde otras secciones.
- Buscar: abre el campo de texto y el teclado disponible en tu TV. También puedes usar un teclado USB/Bluetooth; no se ha integrado búsqueda por voz propia.
- En el reproductor, selecciona **Controlar vídeo con mando**. Aparece un puntero blanco: muévelo con la cruceta y pulsa OK sobre Play, pausa o pantalla completa.
- Con el puntero cerca del borde superior/inferior, sigue pulsando para desplazar la página. Mantener pulsada una dirección acelera el movimiento.
- Atrás sale del puntero y devuelve el foco al botón de control. Pulsa Atrás otra vez para regresar. En pantalla completa del proveedor puede ser necesario salir primero de esa pantalla.
- Play/Pausa del mando depende del reproductor web: si no responde, usa el puntero sobre el control del vídeo.

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
| 8. Reproducción | Abrir un episodio, dejar Reproductor de la web, entrar al control con mando y pulsar Play | Vídeo y audio; sin necesidad de pantalla táctil ni ratón externo |
| 9. Pantalla completa | Pulsar el control del proveedor con el puntero y salir con Atrás | Entra y sale sin cierre; se puede recuperar el foco de los controles de la app |
| 10. Servidores | Si falla el predeterminado, probar Elegir servidor | Se cargan las opciones; los fallos permiten volver o usar otra opción. No todos los proveedores están validados |
| 11. Minuto guardado | Adelantar a 2:00, dejar 10 segundos, pausar, salir y retomar | En un proveedor compatible aparece Minuto guardado y vuelve aproximadamente al punto guardado; hay que pulsar Play |
| 12. Historial | Abrir varios episodios y volver a Historial | Orden de apertura correcto; se pueden reabrir y cambiar las marcas de visto |
| 13. Segundo plano | Mientras reproduce, pulsar Inicio; regresar después | No queda audio inesperado en segundo plano; al cerrar el reproductor se libera el vídeo |
| 14. Sin conexión | Desconectar la red después de cargar títulos y volver a abrir la app | Se conserva el catálogo local; los fallos de red no borran favoritos, historial, vistos ni progreso |
| 15. Tamaño de pantalla | Revisar textos, botones y márgenes desde la distancia habitual | Contenido legible, sin recortes por los bordes de la TV |

No marques como superada la reproducción solo porque carga la página: confirma movimiento del vídeo y audio. Tampoco el progreso solo por ver un tiempo: cierra y retoma para comprobar la restauración.

## Límites conocidos

- No se ha validado físicamente la navegación del mando, el puntero, el teclado ni el vídeo en TV. Es una primera versión de prueba.
- El vídeo usa los reproductores web originales de AnimeAV1. Su compatibilidad y controles dependen del proveedor, del firmware y de Android System WebView. La alternativa Elegir servidor no tiene la misma validación que el modo web probado en móvil.
- La posición automática está limitada a los orígenes de reproductor configurados y a las funciones disponibles en WebView. No se inventa un minuto donde no llegue progreso válido.
- Los datos de TV son independientes de los del móvil. No hay cuenta, Supabase ni sincronización todavía.
- No hay descargas de episodios ni reproducción sin conexión.

## Qué enviarme si algo falla

```text
Modelo de TV o dispositivo:
Versión de Android/Google TV:
Versión de Android System WebView (si aparece en Ajustes):
Versión de AnimeFlix: 0.5.0-tv-preview1
Número de prueba:
Anime / episodio / servidor:
Pasos exactos y tecla pulsada:
Resultado observado y mensaje de error:
¿Se reproduce ese episodio desde un navegador en ese mismo dispositivo?:
```

Si se pierde el foco, indica qué control estaba seleccionado y la última dirección pulsada. Si el puntero no llega a un control o desaparece en pantalla completa, indica en qué pantalla ocurrió.
