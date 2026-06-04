package fail.tiger.komgarot.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.R
import fail.tiger.komgarot.data.remote.dto.*
import fail.tiger.komgarot.data.repository.AdminRepository
import fail.tiger.komgarot.ui.i18n.UiTextProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

class AdminViewModel(
    private val repo: AdminRepository,
    private val textProvider: UiTextProvider
) : ViewModel() {
    val libraries = mutableStateListOf<LibraryDto>()
    val users = mutableStateListOf<UserDto>()
    val apiKeys = mutableStateListOf<ApiKeyDto>()
    val authActivity = mutableStateListOf<AuthenticationActivityDto>()
    val history = mutableStateListOf<HistoricalEventDto>()
    val duplicateBooks = mutableStateListOf<BookDto>()
    val knownHashes = mutableStateListOf<PageHashKnownDto>()
    val unknownHashes = mutableStateListOf<PageHashUnknownDto>()
    val announcements = mutableStateListOf<AnnouncementDto>()
    val oauthProviders = mutableStateListOf<OAuth2ClientDto>()
    val releases = mutableStateListOf<ReleaseDto>()

    var settings by mutableStateOf<SettingsDto?>(null)
    var claimStatus by mutableStateOf<ClaimStatusDto?>(null)
    var loading by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var feedback by mutableStateOf<String?>(null)

    fun load() {
        viewModelScope.launch {
            loading = true
            error = null
            val failures = supervisorScope {
                listOf(
                    async { repo.getLibraries().applyResult(textProvider.get(R.string.admin_library_load_failed)) { libraries.replaceAllWith(it) } },
                    async { repo.getSettings().applyResult(textProvider.get(R.string.admin_settings_load_failed)) { settings = it } },
                    async { repo.getUsers().applyResult(textProvider.get(R.string.admin_users_load_failed)) { users.replaceAllWith(it) } },
                    async { repo.getApiKeys().applyResult(textProvider.get(R.string.admin_api_keys_load_failed)) { apiKeys.replaceAllWith(it) } },
                    async { repo.getAuthenticationActivity().applyResult(textProvider.get(R.string.admin_auth_activity_load_failed)) { authActivity.replaceAllWith(it.content) } },
                    async { repo.getHistory().applyResult(textProvider.get(R.string.admin_history_load_failed)) { history.replaceAllWith(it.content) } },
                    async { repo.getDuplicateBooks().applyResult(textProvider.get(R.string.admin_duplicate_books_load_failed)) { duplicateBooks.replaceAllWith(it.content) } },
                    async { repo.getKnownPageHashes().applyResult(textProvider.get(R.string.admin_duplicate_pages_load_failed)) { knownHashes.replaceAllWith(it.content) } },
                    async { repo.getUnknownPageHashes().applyResult(textProvider.get(R.string.admin_unknown_duplicate_pages_load_failed)) { unknownHashes.replaceAllWith(it.content) } },
                    async { repo.getAnnouncements().applyResult(textProvider.get(R.string.admin_announcements_load_failed)) { announcements.replaceAllWith(it) } },
                    async { repo.getClaimStatus().applyResult(textProvider.get(R.string.admin_claim_load_failed)) { claimStatus = it } },
                    async { repo.getOAuthProviders().applyResult(textProvider.get(R.string.admin_oauth_load_failed)) { oauthProviders.replaceAllWith(it) } },
                    async { repo.getReleases().applyResult(textProvider.get(R.string.admin_releases_load_failed)) { releases.replaceAllWith(it) } }
                ).awaitAll().filterNotNull()
            }

            error = failures.firstOrNull()
            loading = false
        }
    }

    fun createLibrary(name: String, root: String, onDone: (Boolean) -> Unit) {
        runAction(textProvider.get(R.string.admin_library_created)) {
            repo.createLibrary(LibraryCreationDto(name = name, root = root))
                .onSuccess { load() }
                .map { Unit }
        }.invokeOnCompletion { onDone(error == null) }
    }

    fun updateLibrary(library: LibraryDto, name: String, root: String, onDone: (Boolean) -> Unit) {
        runAction(textProvider.get(R.string.admin_library_updated)) {
            repo.updateLibrary(library.id, LibraryUpdateDto(name = name, root = root))
                .onSuccess { load() }
                .map { Unit }
        }.invokeOnCompletion { onDone(error == null) }
    }

    fun deleteLibrary(id: String) = runAction(textProvider.get(R.string.admin_library_deleted)) {
        repo.deleteLibrary(id).onSuccess { load() }
    }

    fun scanLibrary(id: String, path: String? = null) = runAction(textProvider.get(R.string.admin_scan_requested)) {
        repo.scanLibrary(id, path)
    }

    fun analyzeLibrary(id: String) = runAction(textProvider.get(R.string.admin_analyze_requested)) {
        repo.analyzeLibrary(id)
    }

    fun refreshLibraryMetadata(id: String) = runAction(textProvider.get(R.string.admin_metadata_refresh_requested)) {
        repo.refreshLibraryMetadata(id)
    }

    fun emptyLibraryTrash(id: String) = runAction(textProvider.get(R.string.admin_empty_trash_requested)) {
        repo.emptyLibraryTrash(id)
    }

    fun updateSettings(body: SettingsUpdateDto) = runAction(textProvider.get(R.string.admin_settings_updated)) {
        repo.updateSettings(body).onSuccess { settings = it }.map { Unit }
    }

    fun createUser(email: String, password: String, admin: Boolean, allLibraries: Boolean, libraryIds: List<String>, onDone: (Boolean) -> Unit) {
        runAction(textProvider.get(R.string.admin_user_created)) {
            repo.createUser(
                UserCreationDto(
                    email = email,
                    password = password,
                    roles = if (admin) listOf("ADMIN", "USER") else listOf("USER"),
                    sharedLibraries = SharedLibrariesUpdateDto(allLibraries, libraryIds)
                )
            ).onSuccess { load() }.map { Unit }
        }.invokeOnCompletion { onDone(error == null) }
    }

    fun updateUser(user: UserDto, admin: Boolean, allLibraries: Boolean, libraryIds: List<String>) = runAction(textProvider.get(R.string.admin_user_updated)) {
        repo.updateUser(
            user.id,
            UserUpdateDto(
                roles = if (admin) listOf("ADMIN", "USER") else listOf("USER"),
                sharedLibraries = SharedLibrariesUpdateDto(allLibraries, libraryIds),
                labelsAllow = user.labelsAllow,
                labelsExclude = user.labelsExclude,
                ageRestriction = user.ageRestriction?.let { AgeRestrictionUpdateDto(it.age, it.restriction) }
            )
        ).onSuccess { load() }.map { Unit }
    }

    fun deleteUser(id: String) = runAction(textProvider.get(R.string.admin_user_deleted)) {
        repo.deleteUser(id).onSuccess { load() }
    }

    fun updateUserPassword(id: String, password: String) = runAction(textProvider.get(R.string.admin_password_updated)) {
        repo.updateUserPassword(id, password)
    }

    fun createApiKey(comment: String) = runAction(textProvider.get(R.string.admin_api_key_created)) {
        repo.createApiKey(comment).onSuccess { load() }.map { Unit }
    }

    fun deleteApiKey(id: String) = runAction(textProvider.get(R.string.admin_api_key_deleted)) {
        repo.deleteApiKey(id).onSuccess { load() }
    }

    fun markAnnouncementsRead() = runAction(textProvider.get(R.string.admin_announcements_read)) {
        repo.markAnnouncementsRead().onSuccess { load() }
    }

    fun markPageHashKnown(hash: String, action: String) = runAction(textProvider.get(R.string.admin_duplicate_page_marked)) {
        repo.markPageHashKnown(hash, action).onSuccess { load() }
    }

    fun deleteAllDuplicatePages(hash: String) = runAction(textProvider.get(R.string.admin_duplicate_page_deleted)) {
        repo.deleteAllDuplicatePages(hash).onSuccess { load() }
    }

    fun clearTaskQueue() = runAction(textProvider.get(R.string.admin_task_queue_cleared)) {
        repo.clearTaskQueue().map { Unit }
    }

    fun clearFeedback() {
        feedback = null
    }

    private fun runAction(successMessage: String, block: suspend () -> Result<Unit>) =
        viewModelScope.launch {
            loading = true
            error = null
            block()
                .onSuccess { feedback = successMessage }
                .onFailure { error = it.message ?: textProvider.get(R.string.operation_failed) }
            loading = false
        }

    class Factory(
        private val repo: AdminRepository,
        private val textProvider: UiTextProvider
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AdminViewModel(repo, textProvider) as T
    }
}

private fun <T> MutableList<T>.replaceAllWith(items: List<T>) {
    clear()
    addAll(items)
}

private inline fun <T> Result<T>.applyResult(defaultMessage: String, onSuccess: (T) -> Unit): String? =
    fold(
        onSuccess = {
            onSuccess(it)
            null
        },
        onFailure = { it.message ?: defaultMessage }
    )
