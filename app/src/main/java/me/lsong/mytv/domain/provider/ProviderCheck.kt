package me.lsong.mytv.domain.provider

sealed interface ProviderCheck {
    data object Loading : ProviderCheck
    data class Result(val message: String, val successful: Boolean) : ProviderCheck
}
