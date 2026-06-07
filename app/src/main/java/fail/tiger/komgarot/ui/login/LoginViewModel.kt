package fail.tiger.komgarot.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.repository.AuthRepository
import fail.tiger.komgarot.ui.i18n.UiTextProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(
    private val repo: AuthRepository,
    private val textProvider: UiTextProvider
) : ViewModel() {
    private val _state = MutableStateFlow<LoginState>(LoginState.Idle)
    val state = _state.asStateFlow()

    fun login(url: String, username: String, password: String) {
        viewModelScope.launch {
            _state.value = LoginState.Loading
            repo.login(url, username, password)
                .onSuccess { _state.value = LoginState.Success }
                .onFailure { _state.value = LoginState.Error(it.message ?: textProvider.get(R.string.login_failed)) }
        }
    }

    class Factory(
        repo: AuthRepository,
        textProvider: UiTextProvider
    ) : ViewModelProvider.Factory by viewModelFactory({
        initializer { LoginViewModel(repo, textProvider) }
    })
}

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}
