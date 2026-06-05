package fail.tiger.komgarot.ui.metadata

import fail.tiger.komgarot.data.remote.dto.UserDto

internal fun canEditKomgaMetadata(user: UserDto?): Boolean =
    user?.isAdmin == true
