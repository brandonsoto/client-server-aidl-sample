package com.brandonsoto.sample_aidl_library;

import com.brandonsoto.sample_aidl_library.ServerData;
import com.brandonsoto.sample_aidl_library.IServerStatusListener;

oneway interface IServer {
    void doSomething(in ServerData data) = 0;
    void doSomethingSuspended(in ServerData data) = 1;
    void registerStatusListener(in IServerStatusListener listener) = 2;
    void unregisterStatusListener(in IServerStatusListener listener) = 3;
}
