package com.brandonsoto.sample_aidl_library.common

import com.brandonsoto.sample_aidl_library.ServerData

enum class ServerError {
    NONE,
    UNKNOWN,
    GENERIC
}

/**
 * Represents the types of events received from the Server Service.
 */
sealed class ServerEvent {
    data class Result(
        val data: ServerData,
        val error: ServerError = ServerError.NONE
    ) : ServerEvent()
}
