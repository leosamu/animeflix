# Código y versiones en GitHub

Repositorio: https://github.com/leosamu/animeflix

- `main`: código actual, registrado mediante commits.
- Etiquetas Git: instantáneas de código de cada entrega.
- Releases: APK y notas correspondientes a cada etiqueta; las versiones anteriores se conservan.
- GitHub Actions: comprueba compilación móvil/TV, pruebas JVM, lint y compilación de pruebas QA. No ejecuta pruebas en dispositivos.

La primera etiqueta es `v0.5.0-tv-preview.1`. Se marca como prerelease porque TV aún necesita validación física. No se han reconstruido versiones históricas que nunca se guardaron como commits.

## Descargas y visibilidad

El repositorio es público (comprobado el 10 de noviembre de 2026). Los archivos de Releases publicados se descargan sin iniciar sesión. Este despliegue no cambia la visibilidad del repositorio. Para TV puede usarse el enlace directo al APK correspondiente a la etiqueta.

Según la entrega, se adjunta `AnimeFlix-Mobile.apk`, `AnimeFlix-TV.apk` o ambos; los enlaces incluyen la etiqueta y quedan asociados a esa versión. `v0.7.0-tv-preview.1` entrega el APK TV nativo, su guía y SHA-256. `releases/latest/download/…` se reserva para una futura versión estable: GitHub no trata un prerelease como la última versión estable.

`v0.8.0-tv-preview.1` entrega ambos APK con marcadores de pendientes y fecha del último episodio, guía TV y SHA-256 de móvil/TV. Mantiene las firmas de prueba de 0.7.0.

`v0.8.1-tv-preview.1` añade el interruptor Ocultar al día en ambos APK, conservando los archivos de la entrega 0.8.0 y las firmas de actualización.

## Publicar la siguiente versión

1. Cambiar `versionName` y aumentar `versionCode` en los módulos de aplicación que se actualicen.
2. Actualizar `CHANGELOG.md` y ejecutar las comprobaciones del proyecto.
3. Compilar los APK localmente conservando la misma clave de firma que en la entrega anterior.
4. Guardar los cambios con un commit, subir `main` y esperar el resultado de Actions.
5. Crear una etiqueta nueva y subirla, por ejemplo:

```powershell
git tag -a v0.5.0-tv-preview.2 -m "Segunda prueba de Android TV"
git push origin v0.5.0-tv-preview.2
```

6. En GitHub, abrir Releases → Draft a new release, elegir esa etiqueta, añadir notas y subir los APK y su SHA-256. Marcar prerelease mientras siga en pruebas.

No sobrescribir etiquetas ni archivos de entregas anteriores: publicar una versión nueva para conservar el historial.

## Firma de las actualizaciones

### Entrega 0.9.0 del 10 de noviembre de 2026

`v0.9.0-tv-preview.1` se distribuye como prerelease para pruebas en dispositivo real, con APK release de móvil/TV no depurables, firma verificada, notas y hashes. La publicación se asocia al commit de la versión y se realiza primero como borrador, subiendo los archivos antes de hacerla pública. No se sobrescriben etiquetas ni archivos de otras versiones. La entrega local inicial conserva su manifiesto e instantánea anteriores a la publicación; los archivos publicados llevan su propio manifiesto con el commit de entrega.

La compilación de pruebas se realiza con:

```powershell
& ./scripts/build.ps1 -Tasks @(':app-mobile:assembleRelease', ':app-tv:assembleRelease', ':app-mobile:lintRelease', ':app-tv:lintRelease', ':core:data:testDebugUnitTest', '-PtestReleaseSigning=true')
& ./scripts/package-test-release.ps1
```

La opción explícita conserva la clave debug de este PC, pero el tipo release no activa la depuración. La clave difiere de la usada en el otro equipo: no se garantiza actualización sobre los APK históricos. Se conserva una copia privada adicional en `.tools/signing/this-pc-debug.keystore`, excluida de Git y del paquete de entrega. El empaquetador no sobrescribe una entrega local existente.

### Entregas anteriores

Las entregas actuales usan la firma local de desarrollo. La clave privada no se sube al repositorio. Debe conservarse localmente para que Android acepte actualizaciones sobre estas instalaciones. El certificado público se puede comprobar con `apksigner verify --print-certs`.

Actions compila con su propia clave temporal de desarrollo y guarda solo informes; esos APK no se publican como actualizaciones instalables. Antes de automatizar una distribución estable se necesitará una clave de firma persistente almacenada como secreto de GitHub, y planificar la transición desde las instalaciones de prueba. No se han inventado secretos ni una firma de producción.
