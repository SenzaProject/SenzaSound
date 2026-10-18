package com.senzaproject.senzasound

import android.app.Application
import com.senzaproject.senzasound.data.AppRepo

class SenzaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppRepo.init(this)
    }
}
