package com.example.btmic

import android.content.Context

object LocationState {
    @Volatile var sharing = false
    @Volatile var room = "1234"
    @Volatile var serverHost = ""

    fun load(ctx: Context) {
        val p = ctx.getSharedPreferences("btmic", Context.MODE_PRIVATE)
        room = p.getString("loc_room", "1234") ?: "1234"
        serverHost = p.getString("loc_host", "") ?: ""
    }

    fun save(ctx: Context) {
        ctx.getSharedPreferences("btmic", Context.MODE_PRIVATE).edit()
            .putString("loc_room", room)
            .putString("loc_host", serverHost)
            .apply()
    }
}
