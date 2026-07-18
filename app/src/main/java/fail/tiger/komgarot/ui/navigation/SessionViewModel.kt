package fail.tiger.komgarot.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fail.tiger.komgarot.data.remote.dto.UserDto
import fail.tiger.komgarot.data.repository.UserRepository
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface SessionState {
    val userOrNull: UserDto?

    data class Initializing(val user: UserDto? = null) : SessionState {
        override val userOrNull: UserDto? = user
    }

    data class Authenticated(val user: UserDto) : SessionState {
        override val userOrNull: UserDto = user
    }

    data class RetryableFailure(
        val user: UserDto?,
        val reason: SessionFailureReason,
        val detail: String
    ) : SessionState {
        override val userOrNull: UserDto? = user
    }

    data object AuthenticationRequired : SessionState {
        override val userOrNull: UserDto? = null
    }
}

enum class SessionFailureReason {
    NETWORK,
    TIMEOUT,
    RATE_LIMIT,
    SERVER,
    UNKNOWN
}

internal class SessionManager(
    private val scope: CoroutineScope,
    private val retryDelaysMs: List<Long> = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L),
    private val fetchCurrentUser: suspend () -> Result<UserDto>
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.Initializing())
    val state: StateFlow<SessionState> = _state.asStateFlow()
    private var requestJob: Job? = null
    private var retryJob: Job? = null

    fun refresh(force: Boolean = false) {
        if (force) retryJob?.cancel()
        if (requestJob?.isActive == true) return
        launchRequest(attempt = 0, publishLoading = true)
    }

    suspend fun awaitIdle() {
        requestJob?.join()
    }

    fun clear() {
        retryJob?.cancel()
        requestJob?.cancel()
        retryJob = null
        requestJob = null
        _state.value = SessionState.Initializing()
    }

    private fun launchRequest(attempt: Int, publishLoading: Boolean) {
        if (requestJob?.isActive == true) return
        if (publishLoading) {
            _state.value = SessionState.Initializing(_state.value.userOrNull)
        }
        requestJob = scope.launch {
            val result = fetchCurrentUser()
            result.onSuccess { user ->
                retryJob?.cancel()
                retryJob = null
                _state.value = SessionState.Authenticated(user)
            }.onFailure { failure ->
                val classification = failure.classifySessionFailure()
                if (classification.authenticationRequired) {
                    retryJob?.cancel()
                    retryJob = null
                    _state.value = SessionState.AuthenticationRequired
                } else {
                    val retainedUser = _state.value.userOrNull
                    _state.value = SessionState.RetryableFailure(
                        user = retainedUser,
                        reason = classification.reason,
                        detail = failure.message.orEmpty()
                    )
                    if (classification.autoRetry && retryDelaysMs.isNotEmpty()) {
                        val delayMs = retryDelaysMs[attempt.coerceAtMost(retryDelaysMs.lastIndex)]
                        retryJob?.cancel()
                        retryJob = scope.launch {
                            delay(delayMs)
                            requestJob?.join()
                            launchRequest(attempt = attempt + 1, publishLoading = false)
                        }
                    }
                }
            }
        }
    }
}

private data class SessionFailureClassification(
    val reason: SessionFailureReason,
    val autoRetry: Boolean,
    val authenticationRequired: Boolean = false
)

private fun Throwable.classifySessionFailure(): SessionFailureClassification = when (this) {
    is HttpException -> when (code()) {
        401, 403 -> SessionFailureClassification(
            reason = SessionFailureReason.UNKNOWN,
            autoRetry = false,
            authenticationRequired = true
        )
        408 -> SessionFailureClassification(SessionFailureReason.TIMEOUT, autoRetry = true)
        429 -> SessionFailureClassification(SessionFailureReason.RATE_LIMIT, autoRetry = true)
        in 500..599 -> SessionFailureClassification(SessionFailureReason.SERVER, autoRetry = true)
        else -> SessionFailureClassification(SessionFailureReason.UNKNOWN, autoRetry = false)
    }
    is IOException -> SessionFailureClassification(SessionFailureReason.NETWORK, autoRetry = true)
    else -> SessionFailureClassification(SessionFailureReason.UNKNOWN, autoRetry = false)
}

class SessionViewModel(private val repo: UserRepository) : ViewModel() {
    private val manager = SessionManager(viewModelScope) { repo.getCurrentUser() }
    val state: StateFlow<SessionState> = manager.state

    fun refresh(force: Boolean = false) {
        manager.refresh(force)
    }

    suspend fun refreshCurrentUser() {
        manager.refresh(force = true)
        manager.awaitIdle()
    }

    fun clear() {
        manager.clear()
    }

    class Factory(repo: UserRepository) : ViewModelProvider.Factory by viewModelFactory({
        initializer { SessionViewModel(repo) }
    })
}
