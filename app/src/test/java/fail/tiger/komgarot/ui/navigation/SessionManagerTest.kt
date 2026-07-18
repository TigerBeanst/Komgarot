package fail.tiger.komgarot.ui.navigation

import fail.tiger.komgarot.data.remote.dto.UserDto
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class SessionManagerTest {
    @Test
    fun transientFailureRetriesAndRecoversAuthenticatedUser() = runBlocking {
        val attempts = AtomicInteger(0)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = SessionManager(
            scope = scope,
            retryDelaysMs = listOf(1L),
            fetchCurrentUser = {
                if (attempts.incrementAndGet() == 1) Result.failure(IOException("offline"))
                else Result.success(UserDto(id = "user", email = "reader@example.com", roles = listOf("ADMIN")))
            }
        )

        manager.refresh()
        withTimeout(1_000) {
            while (manager.state.value !is SessionState.Authenticated) {
                kotlinx.coroutines.yield()
            }
        }

        assertEquals(2, attempts.get())
        assertEquals("reader@example.com", manager.state.value.userOrNull?.email)
        scope.cancel()
    }

    @Test
    fun refreshKeepsPreviouslyLoadedUserDuringTemporaryFailure() = runBlocking {
        val currentUser = UserDto(id = "user", email = "reader@example.com", roles = listOf("ADMIN"))
        var result: Result<UserDto> = Result.success(currentUser)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = SessionManager(scope, retryDelaysMs = emptyList()) { result }
        manager.refresh()
        manager.awaitIdle()

        result = Result.failure(IOException("offline"))
        manager.refresh(force = true)
        manager.awaitIdle()

        val state = manager.state.value as SessionState.RetryableFailure
        assertEquals(currentUser, state.user)
        assertEquals(currentUser, state.userOrNull)
        scope.cancel()
    }

    @Test
    fun concurrentRefreshesShareOneInFlightRequest() = runBlocking {
        val response = CompletableDeferred<Result<UserDto>>()
        val attempts = AtomicInteger(0)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = SessionManager(scope, retryDelaysMs = emptyList()) {
            attempts.incrementAndGet()
            response.await()
        }

        manager.refresh()
        manager.refresh()
        manager.refresh(force = true)
        assertEquals(1, attempts.get())
        response.complete(Result.success(UserDto(id = "user")))
        manager.awaitIdle()

        assertEquals(1, attempts.get())
        scope.cancel()
    }

    @Test
    fun authenticationFailureRequiresLoginWithoutAutomaticRetry() = runBlocking {
        val attempts = AtomicInteger(0)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = SessionManager(scope, retryDelaysMs = listOf(1L)) {
            attempts.incrementAndGet()
            Result.failure(httpFailure(401))
        }

        manager.refresh()
        manager.awaitIdle()

        assertTrue(manager.state.value is SessionState.AuthenticationRequired)
        assertEquals(1, attempts.get())
        scope.cancel()
    }

    @Test
    fun manualRetryRunsImmediatelyWhileAutomaticRetryIsWaiting() = runBlocking {
        val user = UserDto(id = "user", email = "reader@example.com")
        var result: Result<UserDto> = Result.failure(IOException("offline"))
        val attempts = AtomicInteger(0)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val manager = SessionManager(scope, retryDelaysMs = listOf(60_000L)) {
            attempts.incrementAndGet()
            result
        }
        manager.refresh()
        manager.awaitIdle()
        assertTrue(manager.state.value is SessionState.RetryableFailure)

        result = Result.success(user)
        manager.refresh(force = true)
        manager.awaitIdle()

        assertEquals(2, attempts.get())
        assertEquals(user, manager.state.value.userOrNull)
        scope.cancel()
    }

    private fun httpFailure(status: Int): HttpException =
        HttpException(Response.error<Unit>(status, "".toResponseBody()))
}
