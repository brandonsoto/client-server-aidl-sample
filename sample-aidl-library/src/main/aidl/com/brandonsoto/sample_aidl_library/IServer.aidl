package com.brandonsoto.sample_aidl_library;

import com.brandonsoto.sample_aidl_library.ServerData;
import com.brandonsoto.sample_aidl_library.IServerStatusListener;

interface IServer {
    void doSomething(in ServerData data) = 0;
    void registerStatusListener(in IServerStatusListener listener) = 2;
    void unregisterStatusListener(in IServerStatusListener listener) = 3;
}
