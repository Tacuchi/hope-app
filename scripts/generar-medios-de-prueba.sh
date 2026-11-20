#!/usr/bin/env bash
# Genera los archivos de prueba sintéticos (tonos y colores, sin material ajeno).
#   scripts/generar-medios-de-prueba.sh            -> los pequeños, en app/src/androidTest/assets/media
#   scripts/generar-medios-de-prueba.sh <carpeta>  -> además los grandes para el emulador (no van al repo):
#                                                     un video de 60 s y uno de más de 10 minutos.
set -euo pipefail
FFMPEG="${FFMPEG:-ffmpeg}"
Q=(-hide_banner -loglevel error -y)
cd "$(dirname "$0")/.."
OUT=app/src/androidTest/assets/media
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
