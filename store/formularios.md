# Respuestas sugeridas para los formularios de Google Play

Guía para completar las declaraciones pendientes de **Hope** (`dev.tacuchi.hope`) en Play Console. Cada respuesta refleja el comportamiento real del código fuente citado.

> [!NOTE]
> Las preguntas exactas de Play Console pueden variar ligeramente de redacción. Ante cualquier duda, manda el texto actual del formulario en Play Console.

---

## 1. Seguridad de los datos (Data safety)

### Resumen rápido

| Pregunta del formulario | Respuesta sugerida | Fundamento en el código |
|---|---|---|
| ¿Tu app recopila o comparte alguno de los tipos de datos de usuario obligatorios? | **No** (ver nota sobre vista previa de YouTube abajo) | Todo se guarda solo en almacenamiento interno (`filesDir`, Room, `SharedPreferences`) sin servidor propio, sin cuentas, sin analítica y sin SDKs de terceros que envíen telemetría (`app/src/main/java/dev/tacuchi/hope/AppContainer.kt`, `app/src/main/java/dev/tacuchi/hope/data/HopeDatabase.kt`, `app/src/main/java/dev/tacuchi/hope/data/AppearanceRepository.kt`, `app/build.gradle.kts`). |
| ¿Todos los datos recopilados se encriptan en tránsito? | *(Solo aplica si se responde Sí arriba; si se declara consulta oEmbed: **Sí**, solo HTTPS)* | Toda conexión saliente usa exclusivamente `https://` (`app/src/main/java/dev/tacuchi/hope/links/LinkPreviews.kt`, `app/src/main/java/dev/tacuchi/hope/ui/common/LinkUi.kt`). |
| ¿Ofreces una forma de que los usuarios soliciten la eliminación de sus datos? | *(No aplica si no recopila datos fuera del dispositivo)* | No existe servidor ni cuenta; borrar una hope/asset o desinstalar la app elimina los datos locales (`app/src/main/java/dev/tacuchi/hope/data/HopeRepository.kt`, `app/src/main/java/dev/tacuchi/hope/data/AssetRepository.kt`). |

### Detalle por permiso y capacidad (evidencia en código)

1. **Almacenamiento local (archivos, hopes y preferencias): NO sale del dispositivo**
   - Base de datos SQLite (Room) con hopes y catálogo de assets: `app/src/main/java/dev/tacuchi/hope/data/HopeDatabase.kt`.
   - Archivos de audio e imágenes importados en `filesDir/assets/`: `app/src/main/java/dev/tacuchi/hope/AppContainer.kt` y `app/src/main/java/dev/tacuchi/hope/data/AssetRepository.kt`.
   - Carpetas temporales borradas al terminar o cancelar: `filesDir/import-tmp/` (`app/src/main/java/dev/tacuchi/hope/importing/ImportStaging.kt`) y `filesDir/recording-tmp/` (`app/src/main/java/dev/tacuchi/hope/recording/VoiceRecorder.kt`).
   - Preferencias de tema y paleta en `SharedPreferences("appearance", MODE_PRIVATE)`: `app/src/main/java/dev/tacuchi/hope/data/AppearanceRepository.kt`.
   - Según la definición de Google Play Data Safety, los datos que solo se procesan y guardan localmente en el dispositivo **no cuentan como «recopilación»**.

2. **Micrófono (`RECORD_AUDIO`): solo grabación local iniciada por la persona**
   - Declaración de permiso: `app/src/main/AndroidManifest.xml`.
   - Solicitud en tiempo de ejecución únicamente al tocar «Grabar»: `app/src/main/java/dev/tacuchi/hope/ui/editor/EditorScreen.kt`.
   - Captura a archivo `.m4a` local con `MediaRecorder`: `app/src/main/java/dev/tacuchi/hope/recording/VoiceRecorder.kt`.
   - Nunca se transmite audio por red ni se accede al micrófono en segundo plano.

3. **Red (`INTERNET` y `ACCESS_NETWORK_STATE`): únicamente vista previa oEmbed de YouTube y apertura de enlaces**
   - Permiso `INTERNET` declarado en `app/src/main/AndroidManifest.xml`; `ACCESS_NETWORK_STATE` lo hereda la dependencia Media3 en el manifiesto combinado.
   - **Vista previa de enlaces de YouTube:** cuando la persona pega o comparte una URL de YouTube, `LinkPreviews.of` hace un `GET` sin caché a `https://www.youtube.com/oembed?format=json&url=...` para leer título y URL de miniatura (`app/src/main/java/dev/tacuchi/hope/links/LinkPreviews.kt`). Luego `RemoteThumbnail` carga esa imagen `https://` solo en memoria (`app/src/main/java/dev/tacuchi/hope/ui/common/LinkUi.kt`).
   - **Nota de criterio sobre Data Safety:** la URL enviada es un enlace público de YouTube que la persona pegó explícitamente, sin identificadores de usuario ni telemetría; según la guía de Data Safety de Play, abrir enlaces o pedir metadatos públicos iniciados por el usuario sin transmitir datos personales del usuario se responde habitualmente como **«No recopila ni comparte datos de usuario»**. Sin embargo, esta consulta sí envía la URL del video y la dirección IP del dispositivo a servidores de YouTube (Google), por lo que está explicada de forma transparente en `docs/privacidad/index.html`.
   - **Abrir enlaces externos:** «Abrir original», «Buscar en redes» y «Política de privacidad» lanzan `Intent.ACTION_VIEW` hacia la app o navegador del sistema (`app/src/main/java/dev/tacuchi/hope/ui/common/LinkUi.kt`, `app/src/main/java/dev/tacuchi/hope/links/Links.kt`, `app/src/main/java/dev/tacuchi/hope/ui/settings/SettingsScreen.kt`). Hope no descarga contenido ni envía datos propios.

4. **Dependencias y SDKs**
   - Todas las dependencias en `app/build.gradle.kts` son bibliotecas locales de AndroidX (Compose, Navigation 3, Room, Media3, Lifecycle, Core) y `AboutLibraries` (solo muestra licencias desde `res/raw/aboutlibraries.json`).
   - No hay Firebase, Crashlytics, AdMob, Analytics ni SDKs publicitarios o de seguimiento.

---

## 2. Permisos de servicio en primer plano (Foreground service permissions)

Permisos presentes en el manifiesto combinado de lanzamiento:
- `android.permission.FOREGROUND_SERVICE` (`app/src/main/AndroidManifest.xml`)
- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK` (`app/src/main/AndroidManifest.xml`)
- `android.permission.WAKE_LOCK` (incorporado por Media3 para mantener la reproducción activa con la pantalla apagada)

### Tipo de servicio declarado

| Campo en Play Console | Respuesta sugerida | Fundamento en el código |
|---|---|---|
| Tipo de servicio en primer plano | **Reproducción de contenido multimedia (`mediaPlayback`)** | `<service android:name=".playback.PlaybackService" android:foregroundServiceType="mediaPlayback" android:exported="false">` en `app/src/main/AndroidManifest.xml`. |
| ¿Cuál es la funcionalidad de la app que requiere este servicio? | Reproducción continua de *hopes* (mezcla en vivo de un audio de fondo y una grabación de voz del usuario) cuando la pantalla se apaga o el usuario cambia a otra app. | `PlaybackService` extiende `MediaSessionService` de Media3 y expone `MixPlayer` mediante una `MediaSession` con controles en la notificación (pausa/reproducir, barra de posición y botón «Cerrar») (`app/src/main/java/dev/tacuchi/hope/playback/PlaybackService.kt`, `app/src/main/java/dev/tacuchi/hope/playback/MixPlayer.kt`, `app/src/main/java/dev/tacuchi/hope/playback/SessionPlayer.kt`). |
| ¿Cómo inicia y detiene el servicio el usuario? | **Inicio:** solo cuando el usuario toca Reproducir en una hope desde la biblioteca o el editor (`AppContainer.kt` → `startService(PlaybackService)`). **Control y parada:** el usuario puede pausar, buscar posición o cerrar definitivamente la reproducción desde el botón «Cerrar» de la notificación o desde la app; al detenerse o terminar la sesión, el servicio llama a `stopForeground(STOP_FOREGROUND_REMOVE)` y `stopSelf()`. También se pausa al perder el foco de audio o desconectar audífonos. | `app/src/main/java/dev/tacuchi/hope/AppContainer.kt`, `app/src/main/java/dev/tacuchi/hope/playback/PlaybackService.kt`, `app/src/main/java/dev/tacuchi/hope/playback/PlaybackFocus.kt`. |
| ¿Por qué afecta al usuario si se interrumpe? | El propósito central de Hope es escuchar mensajes breves de ánimo grabados por la propia persona mientras camina, guarda el teléfono en el bolsillo o usa otra app. Sin el servicio en primer plano, Android cortaría el audio al apagar la pantalla o salir de la app. | `app/src/main/java/dev/tacuchi/hope/playback/PlaybackService.kt`, `app/src/main/java/dev/tacuchi/hope/playback/MixPlayer.kt`. |
| Video de demostración requerido | Adjuntar `store/video-segundo-plano.mp4` (subirlo a YouTube como «No listado» o como enlace directo según pida Play Console). | Muestra una hope reproduciéndose en la app, el paso a la pantalla de inicio/bloqueo con la notificación multimedia visible y el audio activo en segundo plano. |

### Texto breve listo para pegar en Play Console (español)

> Hope permite al usuario crear y escuchar *hopes* (mezclas de audio locales compuestas por una pista de fondo y su propia voz). El servicio en primer plano `PlaybackService` (`mediaPlayback`, basado en `MediaSessionService` de Media3) se inicia únicamente cuando el usuario pulsa Reproducir, para que el audio siga sonando con la pantalla apagada o al cambiar a otra aplicación. Mientras está activo, muestra una notificación multimedia con controles de pausa, posición y un botón «Cerrar» que detiene el servicio y retira la notificación de inmediato.

### Texto breve listo para pegar en Play Console (inglés, opcional si el revisor lo prefiere)

> Hope lets users create and listen to personal audio mixes ("hopes") combining a local background track and their own recorded voice. The foreground service `PlaybackService` (`mediaPlayback`, built on Media3 `MediaSessionService`) starts only when the user taps Play, keeping playback running while the screen is locked or while using other apps. It shows a persistent media notification with play/pause, seek position, and a Close action that stops playback and removes the foreground service immediately.
