package com.example.phoneagent.data.auth

import com.example.phoneagent.domain.fakes.FakeAppLogger
import com.example.phoneagent.domain.fakes.FakeDispatcherProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The blocking socket accept must run on the dispatcher supplied through the port. Observable by
 * occupying the port's only worker: then the accept cannot even start while a knock arrives, so
 * the caller does not complete within a short grace window. Without the hop the accept would run
 * on the caller's thread and the knock would be answered as Received right away.
 */
class LoopbackCallbackReceiverDispatcherTest {
    @Test
    fun `the callback accept runs on the dispatcher provided by the port`() {
        val port = ServerSocket(0).use { it.localPort }
        val ioWorkerBusy = CountDownLatch(1)
        val io =
            Executors.newSingleThreadExecutor { r -> Thread(r, "loopback-test-io") }.asCoroutineDispatcher()
        try {
            val receiver =
                LoopbackCallbackReceiver(FakeAppLogger(), port, FakeDispatcherProvider(main = io, io = io, default = io))
            assertEquals(CallbackReceiverStartResult.Started, receiver.start())

            // Occupy the single io worker so nothing dispatched to it can proceed.
            io.executor.execute { ioWorkerBusy.await() }
            try {
                runBlocking {
                    val callback =
                        async(start = CoroutineStart.UNDISPATCHED) {
                            receiver.awaitCallback(timeoutMillis = 400L)
                        }
                    // The knock runs on its own daemon thread with a read timeout: in the fixed
                    // world nobody accepts it until the port's worker is released.
                    val knock =
                        thread(isDaemon = true) {
                            runCatching {
                                Socket("localhost", port).use { socket ->
                                    socket.soTimeout = 5_000
                                    socket.getOutputStream().write(
                                        "GET /auth/callback?code=c&state=s HTTP/1.1\r\nHost: localhost\r\n\r\n".toByteArray(),
                                    )
                                    socket.getInputStream().read()
                                }
                            }
                        }
                    Thread.sleep(150)

                    // The callback must still be pending: its accept is queued behind the busy
                    // worker, so a short grace window cannot complete it.
                    val finishedWithinGrace =
                        withTimeoutOrNull(1_000L) {
                            callback.await()
                            true
                        } ?: false
                    assertFalse("the accept ran outside the port's dispatcher", finishedWithinGrace)

                    // Unblock the port's worker: the queued accept then takes the waiting knock
                    // and the callback completes.
                    ioWorkerBusy.countDown()
                    assertEquals(CallbackResult.Received(code = "c", state = "s"), callback.await())
                    knock.join(5_000)
                }
            } finally {
                ioWorkerBusy.countDown()
                receiver.close()
            }
        } finally {
            io.close()
        }
    }
}
