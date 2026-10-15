package com.dhama.homefinance.ui.navigation

import androidx.compose.runtime.Composable
import androidx.core.os.bundleOf
import androidx.lifecycle.DEFAULT_ARGS_KEY
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner

/**
 * Nav3 routes are typed objects, not Bundles. This hands a route's arguments to the
 * ViewModel's SavedStateHandle (Hilt's factory builds it from DEFAULT_ARGS_KEY), the
 * equivalent of navArgs/toRoute in Navigation 2. Saved form state in the same handle
 * survives process death via the nav entry's saved-state registry.
 */
@Composable
fun navArgsExtras(vararg args: Pair<String, Any?>): CreationExtras {
    val owner = LocalViewModelStoreOwner.current
    val base = (owner as? HasDefaultViewModelProviderFactory)?.defaultViewModelCreationExtras
        ?: CreationExtras.Empty
    return MutableCreationExtras(base).apply { set(DEFAULT_ARGS_KEY, bundleOf(*args)) }
}
