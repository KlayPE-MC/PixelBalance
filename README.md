# Pixel Balance

Aplicación local y sin anuncios para revisar batería, temperatura, memoria y
almacenamiento de Android. Incluye accesos a la limpieza y administración de
almacenamiento oficial del sistema, sin matar procesos ni borrar datos por su
cuenta.

Aplicación de diagnóstico local para Android 10 o posterior. Diseñada para ser
una alternativa honesta a los limpiadores agresivos: no mata procesos, no borra
archivos personales, no contiene publicidad y no envía información a servidores.

## Funciones

- Batería, temperatura, estado térmico, almacenamiento y memoria disponible.
- Detección opcional de aplicaciones de usuario sin abrir en los últimos 30 días.
- Accesos directos a los ajustes oficiales de almacenamiento y batería.
- Todas las decisiones de limpieza o desinstalación quedan en manos del usuario.

## Privacidad

No incluye el permiso `INTERNET`. `PACKAGE_USAGE_STATS` es opcional y se concede
desde Ajustes únicamente para detectar aplicaciones abandonadas de forma local.

## Límites deliberados

Android administra la RAM por sí mismo. La app no intenta matar aplicaciones ni
"liberar RAM", porque eso suele aumentar el consumo al obligar a reiniciarlas.

## Compilación con GitHub Actions

Al subir este proyecto a GitHub, la acción **Build Pixel Balance APK** aparece en
la pestaña *Actions*. También se ejecuta automáticamente con cada cambio enviado
a `main`. El APK queda disponible durante 14 días como el artefacto
`PixelBalance-debug-apk`.

