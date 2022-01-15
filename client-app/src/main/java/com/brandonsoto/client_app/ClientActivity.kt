package com.brandonsoto.client_app

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.brandonsoto.sample_aidl_library.ServerData
import com.brandonsoto.sample_aidl_library.ServerProxy
import com.brandonsoto.sample_aidl_library.common.ServerEvent
import kotlinx.coroutines.*

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

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)

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

        var updateViewJob: Job? = null

        val listener = object : ServerProxy.Companion.ServerConnectionListener {
            override fun onServerConnected() {
                runBlocking {
                    updateViewJob?.cancelAndJoin()
                }
                updateViewJob = lifecycleScope.launchWhenResumed {
                    mServerProxy?.serverEvents?.collect {
                        mResultTextView.text = when (it) {
                            is ServerEvent.EventA -> {
                                "EventA(data=${it.data.asString()}, error=${it.error})"
                            }
                            else -> {
                                "Unknown Type"
                            }
                        }
                    }
                }
            }

            override fun onServerDisconnected() {
                mResultTextView.text = "Server disconnected"
            }
        }


        mServerProxy = ServerProxy.create(this, listener)
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
        Log.d(TAG, "onDestroy: +")
        Log.d(TAG, "onDestroy: proxy=$mServerProxy")
        mServerProxy?.teardown()
        super.onDestroy()
        Log.d(TAG, "onDestroy: -")
    }
}

private fun ServerData.asString(): String {
    return "ServerData(b=$b, s=$s, i=$i)"
}
