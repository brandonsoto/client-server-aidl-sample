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

class ServerProxy private constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val handler: Handler, // ServiceEventListener callbacks will be called here
    private val eventListener: ServerEventListener
) {
    companion object {
        private val TAG = ServerProxy::class.java.simpleName
        private val SERVER_SERVICE_INTENT = Intent().apply {
            component = ComponentName(SERVER_PACKAGE, SERVER_CLASS_NAME)
        }

        interface ServerEventListener {
            fun onServerConnected()
            fun onServerDisconnected()
            fun onServerConnectedAndReady()
            fun onServerEvent(event: ServerEvent)
        }


        @RequiresPermission(SERVER_PERMISSION)
        fun create(context: Context, scope: CoroutineScope, handler: Handler, listener: ServerEventListener): ServerProxy? {
            val proxy = ServerProxy(context, scope, handler, listener)
            val isBound = proxy.bindToServer()
            if (isBound) {
                return proxy
            }

            proxy.teardown()
            return null
        }
    }

    private val mEventChannel = Channel<ServerEvent>(Channel.RENDEZVOUS)
//    val events: Flow<ServerEvent> = mEventChannel.receiveAsFlow().shareIn(scope, SharingStarted.Eagerly, 10)
//    private val mEventFlow = MutableSharedFlow<ServerEvent>(25)
    private val mEventFlow = MutableSharedFlow<ServerEvent>()
    val eventFlow: SharedFlow<ServerEvent> = mEventFlow
//    val events: SharedFlow<ServerEvent> = mEventChannel.receiveAsFlow().shareIn(scope, SharingStarted.Eagerly, 10)
//    val events: SharedFlow<ServerEvent> = mEventChannel.receiveAsFlow()
//        .shareIn(scope, SharingStarted.Lazily)

    private val mState = MutableStateFlow<ServerState>(ServerState.Disconnected)
    val state: StateFlow<ServerState> = mState.asStateFlow()

    private val mLock = Any()

    @GuardedBy("mLock")
    private var mBound = false

    @GuardedBy("mLock")
    private var mServerService: IServer? = null

    @GuardedBy("mLock")
    private var mServerStatusListener = ServerStatusListenerImpl()

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
                mServerService = serverService.apply {
//                    handler.post { eventListener.onServerConnected() }
                    mState.value = ServerState.Connected
                    registerStatusListener(mServerStatusListener)
                }
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

//            handler.post { eventListener.onServerDisconnected() }
            mState.value = ServerState.Disconnected
        }

    }

    /**
     * Sends a Server request for [data]. Listen for the result of the connect request
     * via [events].
     *
     * @param data the data to be connected
     * @return true if the request was successfully sent to the Server service; otherwise false
     */
    @RequiresPermission(SERVER_PERMISSION)
    fun doSomething(data: ServerData): Boolean {
        Log.v(TAG, "doSomething: $data")

        try {
            synchronized(mLock) {
                mServerService?.run {
                    doSomething(data)
                    return true
                } ?: Log.w(TAG, "doSomething: Server service not available")
            }
        } catch (e: RemoteException) {
            Log.e(TAG, "doSomething: remote exception occurred - $e")
        }

        return false
    }

    suspend fun doSomethingSuspended(data: ServerData): ServerEvent? {
        Log.v(TAG, "doSomethingSuspended: ${data.i}")
        return withTimeoutOrNull(5_000L) {
            val deferredResult: Deferred<ServerEvent?> = async {
                val replyEvent = eventFlow
                    .onSubscription {
                        launch { synchronized(mLock) { mServerService?.doSomethingSuspended(data) } }
                    }
                    .firstOrNull {
                        when (it) {
                            is ServerEvent.Success -> data.i == it.data.i
                            is ServerEvent.Failure -> data.i == it.data.i
                        }
                    }
                return@async replyEvent
            }
            return@withTimeoutOrNull deferredResult.await()
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
            try {
                mServerService?.unregisterStatusListener(mServerStatusListener)
            } catch (e: RemoteException) {
                Log.e(TAG, "teardown: failed to unregister listener due to $e")
            }

            mEventChannel.close()
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

    private inner class ServerStatusListenerImpl: IServerStatusListener.Stub() {
        override fun onSuccess(data: ServerData?) {
            Log.i(TAG, "onSuccess: data=${data?.asString()}, ${Thread.currentThread()}")
            data?.let {
//                handler.post { eventListener.onServerEvent(ServerEvent.Success(it)) }
//                mEventChannel.trySend(ServerEvent.Success(it))
                scope.launch {
                    withTimeoutOrNull(1_000) {
                        mEventFlow.emit( ServerEvent.Success(it) )
                    }
                }
            }
        }

        override fun onFailure(data: ServerData?, errorCode: Int) {
            val error = errorCode.asEnumOrDefault(ServerError.UNKNOWN)
            Log.e(TAG, "onFailure: data=${data?.asString()}, errorCode=$errorCode, ${Thread.currentThread()}")
            data?.let {
//                handler.post { eventListener.onServerEvent(ServerEvent.Failure(it, error)) }
//                mEventChannel.trySend(ServerEvent.Failure(it, error))
                scope.launch {
                    withTimeoutOrNull(1_000) {
                        mEventFlow.emit( ServerEvent.Failure(it, error) )
                    }
                }
            }
        }

        override fun onServerReady() {
            Log.i(TAG, "onServerReady: ${Thread.currentThread()}")
//            handler.post { eventListener.onServerConnectedAndReady() }
            mState.value = ServerState.Ready
        }
    }
}

private fun ServerData.asString(): String {
    return "ServerData(b=$b, s=$s, i=$i)"
}
