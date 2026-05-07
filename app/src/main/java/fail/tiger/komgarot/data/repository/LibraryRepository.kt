package fail.tiger.komgarot.data.repository

import fail.tiger.komgarot.data.remote.KomgaApi
import fail.tiger.komgarot.data.remote.dto.LibraryDto

class LibraryRepository(private val api: KomgaApi) {
    suspend fun getLibraries(): List<LibraryDto> = api.getLibraries()
}
