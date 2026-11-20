package me.lsong.mytv.ui.browser

import me.lsong.mytv.domain.live.LiveChannel
import me.lsong.mytv.ui.navigation.Section

data class BrowseCategory(val id: String, val title: String, val subtitle: String? = null)

sealed interface BrowseEntry {
    val id: String
    val title: String
    val subtitle: String? get() = null
    val playing: Boolean get() = false

    data class Application(val app: me.lsong.mytv.data.apps.InstalledApp) : BrowseEntry {
        override val id get() = app.id
        override val title get() = app.title
    }

    data class Navigation(val section: Section, val providerCount: Int, override val playing: Boolean = false) : BrowseEntry {
        override val id get() = section.key
        override val title get() = section.title
        override val subtitle get() = "$providerCount 个源"
    }
    data class Category(val category: BrowseCategory) : BrowseEntry {
        override val id get() = category.id
        override val title get() = category.title
        override val subtitle get() = category.subtitle
    }
    data class Channel(val channel: LiveChannel, override val playing: Boolean, override val subtitle: String? = null, val logo: String? = channel.logo, val showLogo: Boolean = true, val fallbackLogo: String? = null) : BrowseEntry {
        override val id get() = channel.id
        override val title get() = channel.title
    }
}
