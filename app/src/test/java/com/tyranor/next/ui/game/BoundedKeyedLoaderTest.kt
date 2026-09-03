package com.tyranor.next.ui.game

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundedKeyedLoaderTest {
    @Test
    fun sameKeyLoadsOnlyOnce() = runTest {
        val coordinator = BoundedKeyedLoader<String>(parallelism = 2)
        val cache = ConcurrentHashMap<String, Int>()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val calls = AtomicInteger()

        suspend fun load() = coordinator.load("cover", { cache["cover"] }) {
            calls.incrementAndGet()
            entered.complete(Unit)
            release.await()
            7.also { cache["cover"] = it }
        }

        val first = async { load() }
        entered.await()
        val second = async { load() }
        release.complete(Unit)

        assertEquals(7, first.await())
        assertEquals(7, second.await())
        assertEquals(1, calls.get())
    }

    @Test
    fun cancelledOwnerDoesNotPoisonNextWaiter() = runTest {
        val coordinator = BoundedKeyedLoader<String>(parallelism = 2)
        val ownerEntered = CompletableDeferred<Unit>()
        val owner = async {
            coordinator.load("cover", { null as Int? }) {
                ownerEntered.complete(Unit)
                awaitCancellation()
            }
        }
        ownerEntered.await()
        val waiter = async {
            coordinator.load("cover", { null as Int? }) { 9 }
        }

        owner.cancelAndJoin()

        assertEquals(9, waiter.await())
    }

    @Test
    fun limitsDifferentKeysToConfiguredParallelism() = runTest {
        val coordinator = BoundedKeyedLoader<Int>(parallelism = 2)
        val active = AtomicInteger()
        val maximum = AtomicInteger()
        val tasks = (0 until 8).map { key ->
            async {
                coordinator.load(key, { null as Int? }) {
                    val now = active.incrementAndGet()
                    maximum.updateAndGet { previous -> maxOf(previous, now) }
                    delay(10)
                    active.decrementAndGet()
                    key
                }
            }
        }

        val results = tasks.map { it.await() }

        assertTrue(maximum.get() <= 2)
        assertEquals((0 until 8).toList(), results)
    }
}
