package dev.tacuchi.hope.domain.model

/**
 * Con audífonos el fondo suena mientras se graba, para guiar. Por el altavoz se graba en silencio:
 * si no, el micrófono grabaría el fondo y quedaría duplicado en la mezcla.
 */
fun backgroundPlaysWhileRecording(headphones: Boolean, hasBackground: Boolean): Boolean = headphones && hasBackground
