package com.wearytmusic

import android.app.Application
import com.wearytmusic.data.DownloaderImpl
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

class YtmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NewPipe.init(DownloaderImpl(), Localization.DEFAULT, ContentCountry.DEFAULT)
    }
}
