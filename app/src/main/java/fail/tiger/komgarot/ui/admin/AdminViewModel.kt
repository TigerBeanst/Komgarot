package fail.tiger.komgarot.ui.admin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fail.tiger.komgarot.data.remote.dto.*
import fail.tiger.komgarot.data.repository.AdminRepository
import kotlinx.coroutines.launch

class AdminViewModel(private val repo: AdminRepository) : ViewModel() {
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
            val failures = mutableListOf<String>()

            repo.getLibraries().onSuccess { libraries.replaceAllWith(it) }.onFailure { failures += it.message ?: "书库加载失败" }
            repo.getSettings().onSuccess { settings = it }.onFailure { failures += it.message ?: "服务器设置加载失败" }
            repo.getUsers().onSuccess { users.replaceAllWith(it) }.onFailure { failures += it.message ?: "用户加载失败" }
            repo.getApiKeys().onSuccess { apiKeys.replaceAllWith(it) }.onFailure { failures += it.message ?: "API Key 加载失败" }
            repo.getAuthenticationActivity().onSuccess { authActivity.replaceAllWith(it.content) }.onFailure { failures += it.message ?: "认证活动加载失败" }
            repo.getHistory().onSuccess { history.replaceAllWith(it.content) }.onFailure { failures += it.message ?: "历史记录加载失败" }
            repo.getDuplicateBooks().onSuccess { duplicateBooks.replaceAllWith(it.content) }.onFailure { failures += it.message ?: "重复书籍加载失败" }
            repo.getKnownPageHashes().onSuccess { knownHashes.replaceAllWith(it.content) }.onFailure { failures += it.message ?: "重复页加载失败" }
            repo.getUnknownPageHashes().onSuccess { unknownHashes.replaceAllWith(it.content) }.onFailure { failures += it.message ?: "未知重复页加载失败" }
            repo.getAnnouncements().onSuccess { announcements.replaceAllWith(it) }.onFailure { failures += it.message ?: "公告加载失败" }
            repo.getClaimStatus().onSuccess { claimStatus = it }.onFailure { failures += it.message ?: "Claim 状态加载失败" }
            repo.getOAuthProviders().onSuccess { oauthProviders.replaceAllWith(it) }.onFailure { failures += it.message ?: "OAuth 提供方加载失败" }
            repo.getReleases().onSuccess { releases.replaceAllWith(it) }.onFailure { failures += it.message ?: "版本信息加载失败" }

            error = failures.firstOrNull()
            loading = false
        }
    }

    fun createLibrary(name: String, root: String, onDone: (Boolean) -> Unit) {
        runAction("已创建书库") {
            repo.createLibrary(LibraryCreationDto(name = name, root = root))
                .onSuccess { load() }
                .map { Unit }
        }.invokeOnCompletion { onDone(error == null) }
    }

    fun updateLibrary(library: LibraryDto, name: String, root: String, onDone: (Boolean) -> Unit) {
        runAction("已更新书库") {
            repo.updateLibrary(library.id, LibraryUpdateDto(name = name, root = root))
                .onSuccess { load() }
                .map { Unit }
        }.invokeOnCompletion { onDone(error == null) }
    }

    fun deleteLibrary(id: String) = runAction("已删除书库") {
        repo.deleteLibrary(id).onSuccess { load() }
    }

    fun scanLibrary(id: String, path: String? = null) = runAction("已请求扫描") {
        repo.scanLibrary(id, path)
    }

    fun analyzeLibrary(id: String) = runAction("已请求分析") {
        repo.analyzeLibrary(id)
    }

    fun refreshLibraryMetadata(id: String) = runAction("已请求刷新元数据") {
        repo.refreshLibraryMetadata(id)
    }

    fun emptyLibraryTrash(id: String) = runAction("已请求清空回收站") {
        repo.emptyLibraryTrash(id)
    }

    fun updateSettings(body: SettingsUpdateDto) = runAction("已更新服务器设置") {
        repo.updateSettings(body).onSuccess { settings = it }.map { Unit }
    }

    fun createUser(email: String, password: String, admin: Boolean, allLibraries: Boolean, libraryIds: List<String>, onDone: (Boolean) -> Unit) {
        runAction("已创建用户") {
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

    fun updateUser(user: UserDto, admin: Boolean, allLibraries: Boolean, libraryIds: List<String>) = runAction("已更新用户") {
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

    fun deleteUser(id: String) = runAction("已删除用户") {
        repo.deleteUser(id).onSuccess { load() }
    }

    fun updateUserPassword(id: String, password: String) = runAction("已更新密码") {
        repo.updateUserPassword(id, password)
    }

    fun createApiKey(comment: String) = runAction("已创建 API Key") {
        repo.createApiKey(comment).onSuccess { load() }.map { Unit }
    }

    fun deleteApiKey(id: String) = runAction("已删除 API Key") {
        repo.deleteApiKey(id).onSuccess { load() }
    }

    fun markAnnouncementsRead() = runAction("已标记公告为已读") {
        repo.markAnnouncementsRead().onSuccess { load() }
    }

    fun markPageHashKnown(hash: String, action: String) = runAction("已标记重复页") {
        repo.markPageHashKnown(hash, action).onSuccess { load() }
    }

    fun deleteAllDuplicatePages(hash: String) = runAction("已删除重复页") {
        repo.deleteAllDuplicatePages(hash).onSuccess { load() }
    }

    fun clearTaskQueue() = runAction("已清空任务队列") {
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
                .onFailure { error = it.message ?: "操作失败" }
            loading = false
        }

    class Factory(private val repo: AdminRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AdminViewModel(repo) as T
    }
}

private fun <T> MutableList<T>.replaceAllWith(items: List<T>) {
    clear()
    addAll(items)
}
