package com.stardust.auojs.inrt;

import android.util.Log;

import com.fanjun.keeplive.config.KeepLiveService;

/**
 * 保活回调（已清理）：原 AutoXKeepLiveService 会在保活触发时连接远程服务器
 * 并上报 IMEI / 用户码，现已移除全部网络行为，onWorking 不做任何操作。
 */
public class CleanKeepLiveService implements KeepLiveService {
    public static String TAG = "inrt.CleanKeepLiveService";

    @Override
    public void onWorking() {
        Log.d(TAG, "onWorking");
    }

    @Override
    public void onStop() {
        Log.d(TAG, "onStop");
    }
}
