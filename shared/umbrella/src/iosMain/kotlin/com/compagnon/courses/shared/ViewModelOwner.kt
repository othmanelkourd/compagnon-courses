package com.compagnon.courses.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import kotlin.reflect.KClass

/**
 * Owns shared ViewModels on behalf of a Swift object, which has no lifecycle-aware owner of its own.
 *
 * Swift keeps one instance next to the ViewModels it uses and calls [clear] from `deinit`. Clearing runs
 * `ViewModel.onCleared()` and cancels each `viewModelScope`, exactly as an Android screen leaving the
 * back stack does. Only a [ViewModelStore] can clear a ViewModel, hence this class.
 *
 * Not thread-safe: use one owner from one thread at a time (main thread, then the Swift `deinit`).
 */
class ViewModelOwner {

    private val store = ViewModelStore()
    private val owned = mutableListOf<ViewModel>()

    /** Takes ownership of [viewModel]; the next [clear] clears it. Owning the same instance twice is a no-op. */
    fun own(viewModel: ViewModel) {
        if (owned.any { it === viewModel }) return
        @Suppress("UNCHECKED_CAST")
        val modelClass = viewModel::class as KClass<ViewModel>
        // The public way to put an existing instance in a store: a provider whose factory returns it.
        ViewModelProvider.create(store, InstanceFactory(viewModel))["$KEY_PREFIX${owned.size}", modelClass]
        owned += viewModel
    }

    /** Clears every owned ViewModel. Idempotent; the owner can be reused afterwards. */
    fun clear() {
        owned.clear()
        store.clear()
    }

    private class InstanceFactory(private val instance: ViewModel) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: KClass<T>, extras: CreationExtras): T {
            @Suppress("UNCHECKED_CAST")
            return instance as T
        }
    }

    private companion object {
        const val KEY_PREFIX = "com.compagnon.courses.shared.ViewModelOwner:"
    }
}
