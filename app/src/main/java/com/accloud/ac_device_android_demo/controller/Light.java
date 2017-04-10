package com.accloud.ac_device_android_demo.controller;

import android.hardware.Camera;

import java.util.List;

/**
 * Created by Administrator on 2015/8/18.
 */
public class Light {
    static Camera mCamera = Camera.open();

    public static boolean turnLightOn() {
        try {
            if (mCamera == null) {
                return false;
            }
            Camera.Parameters parameters = mCamera.getParameters();
            if (parameters == null) {
                return false;
            }
            List<String> flashModes = parameters.getSupportedFlashModes();
            // Check if camera flash exists
            if (flashModes == null) // Use the screen as a flashlight (next best thing)
                return false;

            String flashMode = parameters.getFlashMode();
            if (!Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode)) {
                // Turn on the flash
                if (flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    mCamera.setParameters(parameters);
                } else {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean turnLightOff() {
        try {
            Camera.Parameters parameters = mCamera.getParameters();
            List<String> flashModes = parameters.getSupportedFlashModes();
            String flashMode = parameters.getFlashMode();
            // Check if camera flash exists
            if (flashModes == null) {
                return false;
            }
            if (!Camera.Parameters.FLASH_MODE_OFF.equals(flashMode)) {
                // Turn off the flash
                if (flashModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    mCamera.setParameters(parameters);
                } else {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}