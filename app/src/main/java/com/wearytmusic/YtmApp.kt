package com.wearytmusic

import android.app.Application
import com.wearytmusic.data.DownloaderImpl
import com.wearytmusic.data.DownloadStore
import com.wearytmusic.data.Prefs
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class YtmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(DownloaderImpl(), Localization.DEFAULT, ContentCountry.DEFAULT)
        Prefs.init(this)
        DownloadStore.init(this)
    }
}
