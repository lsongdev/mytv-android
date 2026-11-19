package me.lsong.mytv.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.lsong.mytv.domain.provider.ProviderKind

class AppViewModel : ViewModel() {
    val liveCatalog = AppContainer.liveCatalog()

    init {
        viewModelScope.launch { AppContainer.epg.observe(androidx.compose.runtime.snapshotFlow { liveCatalog.providerCatalogs }) }
        viewModelScope.launch { AppContainer.epg.observe(androidx.compose.runtime.snapshotFlow { liveCatalog.providerCatalogs }) }
        viewModelScope.launch {
            AppContainer.providerConfigs.providers
                .map { providers -> providers.filter { it.kind == ProviderKind.M3U && it.isEnabled } }
                .distinctUntilChanged()
                .collectLatest { providers -> liveCatalog.load(providers) }
        }
    }
}
