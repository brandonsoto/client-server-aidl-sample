package com.brandonsoto.sample_aidl_library

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import androidx.annotation.RequiresPermission
import com.brandonsoto.sample_aidl_library.common.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.*

class ServerProxy(private val context: Context) {
    private val mEventChannel = Channel<ServerEvent>(Channel.RENDEZVOUS)
    private var mServerService: IServer? = null
    private var mServerStatusListener = ServerStatusListenerImpl(mEventChannel)
    private val mServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "onServiceConnected: name=$name, service=$service")
            synchronized(this) {
                mServerService = IServer.Stub.asInterface(service).apply {
                    registerStatusListener(mServerStatusListener)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "onServiceDisconnected: name=$name")
            synchronized(this) {
                mServerService = null
            }
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
            synchronized(this) {
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
        synchronized(this) {
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

    /**
     * Tears down this manager's resources.
     *
     * Note: This manager can no longer send or receive events to the Server service once this
     * function is called - a new ServerManger must be initialized.
     */
    @RequiresPermission(SERVER_PERMISSION)
    fun teardown() {
        Log.v(TAG, "teardown")
        synchronized(this) {
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

    companion object {
        private val TAG = ServerProxy::class.java.simpleName
    }

}