/*******************************************************************************
 * Copyright (c) 2014-2015 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 *
 * The Eclipse Public License is available at
 *   http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *   http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *    Mike Robertson - initial contribution
 *******************************************************************************/
package com.ibm.iot.android.iotstarter.fragments;

import android.app.AlertDialog;
import android.content.*;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.ibm.iot.android.iotstarter.IoTStarterApplication;
import com.ibm.iot.android.iotstarter.R;
import com.ibm.iot.android.iotstarter.iot.IoTClient;
import com.ibm.iot.android.iotstarter.speech.util.JsonParser;
import com.ibm.iot.android.iotstarter.utils.Constants;
import com.ibm.iot.android.iotstarter.utils.MessageConductor;
import com.ibm.iot.android.iotstarter.utils.MessageFactory;
import com.ibm.iot.android.iotstarter.utils.MyIoTActionListener;
import com.ibm.iot.android.iotstarter.views.DrawingView;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.ISpeechDelegate;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.dto.SpeechConfiguration;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.InitListener;
import com.iflytek.cloud.RecognizerListener;
import com.iflytek.cloud.RecognizerResult;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechEvent;
import com.iflytek.cloud.SpeechRecognizer;
import com.iflytek.cloud.SpeechUtility;
import com.iflytek.cloud.SynthesizerListener;
import com.skyfishjy.library.RippleBackground;


import org.eclipse.paho.client.mqttv3.MqttException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Timestamp;
import java.util.Date;

import pl.droidsonroids.gif.GifDrawable;


/**
 * The IoT Fragment is the main fragment of the application that will be displayed while the device is connected
 * to IoT. From this fragment, users can send text event messages. Users can also see the number
 * of messages the device has published and received while connected.
 */
public class IoTPagerFragment extends IoTStarterPagerFragment implements ISpeechDelegate {
    private final static String TAG = IoTPagerFragment.class.getName();
    private DrawingView drawingView;
    private Handler mHandler = null;
    private static boolean first=true;
    private static String mRecognitionResults = "";
    private SpeechRecognizer mIat;
    static private RippleBackground rippleBackground;
    private enum ConnectionState {
        IDLE, CONNECTING, CONNECTED
    }
    static ConnectionState mState = ConnectionState.IDLE;
    static public SynthesizerListener mSynListenermSynListener = new SynthesizerListener(){
        @Override
        public void onSpeakBegin() {
            rippleBackground.startRippleAnimation();
        }

        @Override
        public void onBufferProgress(int i, int i1, int i2, String s) {

        }

        @Override
        public void onSpeakPaused() {

        }

        @Override
        public void onSpeakResumed() {

        }

        @Override
        public void onSpeakProgress(int i, int i1, int i2) {

        }

        @Override
        public void onCompleted(SpeechError speechError) {
            rippleBackground.stopRippleAnimation();
        }

        @Override
        public void onEvent(int i, int i1, int i2, Bundle bundle) {

        }
    };
    private RecognizerListener mRecoListener = new RecognizerListener(){

        public void onResult(RecognizerResult results,boolean isLast) {
            Log.d(TAG, "speech result:"+ results.getResultString());
            printResult(results);
            if (isLast) {
                Log.d(TAG, "錄音結果");
            }
        }
        public void onError(SpeechError error) {
            Log.d(TAG, "error:" + error.getPlainDescription(true));
            mState = ConnectionState.IDLE;
            displayStatus(error.getPlainDescription(true));
        }

        @Override
        public void onVolumeChanged(int i) {

        }

        public void onBeginOfSpeech() {
            Log.d(TAG, "onOpen");
            mState = ConnectionState.CONNECTED;
            displayButton("Stop recording");
        }


        public void onEndOfSpeech() {
            mState = ConnectionState.IDLE;
            displayButton("Audio");
        }
        public void onEvent(int eventType, int arg1, int arg2, Bundle obj){
            if (SpeechEvent.EVENT_SESSION_ID == eventType) {
                		String sid = obj.getString(SpeechEvent.KEY_EVENT_SESSION_ID);
                		Log.d(TAG, "session id =" + sid);
            }
        }
    };

    /**************************************************************************
     * Fragment functions for establishing the fragment
     **************************************************************************/

    public static IoTPagerFragment newInstance() {
        IoTPagerFragment i = new IoTPagerFragment();
        return i;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mHandler = new Handler();
        SpeechUtility.createUtility(this.getActivity(), SpeechConstant.APPID+"=572bfac5");
        return inflater.inflate(R.layout.iot, container, false);

    }

    /**
     * Called when the fragment is resumed.
     */
    @Override
    public void onResume() {
        Log.d(TAG, ".onResume() entered");
        super.onResume();
        rippleBackground=(RippleBackground)getActivity().findViewById(R.id.content);

        app = (IoTStarterApplication) getActivity().getApplication();
        app.setCurrentRunningActivity(TAG);
        if(first==true){
            MessageConductor.getInstance(getActivity()).getmTt().startSpeaking("歡迎來到手機機器人",mSynListenermSynListener);
            first=false;
        }

        if (broadcastReceiver == null) {
            Log.d(TAG, ".onResume() - Registering iotBroadcastReceiver");
            broadcastReceiver = new BroadcastReceiver() {

                @Override
                public void onReceive(Context context, Intent intent) {
                    Log.d(TAG, ".onReceive() - Received intent for iotBroadcastReceiver");
                    processIntent(intent);
                }
            };
        }

        getActivity().getApplicationContext().registerReceiver(broadcastReceiver,
                new IntentFilter(Constants.APP_ID + Constants.INTENT_IOT));

        // initialise
        initializeIoTActivity();
    }

    /**
     * Called when the fragment is destroyed.
     */
    @Override
    public void onDestroy() {
        Log.d(TAG, ".onDestroy() entered");

        try {
            getActivity().getApplicationContext().unregisterReceiver(broadcastReceiver);
        } catch (IllegalArgumentException iae) {
            // Do nothing
        }
        super.onDestroy();
    }

    /**
     * Initializing onscreen elements and shared properties
     */
    private InitListener mTtsInitListener = new InitListener() {
        @Override
        public void onInit(int code) {
            Log.d(TAG, "InitListener init() code = " + code);
            if (code != ErrorCode.SUCCESS) {
               Log.d("stt","初始化失败,错误码：" + code);
            } else {

            }
        }
    };

    private void initializeIoTActivity() {
        Log.d(TAG, ".initializeIoTFragment() entered");

        context = getActivity().getApplicationContext();

        updateViewStrings();

        Log.d("STT", "setup STT");
        mIat=SpeechRecognizer.createRecognizer(context,mTtsInitListener);
        mIat.setParameter(SpeechConstant.DOMAIN,"iat");
        mIat.setParameter(SpeechConstant.LANGUAGE,"zh_cn");
        mIat.setParameter(SpeechConstant.ACCENT, "mandarin");
        mIat.setParameter(SpeechConstant.NET_TIMEOUT, "30000");
        mIat.setParameter(SpeechConstant.ASR_PTT, "0");
        mIat.setParameter(SpeechConstant.TEXT_ENCODING, "utf-8");
        /*
        if (initSTT() == false) {
            Toast.makeText(this.getActivity(), "STT Error: no authentication", Toast.LENGTH_LONG).show();
        }
        */
        // setup button listeners
        Button button = (Button) getActivity().findViewById(R.id.sendText);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleSendText();
            }
        });

        Button buttonhand = (Button) getActivity().findViewById(R.id.sendHandText);
        buttonhand.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleHandSendText();
            }
        });

        drawingView = (DrawingView) getActivity().findViewById(R.id.drawing);
        drawingView.setContext(context);
    }

    /**
     * Update strings in the fragment based on IoTStarterApplication values.
     */
    @Override
    void updateViewStrings() {
        Log.d(TAG, ".updateViewStrings() entered");
        // DeviceId should never be null at this point.
        if (app.getDeviceId() != null) {
            ((TextView) getActivity().findViewById(R.id.deviceIDIoT)).setText(app.getDeviceId());
        } else {
            ((TextView) getActivity().findViewById(R.id.deviceIDIoT)).setText("-");
        }

        // Update publish count view.
        processPublishIntent();

        // Update receive count view.
        processReceiveIntent();

        // TODO: Update badge value?
        //int unreadCount = app.getUnreadCount();
        //((MainActivity) getActivity()).updateBadge(getActivity().getActionBar().getTabAt(2), unreadCount);
    }

    /**************************************************************************
     * Functions to handle button presses
     **************************************************************************/

    /**
     * Handle pressing of the send text button. Prompt the user to enter text
     * to send.
     */

    // initialize the connection to the Watson STT service
    private boolean initSTT() {
        // initialize the connection to the Watson STT service
        String username = getString(R.string.STTdefaultUsername);
        String password = getString(R.string.STTdefaultPassword);
        String tokenFactoryURL = getString(R.string.STTdefaultTokenFactory);
        String serviceURL = "wss://stream.watsonplatform.net/speech-to-text/api";
        SpeechConfiguration sConfig = new SpeechConfiguration(SpeechConfiguration.AUDIO_FORMAT_OGGOPUS); //壓縮音擋
        SpeechToText.sharedInstance().initWithContext(this.getHost(serviceURL), getActivity(), sConfig); //時體化
        // Basic Authentication
        SpeechToText.sharedInstance().setCredentials(username, password);
        SpeechToText.sharedInstance().setModel(getString(R.string.modelDefault)); //預設SST語言
        SpeechToText.sharedInstance().setDelegate(this);
        return true;
    }

    public URI getHost(String url){
        try {
            return new URI(url);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void handleHandSendText() {
        if (app.getConnectionType() != Constants.ConnectionType.QUICKSTART) {
            final EditText input = new EditText(context);
            new AlertDialog.Builder(getActivity())
                    .setTitle(getResources().getString(R.string.send_text_title))
                    .setMessage(getResources().getString(R.string.send_text_text))
                    .setView(input)
                    .setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {

                                public void onClick(DialogInterface dialog, int whichButton) {
                                    Editable value = input.getText();
                                    String messageData = MessageFactory.getTextMessage(value.toString());

                                    //LOG
                                    Date date = new Date();
                                    String logMessage = "我: ";
                                    app.getMessageLog().add(logMessage + value.toString());

                                        Intent LogIntent = new Intent(Constants.APP_ID + Constants.INTENT_LOG);
                                        LogIntent.putExtra(Constants.INTENT_DATA, Constants.TEXT_EVENT);
                                        context.sendBroadcast(LogIntent);

                                    //MQTT
                                    try {
                                        // create ActionListener to handle message published results
                                        MyIoTActionListener listener = new MyIoTActionListener(context, Constants.ActionStateStatus.PUBLISH);
                                        IoTClient iotClient = IoTClient.getInstance(context);
                                        iotClient.publishEvent("text", "json", messageData, 0, false, listener);

                                        int count = app.getPublishCount();
                                        app.setPublishCount(++count);

                                        String runningActivity = app.getCurrentRunningActivity();
                                        if (runningActivity != null && runningActivity.equals(IoTPagerFragment.class.getName())) {
                                            Intent actionIntent = new Intent(Constants.APP_ID + Constants.INTENT_IOT);
                                            actionIntent.putExtra(Constants.INTENT_DATA, Constants.INTENT_DATA_PUBLISHED);
                                            context.sendBroadcast(actionIntent);
                                        }
                                    } catch (MqttException e) {
                                        // Publish failed
                                    }
                                }
                            }

                    ).

                        setNegativeButton(getResources()

                                        .

                                                getString(R.string.cancel),

                                new DialogInterface.OnClickListener()

                                {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        // Do nothing.
                                    }
                                }

                        ).

                        show();
                    }
        }
    private void handleSendText() {
        Log.d(TAG, ".handleSendText() entered");
        if (app.getConnectionType() != Constants.ConnectionType.QUICKSTART) {
            if (mState == ConnectionState.IDLE) {
                mState = ConnectionState.CONNECTING;
                displayButton("CONNECTING Audio");
                Log.d("STT", "onClickRecord: IDLE -> CONNECTING");
                mRecognitionResults = "";
                int ret = mIat.startListening(mRecoListener);
                if (ret != ErrorCode.SUCCESS) {
                    Log.d(TAG, "錄音Error");
                }

            } else if (mState == ConnectionState.CONNECTED) {
                mState = ConnectionState.IDLE;
                Log.d("STT", "onClickRecord: CONNECTED -> IDLE");
                mIat.stopListening();
                displayButton("Audio");

            }



        } else {
            new AlertDialog.Builder(getActivity())
                    .setTitle(getResources().getString(R.string.send_text_title))
                    .setMessage(getResources().getString(R.string.send_text_invalid))
                    .setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                        }
                    }).show();
        }
    }

    /**************************************************************************
     * Functions to process intent broadcasts from other classes
     **************************************************************************/

    /**
     * Process the incoming intent broadcast.
     * @param intent The intent which was received by the fragment.
     */
    private void processIntent(Intent intent) {
        Log.d(TAG, ".processIntent() entered");

        // No matter the intent, update log button based on app.unreadCount.
        updateViewStrings();

        String data = intent.getStringExtra(Constants.INTENT_DATA);
        assert data != null;
        if (data.equals(Constants.INTENT_DATA_PUBLISHED)) {
            processPublishIntent();
        } else if (data.equals(Constants.INTENT_DATA_RECEIVED)) {
            processReceiveIntent();
        } else if (data.equals(Constants.ACCEL_EVENT)) {
            processAccelEvent();
        } else if (data.equals(Constants.COLOR_EVENT)) {
            Log.d(TAG, "Updating background color");
            drawingView.setBackgroundColor(app.getColor());
        } else if (data.equals(Constants.ALERT_EVENT)) {
            String message = intent.getStringExtra(Constants.INTENT_DATA_MESSAGE);
            new AlertDialog.Builder(getActivity())
                    .setTitle(getResources().getString(R.string.alert_dialog_title))
                    .setMessage(message)
                    .setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                        }
                    }).show();
        }
    }

    /**
     * Intent data contained INTENT_DATA_PUBLISH
     * Update the published messages view based on app.getPublishCount()
     */
    private void processPublishIntent() {
        Log.v(TAG, ".processPublishIntent() entered");
        String publishedString = this.getString(R.string.messages_published);
        publishedString = publishedString.replace("0",Integer.toString(app.getPublishCount()));
        ((TextView) getActivity().findViewById(R.id.messagesPublishedView)).setText(publishedString);
    }

    /**
     * Intent data contained INTENT_DATA_RECEIVE
     * Update the received messages view based on app.getReceiveCount();
     */
    private void processReceiveIntent() {
        Log.v(TAG, ".processReceiveIntent() entered");
        String receivedString = this.getString(R.string.messages_received);
        receivedString = receivedString.replace("0",Integer.toString(app.getReceiveCount()));
        ((TextView) getActivity().findViewById(R.id.messagesReceivedView)).setText(receivedString);
    }

    /**
     * Update acceleration view strings
     */
    private void processAccelEvent() {
        Log.v(TAG, ".processAccelEvent()");
        float[] accelData = app.getAccelData();
        ((TextView) getActivity().findViewById(R.id.accelX)).setText("x: " + accelData[0]);
        ((TextView) getActivity().findViewById(R.id.accelY)).setText("y: " + accelData[1]);
        ((TextView) getActivity().findViewById(R.id.accelZ)).setText("z: " + accelData[2]);
    }

    @Override
    public void onOpen() {
        Log.d(TAG, "onOpen");
        mState = ConnectionState.CONNECTED;
        displayButton("Stop recording");
    }

    @Override
    public void onError(String error) {
        Log.d(TAG,error);
        mState = ConnectionState.IDLE;
        displayStatus(error);
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        mState = ConnectionState.IDLE;
        Log.d(TAG, "onClose, code: " + code + " reason: " + reason);
        displayButton("Audio");
    }

    @Override
    public void onMessage(String message) {
        Log.d(TAG, "onMessage, message: " + message);
        try {
            JSONObject jObj = new JSONObject(message);
            // state message
            if(jObj.has("state")) {
                Log.d("TTS", "Status message: " + jObj.getString("state"));
            }
            // results message
            else if (jObj.has("results")) {
                //if has result
                JSONArray jArr = jObj.getJSONArray("results");
                for (int i=0; i < jArr.length(); i++) {
                    JSONObject obj = jArr.getJSONObject(i);
                    JSONArray jArr1 = obj.getJSONArray("alternatives");
                    String str = jArr1.getJSONObject(0).getString("transcript").trim();
                    // remove whitespaces if the language requires it
                    Log.d("TTS", "To checkTerm: " + mRecognitionResults);
                    String model = getString(R.string.modelDefault);
                    if (model.startsWith("ja-JP") || model.startsWith("zh-CN")){
                        str = str.replaceAll(" +","");
                    }
                    if(obj.getString("final").equals("true")) {
                        mRecognitionResults = str;
                        if (mRecognitionResults != null || mRecognitionResults != "") {
                            displayStatus("get " + mRecognitionResults);

                            //send MQTT message
                            try {
                                // create ActionListener to handle message published results
                                MyIoTActionListener listener = new MyIoTActionListener(context, Constants.ActionStateStatus.PUBLISH);
                                IoTClient iotClient = IoTClient.getInstance(context);
                                String messageData = MessageFactory.getTextMessage(mRecognitionResults);
                                Log.d("text","message: "+messageData);
                                iotClient.publishEvent(Constants.TEXT_EVENT, "json", message, 0, false, listener);

                                int count = app.getPublishCount();
                                app.setPublishCount(++count);

                                String runningActivity = app.getCurrentRunningActivity();
                                if (runningActivity != null && runningActivity.equals(IoTPagerFragment.class.getName())) {
                                    Intent actionIntent = new Intent(Constants.APP_ID + Constants.INTENT_IOT);
                                    actionIntent.putExtra(Constants.INTENT_DATA, Constants.INTENT_DATA_PUBLISHED);
                                    context.sendBroadcast(actionIntent);
                                }
                            } catch (MqttException e) {
                                // Publish failed
                            }
                        }
                    }
                    break;
                }
            } else {
                Log.d("STT", "03: "+"unexpected data coming from stt server: \n");
                //displayResult("unexpected data coming from stt server: \n" + message);
            }

        } catch (JSONException e) {
            Log.e("STT", "Error parsing JSON");
            e.printStackTrace();
        }
    }

    @Override
    public void onAmplitude(double amplitude, double volume) {

    }

    public void displayButton(final String result) {
        final Runnable runnableUi = new Runnable(){
            @Override
            public void run() {
                Button button = (Button) getActivity().findViewById(R.id.sendText);
                button.setText(result);
            }
        };

        new Thread(){
            public void run(){
                mHandler.post(runnableUi);
            }
        }.start();
    }

    public void displayStatus(final String result) {
        final Runnable runnableUi = new Runnable(){
            @Override
            public void run() {
                TextView text = (TextView) getActivity().findViewById(R.id.result);
                text.setText(result);
            }
        };

        new Thread(){
            public void run(){
                mHandler.post(runnableUi);
            }
        }.start();
    }

    private void printResult(RecognizerResult results) {
        String text = JsonParser.parseIatResult(results.getResultString());
        Log.d(TAG,text);
        String sn = "null"; //讀取sn字段
        try {
            JSONObject resultJson = new JSONObject(results.getResultString());
            sn = resultJson.optString("sn");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if(!text.equals(" ")){
            displayStatus("get "+text);
            //send MQTT message
            try {
                // create ActionListener to handle message published results
                MyIoTActionListener listener = new MyIoTActionListener(context, Constants.ActionStateStatus.PUBLISH);
                IoTClient iotClient = IoTClient.getInstance(context);
                String messageData = MessageFactory.getTextMessage(text);
                Log.d("text","message: "+messageData);
                iotClient.publishEvent("text", "json", messageData, 0, false, listener);

                int count = app.getPublishCount();
                app.setPublishCount(++count);

                String runningActivity = app.getCurrentRunningActivity();
                if (runningActivity != null && runningActivity.equals(IoTPagerFragment.class.getName())) {
                    Intent actionIntent = new Intent(Constants.APP_ID + Constants.INTENT_IOT);
                    actionIntent.putExtra(Constants.INTENT_DATA, Constants.INTENT_DATA_PUBLISHED);
                    context.sendBroadcast(actionIntent);
                }
            } catch (MqttException e) {
                // Publish failed
            }
        }


    }

}
