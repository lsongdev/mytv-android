package me.lsong.mytv.app

import android.content.Context
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import me.lsong.mytv.data.live.LiveCatalogRepository
import me.lsong.mytv.data.preferences.*
import me.lsong.mytv.data.providers.maccms.MacCmsVideoProvider
import me.lsong.mytv.domain.provider.ProviderConfigRepository
import me.lsong.mytv.domain.identity.CatalogIdentityStore
import me.lsong.mytv.ui.video.VideoLibraryViewModel

/** Android composition root; domain and :media never reference this container. */
object AppContainer {
    lateinit var providerConfigs: ProviderConfigRepository
        private set
    lateinit var providerStorage: me.lsong.mytv.data.providers.cache.ProviderStorage
        private set
    lateinit var livePreferences: LivePreferences
        private set
    lateinit var epg: me.lsong.mytv.data.epg.EpgRepository
        private set
    lateinit var identities: CatalogIdentityStore
        private set

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        AppPreferences.init(context)
        val preferences = context.getSharedPreferences(AppConstants.PREFERENCES_NAME,Context.MODE_PRIVATE)
        providerStorage = me.lsong.mytv.data.providers.cache.ProviderStorage(java.io.File(context.cacheDir,"provider-metadata-v1"),preferences)
        livePreferences = LivePreferences(preferences)
        epg = me.lsong.mytv.data.epg.EpgRepository(livePreferences,providerStorage)
        danmakuPreferences = DanmakuPreferenceStore(preferences)
        providerConfigs = PreferencesProviderConfigRepository(preferences)
        identities = PreferencesCatalogIdentityStore(preferences)
    }
    private lateinit var applicationContext: Context
    private lateinit var danmakuPreferences: DanmakuPreferenceStore
    val danmakuViewModelFactory = viewModelFactory {
        initializer { me.lsong.mytv.ui.danmaku.DanmakuViewModel(
            me.lsong.mytv.data.danmaku.DanmakuRepository(java.io.File(applicationContext.cacheDir,"danmaku-v1")), danmakuPreferences) }
    }
    fun liveCatalog() = LiveCatalogRepository(provider = me.lsong.mytv.data.providers.m3u.M3uLiveProviderAdapter(storage = providerStorage), identities = identities)
    val videoViewModelFactory = viewModelFactory {
        initializer { VideoLibraryViewModel(providerConfigs, MacCmsVideoProvider(storage = providerStorage), identities) }
    }
}
