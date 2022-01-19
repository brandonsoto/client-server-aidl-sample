package com.brandonsoto.sample_aidl_library

import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.RemoteException
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresPermission
import com.brandonsoto.sample_aidl_library.common.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

@ExperimentalCoroutinesApi
class ServerProxy private constructor(private val context: Context) {
    companion object {
        private val TAG = ServerProxy::class.java.simpleName
        private val SERVER_SERVICE_INTENT = Intent().apply {
            component = ComponentName(SERVER_PACKAGE, SERVER_CLASS_NAME)
        }

        @RequiresPermission(SERVER_PERMISSION)
        fun create(context: Context): ServerProxy? {
            val proxy = ServerProxy(context)
            val isBound = proxy.bindToServer()
            if (isBound) {
                return proxy
            }

            proxy.teardown()
            return null
        }
    }

    private val mState = MutableStateFlow<ServerState>(ServerState.Disconnected)
    val state: StateFlow<ServerState> = mState.asStateFlow()

    private val mLock = Any()

    @GuardedBy("mLock")
    private var mBound = false

    @GuardedBy("mLock")
    private var mServerService: IServer? = null

    @GuardedBy("mLock")
    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "onServiceConnected: name=$name, service=$service, ${Thread.currentThread()}")
            synchronized(mLock) {
                if (mServerService != null) {
                    Log.w(TAG, "onServiceConnected: binder already connected")
                    return
                }

                val serverService = IServer.Stub.asInterface(service)
                if (serverService == null) {
                    Log.e(TAG, "onServiceConnected: binder service is null")
                    return
                }

                mBound = true
                mServerService = serverService
                mState.value = ServerState.Connected
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected: name=$name, ${Thread.currentThread()}")
            synchronized(mLock) {
                if (mServerService == null) {
                    Log.w(TAG, "onServiceDisconnected: binder already disconnected")
                    return
                }

                mServerService = null
                mBound = false
            }

            mState.value = ServerState.Disconnected
        }

    }

    suspend fun doSomething(data: ServerData): ServerEvent? {
        Log.v(TAG, "doSomethingSuspended: ${data.i}")
        return withTimeoutOrNull(5_000L) {
            requestAndReceive(data, false)
        }
    }

    suspend fun ready(): ServerEvent? {
        Log.v(TAG, "ready:")
        return withTimeoutOrNull(5_000) {
            requestAndReceive(ServerData(), true)
        }
    }

    /**
     * Tears down this manager's resources.
     *
     * Note: This manager can no longer send or receive events to the Server service once this
     * function is called - a new ServerManger must be initialized.
     */
    @RequiresPermission(SERVER_PERMISSION)
    fun teardown() {
        Log.v(TAG, "teardown: ${Thread.currentThread()}")
        synchronized(mLock) {
            context.unbindService(mServiceConnection)
            mServerService = null
            mBound = false
        }
    }

    private fun bindToServer(): Boolean {
        synchronized(mLock) {
            if (mBound) {
                Log.v(TAG, "bindToService: already bound to service")
                return true
            }

            mBound = context.bindService(SERVER_SERVICE_INTENT, mServiceConnection, BIND_AUTO_CREATE)
            Log.v(TAG, "bindToService: bound = $mBound")
            return mBound
        }
    }

    private suspend fun requestAndReceive(data: ServerData, ready: Boolean): ServerEvent = suspendCancellableCoroutine { continuation ->
        val callback = object : IServerStatusListener.Stub() {
            override fun onSuccess(data: ServerData?) {
                Log.i(TAG, "onSuccess: data=${data?.asString()}, ${Thread.currentThread()}")
                data?.let { continuation.resume(ServerEvent.Success(it), null) }
            }

            override fun onFailure(data: ServerData?, errorCode: Int) {
                val error = errorCode.asEnumOrDefault(ServerError.UNKNOWN)
                Log.e(TAG, "onFailure: data=${data?.asString()}, errorCode=$errorCode, ${Thread.currentThread()}")
                data?.let { continuation.resume(ServerEvent.Failure(it, error), null) }
            }

            override fun onReady() {
                Log.i(TAG, "onReady: !!!")
                continuation.resume(ServerEvent.Success(ServerData()), null)
            }
        }

        if (ready) mServerService?.ready(callback)
        else mServerService?.doSomething(data, callback)
    }
}

private fun ServerData.asString(): String {
    return "ServerData(b=$b, s=$s, i=$i)"
}
