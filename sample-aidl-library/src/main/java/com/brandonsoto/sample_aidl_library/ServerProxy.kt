package com.brandonsoto.sample_aidl_library

import android.content.ComponentName
import android.content.Context
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
import java.lang.RuntimeException

// TODO: incorporate Dependency Injection

//class ServerProxy private constructor(
class ServerProxy private constructor(
    private val context: Context,
//    private val dispatcher: CoroutineDispatcher,
//    private val scope: CoroutineScope,
    private val connectionListener: ServerConnectionListener
) {


    companion object {
        private val TAG = ServerProxy::class.java.simpleName
        private val BINDER_POLLING_INTERVAL_MS = 50L
        private val BINDER_POLLING_MAX_RETRY = 100
        private val BIND_RETRY_MAX = 3 // CAR_SERVICE_BIND_MAX_RETRY
        private val BIND_RETRY_INTERVAL_MS = 500L //   CAR_SERVICE_BIND_RETRY_INTERVAL_MS

        interface ServerConnectionListener {
            fun onServerConnected()
            fun onServerDisconnected()
        }

        fun create(
            context: Context,
//            dispatcher: CoroutineDispatcher,
//            scope: CoroutineScope,
            listener: ServerConnectionListener
        ): ServerProxy? {
//            var started = false

            for (i in 0..BINDER_POLLING_MAX_RETRY) {
//                val proxy = ServerProxy(context, dispatcher, scope, listener)
                val proxy = ServerProxy(context, listener)
                val isBound = proxy.setupNew()

                if (isBound) {
                    return proxy
                }

                try {
                    Thread.sleep(BINDER_POLLING_INTERVAL_MS)
                } catch (e: InterruptedException) {
                    Log.e(TAG, "create: interrupted while binding to service")
                    return null
                }

//                if (proxy == null) {
//                    proxy = ServerProxy(context, dispatcher, scope, listener)
//                }

//                if (service != null) {
//                    if (!started) {
//                        proxy.setupNew()
//                        return proxy
//                    }
//                    break
//                }

//                if (!started) {
//                    proxy.setupNew()
//                    started = true
//                }
            }

            Log.e(TAG, "cannot bind to service! Waited for service " +
                    "interval=$BINDER_POLLING_INTERVAL_MS(ms)," +
                    "retries=$BINDER_POLLING_MAX_RETRY"
            )

            return null

        }
    }

    private val mEventChannel = Channel<ServerEvent>(Channel.RENDEZVOUS)

    private val mLock = Any()

    @GuardedBy("mLock")
    private var mBound = false

    @GuardedBy("mLock")
    private val mRetryJobs = mutableListOf<Job>()

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
                val serverService = IServer.Stub.asInterface(service)

                if (serverService == null) {
                    Log.wtf(TAG, "onServiceConnected: binder service is null")
                    return
                } else if (serverService.asBinder() == mServerService?.asBinder()) {
                    Log.v(TAG, "onServiceConnected: binder already connected")
                    return
                }

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
                    Log.wtf(TAG, "onServiceDisconnected: binder already disconnected")
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
     * Initializes this proxy's connection to the server.
     *
     * @return true if the service was successfully bound to; otherwise false
     */
    @RequiresPermission(SERVER_PERMISSION)
    fun setup(): Boolean {
        synchronized(mLock) {
            return if (mServerService == null) {
                val intent = Intent().apply {
                    component = ComponentName(SERVER_PACKAGE, SERVER_CLASS_NAME)
                }
                val result = context.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)
                Log.d(TAG, "setup: bindService result: $result")
                result
            } else {
                Log.d(TAG, "setup: service is already bound")
                true
            }
        }
    }

    // TODO: clean up
    fun setupNew(): Boolean {
        val intent = Intent().apply {
//            component = ComponentName(SERVER_PACKAGE, SERVER_CLASS_NAME)
            component = ComponentName(SERVER_PACKAGE, SERVER_CLASS_NAME)
        }

        synchronized(mLock) {
            if (mBound) {
                return true
            }

            val isBound = context.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE)
            if (!isBound) {
                return false
//                if (mBindRetryCount < BIND_RETRY_MAX) {
//                    mBindRetryCount++
//                    Log.w(TAG, "setupNew: failed to bind! Retrying... (attempt=$mBindRetryCount)")
//                    val job = scope.launch { withContext(dispatcher) {
//                        delay(BIND_RETRY_INTERVAL_MS)
//                        setupNew()
//                    }}
//                    job.invokeOnCompletion {
//                        synchronized(mLock) {
//                            mRetryJobs.remove(job)
//                        }
//                    }
//                    mRetryJobs.add(job)
//                } else {
//                    Log.w(TAG, "setupNew: failed to bind! Exhausted all retries. Try again later.")
//                    scope.launch {
//                        withContext(Dispatchers.Main) {
//                            synchronized(mLock) {
//                                connectionListener.onServerDisconnected()
//                            }
//                        }
//                    }
//                }
            } else {
                Log.d(TAG, "setupNew: successfully bound to service")
//                mRetryJobs.apply {
//                    forEach { it.cancel() }
//                    clear()
//                }
                mBindRetryCount = 0
                mBound = true
                return true
            }
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
        }
    }

    fun isConnected(): Boolean {
        return synchronized(mLock) {
            mServerService != null
        }
    }

    private inner class ServerStatusListenerImpl: IServerStatusListener.Stub() {
        override fun onSuccess(data: ServerData?) {
            Log.d(TAG, "onSuccess: data=$data")
            data?.let { mEventChannel.sendAndLogResult(ServerEvent.EventA(it)) }
        }

        override fun onFailure(data: ServerData?, errorCode: Int) {
            val error = errorCode.asEnumOrDefault(ServerError.UNKNOWN)
            Log.d(TAG, "onFailure: data=$data, errorCode=$errorCode, error=$error")
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