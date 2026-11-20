# Hope · guía para agentes

App Android para armar *hopes*: audio que da ánimo, una portada y la voz de la persona encima. Todo vive en el teléfono: sin login ni servidor.

Specs, planes y diseño viven en el hub `personal-lab/projects/hope-app`, no en este repo.

## Versiones fijadas

Las versiones viven en `gradle/libs.versions.toml`. Esta tabla las repite y se actualiza en el mismo cambio.

| Componente | Versión |
|---|---|
| Android Gradle Plugin | 9.5.1 |
| Gradle (wrapper) | 9.9.2 |
| Kotlin | 2.4.21 |
| Compose BOM | 2026.11.01 (Material 3 1.5.0) |
| Navigation 3 | 1.2.2 |
| Activity Compose | 1.13.0 |
| Lifecycle (runtime, ViewModel, ViewModel para Navigation 3) | 2.11.1 |
| Core KTX | 1.19.1 |
| Core SplashScreen | 1.2.0 |
| kotlinx-serialization | 1.13.0 |
| Room | 2.8.5 |
| KSP | 2.3.14 |
| Media3 (Transformer, ExoPlayer, Common) | 1.11.1 |
| JUnit | 4.13.2 |
| Robolectric | 4.18.1 |
| AndroidX Test core / ext-junit / runner | 1.7.0 / 1.3.0 / 1.7.0 |
| JDK | 21 |
| SDK | minSdk 29 · targetSdk 36 · compileSdk 37.2 |

- **compileSdk 37.2 con targetSdk 36:** core 1.19 y el BOM 2026.11 exigen compilar contra 37.1 o más. targetSdk 36 es el que pide Play, así que el comportamiento en ejecución no cambia. Requiere `platforms;android-37.2` en el SDK.
- Solo versiones estables: nunca alfa, beta ni RC. Cada coordenada se verifica en Google Maven o Maven Central antes de subirla.

## Arquitectura

- Un solo módulo `app`, paquete `dev.tacuchi.hope`.
- Una sola actividad (`MainActivity`) con la API de pantalla de inicio y `enableEdgeToEdge()`. Todo se dibuja en Compose.
- Navegación con Navigation 3: rutas `@Serializable` que implementan `NavKey` en `navigation/HopeNavigation.kt`, pila con `rememberNavBackStack`. Cada destino tiene sus ViewModels (`rememberViewModelStoreNavEntryDecorator`): se limpian al salir de la pila.
- Pantallas en `ui/<pantalla>/`. Cada una recibe callbacks de navegación, nunca la pila.
- Estado: cuando una pantalla tiene datos, un `ViewModel` expone `StateFlow` y la pantalla lo lee con `collectAsStateWithLifecycle()`. La parte visual sin estado (`AssetsContent`) se prueba sola.
- Inyección manual: `HopeApplication` crea un `AppContainer` con la base, los repositorios, los servicios de importación, el reproductor de mezcla y el grabador; cada ViewModel tiene su `Factory`. Con un solo módulo, Hilt sumaría plugin y anotaciones sin beneficio.
- Datos (`data/`): Room guarda el catálogo de assets y las hopes; los archivos viven en `filesDir/assets/`. El nombre visible se arma con el tipo y el número (o el momento del fotograma), no se guarda. «Nota de voz» es solo para OGG u Opus; el resto de los audios importados es «Audio». El esquema se exporta a `app/schemas/`.
- Hopes: guardan qué assets usan (portada, fondo, voz) y sus ajustes (volumen por pista, recorte del fondo, desfase de la voz). La mezcla no se graba: ocurre al reproducir. Las referencias a assets son claves foráneas `RESTRICT`: un asset en uso no se borra, y Assets muestra qué hopes lo usan. Borrar una hope no borra sus assets.
- Migraciones: automáticas (`AutoMigration`) mientras solo sumen tablas o columnas, nunca destructivas. Cada versión nueva se prueba abriendo una base de la anterior con assets (`HopeRepositoryTest`).
- Mezcla (`playback/MixPlayer`): un solo reproductor para el editor y la biblioteca, en el contenedor. Es un `CompositionPlayer` de Media3 con dos secuencias que se mezclan en una sola salida de audio: el fondo recortado y la voz detrás de un silencio del largo de su desfase. Por salir juntas no se desfasan (con dos ExoPlayer la voz llegaba con 230 a 320 ms de atraso). El volumen de cada pista es un `GainProcessor` que se ajusta en vivo. La duración de una hope va del inicio del recorte al final de la última pista. Solo suena con la app a la vista: `MainActivity.onStop` lo pausa.
- Grabación (`recording/`): `MediaRecorder` en AAC dentro de M4A a `filesDir/recording-tmp/`. «Listo» crea el asset «Grabación N»; descartar borra el temporal. El permiso de micrófono se pide al tocar Grabar. Con audífonos (cable, USB, Bluetooth A2DP o LE, según `AudioManager.getDevices`) el fondo suena mientras se graba; por el altavoz se graba en silencio, con un aviso.
- Importación (`importing/`): todo lo recibido se copia primero a `filesDir/import-tmp/<sesión>/`, se inspecciona por su contenido (no por la extensión) y solo al confirmar pasa a ser asset. Cancelar o fallar borra la sesión.
- Extracción: Media3 Transformer quita el video y deja M4A con AAC. Transformer es API inestable de Media3: su uso queda encerrado en `AudioExtractor` y `MixPlayer`, con `@OptIn(UnstableApi::class)`.
- Compartir: `MainActivity` es `singleTask` y recibe `ACTION_SEND` de video, audio e imagen en `onCreate` y `onNewIntent`. El origen sale del referrer con una tabla de apps conocidas; si no, «Compartido».
- Al terminar, una importación va adonde la pidieron (`ImportPurpose`): desde Assets, a Assets; compartida, a Nueva hope con el audio y la portada; desde el editor, de vuelta al editor. Cancelar en «Elegir portada» guarda igual el audio extraído.

## Tema y diseño

- `ui/theme/Theme.kt` define `HopeTheme` con los esquemas claro y oscuro de la marca. Sin color dinámico.
- El lima `#b6f23c` es relleno (`primaryContainer`) en los dos temas. En claro nunca va como texto: el acento de texto es `primary` `#4c6700`.
- Componentes, tipografía (Roboto) y formas de Material 3 por defecto. Lo único propio es la paleta.
- Íconos: Material Symbols Outlined como vectores en `res/drawable/ic_*.xml`. Se usan con `painterResource`.
- Lo que una fase todavía no implementa se ve deshabilitado, nunca roto.

## Convenciones

- Kotlin con el estilo oficial (`kotlin.code.style=official`). Análisis: solo Android Lint.
- Todo texto visible vive en `res/values/strings.xml`, en español.
- De borde a borde: cada pantalla usa `Scaffold` y aplica su `innerPadding`.
- Commits cortos en español, con prefijo de Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `test:`).

## Pruebas

- Unitarias en `app/src/test`. Las de interfaz y las de Room (base en memoria) corren con Robolectric dentro de `testDebugUnitTest`, sin emulador.
- Instrumentadas en `app/src/androidTest`: inspección de archivos reales, extracción con Media3 y la mezcla (`MixPlayerTest` mide el nivel de la salida mezclada: la voz entra a su desfase con ±100 ms y el fondo baja a su volumen). Necesitan emulador: `./gradlew connectedDebugAndroidTest`. No corren en CI.
- Los archivos de prueba son sintéticos (tonos y colores). Se regeneran con `scripts/generar-medios-de-prueba.sh`; con una carpeta como argumento genera además los grandes (60 s y 11 min) para probar en el emulador, que no van al repo.
- `ui/theme/ContrastTest.kt` mide el contraste WCAG de los pares de roles usados: texto 4,5:1, íconos y bordes 3:1.
- `navigation/NavigationTest.kt` recorre los destinos, vuelve con Atrás y comprueba que la pila sobrevive a recrear la actividad.
- Cada prueba demuestra un comportamiento; no se prueban mapeos triviales.

## Comandos

```sh
./gradlew assembleDebug lintDebug testDebugUnitTest   # lo mismo que corre CI
./gradlew installDebug                                # instala en el dispositivo conectado
```

Gradle necesita el SDK: `ANDROID_HOME` o `local.properties` con `sdk.dir`.

## Probar en el emulador

El emulador necesita micrófono del host para grabar (`hw.audioInput=yes` en el `config.ini` del AVD). Los audífonos no se pueden conectar: la decisión de si suena el fondo se cubre con pruebas unitarias.

Con Android CLI (`android`, agregar `--no-metrics`):

```sh
android emulator start Pixel_9_Pro_XL
./gradlew installDebug
adb shell am start -n dev.tacuchi.hope/.MainActivity
android screen capture -o captura.png
android layout                                  # textos y estado de los controles
adb shell cmd uimode night yes                  # tema oscuro (no para claro)
android emulator stop
```

Compartir desde la terminal con `adb shell am start -a android.intent.action.SEND` no sirve: el shell no puede conceder permiso de lectura sobre un archivo de MediaStore. Se comparte desde la app Archivos del emulador (mantener pulsado, Compartir, Hope).

## Probar en el teléfono

1. Activar las opciones de desarrollador y la depuración USB.
2. Conectar por USB y confirmar con `adb devices`.
3. `./gradlew installDebug` y abrir Hope.
4. Capturas con `android screen capture --device=<serial> -o captura.png`.
5. Errores: `adb logcat --pid=$(adb shell pidof dev.tacuchi.hope)`; no debe aparecer `FATAL EXCEPTION`.

## Integración continua

`.github/workflows/ci.yml` corre `assembleDebug lintDebug testDebugUnitTest` en cada push y pull request a `main`. Un cambio que solo toca `**.md` o `docs/**` no la dispara. Instala la plataforma 37.2 si el runner no la trae.
