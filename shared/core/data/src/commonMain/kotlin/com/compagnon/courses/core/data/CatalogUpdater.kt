package com.compagnon.courses.core.data

import co.touchlab.kermit.Logger
import kotlin.coroutines.cancellation.CancellationException
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The last check of the server's catalog: [running] while it runs; [result] of the last one that ended, null
 * before the first.
 */
data class CatalogCheck(val running: Boolean = false, val result: SyncResult? = null)

/**
 * Keeps the local catalog ready and current for the whole app (ADR-012). [start], once at launch: writes the catalog
 * bundled in the app when the device has none (or an older release), then asks the server for its latest release
 * and applies it when it is newer (`changes`, or the snapshot). A server that does not answer leaves the local catalog
 * as it is: the app never waits for the network.
 *
 * Its jobs run in [appScope], which outlives the screens.
 */
@OptIn(ExperimentalObjCRefinement::class)
@HiddenFromObjC
class CatalogUpdater(
    private val catalog: CatalogRepository,
    private val scope: CatalogScope,
    private val appScope: CoroutineScope,
) {
    private val started = MutableStateFlow(false)
    private val installed = CompletableDeferred<Unit>()

    private val _check = MutableStateFlow(CatalogCheck())
    val check: StateFlow<CatalogCheck> = _check.asStateFlow()

    /** Installs the bundled catalog if needed, then checks the server. Later calls do nothing. */
    fun start() {
        if (!started.compareAndSet(expect = false, update = true)) return
        appScope.launch {
            try {
                catalog.installBundled(scope.country, scope.locale)
            } finally {
                installed.complete(Unit)
            }
            runCheck()
        }
    }

    /** Returns once the bundled catalog is installed (or was not needed); starts the updater if needed. */
    suspend fun awaitInstalled() {
        start()
        installed.await()
    }

    /** Checks the server again (« Mise à jour » on the catalog line); ignored while a check runs. */
    fun checkNow() {
        if (!started.value) return start()
        appScope.launch {
            installed.await()
            runCheck()
        }
    }

    private suspend fun runCheck() {
        val previous = _check.value
        if (previous.running || !_check.compareAndSet(previous, previous.copy(running = true))) return
        val result = try {
            catalog.sync(scope.country, scope.locale)
        } catch (e: CancellationException) {
            _check.value = previous
            throw e
        } catch (e: Exception) {
            // sync returns failures as values: anything else is a bug, but must not crash the app.
            log.e(e) { "Unexpected failure while checking the catalog for ${scope.country}/${scope.locale}" }
            null
        }
        _check.value = CatalogCheck(running = false, result = result ?: previous.result)
        when (result) {
            is SyncResult.Updated -> log.i { "Catalog updated from ${result.fromVersion} to ${result.toVersion} (${result.method})" }
            is SyncResult.Failed -> log.i { "Catalog check failed (${result.failure}): the local catalog is kept" }
            else -> Unit
        }
    }

    private companion object {
        val log = Logger.withTag("CatalogUpdater")
    }
}
