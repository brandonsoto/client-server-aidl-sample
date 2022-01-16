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
    data class Success(val data: ServerData) : ServerEvent() {
        override fun toString(): String = "Success(${data.asString()})"
    }
    data class Failure(val data: ServerData, val error: ServerError) : ServerEvent() {
        override fun toString(): String = "Failure(${data.asString()})"
    }

//    data class EventA(
//        val data: ServerData,
//        val error: ServerError = ServerError.NONE
//    ) : ServerEvent()

    // more events would go here
}

private fun ServerData.asString(): String {
    return "ServerData(b=$b, s=$s, i=$i)"
}
