package com.brandonsoto.sample_aidl_library;

import com.brandonsoto.sample_aidl_library.ServerData;
import com.brandonsoto.sample_aidl_library.IServerStatusListener;

oneway interface IServer {
    void doSomething(in ServerData data, in IServerStatusListener client) = 0;
    void ready(in IServerStatusListener client) = 1;
}
