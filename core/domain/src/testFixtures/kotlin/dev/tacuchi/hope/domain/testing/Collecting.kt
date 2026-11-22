package dev.tacuchi.hope.domain.testing

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** Recoge [flow] como lo haría la pantalla, para que los estados con `WhileSubscribed` se activen. */
fun collectWhileTesting(flow: Flow<*>): Job = CoroutineScope(Dispatchers.Main.immediate).launch { flow.collect {} }
