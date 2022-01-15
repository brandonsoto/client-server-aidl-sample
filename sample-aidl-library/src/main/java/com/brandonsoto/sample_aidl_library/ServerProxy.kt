package com.brandonsoto.sample_aidl_library

import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.RequiresPermission
import androidx.lifecycle.LifecycleCoroutineScope
import com.brandonsoto.sample_aidl_library.common.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*
import kotlin.RuntimeException
import kotlin.coroutines.CoroutineContext

//class ServerProxy private constructor(
class ServerProxy private constructor(
    private val context: Context,
//    private val dispatcher: CoroutineDispatcher,
//    private val scope: CoroutineScope,
    private val connectionListener: ServerConnectionListener
) {


    companion object {
        private val TAG = ServerProxy::class.java.simpleName
        private const val BINDER_POLLING_INTERVAL_MS = 50L
        private const val BINDER_POLLING_MAX_RETRY = 100
        private val SERVER_SERVICE_INTENT = Intent().apply {
            component = ComponentName(SERVER_PACKAGE, SERVER_CLASS_NAME)
        }

        interface ServerConnectionListener {
            fun onServerConnected()
            fun onServerDisconnected()
        }

        @RequiresPermission(SERVER_PERMISSION)
        fun create(context: Context, listener: ServerConnectionListener): ServerProxy? {
            val proxy = ServerProxy(context, listener)

            for (i in 0..BINDER_POLLING_MAX_RETRY) {
                Log.v(TAG, "create: bind attempt ${i + 1}")

                val isBound = proxy.bindToServer()
                if (isBound) {
                    return proxy
                }

                try {
                    Thread.sleep(BINDER_POLLING_INTERVAL_MS)
                } catch (e: InterruptedException) {
                    Log.e(TAG, "interrupted while binding to service")
                    proxy.teardown()
                    return null
                }
            }

            proxy.teardown()
            Log.e(TAG, "Failed to bind service. Exhausted all retries.")
            return null
        }
    }

    private val mEventChannel = Channel<ServerEvent>(Channel.RENDEZVOUS)

    private val mLock = Any()

    @GuardedBy("mLock")
    private var mBound = false

    @GuardedBy("mLock")
    private var mBindRetryCount: Int = 0;

    @GuardedBy("mLock")
    private var mServerService: IServer? = null

    @GuardedBy("mLock")
    private var mServerStatusListener = ServerStatusListenerImpl()

    @GuardedBy("mLock")
    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "onServiceConnected: name=$name, service=$service")
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
                    registerStatusListener(mServerStatusListener)
                }
            }

            connectionListener.onServerConnected()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected: name=$name")
            synchronized(mLock) {
                if (mServerService == null) {
                    Log.w(TAG, "onServiceDisconnected: binder already disconnected")
                    return
                }

                mBindRetryCount = 0
                mServerService = null
                mBound = false
            }

            connectionListener.onServerDisconnected()
        }

    }

    @RequiresPermission(SERVER_PERMISSION)
    val serverEvents: Flow<ServerEvent> = mEventChannel.consumeAsFlow()

    /**
     * Sends a Server request for [data]. Listen for the result of the connect request
     * via [serverEvents].
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

    /**
     * Tears down this manager's resources.
     *
     * Note: This manager can no longer send or receive events to the Server service once this
     * function is called - a new ServerManger must be initialized.
     */
    @RequiresPermission(SERVER_PERMISSION)
    fun teardown() {
        Log.v(TAG, "teardown")
        synchronized(mLock) {
            try {
                mServerService?.unregisterStatusListener(mServerStatusListener)
            } catch (e: RemoteException) {
                Log.e(TAG, "teardown: failed to unregister listener due to $e")
            }

            try {
                mEventChannel.close()
            } catch (e: ClosedReceiveChannelException) {
                Log.e(TAG, "teardown: failed to close event channel due to $e")
            }

            context.unbindService(mServiceConnection)
            mServerService = null
            mBound = false
        }
    }

    fun isConnected(): Boolean {
        synchronized(mLock) {
            return mServerService != null
        }
    }

    private fun bindToServer(): Boolean {
        synchronized(mLock) {
            if (mBound) {
                return true
            }

            mBound = context.bindService(SERVER_SERVICE_INTENT, mServiceConnection, BIND_AUTO_CREATE)
            Log.v(TAG, "bound to service: $mBound")
            return mBound
        }
    }

    private inner class ServerStatusListenerImpl: IServerStatusListener.Stub() {
        override fun onSuccess(data: ServerData?) {
            Log.i(TAG, "onSuccess: data=$data")
            data?.let { mEventChannel.sendAndLogResult(ServerEvent.EventA(it)) }
        }

        override fun onFailure(data: ServerData?, errorCode: Int) {
            val error = errorCode.asEnumOrDefault(ServerError.UNKNOWN)
            Log.e(TAG, "onFailure: data=$data, errorCode=$errorCode, error=$error")
            data?.let { mEventChannel.sendAndLogResult(ServerEvent.EventA(it, error)) }
        }

        private fun Channel<ServerEvent>.sendAndLogResult(event: ServerEvent) {
            trySend(event)
                .onSuccess { Log.v(TAG, "Successfully sent $event") }
                .onFailure { Log.e(TAG, "Failed to send $event: error=$it") }
                .onClosed { Log.e(TAG, "Failed to send $event as channel is closed") }
        }
    }
}