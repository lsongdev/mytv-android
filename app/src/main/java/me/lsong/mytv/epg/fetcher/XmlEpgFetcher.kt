package me.lsong.mytv.epg.fetcher

import okhttp3.Response

class XmlEpgFetcher : EpgFetcher {
    override fun isSupport(url: String): Boolean {
        return url.endsWith(".xml")
    }

    override fun fetch(response: Response): String {
        return response.body!!.string()
    }
}