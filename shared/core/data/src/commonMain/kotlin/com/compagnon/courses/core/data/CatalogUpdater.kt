package com.compagnon.courses.core.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Keeps the local catalog ready for the whole app (ADR-012). [start], once at launch: writes the catalog bundled in
 * the app when the device has none (or an older release). [switchTo] does the same for another language while the
 * app runs.
 *
 * Its jobs run in [appScope], which outlives the screens.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
class CatalogUpdater(
    private val catalog: CatalogRepository,
    scope: CatalogScope,
    private val appScope: CoroutineScope,
) {
    private val scopes = MutableStateFlow(scope)
    private val started = MutableStateFlow(false)
    private val installed = CompletableDeferred<Unit>()

    /** The projection the app works with now: it follows [switchTo]. */
    val currentScope: CatalogScope get() = scopes.value

    /**
     * Installs the bundled catalog if needed, and again after each [switchTo]; a new scope replaces the install still
     * running for the previous one. Later calls do nothing.
     */
    fun start() {
        if (!started.compareAndSet(expect = false, update = true)) return
        appScope.launch {
            scopes.collectLatest { scope ->
                catalog.installBundled(scope.country, scope.locale)
                installed.complete(Unit)
            }
        }
    }

    /** Moves the app to [scope] (the device language changed while the process lives); the same scope does nothing. */
    fun switchTo(scope: CatalogScope) {
        scopes.value = scope
    }

    /** Returns once the bundled catalog is installed (or was not needed); starts the updater if needed. */
    suspend fun awaitInstalled() {
        start()
        installed.await()
    }
}
