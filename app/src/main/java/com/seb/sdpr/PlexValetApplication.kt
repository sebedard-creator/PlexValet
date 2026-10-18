package com.seb.sdpr

import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.android.HiltAndroidApp
import android.app.Application
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class PlexValetApplication : Application(), ImageLoaderFactory {
    @Inject lateinit var okHttpClient: OkHttpClient

    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this).okHttpClient(okHttpClient).build()
}
