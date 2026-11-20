# Hope

App Android para armar *hopes*: audio que te da ánimo, una portada y tu voz encima. Todo vive en el teléfono.

Estado: en pruebas antes de publicarse en Google Play.

Stack: Kotlin, Jetpack Compose, Material 3 y Media3.

## Compilar

Requisitos: JDK 21 y el SDK de Android con la plataforma 37.2.

```sh
./gradlew assembleDebug lintDebug testDebugUnitTest
```

Versiones, arquitectura y cómo probar en el emulador o el teléfono: [AGENTS.md](AGENTS.md).

## Firmar la versión de lanzamiento

La versión de lanzamiento se firma con una clave de subida que vive fuera del repo. La versión de desarrollo no la necesita.

1. Crear la clave una sola vez. `keytool` viene con el JDK y pide las contraseñas y los datos del certificado:

   ```sh
   mkdir -p ~/.android-keys && chmod 700 ~/.android-keys
   keytool -genkeypair -v -keystore ~/.android-keys/hope-upload.jks \
     -alias hope-upload -keyalg RSA -keysize 4096 -validity 10000
   ```

2. Crear `~/.android-keys/hope-upload.properties` con este formato:

   ```properties
   storeFile=hope-upload.jks
   storePassword=<contraseña del almacén>
   keyAlias=hope-upload
   keyPassword=<contraseña de la clave>
   ```

   `storeFile` puede ser absoluta o relativa a la carpeta del archivo. Protegerlo con `chmod 600 ~/.android-keys/hope-upload.properties`.

3. Guardar una copia del `.jks` y de las contraseñas en un gestor de contraseñas. Sin ellos no se pueden subir versiones nuevas: Play permite pedir un cambio de clave de subida, pero tarda.

4. Generar el paquete para Play:

   ```sh
   ./gradlew bundleRelease
   ```

   Sale en `app/build/outputs/bundle/release/app-release.aab`. Sin el archivo de firma, la compilación falla y dice qué archivo crear.

Para usar otro archivo de firma: `./gradlew bundleRelease -Phope.signing=<ruta>` o la variable `HOPE_SIGNING_PROPERTIES`.

## Política de privacidad

La página vive en [`docs/privacidad/index.html`](docs/privacidad/index.html) y se publica con GitHub Pages desde la carpeta `docs/` de `main`, en <https://tacuchi.github.io/hope-app/privacidad/>. «Acerca de», en Ajustes, abre esa dirección.
