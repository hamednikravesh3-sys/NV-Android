package ir.nv.navigation

import android.app.Application
import org.mapsforge.map.android.graphics.AndroidGraphicFactory

class NvApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidGraphicFactory.createInstance(this)
    }
}
