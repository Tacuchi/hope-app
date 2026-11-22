# Hope · guía para agentes

App Android para armar *hopes*: audio que da ánimo, una portada y la voz de la persona encima. Todo vive en el teléfono: sin login ni servidor. La red se usa solo para la vista previa de un enlace (ver «Enlaces»).

Specs, planes y diseño se gestionan fuera de este repo.

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
| Dagger Hilt (plugin, runtime y compilador) | 2.60.1 |
| AndroidX Hilt (ViewModel para Compose) | 1.4.0 |
| Media3 (Transformer, ExoPlayer, Common, Session) | 1.11.1 |
| AboutLibraries (plugin Android y Compose M3) | 15.3.0 |
| kotlinx-coroutines (dominio) | 1.9.0 |
| javax.inject (dominio) | 1 |
| JUnit | 4.13.2 |
| Robolectric | 4.18.1 |
| AndroidX Test core / ext-junit / runner | 1.7.0 / 1.3.0 / 1.7.0 |
| JDK | 21 |
| SDK | minSdk 29 · targetSdk 36 · compileSdk 37.2 |

- **compileSdk 37.2 con targetSdk 36:** core 1.19 y el BOM 2026.11 exigen compilar contra 37.1 o más. targetSdk 36 es el que pide Play, así que el comportamiento en ejecución no cambia. Requiere `platforms;android-37.2` en el SDK.
- Solo versiones estables: nunca alfa, beta ni RC. Cada coordenada se verifica en Google Maven o Maven Central antes de subirla.

## Arquitectura

Clean Architecture estricta en once módulos Gradle. Cada responsabilidad tiene un solo dueño.

### Módulos

| Módulo | Tipo | Qué contiene |
|---|---|---|
| `:app` | aplicación | `HopeApplication`, `MainActivity`, intents de Compartir, rutas y pila de Navigation 3 (`navigation/`), grafo de Hilt (`di/AppModule.kt`), firma, versión y licencias. |
| `:core:domain` | Kotlin/JVM | Modelos propios (`domain.model`), contratos de datos (`domain.repository`) y de medios (`domain.media`), reglas puras y casos de uso (`domain.usecase`). Sin Android. |
| `:core:data` | biblioteca Android | Room (entidades, DAO, migraciones y `schemas/`), mappers, archivos de `filesDir/assets/`, preferencias de apariencia y vista previa por oEmbed. |
| `:core:media` | biblioteca Android | Importación (copia, inspección, fotogramas, extracción), grabación, salidas de audio, preescucha, `MixPlayer`, sesión y `PlaybackService`. |
| `:core:ui` | biblioteca Android | Tema, `AssetImage`, formato, nombres y subtítulos de assets, abrir enlaces, íconos y textos compartidos. |
| `:feature:library` | feature | Biblioteca, reproducción y borrado de hopes. |
| `:feature:assets` | feature | Catálogo, filtros, preescucha y usos. |
| `:feature:editor` | feature | Borrador, pistas, portada, grabación y mezcla. |
| `:feature:importing` | feature | Importación, elegir portada, cancelar y resultado. |
| `:feature:search` | feature | Buscar en redes (sin ViewModel). |
| `:feature:settings` | feature | Apariencia y licencias. |

Grafo de dependencias de proyecto:

- Cada feature depende solo de `:core:domain` y `:core:ui`. Ninguna feature ve a otra, ni a datos, medios o app.
- `:core:data`, `:core:media` y `:core:ui` dependen de `:core:domain`.
- `:core:domain` no depende de ningún proyecto. Usa solo kotlinx-coroutines y `javax.inject`.
- `:app` reúne todo: conecta cada contrato con su implementación y cada feature con su ruta.

Para comprobarlo: `./gradlew projects` y `./gradlew :feature:<f>:dependencies --configuration debugRuntimeClasspath`.

### Capas

- Dominio en Kotlin puro. Las URIs viajan como texto y las imágenes temporales como `File`. `Uri`, `Bitmap` y Media3 se resuelven en datos, medios o la UI.
- Datos y medios implementan los contratos y traducen sus tipos con mappers (`AssetEntity` ↔ `Asset`, por ejemplo).
- Casos de uso: verbo + objeto + `UseCase` (`SaveHopeUseCase`). Tienen `@Inject constructor` de `javax.inject`; Hilt los arma sin módulos.
- Los ViewModels reciben solo casos de uso, también para operaciones de una sola llamada. Nunca un repositorio ni un contrato de medios.

### Navegación

- Una sola actividad (`MainActivity`) con la API de pantalla de inicio y `enableEdgeToEdge()`. Todo se dibuja en Compose.
- Rutas `@Serializable` que implementan `NavKey` en `app/.../navigation/HopeNavigation.kt`, pila con `rememberNavBackStack`. Las rutas son parte de lo guardado: no se renombran.
- Cada feature expone su pantalla y recibe callbacks; nunca la pila. La app traduce entre destinos: por ejemplo, el resultado de Importación (`ImportPurpose`, assets del dominio) pasa al editor como `EditorImport` o como argumentos de `EditorRoute`.
- Cada destino tiene sus ViewModels (`rememberViewModelStoreNavEntryDecorator`): se limpian al salir de la pila.

### Hilt y ámbitos

- `HopeApplication` es `@HiltAndroidApp`; `MainActivity` y `PlaybackService` son `@AndroidEntryPoint`. Las features y `:core:media` generan su código de Hilt y la app lo agrega.
- `SingletonComponent`, una instancia por proceso: catálogo local (`LocalCatalog`: base y carpeta de assets), apariencia, vista previa de enlaces, importación, grabador, salidas de audio y `MixPlayer` (también como `HopePlayer`). Las carpetas de `filesDir` (`assets/`, `import-tmp/`, `recording-tmp/`), el nombre de la base (`hope.db`) y las preferencias (`appearance`) son los de la 0.1.0.
- `ViewModelComponent`: la preescucha de Assets (`AssetPreview`), `@ViewModelScoped`. Sus casos de uso comparten la instancia y el ViewModel la libera en `onCleared`.
- ViewModels de la actividad (`by viewModels()` en `MainActivity`): `SettingsViewModel` (la actividad pinta el tema con su estado) e `ImportViewModel` (recibe lo compartido y la navegación lo muestra).
- ViewModels por entrada de la pila (`hiltViewModel()` en el destino): `LibraryViewModel`, `AssetsViewModel` y `EditorViewModel`. El editor recibe `EditorArgs` con inyección asistida (`hiltViewModel<EditorViewModel, EditorViewModel.Factory> { it.create(args) }`).

### MVVM

- Cada pantalla con datos tiene un `ViewModel` con un único `StateFlow` de estado (`WhileSubscribed(5_000)` cuando combina flujos). La pantalla lo lee con `collectAsStateWithLifecycle()`.
- Ningún ViewModel guarda `Context` ni hereda de `AndroidViewModel`. Compose se queda con permisos, selectores del sistema, navegación y estado visual local. La parte visual sin estado (`LibraryContent`, `AssetsContent`) se prueba sola.
- Búsqueda no tiene ViewModel: solo abre los enlaces fijos de `Links.searchTargets`.

### Datos

- Room guarda el catálogo de assets y las hopes; los archivos viven en `filesDir/assets/`. El nombre cualificado de la base es `dev.tacuchi.hope.data.HopeDatabase` y su esquema se exporta a `core/data/schemas/`.
- El nombre visible se arma con el tipo y el número (o el momento del fotograma), no se guarda. «Nota de voz» es solo para OGG u Opus; el resto de los audios importados es «Audio».
- Hopes: guardan qué assets usan (portada, fondo, voz) y sus ajustes (volumen por pista, recorte del fondo, desfase de la voz). La mezcla no se graba: ocurre al reproducir. Las referencias a assets son claves foráneas `RESTRICT`: un asset en uso no se borra, y Assets muestra qué hopes lo usan. Borrar una hope no borra sus assets.
- Migraciones: automáticas (`AutoMigration`) mientras solo sumen tablas o columnas, nunca destructivas. Cada versión nueva se prueba abriendo una base de la anterior con assets (`HopeRepositoryTest`).

### Medios

- Mezcla (`playback/MixPlayer`): un solo reproductor para el editor, la biblioteca y el servicio. Es un `CompositionPlayer` de Media3 con dos secuencias que se mezclan en una sola salida de audio: el fondo recortado y la voz detrás de un silencio del largo de su desfase. Por salir juntas no se desfasan (con dos ExoPlayer la voz llegaba con 230 a 320 ms de atraso). El volumen de cada pista es un `GainProcessor` que se ajusta en vivo. La duración de una hope va del inicio del recorte al final de la última pista.
- `PlaybackService` (`dev.tacuchi.hope.playback.PlaybackService`, el mismo nombre de componente que antes) recibe esa instancia por inyección de campo y expone la mezcla por una sesión de Media3 para que siga sonando en segundo plano. Abre Hope con la actividad de lanzamiento del paquete. La notificación ofrece pausa, posición y Cerrar. Perder el foco de audio o desconectar los audífonos pausa la reproducción.
- Grabación (`recording/`): `MediaRecorder` en AAC dentro de M4A a `filesDir/recording-tmp/`. «Listo» crea el asset «Grabación N»; descartar borra el temporal. El permiso de micrófono se pide al tocar Grabar. Con audífonos (cable, USB, Bluetooth A2DP o LE, según `AudioManager.getDevices`) el fondo suena mientras se graba; por el altavoz se graba en silencio, con un aviso.
- Importación (`importing/`): todo lo recibido se copia primero a `filesDir/import-tmp/<sesión>/`, se inspecciona por su contenido (no por la extensión) y solo al confirmar pasa a ser asset. Los fotogramas de «Elegir portada» se escriben como JPEG temporales en la sesión. Cancelar o fallar borra la sesión.
- Extracción: Media3 Transformer quita el video y deja M4A con AAC. Transformer es API inestable de Media3: su uso queda encerrado en `TransformerAudioExtractor` y `MixPlayer`, con `@OptIn(UnstableApi::class)`.

### Compartir y enlaces

- Compartir: `MainActivity` es `singleTask` y recibe `ACTION_SEND` de video, audio, imagen y texto en `onCreate` y `onNewIntent`. El origen sale del referrer con una tabla de apps conocidas; si no, «Compartido». Un texto abre Nueva hope con su primer enlace puesto.
- Enlaces (`domain.model.Links`): una hope guarda un enlace a su video original (URL, red, título y URL de la miniatura). Es solo referencia: una hope sigue necesitando una pista, y nada del enlace se descarga ni entra a Assets. `Links.recognize` reconoce TikTok, YouTube (también `youtu.be` y Shorts) e Instagram (`/reel/` y `/p/`); cualquier otro `http(s)` es genérico. Buscar en redes abre `Links.searchTargets` (*hopecore*) con `ACTION_VIEW`: la app de la red si está, si no el navegador. TikTok no recibe enlaces web de búsqueda en su app, así que primero se prueba `tiktok://search?keyword=hopecore` y, si nadie lo abre (`ActivityNotFoundException`), la URL web. Ese esquema no está documentado por TikTok: si deja de abrir la búsqueda, queda la web. YouTube e Instagram abren su app con la URL web.
- Vista previa: solo YouTube, por oEmbed (`OEmbedLinkPreviews` en datos, `HttpURLConnection` sin caché). Se pide al recibir o pegar el enlace y al guardar, y se guarda con la hope: la biblioteca no consulta la red. La miniatura se dibuja desde la red solo en memoria (`RemoteThumbnail`). Sin conexión queda la ficha con la red. Es el único uso del permiso `INTERNET`.
- Al terminar, una importación va adonde la pidieron (`ImportPurpose`): desde Assets, a Assets; compartida, a Nueva hope con el audio y la portada; desde el editor, de vuelta al editor. Cancelar en «Elegir portada» guarda igual el audio extraído.

### Gradle

- Convenciones en el build incluido `build-logic/` (plugins `hope.*`), con el mismo catálogo `gradle/libs.versions.toml`:
  - `hope.android.application` y `hope.android.library`: SDK, JDK 21, Lint y pruebas locales con Robolectric.
  - `hope.android.compose`: compilador, BOM y herramientas de prueba de Compose.
  - `hope.android.hilt`: Hilt con su compilador por KSP.
  - `hope.android.room`: Room por KSP y el esquema en `schemas/` del módulo.
  - `hope.android.feature`: biblioteca + Compose + Hilt, con `:core:domain`, `:core:ui` y los dobles del dominio en las pruebas.
  - `hope.jvm.library`: Kotlin/JVM para el dominio.
- Firma, identidad, versión y AboutLibraries quedan en `app/build.gradle.kts`.
- Una feature nueva: carpeta `feature/<nombre>`, `include` en `settings.gradle.kts`, el plugin `hope.android.feature` y un `namespace` `dev.tacuchi.hope.ui.<nombre>`. La app la conecta con su ruta.

## Tema y diseño

- Ajustes permite elegir Sistema, Claro u Oscuro y las paletas Hope, Lima, Coral y Violeta. En Android 12 o superior también ofrece Dinámico. La app abre con Sistema y Hope y guarda la selección en `SharedPreferences` (`PreferencesAppearanceStore`). AboutLibraries genera las licencias durante la compilación.
- En la paleta Lima, `#b6f23c` es relleno (`primaryContainer`) en los dos temas. En claro nunca va como texto: el acento de texto es `primary` `#4c6700`.
- Componentes, tipografía (Roboto) y formas de Material 3 por defecto. Lo único propio es la paleta.
- Íconos: Material Symbols Outlined como vectores en `core/ui/src/main/res/drawable/ic_*.xml`. Se usan con `painterResource`. La notificación tiene su propio ícono de cierre en `:core:media`.
- Lo que una fase todavía no implementa se ve deshabilitado, nunca roto.

## Convenciones

- Kotlin con el estilo oficial (`kotlin.code.style=official`). Análisis: solo Android Lint.
- Todo texto visible vive en `res/values/strings.xml` del módulo que lo usa, en español. Lo que comparten varios módulos vive en `:core:ui` y se usa con un alias (`import dev.tacuchi.hope.ui.R as UiR`). Los íconos están en `:core:ui`.
- De borde a borde: cada pantalla usa `Scaffold` y aplica su `innerPadding`.
- Commits cortos en español, con prefijo de Conventional Commits (`feat:`, `fix:`, `chore:`, `docs:`, `test:`).

## Pruebas

- Cada módulo tiene sus pruebas locales en `src/test`. Las de interfaz, Room (base en memoria) y medios corren con Robolectric dentro de `testDebugUnitTest`, sin emulador.
- El dominio es Kotlin/JVM: sus pruebas corren con `./gradlew :core:domain:test`. `testDebugUnitTest` no las incluye.
- Cada ViewModel tiene su `*ViewModelTest` en su feature: se construye a mano con los casos de uso reales sobre dobles de sus contratos (nunca mocks).
- Dobles compartidos en `core/domain/src/testFixtures/` (`FakeAssetRepository`, `FakeHopeRepository`, `FakeHopePlayer`, `FakeMediaInspector`, `collectWhileTesting`). Las features los usan con `testImplementation(testFixtures(project(":core:domain")))`, que ya aplica `hope.android.feature`. Un doble que usa una sola suite vive junto a ella.
- Para activar los estados con `WhileSubscribed`, la prueba recoge el flujo mientras corre. `./gradlew testDebugUnitTest --tests '*ViewModelTest'` corre solo esas suites.
- `core/data/.../HopeRepositoryTest.kt` prueba las migraciones y el recorrido de escritura y lectura por los contratos del dominio.
- Las pruebas que abren `MainActivity` (`:app`) usan el grafo real: Robolectric crea la `HopeApplication` de Hilt del manifiesto. No hace falta `hilt-android-testing`.
- `app/.../navigation/NavigationTest.kt` recorre los destinos, vuelve con Atrás, entrega importaciones desde Assets y desde el editor, abre Licencias y comprueba que la pila y la apariencia sobreviven a recrear la actividad. `SharedIntentTest` cubre lo compartido.
- Instrumentadas en `core/media/src/androidTest`: inspección de archivos reales, extracción con Media3 y la mezcla (`MixPlayerTest` mide el nivel de la salida mezclada: la voz entra a su desfase con ±100 ms y el fondo baja a su volumen). Necesitan emulador: `./gradlew connectedDebugAndroidTest`. No corren en CI.
- Los archivos de prueba son sintéticos (tonos y colores). Se regeneran con `scripts/generar-medios-de-prueba.sh`; con una carpeta como argumento genera además los grandes (60 s y 11 min) para probar en el emulador, que no van al repo.
- `core/ui/.../ContrastTest.kt` mide el contraste WCAG de los pares de roles usados: texto 4,5:1, íconos y bordes 3:1.
- Cada prueba demuestra un comportamiento; no se prueban mapeos triviales.

## Comandos

```sh
./gradlew assembleDebug lintDebug testDebugUnitTest :core:domain:test   # lo mismo que corre CI
./gradlew :feature:editor:testDebugUnitTest                             # las pruebas de un módulo
./gradlew connectedDebugAndroidTest                                     # instrumentadas, con emulador
./gradlew installDebug                                                  # instala en el dispositivo conectado
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

Compartir un archivo desde la terminal con `adb shell am start -a android.intent.action.SEND` no sirve: el shell no puede conceder permiso de lectura sobre un archivo de MediaStore. Se comparte desde la app Archivos del emulador (mantener pulsado, Compartir, Hope). Un enlace sí se comparte desde la terminal:

```sh
adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "https://youtu.be/jNQXAC9IVRw" -n dev.tacuchi.hope/.MainActivity
```

## Probar en el teléfono

1. Activar las opciones de desarrollador y la depuración USB.
2. Conectar por USB y confirmar con `adb devices`.
3. `./gradlew installDebug` y abrir Hope.
4. Capturas con `android screen capture --device=<serial> -o captura.png`.
5. Errores: `adb logcat --pid=$(adb shell pidof dev.tacuchi.hope)`; no debe aparecer `FATAL EXCEPTION`.

## Integración continua

`.github/workflows/ci.yml` corre `assembleDebug lintDebug testDebugUnitTest :core:domain:test` en cada push y pull request a `main`. Un cambio que solo toca `**.md` o `docs/**` no la dispara. Instala la plataforma 37.2 si el runner no la trae.
