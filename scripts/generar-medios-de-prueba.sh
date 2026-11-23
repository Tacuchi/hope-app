#!/usr/bin/env bash
# Genera los archivos de prueba sintéticos (tonos y colores, sin material ajeno).
#   scripts/generar-medios-de-prueba.sh            -> los pequeños, en core/media/src/androidTest/assets/media
#   scripts/generar-medios-de-prueba.sh <carpeta>  -> además los grandes para el emulador (no van al repo):
#                                                     un video de 60 s y uno de más de 10 minutos.
set -euo pipefail
FFMPEG="${FFMPEG:-ffmpeg}"
Q=(-hide_banner -loglevel error -y)
cd "$(dirname "$0")/.."
OUT=core/media/src/androidTest/assets/media
mkdir -p "$OUT"

# Video con audio AAC, como uno de WhatsApp o TikTok.
"$FFMPEG" "${Q[@]}" -f lavfi -i testsrc=size=320x240:rate=15 -f lavfi -i sine=frequency=440:sample_rate=44100 -t 5 \
  -c:v libx264 -pix_fmt yuv420p -preset veryfast -crf 35 -c:a aac -b:a 48k -movflags +faststart "$OUT/video-aac.mp4"
# Video sin pista de audio.
"$FFMPEG" "${Q[@]}" -f lavfi -i testsrc2=size=320x240:rate=15 -t 3 \
  -c:v libx264 -pix_fmt yuv420p -preset veryfast -crf 35 "$OUT/video-sin-audio.mp4"
# Video WebM con VP9 y Opus, como lo que entregan las herramientas de extracción.
"$FFMPEG" "${Q[@]}" -f lavfi -i testsrc=size=320x240:rate=15 -f lavfi -i sine=frequency=330:sample_rate=48000 -t 3 \
  -c:v libvpx-vp9 -b:v 80k -deadline realtime -c:a libopus -b:a 32k "$OUT/video-vp9-opus.webm"
# Nota de voz en OGG Opus, como las de WhatsApp.
"$FFMPEG" "${Q[@]}" -f lavfi -i sine=frequency=550:sample_rate=48000 -t 3 -c:a libopus -b:a 24k "$OUT/nota-de-voz.ogg"
# Audio WebM con Opus.
"$FFMPEG" "${Q[@]}" -f lavfi -i sine=frequency=660:sample_rate=48000 -t 3 -c:a libopus -b:a 24k "$OUT/audio-opus.webm"
# MP3.
"$FFMPEG" "${Q[@]}" -f lavfi -i sine=frequency=770:sample_rate=44100 -t 3 -c:a libmp3lame -b:a 64k "$OUT/tono.mp3"
# Para la mezcla: un fondo de 10 s como el audio extraído de un video y una voz de 6 s como una grabación (los dos AAC en M4A).
"$FFMPEG" "${Q[@]}" -f lavfi -i sine=frequency=220:sample_rate=44100 -t 10 -c:a aac -b:a 48k "$OUT/fondo-10s.m4a"
"$FFMPEG" "${Q[@]}" -f lavfi -i sine=frequency=880:sample_rate=44100 -t 6 -c:a aac -b:a 48k "$OUT/voz-6s.m4a"
# Para medir recortes y efectos: tramos de tono distinto a cada lado de los cortes. Cada tramo dura un número
# entero de ciclos, así el cambio de tono no hace clic.
#   fondo-tramos: 220 Hz (0-3 s), 330 Hz (3-7 s), 440 Hz (7-10 s); estéreo a 48 kHz, como un audio de video
#                 (los dos canales iguales, al mismo nivel que los archivos mono).
#   voz-tramos:   660 Hz (0-2 s), 880 Hz (2-5 s), 990 Hz (5-6 s); mono a 44,1 kHz, como una grabación.
"$FFMPEG" "${Q[@]}" -f lavfi -i sine=frequency=220:sample_rate=48000:duration=3 \
  -f lavfi -i sine=frequency=330:sample_rate=48000:duration=4 -f lavfi -i sine=frequency=440:sample_rate=48000:duration=3 \
  -filter_complex "[0][1][2]concat=n=3:v=0:a=1,pan=stereo|c0=c0|c1=c0" -c:a aac -b:a 96k "$OUT/fondo-tramos.m4a"
"$FFMPEG" "${Q[@]}" -f lavfi -i sine=frequency=660:sample_rate=44100:duration=2 \
  -f lavfi -i sine=frequency=880:sample_rate=44100:duration=3 -f lavfi -i sine=frequency=990:sample_rate=44100:duration=1 \
  -filter_complex "[0][1][2]concat=n=3:v=0:a=1" -c:a aac -b:a 64k "$OUT/voz-tramos.m4a"
# Tren de pulsos: 9 pulsos de 1 kHz y 50 ms, uno cada 500 ms desde los 0,5 s (velocidad).
"$FFMPEG" "${Q[@]}" -f lavfi -i "aevalsrc=0.125*sin(2*PI*1000*t)*gte(t\,0.5)*lt(t\,4.6)*lt(mod(t-0.5\,0.5)\,0.05):s=48000:d=5" \
  -af "pan=stereo|c0=c0|c1=c0" -c:a aac -b:a 96k "$OUT/pulsos.m4a"
# Un pulso de ruido de 30 ms a los 0,5 s y silencio hasta los 4 s (cola de la sala).
"$FFMPEG" "${Q[@]}" -f lavfi -i "aevalsrc=0.25*(2*random(0)-1)*between(t\,0.5\,0.53):s=44100:d=4" \
  -c:a aac -b:a 64k "$OUT/pulso.m4a"
# Un grave de 80 Hz y un medio de 1 kHz a la vez, del mismo nivel (voz clara).
"$FFMPEG" "${Q[@]}" -f lavfi -i "aevalsrc=0.0625*sin(2*PI*80*t)+0.0625*sin(2*PI*1000*t):s=44100:d=4" \
  -c:a aac -b:a 64k "$OUT/graves-medios.m4a"
# Una voz para todos los efectos a la vez: el grave y el medio hasta los 2 s, un pulso de ruido de 30 ms y silencio.
"$FFMPEG" "${Q[@]}" -f lavfi \
  -i "aevalsrc=(0.0625*sin(2*PI*80*t)+0.0625*sin(2*PI*1000*t))*lt(t\,2)+0.25*(2*random(0)-1)*between(t\,2\,2.03):s=44100:d=4" \
  -c:a aac -b:a 64k "$OUT/voz-efectos.m4a"
# Imágenes.
"$FFMPEG" "${Q[@]}" -f lavfi -i "color=c=0xb6f23c:size=320x240" -frames:v 1 "$OUT/imagen.png"
"$FFMPEG" "${Q[@]}" -f lavfi -i "color=c=0x2b2b28:size=320x240" -frames:v 1 -q:v 4 "$OUT/imagen.jpg"
# Un audio que Android no sabe leer (WMA): llega como audio/* y no es compatible.
"$FFMPEG" "${Q[@]}" -f lavfi -i sine=frequency=880:sample_rate=44100 -t 2 -c:a wmav2 -b:a 32k "$OUT/audio.wma"
# Un archivo que no es un medio.
printf '%%PDF-1.4\n%% Archivo de prueba: no es un medio.\ntrailer << >>\n%%%%EOF\n' > "$OUT/documento.pdf"

if [[ $# -ge 1 ]]; then
  BIG="$1"
  mkdir -p "$BIG"
  # 60 s en 720p con AAC, para medir la extracción.
  "$FFMPEG" "${Q[@]}" -f lavfi -i testsrc2=size=1280x720:rate=30 -f lavfi -i sine=frequency=440:sample_rate=44100 -t 60 \
    -c:v libx264 -pix_fmt yuv420p -preset veryfast -crf 30 -c:a aac -b:a 128k -movflags +faststart "$BIG/video-60s.mp4"
  # 60 s en WebM con VP9 y Opus: obliga a recodificar el audio.
  "$FFMPEG" "${Q[@]}" -f lavfi -i testsrc2=size=640x360:rate=30 -f lavfi -i sine=frequency=330:sample_rate=48000 -t 60 \
    -c:v libvpx-vp9 -b:v 300k -deadline realtime -c:a libopus -b:a 96k "$BIG/video-60s.webm"
  # Más de 10 minutos, con muy baja resolución para que pese poco.
  "$FFMPEG" "${Q[@]}" -f lavfi -i "color=c=0x3a4a1a:size=64x64:rate=1" -f lavfi -i sine=frequency=220:sample_rate=22050 -t 630 \
    -c:v libx264 -pix_fmt yuv420p -preset veryfast -crf 40 -c:a aac -b:a 24k -movflags +faststart "$BIG/video-11min.mp4"
fi
