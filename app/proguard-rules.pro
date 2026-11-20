# Reglas de R8 para la versión de lanzamiento.
#
# Media3, Room, AboutLibraries, Navigation 3 y kotlinx-serialization traen sus propias reglas
# (consumer rules) dentro de cada librería. Aquí va solo lo que la app necesita además.

# Los números de línea de los errores se conservan; el nombre del archivo, no.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
