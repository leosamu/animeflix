# Actualizaciones desde la aplicación

Desde la selección de perfiles, pulsa **Buscar actualización**. La comprobación es manual y consulta las releases públicas de `leosamu/animeflix`, incluidas las versiones de prueba. No depende de `/releases/latest`, que omite las prereleases.

Si hay un código de versión superior para el paquete instalado, se descarga el APK correspondiente mediante DownloadManager. Puedes salir de la pantalla: Android continúa la transferencia y la aplicación recupera su estado al volver a abrirse. Los APK se guardan en el directorio privado externo `updates/`; no se modifican las descargas de episodios.

Al terminar aparece **Instalar actualización**. Se comprueban SHA-256, paquete móvil/TV, código superior, ausencia de modo depuración y coincidencia de la firma con la aplicación instalada. Android puede pedir que autorices a AnimeFlix para instalar aplicaciones; vuelve después y pulsa Instalar otra vez. La instalación requiere confirmación del sistema. No desinstales la aplicación: así conserva sus datos.

La primera versión con este botón todavía debe instalarse manualmente. QA y las variantes de perfiles de prueba no instalan actualizaciones de producción. La recuperación de la descarga no implica instalación automática ni comprobaciones periódicas.

El cliente consulta los veinte últimos lanzamientos. Cada release compatible debe incluir `release-manifest.json` con `artifacts` y el APK referido por `file`, `applicationId`, `versionCode`, `versionName`, `sha256` y `debuggable: false`. Un cambio de firma requiere un procedimiento de migración y no se acepta como actualización ordinaria.

Referencias: [instalación desde fuentes autorizadas](https://developer.android.com/reference/android/content/pm/PackageManager#canRequestPackageInstalls()), [FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider).

Validación local: cuatro pruebas JVM de selección de artefactos. La instalación completa desde una release futura y la pantalla de permisos de cada fabricante deben validarse al publicar esa versión.
