package app.orcinus.shadow

import android.app.Application
import app.orcinus.shadow.di.AppContainer

/**
 * Holds the composition root for the life of the process, so activities are
 * recreated without reconnecting to the engine. The class also starts in the
 * :slicer process, where the container stays unused.
 */
class OrcinusApplication : Application() {
    val container by lazy { AppContainer(this) }
}
