package dev.tacuchi.hope.playback

import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.tacuchi.hope.playback.Tolerance.TONE
import dev.tacuchi.hope.playback.Tolerance.TONE_RMS
import dev.tacuchi.hope.domain.model.DuckLevel
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cada efecto como procesador de una sola pista: sala en fondo y voz, velocidad y tono en el
 * fondo, voz clara en la voz y el fondo que baja bajo la voz como envolvente de su ganancia. Se
 * mide cada pista por separado (una captura al final de su cadena) y la mezcla.
 */
@RunWith(AndroidJUnit4::class)
class MixEffectsTest {
    private val session = ProbeSession()

    @After
    fun tearDown() = session.close()

    private val pulse get() = session.media("pulso.m4a")

    /** El fondo en su tramo de 330 Hz (de 3 a 7 s del archivo): 4 s. */
    private fun backgroundTone(room: RoomKind? = null, speed: Float = 1f) =
        ProbeTrack(session.media("fondo-tramos.m4a"), 10_000, startMs = 3_000, endMs = 7_000, room = room, speed = speed)

    /** La voz en su tramo de 880 Hz (de 2 a 5 s del archivo): 3 s, o menos con [endMs]. */
    private fun voiceTone(room: RoomKind? = null, endMs: Long = 5_000) =
        ProbeTrack(session.media("voz-tramos.m4a"), 6_000, startMs = 2_000, endMs = endMs, room = room)

    /** El pulso de ruido de un archivo, su nivel y lo que queda de la cola 100 ms después. */
    private class PulseTail(take: Take, expectedMs: Double) {
        val onsetMs = take.onsetNear(expectedMs)
        val pulseRms = take.rms(onsetMs, onsetMs + PULSE_MS)
        val tail100Db = (take.rms(onsetMs + PULSE_MS + 90, onsetMs + PULSE_MS + 110) / pulseRms).db()
        val rt60 = take.rt60(onsetMs + PULSE_MS + 10, take.endMs - 10)
    }

    @Test
    fun eachRoomLeavesItsTailOnItsOwnTrack() {
        val checks = Checks("Sala: cola tras un pulso de ruido de 30 ms, en el fondo y en la voz por separado (100 %)")
        val pairs = listOf(
            RoomKind.Room to RoomKind.Hall, RoomKind.Hall to RoomKind.Church, RoomKind.Church to RoomKind.Room, null to null,
        )
        for ((backgroundRoom, voiceRoom) in pairs) {
            val mix = ProbeMix(
                background = ProbeTrack(pulse, 4_000, room = backgroundRoom),
                voice = ProbeTrack(pulse, 4_000, room = voiceRoom),
            )
            val run = session.play(mix)
            listOf("fondo" to (backgroundRoom to run.backgroundCapture), "voz" to (voiceRoom to run.voiceCapture)).forEach { (track, pair) ->
                val (room, capture) = pair
                val tail = PulseTail(capture.first(), 500.0)
                if (room == null) {
                    checks.atMost("$track sin sala: cola a los 100 ms", tail.tail100Db, -60.0, " dB")
                } else {
                    val expected = SIMULATED.getValue(room)
                    checks.relative("$track ${room.name}: RT60", tail.rt60, expected.first, Tolerance.RT60, " s")
                    checks.near("$track ${room.name}: cola a los 100 ms", tail.tail100Db, expected.second, Tolerance.TAIL_DB, " dB")
                }
            }
        }
        // «Cuánto se nota» al 50 %: la cola sumada es la mitad, 6 dB menos.
        val half = session.play(ProbeMix(background = ProbeTrack(pulse, 4_000, room = RoomKind.Hall, roomAmount = 0.5f)))
        val tail = PulseTail(half.backgroundCapture.first(), 500.0)
        checks.near("fondo Hall al 50 %: cola a los 100 ms", tail.tail100Db, SIMULATED.getValue(RoomKind.Hall).second - 6.02, Tolerance.TAIL_DB, " dB")
        checks.relative("fondo Hall al 50 %: RT60", tail.rt60, SIMULATED.getValue(RoomKind.Hall).first, Tolerance.RT60, " s")
        checks.assertAll()
    }

    @Test
    fun aRoomOnOneTrackLeavesTheOtherUntouched() {
        val checks = Checks("Sala Iglesia al 100 % en una pista: la otra sale igual")
        val onBackground = session.play(ProbeMix(background = backgroundTone(room = RoomKind.Church), voice = voiceTone(), voiceOffsetMs = 500))
        val voiceTrack = onBackground.voiceCapture.first()
        checks.near("voz sin efecto: nivel en su pista", (voiceTrack.tone(880.0, 200.0, 2_800.0) / TONE).db(), 0.0, Tolerance.PLATEAU_DB, " dB")
        checks.near("voz sin efecto: nivel en la mezcla", (onBackground.mixCapture.first().tone(880.0, 700.0, 3_300.0) / TONE).db(), 0.0, Tolerance.PLATEAU_DB, " dB")
        checks.relative("voz sin efecto: tono", voiceTrack.peakHz(200.0, 2_800.0, 860.0, 900.0), 880.0, Tolerance.PITCH, " Hz")

        val onVoice = session.play(ProbeMix(background = backgroundTone(), voice = voiceTone(room = RoomKind.Church), voiceOffsetMs = 500))
        val backgroundTrack = onVoice.backgroundCapture.first()
        checks.near("fondo sin efecto: nivel en su pista", (backgroundTrack.tone(330.0, 200.0, 3_800.0) / TONE).db(), 0.0, Tolerance.PLATEAU_DB, " dB")
        checks.near("fondo sin efecto: nivel en la mezcla", (onVoice.mixCapture.first().tone(330.0, 200.0, 3_800.0) / TONE).db(), 0.0, Tolerance.PLATEAU_DB, " dB")
        checks.relative("fondo sin efecto: tono", backgroundTrack.pitchHz(200.0, 3_800.0), 330.0, Tolerance.PITCH, " Hz")
        checks.assertAll()
    }

    @Test
    fun aSlowerBackgroundLastsLongerAndSoundsLower() {
        val checks = Checks("Velocidad y tono del fondo: 1×, 0,9× y 0,8×")
        for (level in SlowLevel.entries) {
            val speed = level.speed.toDouble()
            // Tren de 9 pulsos, uno cada 500 ms desde los 0,5 s: duración y separación.
            val pulses = ProbeMix(background = ProbeTrack(session.media("pulsos.m4a"), 5_000, speed = level.speed))
            val pulseRun = session.play(pulses)
            val out = pulseRun.mixCapture.first()
            val onsets = out.pulseOnsets(TONE_RMS / 2, 0.0, out.endMs)
            checks.near("${level.name}: pulsos", onsets.size.toDouble(), 9.0, 0.0)
            checks.near("${level.name}: del primer al último pulso", onsets.last() - onsets.first(), 4_000 / speed, Tolerance.PULSE_SPAN_MS, " ms")
            checks.near("${level.name}: primer pulso", onsets.first(), 500 / speed, Tolerance.ENTRY_MS, " ms")
            checks.near("${level.name}: duración calculada", pulses.durationMs, 5_000 / speed, 1.0, " ms")
            checks.near("${level.name}: final de la captura", out.endMs, 5_000 / speed, Tolerance.END_MS, " ms")

            // El tramo de 330 Hz del fondo con la voz encima: tono, recorte y voz intacta.
            val tones = ProbeMix(background = backgroundTone(speed = level.speed), voice = voiceTone(), voiceOffsetMs = 1_000)
            val toneRun = session.play(tones)
            val mix = toneRun.mixCapture.first()
            val lengthMs = 4_000 / speed
            checks.relative("${level.name}: tono del fondo", mix.pitchHz(100.0, 900.0), 330 * speed, Tolerance.PITCH, " Hz")
            checks.near("${level.name}: largo del fondo en la hope", toneRun.backgroundCapture.first().endMs, lengthMs, Tolerance.END_MS, " ms")
            checks.near("${level.name}: fin del fondo en la mezcla", mix.toneEnd(330 * speed, TONE / 2, lengthMs - 500, lengthMs + 500)!!, lengthMs, Tolerance.ENTRY_MS, " ms")
            val neighbors = maxOf(mix.tone(220 * speed, 100.0, lengthMs - 100), mix.tone(440 * speed, 100.0, lengthMs - 100)) / TONE
            checks.atMost("${level.name}: tramos vecinos del fondo", neighbors.db(), Tolerance.NEIGHBOR_DB, " dB")
            checks.near("${level.name}: duración de la hope", tones.durationMs, maxOf(lengthMs, 4_000.0), 1.0, " ms")
            checks.relative("${level.name}: tono de la voz", mix.peakHz(1_200.0, 3_800.0, 860.0, 900.0), 880.0, Tolerance.PITCH, " Hz")
            checks.near("${level.name}: entrada de la voz", mix.toneOnset(880.0, TONE / 2, 500.0, 1_500.0)!!, 1_000.0, Tolerance.ENTRY_MS, " ms")
            checks.near("${level.name}: nivel de la voz en su pista", (toneRun.voiceCapture.first().tone(880.0, 200.0, 2_800.0) / TONE).db(), 0.0, Tolerance.PLATEAU_DB, " dB")
        }
        checks.assertAll()
    }

    @Test
    fun clearVoiceLowersTheLowsAgainstTheMids() {
        val checks = Checks("Voz clara: un grave de 80 Hz frente a un medio de 1 kHz")
        fun play(clear: Boolean) = session.play(
            ProbeMix(background = backgroundTone(), voice = ProbeTrack(session.media("graves-medios.m4a"), 4_000, clearVoice = clear)),
        ).mixCapture.first()
        val off = play(clear = false)
        val on = play(clear = true)
        val low = (on.tone(80.0, 300.0, 3_700.0) / off.tone(80.0, 300.0, 3_700.0)).db()
        val mid = (on.tone(1_000.0, 300.0, 3_700.0) / off.tone(1_000.0, 300.0, 3_700.0)).db()
        val expectedLow = ClearVoice.responseAt(80.0, 44_100).db()
        val expectedMid = ClearVoice.responseAt(1_000.0, 44_100).db()
        checks.near("cambio del grave", low, expectedLow, Tolerance.RESPONSE_DB, " dB")
        checks.near("cambio del medio", mid, expectedMid, Tolerance.RESPONSE_DB, " dB")
        checks.near("grave frente a medio", low - mid, expectedLow - expectedMid, Tolerance.RESPONSE_DB, " dB")
        checks.near("fondo sin efecto, igual en las dos", (on.tone(330.0, 300.0, 3_700.0) / off.tone(330.0, 300.0, 3_700.0)).db(), 0.0, Tolerance.PLATEAU_DB, " dB")
        checks.assertAll()
    }

    @Test
    fun theBackgroundDucksUnderTheVoice() {
        val checks = Checks("Fondo bajo la voz: voz de 2 s desde 1 s; bajada en 200 ms antes de la voz y subida en 500 ms después")
        for (level in DuckLevel.entries) {
            val mix = ProbeMix(background = backgroundTone(), voice = voiceTone(endMs = 4_000), voiceOffsetMs = 1_000, duck = level)
            val run = session.play(mix)
            val out = run.mixCapture.first()
            val gain = run.backgroundGain!!
            checks.near("${level.name}: fondo antes de la voz", (out.tone(330.0, 200.0, 700.0) / TONE).db(), 0.0, Tolerance.PLATEAU_DB, " dB")
            checks.near("${level.name}: mitad de la bajada", out.toneAt(330.0, 900.0) / TONE, gain.gainAt(900.0), Tolerance.GAIN)
            checks.near("${level.name}: fondo bajo la voz", (out.tone(330.0, 1_200.0, 2_800.0) / TONE).db(), level.db, Tolerance.PLATEAU_DB, " dB")
            checks.near("${level.name}: mitad de la subida", out.toneAt(330.0, 3_250.0) / TONE, gain.gainAt(3_250.0), Tolerance.GAIN)
            checks.near("${level.name}: fondo después", (out.tone(330.0, 3_600.0, 3_950.0) / TONE).db(), 0.0, Tolerance.PLATEAU_DB, " dB")
            checks.near("${level.name}: voz intacta", (out.tone(880.0, 1_200.0, 2_800.0) / TONE).db(), 0.0, Tolerance.PLATEAU_DB, " dB")
            checks.near("${level.name}: entrada de la voz", out.toneOnset(880.0, TONE / 2, 500.0, 1_500.0)!!, 1_000.0, Tolerance.ENTRY_MS, " ms")
        }
        checks.assertAll()
    }

    /**
     * Todos los efectos a la vez. Fondo: tren de pulsos a 0,9×, Iglesia al 40 %, volumen 80 %,
     * fundidos de 0,5 y 1 s y bajada «Medio» bajo la voz. Voz: grave y medio hasta los 2 s, un pulso
     * y silencio, desde 1 s, con Sala al 50 %, voz clara y fundidos de 0,3 y 0,2 s. Hope: fundidos de
     * 0,5 y 0,8 s.
     *
     * La referencia es la misma hope sin voz clara y con el fondo sin envolvente (volumen 100 %, sin
     * fundidos ni bajada). Como la ganancia va después de la sala, la proporción entre las dos es
     * exactamente la envolvente, aunque la cola de un pulso se sume al siguiente.
     */
    @Test
    fun everyEffectAtOnceAlsoAfterASeek() {
        fun allEffects(reference: Boolean = false) = ProbeMix(
            background = ProbeTrack(
                session.media("pulsos.m4a"), 5_000, speed = SlowLevel.Slow.speed, room = RoomKind.Church, roomAmount = 0.4f,
                volume = if (reference) 1f else 0.8f, fadeInMs = if (reference) 0 else 500, fadeOutMs = if (reference) 0 else 1_000,
            ),
            voice = ProbeTrack(
                session.media("voz-efectos.m4a"), 4_000, endMs = 3_000, room = RoomKind.Hall, roomAmount = 0.5f,
                clearVoice = !reference, fadeInMs = 300, fadeOutMs = 200,
            ),
            voiceOffsetMs = 1_000, fadeInMs = 500, fadeOutMs = 800, duck = if (reference) null else DuckLevel.Medium,
        )
        val checks = Checks("Todos los efectos a la vez")
        val mix = allEffects()
        var playerDurationMs = 0L
        val run = session.play(mix) { playerDurationMs = durationMs }
        val reference = session.play(allEffects(reference = true))
        val bgTrack = run.backgroundCapture.first()
        val bgReference = reference.backgroundCapture.first()
        val voiceTrack = run.voiceCapture.first()
        val out = run.mixCapture.first()
        val bgGain = run.backgroundGain!!
        val spacing = 500 / 0.9
        fun Take.pulseLevel(onset: Double) = rms(onset + 2, onset + 32)

        checks.near("duración calculada (el fondo lento manda)", mix.durationMs, 5_000 / 0.9, 1.0, " ms")
        checks.near("duración del reproductor", playerDurationMs.toDouble(), mix.durationMs, 1.0, " ms")
        checks.near("final de la captura", out.endMs, mix.durationMs, Tolerance.END_MS, " ms")
        // Fondo: velocidad, tono, sala y envolvente (volumen, fundidos y bajada), en su pista.
        val onsets = (1..9).map { bgTrack.onsetNear(it * spacing) }
        checks.near("fondo: del primer al último pulso", onsets.last() - onsets.first(), 8 * spacing, Tolerance.PULSE_SPAN_MS, " ms")
        checks.relative("fondo: tono del primer pulso", bgTrack.peakHz(onsets[0] + 5, onsets[0] + 50, 850.0, 950.0), 900.0, 0.01, " Hz")
        onsets.forEachIndexed { i, onset ->
            val envelope = bgTrack.pulseLevel(onset) / bgReference.pulseLevel(onset)
            checks.near("fondo: envolvente en el pulso ${i + 1} (%.0f ms)".format(onset), envelope, bgGain.gainAt(onset + 17), Tolerance.GAIN)
        }
        val tailDb = (bgReference.rms(onsets[0] + 150, onsets[0] + 170) / bgReference.pulseLevel(onsets[0])).db()
        checks.atLeast("fondo: cola de la Iglesia 100 ms después del primer pulso", tailDb, -30.0, " dB")
        // Voz: la cola de su sala y voz clara frente a la referencia sin ella.
        val voicePulse = PulseTail(voiceTrack, 2_000.0)
        checks.relative("voz: RT60 de la Sala tras su pulso", voiceTrack.rt60(voicePulse.onsetMs + PULSE_MS + 10, 2_790.0), SIMULATED.getValue(RoomKind.Hall).first, Tolerance.RT60, " s")
        val voiceReference = reference.voiceCapture.first()
        val low = (voiceTrack.tone(80.0, 400.0, 1_900.0) / voiceReference.tone(80.0, 400.0, 1_900.0)).db()
        val mid = (voiceTrack.tone(1_000.0, 400.0, 1_900.0) / voiceReference.tone(1_000.0, 400.0, 1_900.0)).db()
        checks.near("voz clara: grave", low, ClearVoice.responseAt(80.0, 44_100).db(), Tolerance.RESPONSE_DB, " dB")
        checks.near("voz clara: medio", mid, ClearVoice.responseAt(1_000.0, 44_100).db(), Tolerance.RESPONSE_DB, " dB")
        // Entrada de la voz: su primer instante audible (1 kHz al 5 % de su nivel) en su pista, más el desfase. El pulso
        // de la voz confirma que el tiempo de la pista más el desfase es el de la mezcla.
        val voiceMid = voiceTrack.tone(1_000.0, 500.0, 1_900.0)
        val entry = voiceTrack.toneOnset(1_000.0, voiceMid * 0.05, 0.0, 500.0)!! + mix.voiceOffsetMs
        checks.near("entrada de la voz (1 kHz al 5 %)", entry, 1_000.0, Tolerance.ENTRY_MS, " ms")
        val voicePulseInMix = out.onsetNear(3_000.0)
        checks.near("pulso de la voz en la mezcla", voicePulseInMix, 3_000.0, Tolerance.ENTRY_MS, " ms")
        checks.near("pulso de la voz: mezcla contra pista más desfase", voicePulseInMix, voicePulse.onsetMs + mix.voiceOffsetMs, Tolerance.SAME_MARK_MS, " ms")
        // Los pulsos 1, 8 y 9 caen donde no suena la voz.
        listOf(0, 7, 8).forEach { checks.near("pulso ${it + 1} del fondo en la mezcla", out.onsetNear(onsets[it]), onsets[it], Tolerance.SAME_MARK_MS, " ms") }
        val lastPulse = onsets.last()
        checks.near("fundido de la hope sobre el último pulso", out.pulseLevel(lastPulse) / bgTrack.pulseLevel(lastPulse), run.mixGain.gainAt(lastPulse + 17), Tolerance.GAIN)

        // Lo mismo después de saltar a 2,7 s: pulsos, voz y ganancias en su lugar.
        val seek = session.play(allEffects()) {
            waitForPosition(200)
            seekTo(2_700)
        }
        val seekOut = seek.mixCapture.takeFrom(2_700)
        val seekTrack = seek.backgroundCapture.takeFrom(2_700)
        checks.near("tras saltar: pulso de la voz", seekOut.onsetNear(3_000.0), voicePulseInMix, Tolerance.SAME_MARK_MS, " ms")
        (4..8).forEach {
            val onset = seekTrack.onsetNear(onsets[it])
            checks.near("tras saltar: pulso ${it + 1} del fondo", onset, onsets[it], Tolerance.SAME_MARK_MS, " ms")
            // Al saltar la sala arranca sin la cola anterior: el pulso 5 no tiene la del 4 (unos -31 dB).
            val envelope = seekTrack.pulseLevel(onset) / bgReference.pulseLevel(onset)
            checks.near("tras saltar: envolvente en el pulso ${it + 1}", envelope, bgGain.gainAt(onset + 17), Tolerance.GAIN)
        }
        checks.near("tras saltar: fundido de la hope sobre el último pulso", seekOut.pulseLevel(lastPulse) / seekTrack.pulseLevel(lastPulse), run.mixGain.gainAt(lastPulse + 17), Tolerance.GAIN)
        checks.near("tras saltar: final de la captura", seekOut.endMs, mix.durationMs, Tolerance.END_MS, " ms")
        checks.note("diferencia de la voz con y sin salto", "%.1f ms".format(seekOut.onsetNear(3_000.0) - voicePulseInMix))
        checks.assertAll()
    }

    private companion object {
        const val PULSE_MS = 30.0

        /**
         * RT60 (s) y cola a los 100 ms (dB respecto del pulso) de cada sala al 100 %, de una simulación
         * en el host del mismo algoritmo con un pulso de ruido blanco de 30 ms a 44,1 kHz.
         */
        val SIMULATED = mapOf(
            RoomKind.Room to (0.353 to -13.9),
            RoomKind.Hall to (1.174 to -10.1),
            RoomKind.Church to (2.843 to -10.3),
        )
    }
}
