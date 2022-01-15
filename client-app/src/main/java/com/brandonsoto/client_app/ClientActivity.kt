package com.brandonsoto.client_app

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.brandonsoto.sample_aidl_library.ServerData
import com.brandonsoto.sample_aidl_library.ServerProxy
import com.brandonsoto.sample_aidl_library.common.ServerEvent

class ClientActivity : AppCompatActivity() {
    companion object {
        private val TAG = ClientActivity::class.java.simpleName
    }

    private var mServerData = ServerData().apply {
        b = false
        s = ""
        i = 0
    }
    private lateinit var mServerProxy: ServerProxy
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
            mServerProxy.doSomething(data)
        }

        mServerProxy = ServerProxy(this)
        mServerProxy.setup()
        lifecycleScope.launchWhenResumed {
            mServerProxy.serverEvents.collect {
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
        super.onStop()
    }

    override fun onPause() {
        Log.d(TAG, "onPause")
        super.onPause()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        mServerProxy.teardown()
        super.onDestroy()
    }
}

private fun ServerData.asString(): String {
    return "ServerData(b=$b, s=$s, i=$i)"
}
