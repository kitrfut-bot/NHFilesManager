package com.nhdeveloper.nhfiles.vpo

import android.app.Application
import com.nhdeveloper.nhfiles.vpo.util.ThemePrefs

class VpoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemePrefs.apply(ThemePrefs.get(this))
    }
}
