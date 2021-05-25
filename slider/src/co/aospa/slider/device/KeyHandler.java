/*
 * Copyright (C) 2018 The LineageOS Project
 * Copyright (C) 2021 Paranoid Android
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package co.aospa.slider.device;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraManager;
import android.media.MediaPlayer;
import android.os.PowerManager;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.KeyEvent;

import com.android.internal.os.DeviceKeyHandler;

import java.util.Timer;
import java.util.TimerTask;

import static android.view.KeyEvent.ACTION_DOWN;

public class KeyHandler extends CameraManager.AvailabilityCallback
        implements DeviceKeyHandler {
    private static final int KEYCODE_SLIDER_UP = 594;
    private static final int KEYCODE_SLIDER_DOWN = 595;

    private final Context mContext;
    private final Vibrator mVibrator;
    private final CameraManager mCameraManager;
    private final PowerManager mPowerManager;

    private boolean mIsCameraAppOpen = false;
    private boolean mIsDefaultCameraAppOpen = false;
    private Timer mCameraInUseTimer;
    private String sound_name;
    private String sound_state;

    public KeyHandler(Context context) {
        mContext = context;

        mVibrator = mContext.getSystemService(Vibrator.class);
        mCameraManager = mContext.getSystemService(CameraManager.class);
        mPowerManager = mContext.getSystemService(PowerManager.class);

        if (mCameraManager != null) {
            mCameraManager.registerAvailabilityCallback(this, null /* handler */);
        }
    }

    @Override
    public void onCameraAvailable(String cameraId) {
        super.onCameraAvailable(cameraId);

        mCameraInUseTimer = new Timer();
        mCameraInUseTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mIsCameraAppOpen = false;
                mIsDefaultCameraAppOpen = false;
                mCameraInUseTimer.cancel();
            }
        }, 1000);
    }

    @Override
    public void onCameraUnavailable(String cameraId) {
        super.onCameraUnavailable(cameraId);
        mIsCameraAppOpen = true;

        try {
            mCameraInUseTimer.cancel();
        } catch (Exception e) {}
    }

    public KeyEvent handleKeyEvent(KeyEvent event) {
        int slider = SystemProperties.getInt("persist.slider.disable", 0);

        if (slider == 1) {
            return event;
        }

        if (!mPowerManager.isInteractive()) {
            return event;
        }

        int action = event.getAction();
        if (action != ACTION_DOWN) {
            return event;
        }

        int scanCode = event.getScanCode();
        switch (scanCode) {
            case KEYCODE_SLIDER_UP:
                sound_state = "close";
                playSound();
                handleSliderUp();
                break;
            case KEYCODE_SLIDER_DOWN:
                sound_state = "open";
                playSound();
                handleSliderDown();
                break;
            default:
                return event;
        }

        doHapticFeedback();

        return null;
    }

    boolean isUserSetupComplete() {
        return Settings.Secure.getIntForUser(mContext.getContentResolver(),
                Settings.Secure.USER_SETUP_COMPLETE, 0, UserHandle.USER_CURRENT) != 0;
    }

    private void startActivityAsUser(Intent intent, UserHandle handle) {
        if (isUserSetupComplete()) {
            mContext.startActivityAsUser(intent, handle);
        }
    }

    private void openDefaultCameraApp(boolean frontCamera) {
        KeyguardManager keyguardManager = mContext.getSystemService(KeyguardManager.class);
        if (keyguardManager == null) {
            return;
        }

        Intent intent;

        if (keyguardManager.isDeviceLocked()) {
            intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA_SECURE);
        } else {
            intent = new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);
        }

        intent.putExtra("android.intent.extra.USE_FRONT_CAMERA", frontCamera);

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        startActivityAsUser(intent, UserHandle.CURRENT_OR_SELF);

        mIsDefaultCameraAppOpen = true;
    }

    private void doHapticFeedback() {
        if (mVibrator == null || !mVibrator.hasVibrator()) {
            return;
        }

        mVibrator.vibrate(40);
    }

    private void handleSliderDown() {
        if (mIsCameraAppOpen && mIsDefaultCameraAppOpen) {
            return;
        }

        openDefaultCameraApp(true /* frontCamera */);
    }

    private void handleSliderUp() {
        if (!mIsDefaultCameraAppOpen) {
            return;
        }

        Instrumentation m_Instrumentation = new Instrumentation();
        m_Instrumentation.sendKeyDownUpSync( KeyEvent.KEYCODE_BACK );
    }

    private void playSound(){
       sound_name = SystemProperties.get("persist.slider.sound");
       if (sound_name == "none") {
          return;
       }

       MediaPlayer mp = new MediaPlayer();
       try {
           mp.setDataSource("file:///product/media/audio/ui/slide_"+sound_name+"_"+sound_state+".ogg");
           mp.prepare();
           mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

             @Override
             public void onCompletion(MediaPlayer mp)
             {
              mp.stop();
              mp.reset();
              mp.release();
              mp=null;
             }
           });
           mp.start();
       } catch (Exception e) {
           e.printStackTrace();
       }
   }

}
