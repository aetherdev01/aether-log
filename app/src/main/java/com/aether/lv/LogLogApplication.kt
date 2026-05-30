package com.aether.lv

import android.app.Application
import com.aether.lv.data.db.LogLogDatabase

class LogLogApplication : Application() {
    val database: LogLogDatabase by lazy { LogLogDatabase.getInstance(this) }
}
