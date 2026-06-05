package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.remote.KomgaApi
import fail.tiger.komgarot.data.remote.dto.ApiKeyDto
import fail.tiger.komgarot.data.remote.dto.ApiKeyRequestDto
import fail.tiger.komgarot.data.remote.dto.AuthenticationActivityDto
import fail.tiger.komgarot.data.remote.dto.PasswordUpdateDto
import fail.tiger.komgarot.data.remote.dto.PagedDto
import fail.tiger.komgarot.data.remote.dto.UserDto

class UserRepository(private val api: KomgaApi) {
    suspend fun getCurrentUser(): Result<UserDto> = runCatching { api.getCurrentUser() }

    suspend fun getApiKeys(): Result<List<ApiKeyDto>> = runCatching {
        api.getApiKeysForCurrentUserRaw().toFlexibleList("apiKeys", "keys")
    }

    suspend fun createApiKey(comment: String): Result<ApiKeyDto> =
        runCatching { api.createApiKeyForCurrentUser(ApiKeyRequestDto(comment)) }

    suspend fun deleteApiKey(id: String): Result<Unit> = runCatching { api.deleteApiKey(id) }

    suspend fun getMyAuthenticationActivity(page: Int = 0): Result<PagedDto<AuthenticationActivityDto>> =
        runCatching { api.getMyAuthenticationActivityRaw(page = page).toFlexiblePage("authenticationActivity") }

    suspend fun updatePassword(password: String): Result<Unit> =
        runCatching { api.updateCurrentUserPassword(PasswordUpdateDto(password)) }
}
