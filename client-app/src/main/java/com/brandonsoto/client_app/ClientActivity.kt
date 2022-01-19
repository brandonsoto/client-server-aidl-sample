package com.brandonsoto.client_app

import android.graphics.Color
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
import com.brandonsoto.sample_aidl_library.common.ServerState
import kotlinx.coroutines.*
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.random.Random
import kotlin.system.measureTimeMillis

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
    private var mCreateJob: Job? = null
    private val mReadyFlag = AtomicBoolean(false)

    private fun createProxy() {
        runBlocking { mCreateJob?.cancelAndJoin() }
        lifecycleScope.launchWhenStarted {  }
        mCreateJob = lifecycleScope.launchWhenStarted {
            withContext(Dispatchers.Default) {
                val proxy = withTimeoutOrNull(30_000) {
                    repeat(25) {
                        val attempt = it + 1
                        Log.d(TAG, "createProxy: attempt: $attempt, ${Thread.currentThread()}")
                        val p = ServerProxy.create( this@ClientActivity )

                        if (p == null) {
                            // wait and try again
                            delay(100)
                        } else {
                            // wait 5 seconds to see if service connects and is ready
                            withTimeoutOrNull(3_000) {
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
                mServerProxy = (proxy as? ServerProxy)?.apply {
                    lifecycleScope.launch {
                        this@apply.state.collect {
                            mResultTextView.text = "Server is $it"

                            when (it) {
                                ServerState.Connected -> mResultTextView.setTextColor(Color.RED)
                                ServerState.Disconnected -> mResultTextView.setTextColor(Color.YELLOW)
                                ServerState.Ready -> mResultTextView.setTextColor(Color.GREEN)
                            }
                        }
                    }

//                    lifecycleScope.launchWhenStarted {
//                        this@apply.events.collect {
//                            mResultTextView.text = "$it"
//
//                            when (it) {
//                                is ServerEvent.Success -> mResultTextView.setTextColor(Color.GREEN)
//                                is ServerEvent.Failure -> mResultTextView.setTextColor(Color.RED)
//                            }
//                        }
//                    }
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

            val i = Random.nextInt(0, 100)
            lifecycleScope.launch(Dispatchers.Default) {
                var result: ServerEvent? = null
                val time = measureTimeMillis {
                    Log.d(TAG, "START: ($i) req=$data")
                    result = mServerProxy?.doSomething(data)
                    Log.d(TAG, "END: ($i) rep=$result")
                }
                handle(result)
                Log.d(TAG, "onCreate: req $i took $time ms")
            }
        }

//        createProxy()
        mServerProxy = ServerProxy.create(this)
        Log.i(TAG, "onCreate: serverProxy=$mServerProxy")
//        lifecycleScope.launch(Dispatchers.Default) {
//            delay(10_000)
//        }
        lifecycleScope.launchWhenStarted {
            mServerProxy?.state?.collect {
                mResultTextView.text = "Server is $it"

                when (it) {
                    ServerState.Connected -> {
                        mResultTextView.setTextColor(Color.RED)
                        lifecycleScope.launch(Dispatchers.Default) { mServerProxy?.doSomething(ServerData()) } // greedily start binder connection; otherwise first reply will be slow
                    }
                    ServerState.Disconnected -> mResultTextView.setTextColor(Color.YELLOW)
                    ServerState.Ready -> mResultTextView.setTextColor(Color.GREEN)
                }
            }
        }
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

    private suspend fun handle(event: ServerEvent?) {
        Log.d(TAG, "handle: $event")
        withContext(Dispatchers.Main) {
            mResultTextView.text = "$event"
            when (event) {
                is ServerEvent.Success -> mResultTextView.setTextColor(Color.GREEN)
                is ServerEvent.Failure -> mResultTextView.setTextColor(Color.RED)
                null -> mResultTextView.setTextColor(Color.RED)
            }
        }
    }
}
