package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.remote.KomgaApi
import fail.tiger.komgarot.data.remote.dto.*

class AdminRepository(private val api: KomgaApi) {
    suspend fun getLibraries(): Result<List<LibraryDto>> = runCatching { api.getLibraries() }
    suspend fun createLibrary(body: LibraryCreationDto): Result<LibraryDto> = runCatching { api.createLibrary(body) }
    suspend fun updateLibrary(id: String, body: LibraryUpdateDto): Result<LibraryDto> = runCatching { api.updateLibrary(id, body) }
    suspend fun deleteLibrary(id: String): Result<Unit> = runCatching { api.deleteLibrary(id) }
    suspend fun scanLibrary(id: String, path: String? = null): Result<Unit> =
        runCatching { api.scanLibrary(id, ScanRequestDto(path.orEmpty())) }
    suspend fun analyzeLibrary(id: String): Result<Unit> = runCatching { api.analyzeLibrary(id) }
    suspend fun refreshLibraryMetadata(id: String): Result<Unit> = runCatching { api.refreshLibraryMetadata(id) }
    suspend fun emptyLibraryTrash(id: String): Result<Unit> = runCatching { api.emptyLibraryTrash(id) }

    suspend fun getSettings(): Result<SettingsDto> = runCatching { api.getServerSettings() }
    suspend fun updateSettings(body: SettingsUpdateDto): Result<SettingsDto> = runCatching { api.updateServerSettings(body) }

    suspend fun getUsers(): Result<List<UserDto>> = runCatching { api.getUsers() }
    suspend fun getApiKeys(): Result<List<ApiKeyDto>> = runCatching { api.getApiKeysForCurrentUser() }
    suspend fun createApiKey(comment: String): Result<ApiKeyDto> = runCatching { api.createApiKeyForCurrentUser(ApiKeyRequestDto(comment)) }
    suspend fun deleteApiKey(id: String): Result<Unit> = runCatching { api.deleteApiKey(id) }
    suspend fun createUser(body: UserCreationDto): Result<UserDto> = runCatching { api.createUser(body) }
    suspend fun updateUser(id: String, body: UserUpdateDto): Result<UserDto> = runCatching { api.updateUser(id, body) }
    suspend fun deleteUser(id: String): Result<Unit> = runCatching { api.deleteUser(id) }
    suspend fun updateUserPassword(id: String, password: String): Result<Unit> =
        runCatching { api.updateUserPassword(id, PasswordUpdateDto(password)) }

    suspend fun getAuthenticationActivity(): Result<PagedDto<AuthenticationActivityDto>> =
        runCatching { api.getAuthenticationActivity() }
    suspend fun getHistory(): Result<PagedDto<HistoricalEventDto>> = runCatching { api.getHistory() }
    suspend fun getDuplicateBooks(): Result<PagedDto<BookDto>> = runCatching { api.getDuplicateBooks() }
    suspend fun getKnownPageHashes(): Result<PagedDto<PageHashKnownDto>> = runCatching { api.getKnownPageHashes() }
    suspend fun getUnknownPageHashes(): Result<PagedDto<PageHashUnknownDto>> = runCatching { api.getUnknownPageHashes() }
    suspend fun markPageHashKnown(hash: String, action: String): Result<Unit> =
        runCatching { api.markPageHashKnown(PageHashCreationDto(hash = hash, action = action)) }
    suspend fun deleteAllDuplicatePages(hash: String): Result<Unit> = runCatching { api.deleteAllDuplicatePages(hash) }
    suspend fun clearTaskQueue(): Result<Int> = runCatching { api.clearTaskQueue() }
    suspend fun getAnnouncements(): Result<List<AnnouncementDto>> = runCatching { api.getAnnouncements() }
    suspend fun markAnnouncementsRead(): Result<Unit> = runCatching { api.markAnnouncementsRead() }
    suspend fun getClaimStatus(): Result<ClaimStatusDto> = runCatching { api.getClaimStatus() }
    suspend fun getOAuthProviders(): Result<List<OAuth2ClientDto>> = runCatching { api.getOAuthProviders() }
    suspend fun getReleases(): Result<List<ReleaseDto>> = runCatching { api.getReleases() }
}
