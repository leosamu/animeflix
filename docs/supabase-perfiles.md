# Cuenta familiar y perfiles — 0.9.0

Una cuenta Supabase Auth (correo y contraseña) contiene varios perfiles de visualización, por ejemplo Niño y Niña. No son cuentas de acceso separadas: cualquiera que haya iniciado sesión en la cuenta puede cambiar entre sus perfiles. No hay PIN ni filtros infantiles por edad en esta entrega.

Cada perfil tiene su propia lista de favoritos, historial de aperturas, episodios vistos, minuto de reproducción y preferencia «Ocultar al día». La caché de catálogo e inventario de episodios sigue siendo local y recuperable desde AnimeAV1; los datos personales se replican en Supabase.

## Estado de la integración

Proyecto `tgbcswlcvbnwnfchmtpt` configurado localmente el 10 de noviembre de 2026 mediante URL y clave pública en `local.properties` (excluido de Git). Se han comprobado Auth y la existencia de ambas tablas con acceso anónimo denegado. El usuario confirmó entrada y creación/separación de perfiles en móvil; en el emulador TV se verificaron entrada, dos perfiles y recuperación de listas/historial/progreso de la cuenta. Siguen pendientes la prueba SQL de RLS entre cuentas, la matriz completa de conflictos entre dispositivos y la recuperación real de contraseña. La vista previa local sigue disponible por separado y no envía sus datos a la nube.

## Crear y conectar el proyecto

1. Crear una cuenta/proyecto en [Supabase](https://supabase.com/dashboard). Elegir región y plan desde el panel. Guardar la contraseña de la base de datos fuera del repositorio.
2. Ejecutar `supabase/migrations/202611100001_accounts_profiles.sql` en SQL Editor, una vez sobre un proyecto nuevo. Puede aplicarse también con las migraciones de Supabase CLI.
3. Ejecutar `supabase/tests/profile_isolation.sql`. Es una prueba transaccional con rollback: comprueba aislamiento entre cuentas, perfiles, anonimato, inserciones manipuladas y el upsert que usa Android. No ejecutada todavía contra Supabase.
4. En Authentication, habilitar el proveedor de correo/contraseña. Si se mantiene la confirmación de correo, confirmar la cuenta desde el mensaje antes de iniciar sesión. Configurar Site URL y SMTP según el entorno; la app informa cuando hace falta confirmar.
   En Authentication → URL Configuration, añadir las direcciones exactas de retorno de las variantes que se vayan a usar:
   - `com.animeflix.mobile.preview://auth-callback` (AnimeFlix Perfiles)
   - `com.animeflix.mobile://auth-callback` (móvil principal)
   - `com.animeflix.tv://auth-callback` (TV)
   Para las pruebas actuales, Site URL también es `com.animeflix.mobile.preview://auth-callback`. La app solicita explícitamente la dirección de su propia variante al registrar o recuperar una cuenta.
5. Añadir a `local.properties` (excluido de Git), junto a `sdk.dir`:

```properties
SUPABASE_URL=https://TU_PROYECTO.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_TU_CLAVE_PUBLICA
```

También acepta la clave pública legacy `anon`. El código rechaza claves `service_role` y `sb_secret_`. La clave pública va dentro del APK por diseño: el control de acceso real está en el token de sesión y en las políticas RLS.

6. Compilar e instalar. No se requieren claves administrativas en Android.

```powershell
& ./scripts/build.ps1 -Tasks @(':app-mobile:assembleDebug', ':app-tv:assembleDebug')
```

## Vista previa en el móvil

```powershell
& ./scripts/build.ps1 -Tasks @(':app-mobile:assembleCloudPreview')
& ./.tools/android-sdk/platform-tools/adb.exe install -r app-mobile/build/outputs/apk/cloudPreview/app-mobile-cloudPreview.apk
```

Se instala como **AnimeFlix Perfiles** (`com.animeflix.mobile.preview`). La opción «Probar perfiles en este móvil» crea Niño y Niña; se pueden añadir más. «Cambiar perfil» abre el selector sin cerrar sesión. Los datos de esta prueba son locales y no se importan automáticamente en una cuenta real.

## Persistencia y sincronización

- Room v6, con migración explícita v5→v6. Las bases de cada cuenta/perfil tienen nombres derivados de ambos identificadores; no comparten favoritos ni historial. El reproductor mantiene el perfil con el que se abrió aunque llegue un guardado diferido.
- Los cambios se guardan primero en Room, con una revisión y una marca de pendiente en la misma fila. La cola sobrevive al cierre de la app y al cambio de perfil.
- Se sincroniza tras cada cambio, al abrir o volver a la app, cada 30 segundos mientras está visible y con el botón «Sincronizar». Sin conexión, queda pendiente y se muestra un aviso. No es una sincronización de fondo cuando Android cierra la app.
- Supabase guarda filas por `(profile_id, kind, item_key)`. Favoritos desactivados y vistos desmarcados se guardan como valores falsos, evitando que reaparezcan por una descarga posterior. El progreso y los vistos son campos independientes.
- Si dos dispositivos modifican el mismo campo sin conexión, gana el último valor que llegue al servidor. No se usa el reloj del móvil para resolver conflictos. Los cambios en campos distintos no se pisan.
- Una confirmación antigua no borra una edición local más reciente. Una descarga tampoco sustituye una fila local pendiente.
- El cierre de sesión elimina los tokens locales, pero conserva las cachés aisladas para la siguiente entrada de esa misma cuenta. No cierra las sesiones de otros dispositivos. No se guardan contraseñas. Los tokens se cifran con AES-GCM usando Android Keystore y un archivo excluido de backups.
- Se renuevan los tokens antes de caducar y se reintenta una vez ante 401. Si el refresh token es inválido, se vuelve al acceso con cuenta.

## Recuperar la contraseña

En Iniciar sesión, escribir el correo y pulsar **He olvidado mi contraseña**. Abrir el correo nuevo en el mismo dispositivo: el enlace debe abrir AnimeFlix y mostrar **Nueva contraseña**, con la dirección de la cuenta validada mediante Supabase. Introducir dos veces la nueva contraseña, guardar e iniciar sesión. Los enlaces de confirmación solo muestran una indicación para iniciar sesión; no importan una sesión automáticamente.

Si un correo anterior ya llevó a `localhost:3000`, la opción **Ya tengo un enlace de recuperación** permite pegar la dirección final dentro de la propia app, mientras el token siga vigente. No compartir estos enlaces en chats: contienen tokens. La importación de localhost solo se acepta por esa acción manual. Se rechazan otros orígenes, parámetros duplicados, tipos no admitidos y enlaces con errores de expiración. No se guardan ni se registran los enlaces; la sesión de recuperación se mantiene únicamente en memoria, se valida contra `/auth/v1/user` y se utiliza para actualizar la contraseña. Tras guardar se intenta cerrar esa sesión de recuperación y se vuelve al inicio de sesión.

El 10 de noviembre, el usuario confirmó haber guardado la dirección de retorno de la vista previa en Supabase. Se verificaron compilación móvil/TV, lint sin errores, cuatro pruebas del lector de enlaces y resolución del enlace en Android hacia la actividad correcta. Verificación real de cambio de contraseña pendiente del intento del usuario en el móvil.

El primer intento de pedir otro correo encontró un límite de solicitudes. La app distingue `over_email_send_rate_limit` del resto de errores 429; puede importarse el enlace ya recibido mientras su sesión siga vigente, sin enviar otro correo. El proveedor integrado de Supabase permite actualmente dos correos por hora por proyecto; para un despliegue real debe configurarse un SMTP propio. Referencia: [límites de Auth](https://supabase.com/docs/guides/auth/rate-limits). No se hicieron envíos automáticos ni se cambió el límite del proyecto.

La importación manual también admite el enlace original `https://PROYECTO.supabase.co/auth/v1/verify?...` exclusivamente del proyecto configurado: intercambia el hash en `/auth/v1/verify` sin seguir redirecciones. Un enlace original ya utilizado no se puede intercambiar de nuevo; su dirección final con `#access_token=…` es una sesión diferente, válida hasta que caduque o se invalide. El pegado acepta también el formato Markdown de chat, delimitadores y separadores escapados sin relajar la validación del origen. Siete pruebas unitarias del lector de enlaces superadas; ampliación instalada en el móvil. Recuperación real pendiente de confirmar con el usuario.

## Validación pendiente en Supabase

Validación local del 10 de noviembre: ambas apps compilan; lint sin errores; ocho pruebas unitarias y tres instrumentadas correctas (migración v2→v6, aislamiento de perfiles y protección de la cola ante confirmaciones/descargas antiguas). Vista previa instalada y abierta en el móvil Android 15 con catálogo real. Se corrigió el solapamiento de la cabecera con la barra de estado. El dispositivo deniega `adb shell input tap` por falta de `INJECT_EVENTS`; no se automatizó la interacción visual con el selector.

Tras configurar el proyecto, `SupabaseConnectionTest` pasó en el móvil físico: conexión HTTPS real a Auth, registro por correo habilitado y acceso anónimo denegado a ambas tablas. Apps móvil/TV recompiladas y vista previa conectada instalada. Esta prueba no crea cuentas, no envía correos y no acredita operaciones autenticadas. Para entrar desde una sesión de prueba local: Cambiar perfil → Salir de la prueba → Crear una cuenta; confirmar el email y después iniciar sesión.

Tras desplegar: registrar y confirmar una cuenta, crear dos perfiles, guardar favoritos/progreso distintos, cerrar/reabrir, entrar desde un segundo dispositivo, comprobar sincronización y cambios sin conexión. Crear otra cuenta para confirmar que sus perfiles y datos no son accesibles. Probar renovación/caducidad de sesión y desconexión. No considerar la nube validada hasta completar estas pruebas.

Referencias oficiales: [Supabase Auth](https://supabase.com/docs/guides/auth), [sesiones](https://supabase.com/docs/guides/auth/sessions), [seguridad por filas](https://supabase.com/docs/guides/database/postgres/row-level-security).
