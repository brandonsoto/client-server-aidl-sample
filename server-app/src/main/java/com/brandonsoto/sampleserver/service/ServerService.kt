package com.brandonsoto.sampleserver.service

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import androidx.lifecycle.*
import com.brandonsoto.sample_aidl_library.IServer
import com.brandonsoto.sample_aidl_library.IServerStatusListener
import com.brandonsoto.sample_aidl_library.ServerData
import com.brandonsoto.sample_aidl_library.common.BinderInterfaceContainer
import com.brandonsoto.sample_aidl_library.common.SERVER_PERMISSION
import com.brandonsoto.sample_aidl_library.common.ServerError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.io.PrintWriter

class ServerService : LifecycleService() {
    companion object {
        private val TAG = ServerService::class.java.simpleName
        private enum class ServerState { Stopped, Running }
    }

    private val mStatusListeners = BinderInterfaceContainer<IServerStatusListener>()
    private var mState: ServerState = ServerState.Stopped
    private val mLock = Any()

    private val mService = object : IServer.Stub() {
        /**
         * TODO
         *
         * @param data
         * @throws SecurityException on permission
         */
        override fun doSomething(data: ServerData?) {
            Log.i(TAG, "doSomething: $data, ${Thread.currentThread()}")
            assertServicePermission()
            val j = this@ServerService.lifecycleScope.launch {
                Log.i(TAG, "doSomething: before withContext")
                withContext(Dispatchers.Main) {
                    Log.i(TAG, "doSomething: coroutine delay started")
                    delay(500)
                    Log.i(TAG, "doSomething: coroutine delay ended")
                    mStatusListeners.interfaces.forEach { listener ->
                        try {
                            if (data?.b == true) {
                                listener.binderInterface.onSuccess(data)
                            } else {
                                listener.binderInterface.onFailure(data, ServerError.GENERIC.ordinal)
                            }
                        } catch (e: RemoteException) {
                            Log.e(TAG, "doSomething: Error calling status listener")
                        }
                    }
                }
            }

            Log.d(TAG, "doSomething: job=$j")
        }

        /**
         * TODO
         *
         * @param listener
         * @throws SecurityException on permission
         * @throws IllegalArgumentException on adding binder
         */
        override fun registerStatusListener(listener: IServerStatusListener?) {
            Log.i(TAG, "registerStatusListener: $listener")
            assertServicePermission()
            listener?.let {
                mStatusListeners.addBinder(it)

                // TODO: service is automatically ready for now; implement a server state machine later
                // NOTE: this state will be separate from service state - created, started, stopped, destroyed
                it.onServerReady()
            }
        }

        /**
         * TODO
         *
         * @param listener
         * @throws SecurityException on permission
         * @throws NoSuchElementException
         */
        override fun unregisterStatusListener(listener: IServerStatusListener?) {
            Log.i(TAG, "unregisterStatusListener: $listener")
            assertServicePermission()
            listener?.let { mStatusListeners.removeBinder(it) }
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
        synchronized(mLock) { mState = ServerState.Running } // TODO: remove once we use state machine
    }

    override fun onDestroy() {
        Log.i(TAG, "onDestroy")
        super.onDestroy()
    }

    override fun dump(fd: FileDescriptor?, writer: PrintWriter?, args: Array<out String>?) {
        writer?.apply {
            println("**ServerService**")
            synchronized(mLock) {
                print("    Registered status listeners:")
                mStatusListeners.interfaces.apply {
                    if (isEmpty()) {
                        println(" None")
                    } else {
                        forEach { println("\n        $it") }
                    }
                }

                println("    Current server state: $mState")
            }
        }
    }

    private fun assertServicePermission() {
        if (checkCallingOrSelfPermission(SERVER_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException("requires $SERVER_PERMISSION")
        }
    }
}