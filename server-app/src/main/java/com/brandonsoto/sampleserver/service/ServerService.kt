package com.brandonsoto.sampleserver.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.brandonsoto.sample_aidl_library.IServer
import com.brandonsoto.sample_aidl_library.IServerStatusListener
import com.brandonsoto.sample_aidl_library.ServerData
import com.brandonsoto.sample_aidl_library.common.ServerError

class ServerService : Service() {

    companion object {
        private val TAG = ServerService::class.java.simpleName
    }

    private val mListeners = mutableSetOf<IServerStatusListener>()
    private var mCount = 0

    private val mService = object : IServer.Stub() {
        override fun doSomething(data: ServerData?) {
            Log.i(TAG, "doSomething: $data")
            var count = 0
            var listeners: Set<IServerStatusListener>

            synchronized(this) {
                count = mCount
                mCount++
                listeners = mListeners.toSet()
            }

            for (listener in listeners) {
                if (count % 2 == 0) {
                    listener.onSuccess(data)
                } else {
                    listener.onFailure(data, ServerError.GENERIC.ordinal)
                }
            }
        }

        override fun registerStatusListener(listener: IServerStatusListener?) {
            Log.i(TAG, "registerStatusListener: $listener")
            listener?.let {
                synchronized(this) {
                    mListeners.add(it)
                }
            }
        }

        override fun unregisterStatusListener(listener: IServerStatusListener?) {
            Log.i(TAG, "unregisterStatusListener: $listener")
            listener?.let {
                synchronized(this) {
                    mListeners.remove(listener)

                }
            }
        }

    }

    override fun onBind(intent: Intent): IBinder {
        Log.i(TAG, "onBind: $intent")
        return mService
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "onUnbind: $intent")
        return super.onUnbind(intent)
    }

    override fun onRebind(intent: Intent?) {
        Log.i(TAG, "onRebind: $intent")
        super.onRebind(intent)
    }

    override fun onCreate() {
        Log.i(TAG, "onCreate")
        super.onCreate()
    }
}