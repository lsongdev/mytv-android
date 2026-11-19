package me.lsong.mytv.ui.settings

import me.lsong.mytv.app.AppContainer
import me.lsong.mytv.data.providers.ProviderProbe
import me.lsong.mytv.domain.provider.ProviderCheck

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import me.lsong.mytv.domain.provider.ProviderConfig
import me.lsong.mytv.domain.provider.ProviderConfigRepository

class ProviderSettingsViewModel(
    private val repository: ProviderConfigRepository = AppContainer.providerConfigs,
    private val checker: suspend (ProviderConfig) -> ProviderCheck.Result = ProviderProbe::check,
) : ViewModel() {
    val providers = repository.providers
    val health = AppContainer.providerStorage.health
    fun status(provider: ProviderConfig) = AppContainer.providerStorage.state(provider)
    var check by mutableStateOf<ProviderCheck?>(null)
        private set
    private var checkJob: Job? = null

    fun save(source: ProviderConfig): String? = runCatching { repository.save(source) }.exceptionOrNull()?.message
    fun delete(id: String) = repository.delete(id)

    fun clearCheck() {
        checkJob?.cancel()
        check = null
    }

    fun test(source: ProviderConfig) {
        clearCheck()
        check = ProviderCheck.Loading
        checkJob = viewModelScope.launch {
            check = try {
                checker(source.normalized())
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                ProviderCheck.Result("连接失败，请检查地址和网络", false)
            }
        }
    }
}
