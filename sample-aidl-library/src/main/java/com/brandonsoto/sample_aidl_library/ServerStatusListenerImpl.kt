package com.brandonsoto.sample_aidl_library

import android.util.Log
import com.brandonsoto.sample_aidl_library.common.ServerError
import com.brandonsoto.sample_aidl_library.common.ServerEvent
import com.brandonsoto.sample_aidl_library.common.asEnumOrDefault
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onClosed
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.channels.onSuccess

// TODO: Determine how to handle channel failures.

internal class ServerStatusListenerImpl(
    private val channel: Channel<ServerEvent>
): IServerStatusListener.Stub() {
    override fun onSuccess(data: ServerData?) {
        Log.d(TAG, "onSuccess: data=$data")
        data?.let { channel.sendAndLogResult(ServerEvent.Result(it)) }
    }

    override fun onFailure(data: ServerData?, errorCode: Int) {
        val error = errorCode.asEnumOrDefault(ServerError.UNKNOWN)
        Log.d(TAG, "onFailure: data=$data, errorCode=$errorCode, error=$error")
        data?.let { channel.sendAndLogResult(ServerEvent.Result(it, error)) }
    }

    private fun Channel<ServerEvent>.sendAndLogResult(event: ServerEvent) {
        trySend(event)
            .onSuccess { Log.v(TAG, "Successfully sent $event") }
            .onFailure { Log.e(TAG, "Failed to send $event: error=$it") }
            .onClosed { Log.e(TAG, "Failed to send $event as channel is closed") }
    }

    companion object {
        private val TAG = ServerStatusListenerImpl::class.java.simpleName
    }
}
