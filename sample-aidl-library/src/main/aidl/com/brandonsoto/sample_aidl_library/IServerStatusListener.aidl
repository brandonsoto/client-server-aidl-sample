package com.brandonsoto.sample_aidl_library;

import com.brandonsoto.sample_aidl_library.ServerData;
import com.brandonsoto.sample_aidl_library.IServerStatusListener;

/**
 * Listener interface to notify clients of Server events.
 */
oneway interface IServerStatusListener {
    void onSuccess(in ServerData data) = 0;
    void onFailure(in ServerData data, in int errorCode) = 1;
    void onReady() = 2;
}
