package me.lsong.mytv.data.apps

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class InstalledAppsRepositoryTest {
    @Test fun startupScansOnceAndFreshMenuOpensReuseTheSnapshot() = runBlocking {
        val calls = AtomicInteger()
        val caller = Thread.currentThread()
        val repo = InstalledAppsRepository(load = {
            assertNotSame(caller,Thread.currentThread())
            calls.incrementAndGet(); emptyList()
        })
        try {
            repo.start(); repo.start()
            withTimeout(5000) { repo.state.first { it.refreshedAt > 0 } }
            val snapshot = repo.state.value
            repeat(30) { repo.menuOpened(); assertSame(snapshot,repo.state.value) }
            delay(100)
            assertEquals(1,calls.get())
        } finally { repo.close() }
    }

    @Test fun packageEventsDuringAScanAreCoalescedWithoutBeingLost() = runBlocking {
        val calls = AtomicInteger(); val gate = CompletableDeferred<Unit>()
        val repo = InstalledAppsRepository(load = {
            if (calls.incrementAndGet() == 1) gate.await()
            emptyList()
        })
        try {
            repo.start()
            withTimeout(5000) { while (calls.get() == 0) delay(5) }
            repeat(30) { repo.refresh() }
            gate.complete(Unit)
            withTimeout(5000) { while (calls.get() < 2 || repo.state.value.loading) delay(5) }
            delay(100)
            assertEquals(2,calls.get())
        } finally { gate.complete(Unit); repo.close() }
    }

    @Test fun staleMenuTriggersOneBackgroundScanAndFailuresKeepTheOldList() = runBlocking {
        var clock = 100_000L; val calls = AtomicInteger()
        val repo = InstalledAppsRepository(load = {
            if (calls.incrementAndGet() > 1) error("package manager unavailable")
            emptyList()
        },now = { clock })
        try {
            repo.start()
            withTimeout(5000) { repo.state.first { it.refreshedAt > 0 } }
            val apps = repo.state.value.apps
            clock += 61_000
            repo.menuOpened()
            withTimeout(5000) { repo.state.first { it.error != null } }
            assertSame(apps,repo.state.value.apps)
            assertFalse(repo.state.value.loading)
            repeat(30) { repo.menuOpened() }
            delay(100)
            assertEquals(2,calls.get())
        } finally { repo.close() }
    }
}
