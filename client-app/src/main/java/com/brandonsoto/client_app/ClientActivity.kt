package com.brandonsoto.client_app

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.brandonsoto.sample_aidl_library.ServerProxy

class ClientActivity : AppCompatActivity() {
    companion object {
        private val TAG = ClientActivity::class.java.simpleName
    }

    private lateinit var mServerProxy: ServerProxy

    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)

        mServerProxy = ServerProxy(this)
    }

    override fun onResume() {
        Log.d(TAG, "onResume")
        super.onResume()
        mServerProxy.setup()
    }

    override fun onRestart() {
        Log.d(TAG, "onRestart")
        super.onRestart()
    }

    override fun onPause() {
        Log.d(TAG, "onPause")
        mServerProxy.teardown()
        super.onPause()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }
}