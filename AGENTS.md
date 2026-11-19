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
| Lifecycle | 2.11.1 |
| Core KTX | 1.19.1 |
| Core SplashScreen | 1.2.0 |
| kotlinx-serialization | 1.13.0 |
| JUnit | 4.13.2 |
| Robolectric | 4.18.1 |
| AndroidX Test core / ext-junit | 1.7.0 / 1.3.0 |
| JDK | 21 |
| SDK | minSdk 29 · targetSdk 36 · compileSdk 37.2 |

- **compileSdk 37.2 con targetSdk 36:** core 1.19 y el BOM 2026.11 exigen compilar contra 37.1 o más. targetSdk 36 es el que pide Play, así que el comportamiento en ejecución no cambia. Requiere `platforms;android-37.2` en el SDK.
- Solo versiones estables: nunca alfa, beta ni RC. Cada coordenada se verifica en Google Maven o Maven Central antes de subirla.

## Arquitectura

- Un solo módulo `app`, paquete `dev.tacuchi.hope`.
- Una sola actividad (`MainActivity`) con la API de pantalla de inicio y `enableEdgeToEdge()`. Todo se dibuja en Compose.
- Navegación con Navigation 3: rutas `@Serializable` que implementan `NavKey` en `navigation/HopeNavigation.kt`, pila con `rememberNavBackStack`.
- Pantallas en `ui/<pantalla>/`. Cada una recibe callbacks de navegación, nunca la pila.
- Estado: cuando una pantalla tenga datos, un `ViewModel` expone `StateFlow` y la pantalla lo lee con `collectAsStateWithLifecycle()`.
- Inyección manual (constructores y una fábrica por ViewModel). Hilt se evalúa cuando lleguen Room y Media3.

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

- Unitarias en `app/src/test`. Las de interfaz corren con Robolectric dentro de `testDebugUnitTest`, sin emulador.
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

## Probar en el teléfono

1. Activar las opciones de desarrollador y la depuración USB.
2. Conectar por USB y confirmar con `adb devices`.
3. `./gradlew installDebug` y abrir Hope.
4. Capturas con `android screen capture --device=<serial> -o captura.png`.
5. Errores: `adb logcat --pid=$(adb shell pidof dev.tacuchi.hope)`; no debe aparecer `FATAL EXCEPTION`.

## Integración continua

`.github/workflows/ci.yml` corre `assembleDebug lintDebug testDebugUnitTest` en cada push y pull request a `main`. Un cambio que solo toca `**.md` o `docs/**` no la dispara. Instala la plataforma 37.2 si el runner no la trae.
