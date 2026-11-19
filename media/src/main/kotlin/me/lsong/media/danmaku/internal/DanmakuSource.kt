package me.lsong.media.danmaku.internal

import kotlinx.serialization.Serializable
import me.lsong.media.danmaku.DanmakuComment
import me.lsong.media.danmaku.DanmakuMedia
import me.lsong.media.danmaku.DanmakuEpisode

/**
 * 弹幕源契约。镜像 misaka_danmu_server 的 BaseScraper（search / get_episodes / get_comments），
 * 这样 Phase 1 引入脚本引擎后，每个远程 JS 脚本只需实现同样三步，宿主桥接即可一一对应。
 *
 * 约定（与项目其余 service 一致）：
 * - 方法均为同步阻塞，必须在 withContext(Dispatchers.IO) 内调用。
 * - best-effort：失败/无数据返回空列表，绝不抛异常——弹幕缺席不可中断播放。
 */
internal interface DanmakuSource {
    /** 源唯一标识（如 "bilibili"），用于缓存键与日志。 */
    val providerName: String

    /** 按关键词搜索候选媒体。 */
    fun search(keyword: String): List<DanmakuMedia>

    /** 取某媒体的分集列表。 */
    fun getEpisodes(mediaId: String): List<DanmakuEpisode>

    /** 取某集的弹幕，归一为统一模型。 */
    fun getComments(episodeId: String): List<DanmakuComment>

    /**
     * 取某集 **指定时间窗** `[startSec, endSec)` 的弹幕（秒）。`endSec<=0` = 到结尾。
     * 供「先拉当前段、其余后台补」的分段流式拉取用；脚本不支持窗参则退化为全集。
     * 默认实现回退到全集 [getComments]，新源无需都改。
     */
    fun getComments(episodeId: String, startSec: Int, endSec: Int): List<DanmakuComment> =
        getComments(episodeId)
}
