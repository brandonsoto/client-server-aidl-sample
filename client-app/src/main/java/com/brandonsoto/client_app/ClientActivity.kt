package com.brandonsoto.client_app

import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.brandonsoto.sample_aidl_library.ServerData
import com.brandonsoto.sample_aidl_library.ServerProxy
import com.brandonsoto.sample_aidl_library.common.ServerEvent
import kotlinx.coroutines.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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
    private val mHandlerThread = HandlerThread("server_event_thread")
    private lateinit var mHandler: Handler
    private val eventDispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val eventScope = CoroutineScope(lifecycleScope.coroutineContext + eventDispatcher)
//    private val eventScopeB = CoroutineScope(lifecycleScope.newCoroutineContext(eventDispatcher))
//    private lateinit var eventScopeB: CoroutineScope
    private var mCreateJob: Job? = null
    private val mReadyFlag = AtomicBoolean(false)

    private fun createProxy() {
        runBlocking { mCreateJob?.cancelAndJoin() }
        mCreateJob = lifecycleScope.launchWhenStarted {
            withContext(Dispatchers.Default) {
                val proxy = withTimeoutOrNull(30_000) {
                    repeat(25) {
                        val attempt = it + 1
                        Log.d(TAG, "createProxy: attempt: $attempt, ${Thread.currentThread()}")
                        val p = ServerProxy.createSuspend(
                            this@ClientActivity,
                            eventScope,
                            mHandler,
                            listener
                        )

                        if (p == null) {
                            // wait and try again
                            delay(100)
                        } else {
                            // wait 5 seconds to see if service connects and is ready
                            withTimeoutOrNull(5_000) {
                                while (!mReadyFlag.get()) {
                                    delay(50)
                                }
                            }

                            // return proxy if it's connected; otherwise try again
                            if (mReadyFlag.get()) {
                                return@withTimeoutOrNull p
                            } else {
                                Log.w(TAG, "createProxy: attempt $attempt timed out")
                                p.teardown()
                            }
                        }
                    }
                }

                Log.d(TAG, "createProxy: after 30 second withTimeoutOrNull. proxy=$proxy")
                mServerProxy = proxy as? ServerProxy
            }
        }
    }

    private val listener = object : ServerProxy.Companion.ServerEventListener {
        override fun onServerConnected() {
            Log.d(TAG, "onServerConnected: ${Thread.currentThread()}")
            lifecycleScope.launchWhenStarted { mResultTextView.text = "Server Connected" }
        }

        override fun onServerDisconnected() {
            mReadyFlag.set(false)
            Log.d(TAG, "onServerDisconnected: ${Thread.currentThread()}")
            lifecycleScope.launchWhenStarted { withContext(Dispatchers.Main) {
                mResultTextView.text = "Server Disconnected"
//                delay(3_000)
                createProxy()
            } }
        }

        // TODO: should this include proxy as param?
        override fun onServerConnectedAndReady() {
            mReadyFlag.set(true)
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

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)

        mHandlerThread.start()
        mHandler = Handler(mHandlerThread.looper) { msg ->
            Log.d(TAG, "handler received $msg, ${Thread.currentThread()}")
            true
        }
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

        createProxy()
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
        mHandlerThread.quitSafely()
        Log.d(TAG, "onDestroy: +")
        Log.d(TAG, "onDestroy: proxy=$mServerProxy")
        mServerProxy?.teardown()
        super.onDestroy()
        Log.d(TAG, "onDestroy: -")
    }
}
