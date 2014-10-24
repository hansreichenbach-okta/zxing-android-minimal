package com.google.zxing.client.android;

import android.content.Context;
import android.hardware.Camera;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.SurfaceHolder;

import com.google.zxing.client.android.camera.AutoFocusManager;
import com.google.zxing.client.android.camera.CameraManager;
import com.google.zxing.client.android.camera.open.OpenCameraInterface;

import java.io.IOException;

/**
 * Created by hans.reichenbach on 10/23/14.
 */
public class CameraThread extends HandlerThread {
    private static final String TAG = CameraThread.class.getSimpleName();

    /*
     * globals
     */
    private Handler mHandler;
    private Camera camera;
    private final Object cameraMutex;
    private boolean isCameraOpen;
    private boolean pendingCameraOpen;
    private CameraManager manager;
    private Context context;
    private AutoFocusManager autoFocusManager;

    public CameraThread(CameraManager parent, Context context) {
        super("camera_thread");
        start();

        mHandler = new Handler(getLooper());
        isCameraOpen = false;
        pendingCameraOpen = false;
        manager = parent;
        cameraMutex = new Object();
        this.context = context;
    }

    public boolean isCameraOpen() {
        return camera != null && isCameraOpen;
    }

    private void notifyCameraOpened(Camera camera, SurfaceHolder holder) {
        isCameraOpen = true;
        manager.onCameraOpened(camera, holder);
        pendingCameraOpen = false;
    }

    private void notifyCameraClosed() {
        isCameraOpen = false;
        notify();
    }

    public synchronized void openCamera(final int requestedCamera, final SurfaceHolder holder) {
        pendingCameraOpen = true;

        Log.d(TAG, "open camera called");

        mHandler.post(new Runnable() {
            @Override
            public void run() {
                synchronized (cameraMutex) {
                    Log.i(TAG, "opening camera");

                    if (requestedCamera >= 0) {
                        camera = OpenCameraInterface.open(requestedCamera);
                    } else {
                        camera = OpenCameraInterface.open();
                    }
                }

                //TODO this is executing on the camera thread and not the UI thread, need to use a handler or something to get it onto the UI thread
                manager.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        notifyCameraOpened(camera, holder);
                    }
                });
            }
        });
    }

    public synchronized void closeCamera() {
        Log.d(TAG, "close camera called");

        //should be able to just post this if it's pending and it'll execute after open finishes
        if(isCameraOpen() || pendingCameraOpen) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    synchronized (cameraMutex) {
                        Log.i(TAG, "closing camera");

                        camera.stopPreview();
                        camera.setPreviewCallback(null);
                        camera.release();
                        camera = null;
                        isCameraOpen = false;
                    }

                    //TODO this is running on the camera thread not the UI thread, need to fix that
                    manager.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            notifyCameraClosed();
                        }
                    });
                }
            });

            try {
                wait();
            } catch (InterruptedException e) {
                Log.e(TAG, "Error while waiting for camera to close");
                throw new RuntimeException();
            }
        }
    }

    protected synchronized void runOnCameraThread(Runnable run) {
        mHandler.post(run);
    }

    public Camera.Parameters getCameraParameters() {
        synchronized (cameraMutex) {
            if(isCameraOpen()) {
                return camera.getParameters();
            } else {
                return null;
            }
        }
    }

    public void setCameraParameters(Camera.Parameters params) {
        synchronized (cameraMutex) {
            if(isCameraOpen()) {
                camera.setParameters(params);
            }
        }
    }

    public void setOneShotPreviewCallback(Camera.PreviewCallback callback) {
        synchronized (cameraMutex) {
            if(isCameraOpen()) {
                camera.setPreviewCallback(callback);
            }
        }
    }

    public void setDisplayOrientation(int orientation) {
        synchronized (cameraMutex) {
            if(isCameraOpen()) {
                camera.setDisplayOrientation(orientation);
            }
        }
    }

    public void startPreview(SurfaceHolder holder) throws IOException {
        synchronized (cameraMutex) {
            if(isCameraOpen()) {
                camera.setPreviewDisplay(holder);
            }
        }

        startPreview();
    }

    public void startPreview() {
        synchronized (cameraMutex) {
            if(isCameraOpen()) {
                camera.startPreview();
                autoFocusManager = new AutoFocusManager(context, camera);
            }
        }
    }

    public void stopPreview() {
        synchronized (cameraMutex) {
            if(isCameraOpen()) {
                camera.stopPreview();

                if (autoFocusManager != null) {
                    autoFocusManager.stop();
                    autoFocusManager = null;
                }
            }
        }
    }

    public void startAutoFocus() {
        if (autoFocusManager != null) {
            autoFocusManager.start();
        }
    }

    public void stopAutoFocus() {
        if (autoFocusManager != null) {
            autoFocusManager.stop();
        }
    }
}
