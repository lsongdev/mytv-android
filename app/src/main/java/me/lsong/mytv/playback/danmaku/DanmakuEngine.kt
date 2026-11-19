package me.lsong.mytv.playback.danmaku

/**
 * 弹幕布局引擎（纯 Kotlin，零 android.graphics 依赖 → JVM 可单测）。
 *
 * 持一条按 timeMs 排序的列表 + 时间游标：advance(nowMs) 把落入 (上次, now] 的项 spawn 进轨道、
 * retire 过期项、重算每条位置；draw 由外部 DanmakuRenderer 读取 [renderActive] 完成。所有状态只应
 * 在渲染线程触碰（调用方保证），故内部不加锁。
 *
 * 跳转语义：
 * - [seekTo] 软跳转——保留已发射弹幕（重定基使其维持在屏继续飞出），仅重定位游标。用于切集/拖拽/漂移校正。
 * - [clear] 硬清屏——回收全部在屏弹幕。仅用于弹窗切匹配（用户要求必清）。
 *
 * 预览（弹窗打开、视频暂停时）：用独立缓冲 [previewActive] + 独立游标循环重放「当前时间点」的弹幕，
 * 不触碰正常播放状态，故关弹窗即原样恢复、绝不清空已发射弹幕。
 */
class DanmakuEngine(private val measurer: TextMeasurer) {

    var config = DanmakuEngineConfig(); private set

    private var items: List<DanmakuItem> = emptyList()
    private var cursor = 0
    private var clockMs = 0L
    private var viewW = 0
    private var viewH = 0

    private val pool = ArrayList<ActiveDanmaku>()
    private val activeList = ArrayList<ActiveDanmaku>()

    // 按 timeMs 升序的显式比较器：Long.compareTo 是 intrinsic，避免 compareBy/sortedBy 对每个 Long 装箱
    // （分片源每到一块都重排全量窗口，装箱会在低端机上造成 GC 抖动）。
    private val byTime = Comparator<DanmakuItem> { a, b -> a.timeMs.compareTo(b.timeMs) }

    // 预览缓冲与游标：当前时刻一窗弹幕「无缝无限循环」重放，独立于正常播放状态。
    // previewLapOffsetMs 是当前一圈窗口起点对齐到的预览钟值，每圈 += 一个滚动时长 → 旧的飞出、新的续入，绝不清屏。
    private var previewing = false
    private var previewClockMs = 0L
    private var previewBaseMs = 0L
    private var previewCursor = 0
    private var previewLapOffsetMs = 0L
    private val previewActive = ArrayList<ActiveDanmaku>()

    /** 正常活跃集（单测断言 / 正常播放绘制）。 */
    val active: List<ActiveDanmaku> get() = activeList
    /** 当前应绘制的集合：预览时为预览缓冲，否则为正常活跃集。 */
    val renderActive: List<ActiveDanmaku> get() = if (previewing) previewActive else activeList
    val hasContent: Boolean get() = items.isNotEmpty() || renderActive.isNotEmpty()
    val clock: Long get() = clockMs
    val isPreviewing: Boolean get() = previewing

    fun setViewport(w: Int, h: Int) { viewW = w; viewH = h }
    fun setConfig(c: DanmakuEngineConfig) { config = c }

    /** 增量 merge 后按 timeMs 排序，游标重定位到当前钟（早于当前钟的新插入项不补冒）。 */
    fun setItems(list: List<DanmakuItem>) {
        items = list.sortedWith(byTime)
        cursor = firstIndexAtOrAfter(clockMs)
        // 预览中换源/换数据：不清屏——旧的继续飞出，tickPreview 会按新数据自动续上窗口（无缝）。
        if (previewing) previewCursor = firstIndexAtOrAfter(previewBaseMs)
    }

    /** 软跳转：已发射弹幕重定基（保持在屏位置继续飞出），仅重定位游标，不清空。 */
    fun seekTo(ms: Long) {
        val delta = ms - clockMs
        if (delta != 0L) for (i in 0 until activeList.size) activeList[i].spawnTimeMs += delta
        clockMs = ms
        cursor = firstIndexAtOrAfter(ms)
    }

    /** 硬清屏：回收全部在屏弹幕（弹窗切匹配专用）。预览中则把预览缓冲也清掉并重置循环起点。 */
    fun clear() {
        recycleAll(activeList)
        if (previewing) {
            recycleAll(previewActive)
            previewCursor = firstIndexAtOrAfter(previewBaseMs)
            previewLapOffsetMs = previewBaseMs
        }
    }

    /** 推进到绝对时间 nowMs：spawn 到期项、retire 过期项、重算位置。 */
    fun advance(nowMs: Long) {
        clockMs = nowMs
        while (cursor < items.size && items[cursor].timeMs <= nowMs) {
            spawnInto(activeList, items[cursor], nowMs); cursor++
        }
        layoutAndRetire(activeList, nowMs)
    }

    // ---- 预览（循环重放当前时间点） ----

    fun beginPreview() {
        if (previewing) return
        previewing = true
        previewBaseMs = clockMs
        previewClockMs = clockMs
        previewLapOffsetMs = clockMs
        previewCursor = firstIndexAtOrAfter(clockMs)
        // 以当前在屏弹幕为起点，预览首帧与真实画面连贯
        recycleAll(previewActive)
        for (i in 0 until activeList.size) previewActive.add(copyOf(activeList[i]))
    }

    /**
     * 预览推进 dtMs：把「当前时刻一窗」[base, base+滚动时长) 的弹幕无缝无限循环重放。
     * 每条窗口弹幕按 scheduled = lapOffset + (timeMs - base) 在预览钟到点时发射；一圈走完游标回到窗口起点、
     * lapOffset += 一个滚动时长（旧的此时刚好飞出、新的从右侧续入）→ 模拟无限弹幕，绝不清屏。
     */
    fun tickPreview(dtMs: Long) {
        if (!previewing) return
        previewClockMs += dtMs
        val w = config.rollingDurationMs
        if (w > 0L && items.isNotEmpty()) {
            val windowStart = firstIndexAtOrAfter(previewBaseMs)
            val windowEnd = firstIndexAtOrAfter(previewBaseMs + w)
            if (windowEnd > windowStart) {
                if (previewCursor < windowStart || previewCursor >= windowEnd) previewCursor = windowStart
                var guard = 0
                while (guard++ < 4096) {
                    val sched = previewLapOffsetMs + (items[previewCursor].timeMs - previewBaseMs)
                    if (sched > previewClockMs) break
                    spawnInto(previewActive, items[previewCursor], sched)
                    previewCursor++
                    if (previewCursor >= windowEnd) { previewCursor = windowStart; previewLapOffsetMs += w }
                }
            }
        }
        layoutAndRetire(previewActive, previewClockMs)
    }

    /**
     * 结束预览：把预览缓冲「接管」为正常活跃集（而非丢弃回退到开窗前快照），正常钟对齐到预览钟 →
     * 关弹窗时屏上弹幕从预览位置无缝继续飞，不会跳回开窗时的位置（消除「重置」效果）。
     * 随后调用方应 [seekTo] 宿主真实位置（软跳转保位置）以对齐播放并恢复正常装填。
     */
    fun endPreview() {
        if (!previewing) return
        previewing = false
        recycleAll(activeList)                 // 释放开窗前的快照对象回池
        activeList.addAll(previewActive)       // 采用预览缓冲对象（仍 inUse）为正常活跃集
        previewActive.clear()                  // 仅清列表引用，对象已转交，勿 recycle
        clockMs = previewClockMs
        cursor = firstIndexAtOrAfter(clockMs)
    }

    // ---- internals ----

    private fun laneHeight(): Float = config.textSizePx * config.lineHeightFactor

    /** 可用高度 = 视图高 × 显示区域比例（弹幕只占顶部这一带）。 */
    private fun usableHeight(): Float = viewH * config.areaFraction.coerceIn(0f, 1f)

    private fun laneCount(): Int {
        val lh = laneHeight()
        if (lh <= 0f) return 1
        return maxOf(1, (usableHeight() / lh).toInt())
    }

    private fun firstIndexAtOrAfter(ms: Long): Int {
        var lo = 0; var hi = items.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (items[mid].timeMs < ms) lo = mid + 1 else hi = mid
        }
        return lo
    }

    private fun spawnInto(target: MutableList<ActiveDanmaku>, item: DanmakuItem, atClock: Long) {
        if (target.size >= (laneCount() * 4).coerceIn(16,128)) return
        val width = measurer.measure(item.text, config.textSizePx)
        val lane = allocLane(target, item.mode, width, atClock)
        if (lane < 0) return  // 防重叠放不下 / 无空轨 → 丢弃
        val d = obtain()
        d.id = item.id; d.text = item.text; d.colorRgb = item.colorRgb
        d.mode = item.mode; d.spawnTimeMs = atClock; d.width = width; d.lane = lane
        target.add(d)
    }

    private fun layoutAndRetire(target: MutableList<ActiveDanmaku>, nowMs: Long) {
        val laneH = laneHeight()
        var i = 0
        while (i < target.size) {
            val d = target[i]
            val expired = when (d.mode) {
                DanmakuMode.SCROLL -> {
                    val speed = (viewW + d.width) / config.rollingDurationMs.toFloat()
                    d.x = viewW - speed * (nowMs - d.spawnTimeMs)
                    d.y = d.lane * laneH + laneH
                    d.x + d.width < 0f
                }
                DanmakuMode.TOP -> {
                    d.x = (viewW - d.width) / 2f
                    d.y = d.lane * laneH + laneH
                    nowMs - d.spawnTimeMs > config.topBottomDurationMs
                }
                DanmakuMode.BOTTOM -> {
                    d.x = (viewW - d.width) / 2f
                    d.y = usableHeight() - d.lane * laneH
                    nowMs - d.spawnTimeMs > config.topBottomDurationMs
                }
            }
            if (expired) { recycle(d); target.removeAt(i) } else i++
        }
    }

    private fun allocLane(target: List<ActiveDanmaku>, mode: DanmakuMode, width: Float, nowClock: Long): Int =
        when (mode) {
            DanmakuMode.SCROLL -> allocScrollLane(target, laneCount(), width, nowClock)
            DanmakuMode.TOP -> allocStaticLane(target, laneCount(), DanmakuMode.TOP)
            DanmakuMode.BOTTOM -> allocStaticLane(target, laneCount(), DanmakuMode.BOTTOM)
        }

    // 每轨「最后一条滚动弹幕」的暂存（仅 allocScrollLane 内用，跨调用复用避免每次 spawn 分配）。
    private var laneLatestScratch: Array<ActiveDanmaku?> = arrayOfNulls(0)

    private fun allocScrollLane(target: List<ActiveDanmaku>, count: Int, width: Float, nowClock: Long): Int {
        // 单趟扫活跃集，得到每轨最后一条滚动弹幕 → 把原 O(轨数×活跃) 降为 O(活跃+轨数)。
        if (laneLatestScratch.size < count) laneLatestScratch = arrayOfNulls(count)
        val latest = laneLatestScratch
        for (i in 0 until count) latest[i] = null
        for (i in 0 until target.size) {
            val d = target[i]
            if (d.mode == DanmakuMode.SCROLL && d.lane < count) {
                val cur = latest[d.lane]
                if (cur == null || d.spawnTimeMs > cur.spawnTimeMs) latest[d.lane] = d
            }
        }
        var bestLane = -1
        var bestGap = Float.NEGATIVE_INFINITY
        for (lane in 0 until count) {
            val last = latest[lane] ?: return lane  // 空轨直接用（顺序同原实现：最低空轨优先）
            val lastSpeed = (viewW + last.width) / config.rollingDurationMs.toFloat()
            val lastRight = viewW - lastSpeed * (nowClock - last.spawnTimeMs) + last.width
            if (config.antiOverlap) {
                if (lastRight <= viewW) {                 // 前一条已完全入场才允许
                    val gap = viewW - lastRight
                    if (gap > bestGap) { bestGap = gap; bestLane = lane }
                }
            } else {
                val gap = viewW - lastRight                // 越大越空
                if (gap > bestGap) { bestGap = gap; bestLane = lane }
            }
        }
        return bestLane
    }

    private fun allocStaticLane(target: List<ActiveDanmaku>, count: Int, mode: DanmakuMode): Int {
        for (lane in 0 until count) {
            var occupied = false
            for (i in 0 until target.size) { val d = target[i]; if (d.mode == mode && d.lane == lane) { occupied = true; break } }
            if (!occupied) return lane
        }
        return -1
    }

    private fun copyOf(src: ActiveDanmaku): ActiveDanmaku {
        val d = obtain()
        d.id = src.id; d.text = src.text; d.colorRgb = src.colorRgb; d.mode = src.mode
        d.spawnTimeMs = src.spawnTimeMs; d.width = src.width; d.lane = src.lane; d.x = src.x; d.y = src.y
        return d
    }

    private fun obtain(): ActiveDanmaku {
        for (i in 0 until pool.size) { val d = pool[i]; if (!d.inUse) { d.inUse = true; return d } }
        return ActiveDanmaku().also { it.inUse = true; pool.add(it) }
    }
    private fun recycle(d: ActiveDanmaku) { d.inUse = false }
    private fun recycleAll(list: MutableList<ActiveDanmaku>) { for (i in 0 until list.size) list[i].inUse = false; list.clear() }
}
