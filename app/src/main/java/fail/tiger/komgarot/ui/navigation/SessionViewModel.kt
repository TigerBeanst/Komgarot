package fail.tiger.komgarot.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.data.remote.dto.UserDto
import fail.tiger.komgarot.data.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SessionViewModel(private val repo: UserRepository) : ViewModel() {
    private val _user = MutableStateFlow<UserDto?>(null)
    val user = _user.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun refresh() {
        viewModelScope.launch { refreshCurrentUser() }
    }

    suspend fun refreshCurrentUser() {
        repo.getCurrentUser()
            .onSuccess {
                _user.value = it
                _error.value = null
            }
            .onFailure {
                _error.value = it.message
            }
    }

    fun clear() {
        _user.value = null
        _error.value = null
    }

    class Factory(private val repo: UserRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SessionViewModel(repo) as T
    }
}
