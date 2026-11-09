package com.mirae.chageunchageun.runtime

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class ChageunApp : Application() {

    /** 앱 수명 범위. 화면이 바뀌어도 구독과 판단이 이어집니다. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this, appScope)
    }
}
