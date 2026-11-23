package dev.tacuchi.hope.playback

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.playback.Tolerance.TONE
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * Recorte de cada pista antes de ponerla en su secuencia, fundidos lineales por pista y de la
 * hope, volumen en vivo, pausa y saltos. Se mide lo que sale de la mezcla, con tonos distintos en
 * cada pista y a cada lado de los cortes.
 */
@RunWith(AndroidJUnit4::class)
class MixTrimAndRampTest {
    private val session = ProbeSession()

    @After
    fun tearDown() = session.close()

    /** El fondo en su tramo de 330 Hz (de 3 a 7 s del archivo): 4 s. */
    private fun background(fadeInMs: Long = 0, fadeOutMs: Long = 0, volume: Float = 1f) = ProbeTrack(
        session.media("fondo-tramos.m4a"), 10_000, startMs = 3_000, endMs = 7_000,
        fadeInMs = fadeInMs, fadeOutMs = fadeOutMs, volume = volume,
    )

    /** La voz en su tramo de 880 Hz (de 2 a 5 s del archivo): 3 s. */
    private fun voice(fadeInMs: Long = 0, fadeOutMs: Long = 0) = ProbeTrack(
        session.media("voz-tramos.m4a"), 6_000, startMs = 2_000, endMs = 5_000, fadeInMs = fadeInMs, fadeOutMs = fadeOutMs,
    )

    /** Inicio, mitad y fin de una rampa, a 10 ms de cada borde para que la ventana de 20 ms quede dentro. */
    private fun rampPoints(fromMs: Double, toMs: Double) = listOf(fromMs + 10, (fromMs + toMs) / 2, toMs - 10)

    /** La ganancia que se oye de un tono, medida en 20 ms. */
    private fun Take.gain(hz: Double, atMs: Double) = toneAt(hz, atMs) / TONE

    /**
     * Lo que tarda [hz] en llegar a la mitad de lo esperado desde [fromMs]: el hueco que deja un salto.
     * La ventana de 20 ms tiene que quedar dentro del tramo, así que lo mínimo que mide es 10 ms.
     */
    private fun Take.warmUpMs(hz: Double, fromMs: Double, expected: (Double) -> Double): Double =
        generateSequence(fromMs + 10) { it + 1 }.first { gain(hz, it) >= expected(it) / 2 || it > fromMs + 500 } - fromMs

    /** Lo más alto que suena [hz] entre [fromMs] y [toMs], en ventanas de [windowMs] cada 5 ms, en dB respecto del tono. */
    private fun Take.loudestDb(hz: Double, fromMs: Double, toMs: Double, windowMs: Double): Pair<Double, Double> {
        val points = generateSequence(fromMs + windowMs / 2) { it + 5 }.takeWhile { it <= toMs - windowMs / 2 }.toList()
        val loudest = points.maxBy { toneAt(hz, it, windowMs) }
        return (toneAt(hz, loudest, windowMs) / TONE).db() to loudest
    }

    @Test
    fun theTrimmedVoiceStartsAtItsOffset() {
        val mix = ProbeMix(background = background(), voice = voice(), voiceOffsetMs = 1_500)
        val run = session.play(mix)
        val out = run.mixCapture.first()
        val checks = Checks("Recorte: voz de 2 a 5 s del archivo con desfase de 1,5 s; fondo de 3 a 7 s")

        checks.near("duración de la hope", mix.durationMs, 4_500.0, 0.0, " ms")
        checks.near("entrada de la voz (880 Hz)", out.toneOnset(880.0, TONE / 2, 1_000.0, 2_500.0)!!, 1_500.0, Tolerance.ENTRY_MS, " ms")
        checks.near("fin del fondo (330 Hz)", out.toneEnd(330.0, TONE / 2, 3_000.0, 4_400.0)!!, 4_000.0, Tolerance.ENTRY_MS, " ms")
        checks.near("final de la captura", out.endMs, mix.durationMs, Tolerance.END_MS, " ms")
        checks.near("la voz suena hasta su final", out.toneEnd(880.0, TONE / 2, 4_000.0, 5_000.0)!!, mix.voiceEndMs, Tolerance.ENTRY_MS, " ms")
        // Lo que queda fuera del recorte no suena. Se mide en ventanas de 100 ms (las de 20 ms confunden tonos vecinos).
        // Cada pista en su propia captura: en la mezcla, el corte seco del fondo a los 4 s salpica todas las frecuencias.
        val voiceTrack = run.voiceCapture.first()
        val bgTrack = run.backgroundCapture.first()
        listOf(
            Triple("voz: tramo anterior (660 Hz)", 660.0, voiceTrack),
            Triple("voz: tramo posterior (990 Hz)", 990.0, voiceTrack),
            Triple("fondo: tramo anterior (220 Hz)", 220.0, bgTrack),
            Triple("fondo: tramo posterior (440 Hz)", 440.0, bgTrack),
        ).forEach { (label, hz, take) ->
            val (db, at) = take.loudestDb(hz, take.startMs + 50, take.endMs - 50, 100.0)
            checks.atMost("$label en su pista, lo más alto (a los %.0f ms de la pista)".format(at), db, Tolerance.NEIGHBOR_DB, " dB")
        }
        val (click, clickAt) = out.loudestDb(660.0, 3_900.0, 4_200.0, 100.0)
        checks.note("corte seco del fondo en la mezcla", "%.1f dB en 660 Hz a los %.0f ms".format(click, clickAt))
        // Junto al corte de inicio: el fondo, primero de su secuencia, trae el relleno del AAC sin descontar.
        val bgEdge = out.toneEnd(220.0, TONE / 2, 0.0, 100.0) ?: 0.0
        val voiceEdge = out.toneEnd(660.0, TONE / 2, 1_450.0, 1_600.0) ?: 0.0
        checks.note("fondo: tramo anterior (220 Hz) en el corte", if (bgEdge > 0) "suena hasta los %.0f ms".format(bgEdge) else "no suena")
        checks.note("voz: tramo anterior (660 Hz) en el corte", if (voiceEdge > 0) "suena hasta los %.0f ms".format(voiceEdge) else "no suena")
        checks.atMost("fondo: tramo anterior junto al corte", bgEdge, Tolerance.AAC_FRAME_MS, " ms")
        checks.atMost("voz: tramo anterior junto al corte", maxOf(0.0, voiceEdge - 1_500), Tolerance.AAC_FRAME_MS, " ms")
        checks.assertAll()
    }

    @Test
    fun eachTrackRampFollowsItsLine() {
        val mix = ProbeMix(
            background = background(fadeInMs = 1_000, fadeOutMs = 1_500),
            voice = voice(fadeInMs = 800, fadeOutMs = 1_200),
            voiceOffsetMs = 1_000,
        )
        val run = session.play(mix)
        val out = run.mixCapture.first()
        val bg = run.backgroundGain!!
        val vo = run.voiceGain!!
        val checks = Checks("Fundidos de pista por separado: fondo 1 s y 1,5 s; voz 0,8 s y 1,2 s (entra a 1 s)")

        (rampPoints(0.0, 1_000.0) + rampPoints(2_500.0, 4_000.0)).forEach {
            checks.near("fondo a los %.0f ms".format(it), out.gain(330.0, it), bg.gainAt(it), Tolerance.GAIN)
        }
        (rampPoints(0.0, 800.0) + rampPoints(1_800.0, 3_000.0)).forEach {
            checks.near("voz a los %.0f ms de la hope".format(it + 1_000), out.gain(880.0, it + 1_000), vo.gainAt(it), Tolerance.GAIN)
        }
        // Cuándo cruza cada rampa de entrada la mitad, contra lo esperado (negativo: la rampa llega antes que la hope).
        fun crossing(hz: Double, fromMs: Double) = generateSequence(fromMs) { it + 1 }.first { out.gain(hz, it) >= 0.5 || it > fromMs + 400 }
        val bgShift = crossing(330.0, 300.0) - 500.0
        val voiceShift = crossing(880.0, 1_200.0) - 1_400.0
        checks.atMost("fondo: desfase de su rampa contra la hope (%+.0f ms)".format(bgShift), abs(bgShift), Tolerance.AAC_FRAME_MS, " ms")
        checks.atMost("voz: desfase de su rampa contra la hope (%+.0f ms)".format(voiceShift), abs(voiceShift), Tolerance.AAC_FRAME_MS, " ms")
        checks.assertAll()
    }

    @Test
    fun theGlobalRampShapesTheWholeMix() {
        val mix = ProbeMix(background = background(), voice = voice(), voiceOffsetMs = 1_000, fadeInMs = 1_500, fadeOutMs = 1_000)
        val run = session.play(mix)
        val out = run.mixCapture.first()
        val global = run.mixGain
        val checks = Checks("Fundido de la hope: entrada 1,5 s y salida 1 s; pistas sin fundido (duración cero)")

        rampPoints(0.0, 1_500.0).forEach { checks.near("fondo a los %.0f ms".format(it), out.gain(330.0, it), global.gainAt(it), Tolerance.GAIN) }
        checks.near("voz a los 1490 ms", out.gain(880.0, 1_490.0), global.gainAt(1_490.0), Tolerance.GAIN)
        checks.near("fondo sin fundido propio a los 2000 ms", out.gain(330.0, 2_000.0), 1.0, Tolerance.GAIN)
        rampPoints(3_000.0, 4_000.0).forEach {
            checks.near("fondo a los %.0f ms".format(it), out.gain(330.0, it), global.gainAt(it), Tolerance.GAIN)
            checks.near("voz a los %.0f ms".format(it), out.gain(880.0, it), global.gainAt(it), Tolerance.GAIN)
        }
        checks.assertAll()
    }

    @Test
    fun trackAndGlobalRampsMultiply() {
        val mix = ProbeMix(
            background = background(fadeInMs = 1_000, fadeOutMs = 2_000),
            voice = voice(fadeInMs = 500, fadeOutMs = 1_000),
            voiceOffsetMs = 500, fadeInMs = 2_000, fadeOutMs = 1_500,
        )
        val run = session.play(mix)
        val out = run.mixCapture.first()
        val checks = Checks("Fundidos combinados: pista por hope")

        listOf(10.0, 500.0, 990.0, 1_500.0, 2_500.0, 2_750.0, 3_250.0, 3_750.0, 3_990.0).forEach {
            checks.near("fondo a los %.0f ms".format(it), out.gain(330.0, it), run.backgroundGain!!.gainAt(it) * run.mixGain.gainAt(it), Tolerance.GAIN)
        }
        listOf(510.0, 750.0, 990.0, 2_000.0, 2_750.0, 3_000.0, 3_490.0).forEach {
            checks.near("voz a los %.0f ms".format(it), out.gain(880.0, it), run.voiceGain!!.gainAt(it - 500) * run.mixGain.gainAt(it), Tolerance.GAIN)
        }
        checks.assertAll()
    }

    /** Fondo de 10 s a 220 Hz con fundido de salida de 2 s; a los 2 s de reproducción baja de 100 % a 50 %. */
    @Test
    fun theLiveVolumeChangesOverARamp() {
        val mix = ProbeMix(background = ProbeTrack(session.media("fondo-10s.m4a"), 10_000, fadeOutMs = 2_000))
        var changedAtPosition = 0L
        val run = session.play(mix, captureTracks = false) {
            waitForPosition(2_000)
            changedAtPosition = positionMs
            run.backgroundGain!!.volume = 0.5f
        }
        val out = run.mixCapture.first()
        val checks = Checks("Volumen en vivo: el fondo pasa de 100 % a 50 % mientras suena, antes de su fundido de salida")
        val heardAt = generateSequence(changedAtPosition.toDouble()) { it + 1 }.first { out.gain(220.0, it) < 0.75 }

        checks.note("volumen cambiado", "con la reproducción en $changedAtPosition ms; se oye desde los ${"%.0f".format(heardAt)} ms")
        checks.atMost("demora hasta oír el cambio", heardAt - changedAtPosition, Tolerance.LIVE_VOLUME_MS, " ms")
        checks.near("antes del cambio", out.gain(220.0, changedAtPosition - 100.0), 1.0, Tolerance.GAIN)
        checks.near("después del cambio, antes del fundido", out.tone(220.0, heardAt + 200, 7_800.0) / TONE, 0.5, Tolerance.GAIN)
        rampPoints(8_000.0, 10_000.0).forEach {
            checks.near("fundido al 50 %% a los %.0f ms".format(it), out.gain(220.0, it), 0.5 * run.backgroundGain!!.shapeAt(it), Tolerance.GAIN)
        }
        checks.assertAll()
    }

    @Test
    fun pausingInsideARampResumesWhereItWas() {
        val mix = ProbeMix(background = background(), voice = voice(), voiceOffsetMs = 1_000, fadeInMs = 3_000)
        var pausedAt = 0L
        var stillAt = 0L
        val run = session.play(mix) {
            waitForPosition(1_500)
            pause()
            pausedAt = positionMs
            waitMs(1_000)
            stillAt = positionMs
            resume()
        }
        val takes = run.mixCapture.takes()
        val global = run.mixGain
        val checks = Checks("Pausa a mitad del fundido de entrada de la hope (3 s) y reanudación")
        // Si Media3 vaciara la mezcla al pausar o seguir, habría más de un tramo: cada medición usa el que cubre su instante.
        fun gain(hz: Double, atMs: Double) = takes.last { atMs - 10 >= it.startMs && atMs + 10 <= it.endMs }.gain(hz, atMs)

        checks.note("pausa", "en $pausedAt ms; un segundo después sigue en $stillAt ms; tramos capturados ${takes.size}")
        checks.near("la posición no se mueve en pausa", stillAt.toDouble(), pausedAt.toDouble(), 20.0, " ms")
        val worst = (100..2_900 step 10).maxOf { abs(gain(330.0, it.toDouble()) - global.gainAt(it.toDouble())) }
        checks.atMost("mayor desvío del fondo contra la rampa, de 0,1 a 2,9 s cada 10 ms", worst, Tolerance.GAIN)
        rampPoints(0.0, 3_000.0).forEach { checks.near("fondo a los %.0f ms".format(it), gain(330.0, it), global.gainAt(it), Tolerance.GAIN) }
        checks.near("voz a los 2500 ms", gain(880.0, 2_500.0), global.gainAt(2_500.0), Tolerance.GAIN)
        val entry = takes.first().toneOnset(880.0, global.gainAt(1_000.0) * TONE / 2, 600.0, 1_600.0)!!
        checks.near("entrada de la voz", entry, 1_000.0, Tolerance.ENTRY_MS, " ms")
        checks.near("final de la captura", takes.last().endMs, mix.durationMs, Tolerance.END_MS, " ms")
        checks.assertAll()
    }

    /**
     * Fondo de 5 a 10 s del archivo (330 Hz hasta los 2 s de la hope, 440 Hz después) y voz de 1 a
     * 6 s del archivo desde 1 s (660 Hz hasta los 2 s, 880 Hz hasta los 5 s, 990 Hz hasta los 6 s):
     * las dos pistas cambian de tono a los 2 s de la hope, una marca de sincronía.
     */
    @Test
    fun seekingIntoRampsKeepsGainsAndSync() {
        val mix = ProbeMix(
            background = ProbeTrack(session.media("fondo-tramos.m4a"), 10_000, startMs = 5_000, endMs = 10_000, fadeOutMs = 2_000),
            voice = ProbeTrack(session.media("voz-tramos.m4a"), 6_000, startMs = 1_000, endMs = 6_000, fadeInMs = 1_000),
            voiceOffsetMs = 1_000, fadeOutMs = 1_500,
        )
        val run = session.play(mix) {
            waitForPosition(200)
            seekTo(1_500)
            waitForPosition(2_600)
            seekTo(4_000)
        }
        val first = run.mixCapture.takeFrom(1_500)
        val second = run.mixCapture.takeFrom(4_000)
        val bg = run.backgroundGain!!
        val vo = run.voiceGain!!
        val global = run.mixGain
        val checks = Checks("Saltos: a 1,5 s (mitad del fundido de entrada de la voz) y a 4 s (mitad del fundido del fondo)")

        checks.near("duración de la hope", mix.durationMs, 6_000.0, 0.0, " ms")
        // Tras un salto cada pista tarda unos milisegundos en volver a sonar (el decodificador AAC arranca de nuevo).
        checks.atMost("voz: silencio tras saltar a 1,5 s", first.warmUpMs(660.0, 1_500.0) { vo.gainAt(it - 1_000) }, Tolerance.SEEK_GAP_MS, " ms")
        checks.atMost("fondo: silencio tras saltar a 1,5 s", first.warmUpMs(330.0, 1_500.0) { 1.0 }, Tolerance.SEEK_GAP_MS, " ms")
        checks.atMost("fondo: silencio tras saltar a 4 s", second.warmUpMs(440.0, 4_000.0) { bg.gainAt(it) }, Tolerance.SEEK_GAP_MS, " ms")
        checks.atMost("voz: silencio tras saltar a 4 s", second.warmUpMs(880.0, 4_000.0) { 1.0 }, Tolerance.SEEK_GAP_MS, " ms")
        listOf(1_560.0, 1_750.0, 1_990.0).forEach {
            checks.near("voz (660 Hz) a los %.0f ms tras saltar".format(it), first.gain(660.0, it), vo.gainAt(it - 1_000), Tolerance.GAIN)
        }
        val voiceMark = first.toneOnset(880.0, TONE / 2, 1_800.0, 2_300.0)!!
        val bgMark = first.toneOnset(440.0, TONE / 2, 1_800.0, 2_300.0)!!
        checks.near("voz cambia a 880 Hz", voiceMark, 2_000.0, Tolerance.ENTRY_MS, " ms")
        checks.near("fondo cambia a 440 Hz", bgMark, 2_000.0, Tolerance.ENTRY_MS, " ms")
        checks.note("desfase entre las marcas de voz y fondo tras saltar", "%.0f ms".format(voiceMark - bgMark))
        listOf(4_060.0, 4_500.0, 4_990.0).forEach {
            checks.near("fondo (440 Hz) a los %.0f ms tras saltar".format(it), second.gain(440.0, it), bg.gainAt(it) * global.gainAt(it), Tolerance.GAIN)
        }
        // El tono de 990 Hz llega con el relleno del AAC (unos 25 ms): se mide desde los 5,06 s.
        listOf(5_060.0, 5_250.0, 5_990.0).forEach {
            checks.near("voz (990 Hz) a los %.0f ms tras saltar".format(it), second.gain(990.0, it), global.gainAt(it), Tolerance.GAIN)
        }
        val lateMark = second.toneOnset(990.0, global.gainAt(5_000.0) * TONE / 2, 4_800.0, 5_300.0)!!
        checks.near("voz cambia a 990 Hz", lateMark, 5_000.0, Tolerance.ENTRY_MS, " ms")
        checks.near("final de la captura", second.endMs, mix.durationMs, Tolerance.END_MS, " ms")
        checks.assertAll()
    }
}
