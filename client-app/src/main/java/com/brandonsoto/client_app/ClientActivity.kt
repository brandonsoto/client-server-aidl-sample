package com.brandonsoto.client_app

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brandonsoto.sample_aidl_library.ServerData
import com.brandonsoto.sample_aidl_library.ServerProxy
import com.brandonsoto.sample_aidl_library.common.ServerEvent
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext

@ExperimentalCoroutinesApi
class ClientActivity : AppCompatActivity() {
    companion object {
        private val TAG = ClientActivity::class.java.simpleName
    }

    private var mServerData = ServerData().apply {
        b = false
        s = ""
        i = 0
    }
    private var mServerProxy: ServerProxy? = null
    private lateinit var mResultTextView: TextView
    private lateinit var mButton: Button
    private var mHandler = HandlerThread("server_event_thread")
    private val eventDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val eventScope = CoroutineScope(lifecycleScope.coroutineContext + eventDispatcher)
//    private val eventScopeB = CoroutineScope(lifecycleScope.newCoroutineContext(eventDispatcher))
//    private lateinit var eventScopeB: CoroutineScope

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)
//        eventScopeB = CoroutineScope(lifecycleScope.coroutineContext.plus(eventDispatcher))

        lifecycleScope.launch {
            Log.i(
                TAG,
                "onCreate: TEST DISPATCHER (unchanged): ${Thread.currentThread()}"
            )
        }

        lifecycleScope.launch(eventDispatcher) {
            Log.i(
                TAG,
                "onCreate: TEST DISPATCHER: (lifecycle.launch(eventDispatcher)) ${Thread.currentThread()}"
            )
        }

        eventScope.launch {
            Log.i(
                TAG,
                "onCreate: TEST DISPATCHER: (eventScope.launch) ${Thread.currentThread()}"
            )
        }
//
//        eventScopeB.launch {
//            Log.i(
//                TAG,
//                "onCreate: TEST DISPATCHER3: ${Thread.currentThread()}"
//            )
//        }

        mHandler.start()

        mResultTextView = findViewById(R.id.result_text)
        mButton = findViewById(R.id.button)
        mButton.setOnClickListener {
            val data = ServerData().apply {
                val count = mServerData.i + 1
                b = !mServerData.b
                s = count.toString()
                i = count
            }
            mServerData = data
            mServerProxy?.doSomething(data)
        }

//        var updateViewJob: Job? = null
        var mJob: Job? = null

        val listener = object : ServerProxy.Companion.ServerEventListener {
            override fun onServerConnected() {
                Log.d(TAG, "onServerConnected: ${Thread.currentThread()}")
                lifecycleScope.launchWhenStarted { mResultTextView.text = "Server Connected" }
            }

            override fun onServerDisconnected() {
                Log.d(TAG, "onServerDisconnected: ${Thread.currentThread()}")
                lifecycleScope.launchWhenStarted { withContext(Dispatchers.Main) {
                        mResultTextView.text = "Server Disconnected"
                } }
            }

            // TODO: should this include proxy as param?
            override fun onServerConnectedAndReady() {
                Log.d(TAG, "onServerConnectedAndReady: ${Thread.currentThread()}")
                lifecycleScope.launchWhenStarted { withContext(Dispatchers.Main) {
                    mResultTextView.text = "Server connected and ready!"
                } }
            }

            override fun onServerEvent(event: ServerEvent) {
                Log.d(TAG, "onServerEvent: $event, ${Thread.currentThread()}")
                lifecycleScope.launchWhenStarted {
                    withContext(Dispatchers.Main) {
                        mResultTextView.text = when (event) {
                            is ServerEvent.Success,
                            is ServerEvent.Failure -> {
                                event.toString()
                            }
                            else -> {
                                "Unknown event"
                            }
                        }
                    }
                }
            }
        }

//        mServerProxy = ServerProxy.create(this, Handler(mHandler.looper), listener)
        mServerProxy = ServerProxy.create(this, eventScope, Handler(mHandler.looper), listener)
    }

    override fun onStart() {
        Log.i(TAG, "onStart")
        super.onStart()
    }

    override fun onResume() {
        Log.d(TAG, "onResume")
        super.onResume()
    }

    override fun onRestart() {
        Log.d(TAG, "onRestart")
        super.onRestart()
    }

    override fun onStop() {
        Log.d(TAG, "onStop")
        super.onStop()
    }

    override fun onPause() {
        Log.d(TAG, "onPause")
        super.onPause()
    }

    override fun onDestroy() {
        mHandler.quitSafely()
        Log.d(TAG, "onDestroy: +")
        Log.d(TAG, "onDestroy: proxy=$mServerProxy")
        mServerProxy?.teardown()
        super.onDestroy()
        Log.d(TAG, "onDestroy: -")
    }
}
