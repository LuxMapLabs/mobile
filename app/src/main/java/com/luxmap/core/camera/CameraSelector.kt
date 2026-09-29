package com.luxmap.core.camera

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager

// Picks the primary back-facing camera. cameraIdList's ordering is a convention (id "0" =
// back), not an API guarantee - on multi-camera devices the first id is not reliably the main
// rear camera, so both the readiness check and the recorder must agree on the same selection
// logic or they could silently disagree about which camera is being used.
object CameraSelector {
    fun pickBackCameraId(cameraManager: CameraManager): String? {
        val backFacing =
            cameraManager.cameraIdList.firstOrNull { id ->
                cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) ==
                    CameraCharacteristics.LENS_FACING_BACK
            }
        return backFacing ?: cameraManager.cameraIdList.firstOrNull()
    }
}
