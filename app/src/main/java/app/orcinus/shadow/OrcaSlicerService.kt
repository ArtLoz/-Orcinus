package app.orcinus.shadow

import app.orcinus.shadow.slicing.nativebridge.NativeSlicerEngine
import app.orcinus.shadow.slicing.service.SlicerService

/** Runs OrcaSlicer in the :slicer process declared in AndroidManifest.xml. */
class OrcaSlicerService : SlicerService<NativeSlicerEngine>() {
    override fun createEngine() = NativeSlicerEngine(this)
}
