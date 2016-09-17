package com.explosion.zack.facebooklivestreamwithbluetoothaudio;


import android.annotation.SuppressLint;
import android.content.Intent;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

// Add this to the header of your file:
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.FacebookSdk;
import com.facebook.GraphRequest;
import com.facebook.GraphResponse;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;
import com.facebook.login.widget.LoginButton;

import net.butterflytv.rtmp_client.RTMPMuxer;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;


/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class FullscreenActivity extends AppCompatActivity {
    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private static final String LOG_TAG = "FBSDK";
    private View mContentView;
    private View mControlsView;
    private boolean mVisible = true;
    private LoginButton mLoginButton;
    private CallbackManager callbackManager;
    private Camera mCamera;
    private CameraPreview mPreview;
    private MediaRecorder mMediaRecorder;
    private boolean isRecording = false;

    private Integer isConnected = 0;
    private RTMPMuxer mMuxer;

    private String currentVideoID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        // Initialize the SDK before executing any other operations,
        setupFBSDK();
        setContentView(R.layout.activity_fullscreen);

        setupCameraPreview();
        setupStartButton();

        mControlsView = findViewById(R.id.fullscreen_content_controls);
    }

    private void streamToRTMPserver(byte[] data) {
        if( isConnected == 0){
            return;
        }
//        GraphRequest request = GraphRequest.newPostRequest(
//                AccessToken.getCurrentAccessToken(),
//                "/me/live_videos",
//                params,
//                new GraphRequest.Callback() {
//                    @Override
//                    public void onCompleted(GraphResponse response) {
//                        // Insert your code here
//                        Log.d(LOG_TAG, response.toString());
//                        createRTMPClient(response);
////                                startRecording();
//                    }
//                });
//        request.executeAsync();
        mMuxer.writeVideo(data,0, 1, 3);
//        Log.d(LOG_TAG, "streamToRTMPserver");
    }

    private void setupCameraPreview() {

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, new Camera.PreviewCallback() {
            @Override
            public void onPreviewFrame(byte[] data, Camera camera) {
                streamToRTMPserver(data);
            }
        });

        FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
        preview.addView(mPreview);

        mPreview.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
//                toggle();
            }
        });
    }

    private void setupStartButton() {
        findViewById(R.id.dummy_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isConnected == 1){
                    Log.d(LOG_TAG, "already connected to RTMP server");
                    return;
                }

                AccessToken accessToken = AccessToken.getCurrentAccessToken();

                if (accessToken == null){
                    Log.e(LOG_TAG, "not login");
                    return;
                }else{
                    Log.d(LOG_TAG, "access token is " + accessToken.toString());
                }

                JSONObject params = null;
                try {
                    params = new JSONObject("{published:TRUE}");
                } catch (Exception e) {
                    Log.e("fuck", "fucking json object creation failed!");
                    Log.e("fuck", "this error handle sucks");
                }

                GraphRequest request = GraphRequest.newPostRequest(
                        accessToken,
                        "/me/live_videos",
                        params,
                        new GraphRequest.Callback() {
                            @Override
                            public void onCompleted(GraphResponse response) {
                                // Insert your code here
                                Log.d(LOG_TAG, response.toString());
                                createRTMPClient(response);
//                                startRecording();
                            }
                        });
                request.executeAsync();
            }
        });
    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callbackManager.onActivityResult(requestCode, resultCode, data);
    }

    protected void setupFBSDK() {

        FacebookSdk.sdkInitialize(getApplicationContext());
        // AppEventsLogger.activateApp(this);

        callbackManager = CallbackManager.Factory.create();

        LoginManager loginManager = LoginManager.getInstance();
        loginManager.logInWithPublishPermissions(
                this,
                Arrays.asList("publish_actions")
        );


        loginManager.registerCallback(
                callbackManager,
                new FacebookCallback<LoginResult>() {
                    @Override
                    public void onSuccess(LoginResult loginResult) {
                        Log.d(LOG_TAG, "login success" + loginResult.toString());
                    }

                    @Override
                    public void onCancel() {
                        // App code
                    }

                    @Override
                    public void onError(FacebookException exception) {
                        // App code
                        exception.printStackTrace();
                        Log.e(LOG_TAG, "login error");
                    }
                });

    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(LOG_TAG, "on pause");
//        releaseMediaRecorder();       // if you are using MediaRecorder, release it first
//        releaseCamera();              // release the camera immediately on pause event
    }


    protected void createRTMPClient(GraphResponse response) {

        String streamUrl = null;

        try {
            streamUrl = response.getJSONObject().get("stream_url").toString();
            currentVideoID = response.getJSONObject().get("id").toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }

        if(streamUrl == null){
            Log.e(LOG_TAG, "stream url not set");
            return;
        }

        Log.d(LOG_TAG, "streamUrl: " + streamUrl);

        mMuxer = new RTMPMuxer();
        mMuxer.open(streamUrl, 360, 480);

//        Log.d(LOG_TAG, "is connected? " + String.valueOf(isConnected));

        final Timer timer = new Timer();

        timer.schedule( new TimerTask() {
            public void run() {
                isConnected = mMuxer.isConnected();
                Log.d(LOG_TAG, "is connected? " + String.valueOf(isConnected));

                if(isConnected == 1){
                    timer.cancel();
                }

            }
        }, 0, 1000);

    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        // delayedHide(100);
    }

    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }


    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };

    private final Handler mHideHandler = new Handler();
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };

    /**
     * Schedules a call to hide() in [delay] milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }
}
