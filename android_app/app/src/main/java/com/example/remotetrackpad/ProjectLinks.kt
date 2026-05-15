package com.example.remotetrackpad

object ProjectLinks {
    val repo: String get() = R.string.github_repo.res()
    val pcAgentFolder: String get() = R.string.github_pc_agent.res()
    val startServerBat: String get() = R.string.github_start_server_bat.res()
    val installAutostartBat: String get() = R.string.github_install_autostart_bat.res()
    val releases: String get() = R.string.github_releases.res()

    private fun Int.res(): String =
        AppContext.get().getString(this)
}

/** Application context for string resources (set from MainActivity). */
object AppContext {
    private var ctx: android.content.Context? = null

    fun init(context: android.content.Context) {
        ctx = context.applicationContext
    }

    fun get(): android.content.Context =
        ctx ?: error("AppContext not initialized")
}
