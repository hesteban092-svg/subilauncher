# Mi Launcher (Subaru Outback)

## Compilar (sin instalar nada)
1. Crea un repositorio en github.com y sube TODO el contenido de esta carpeta (incluida `.github`).
2. Pestaña **Actions** -> "Build APK" -> espera ~5 min.
3. Entra a la ejecución terminada -> **Artifacts** -> descarga `MiAutoLauncher-apk`.

## Instalar
USB -> instala el APK -> Home -> "Mi Launcher" -> "Siempre".

## Foto del auto
Ya usa la foto de tu diseño. De noche: `drawable-night-nodpi/car_photo.png`; de día: `drawable-nodpi/car_photo.png` (puedes cambiarlas por fotos tuyas, ~1240x600).

## Canbus (puertas / reversa)
VEHICLE o el engrane: reglas `EVENTO|acción|clave|valor` y visor de broadcasts en vivo.

## ELM327 (aceite, refrigerante, CVT)
1. Empareja el ELM327 Bluetooth con la radio (ajustes de Bluetooth). Con WiFi: tipo `wifi` y `192.168.0.10:35000`.
2. En VEHICLE pon tipo `bt` y el nombre del ELM (o déjalo vacío para que busque uno que diga OBD/ELM), y GUARDA.
3. PIDs por defecto: refrigerante `0105` (A-40) y aceite `015C` (A-40, estándar; en foros se reporta que el Outback lo responde).
4. CVT: es un PID propio de Subaru, no estándar. En foros de Legacy/Outback 2015-2019 (TR580) aparece como "10D2", pero no tengo confirmado el servicio, el header ni la fórmula. Pruébalo en el terminal:
   `ATSH7E1` y luego `2210D2` (o `2210D2` con otros headers). Si responde `62 10 D2 xx`, usa una línea tipo `CVT|7E1|2210D2|A-40` y ajusta la fórmula hasta que coincida con otro medidor.
