package me.lsong.mytv.app

import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.map
import android.content.Context
import coil.imageLoader
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
    lateinit var playbackSessions: me.lsong.mytv.data.playback.PlaybackSessionStore
        private set
    lateinit var identities: CatalogIdentityStore
        private set

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        AppPreferences.init(context)
        val preferences = context.getSharedPreferences(AppConstants.PREFERENCES_NAME,Context.MODE_PRIVATE)
        providerStorage = me.lsong.mytv.data.providers.cache.ProviderStorage(java.io.File(context.cacheDir,"provider-metadata-v1"),preferences)
        playbackSessions = me.lsong.mytv.data.playback.PlaybackSessionStore(preferences)
        livePreferences = LivePreferences(preferences)
        epg = me.lsong.mytv.data.epg.EpgRepository(livePreferences,providerStorage)
        danmakuPreferences = DanmakuPreferenceStore(preferences)
        providerConfigs = PreferencesProviderConfigRepository(preferences)
        identities = PreferencesCatalogIdentityStore(preferences)
    }
    val danmakuRepository by lazy {
        me.lsong.mytv.data.danmaku.DanmakuRepository(java.io.File(applicationContext.cacheDir, "danmaku-v1"))
    }
    val cacheManager by lazy {
        me.lsong.mytv.data.cache.AppCacheManager(providerStorage, danmakuRepository, applicationContext.imageLoader)
    }
    private lateinit var applicationContext: Context
    private lateinit var danmakuPreferences: DanmakuPreferenceStore
    val danmakuViewModelFactory = viewModelFactory {
        initializer { me.lsong.mytv.ui.danmaku.DanmakuViewModel(
            danmakuRepository, danmakuPreferences) }
    }
    val liveCatalog by lazy { LiveCatalogRepository(provider = me.lsong.mytv.data.providers.m3u.M3uLiveProviderAdapter(storage = providerStorage), identities = identities) }
    val videoCatalog by lazy { me.lsong.mytv.data.video.VideoCatalogRepository(
        providerConfigs,MacCmsVideoProvider(storage = providerStorage),identities) }
    val installedApps by lazy { me.lsong.mytv.data.apps.InstalledAppsRepository(
        load = { me.lsong.mytv.data.apps.loadInstalledApps(applicationContext) }) }
    private val appsObserver by lazy { me.lsong.mytv.data.apps.InstalledAppsObserver(applicationContext,installedApps) }
    private val cataloguesStarted = java.util.concurrent.atomic.AtomicBoolean()
    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default)
    fun startCatalogues() {
        if (!cataloguesStarted.compareAndSet(false,true)) return
        liveCatalog.start(providerConfigs.providers)
        installedApps.start()
        appsObserver.start()
        videoCatalog.start()
        scope.launch { epg.observe(liveCatalog.state.map { it.providers }) }
    }
    val videoViewModelFactory = viewModelFactory {
        initializer { VideoLibraryViewModel(videoCatalog) }
    }
}
