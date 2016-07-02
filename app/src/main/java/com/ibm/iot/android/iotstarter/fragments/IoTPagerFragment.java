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
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
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
import com.ibm.iot.android.iotstarter.utils.Constants;
import com.ibm.iot.android.iotstarter.utils.MessageFactory;
import com.ibm.iot.android.iotstarter.utils.MyIoTActionListener;
import com.ibm.iot.android.iotstarter.views.DrawingView;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.ISpeechDelegate;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.SpeechToText;
import com.ibm.watson.developer_cloud.android.speech_to_text.v1.dto.SpeechConfiguration;
import com.ibm.watson.developer_cloud.android.text_to_speech.v1.TextToSpeech;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.ksoap2.SoapEnvelope;
import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;
import org.ksoap2.transport.HttpTransportSE;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


/**
 * The IoT Fragment is the main fragment of the application that will be displayed while the device is connected
 * to IoT. From this fragment, users can send text event messages. Users can also see the number
 * of messages the device has published and received while connected.
 */
public class IoTPagerFragment extends IoTStarterPagerFragment implements ISpeechDelegate {
    private final static String TAG = IoTPagerFragment.class.getName();
    private DrawingView drawingView;
    private Handler mHandler = null;
    public static boolean uiStop=true;
    private static String mRecognitionResults = "";
    public static enum ConnectionState {
        IDLE, CONNECTING, CONNECTED
    }
    public static ConnectionState mState = ConnectionState.IDLE;

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
        return inflater.inflate(R.layout.iot, container, false);

    }

    /**
     * Called when the fragment is resumed.
     */
    @Override
    public void onResume() {
        Log.d(TAG, ".onResume() entered");

        super.onResume();
        app = (IoTStarterApplication) getActivity().getApplication();
        app.setCurrentRunningActivity(TAG);

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
    private void initializeIoTActivity() {
        Log.d(TAG, ".initializeIoTFragment() entered");

        context = getActivity().getApplicationContext();

        updateViewStrings();

        Log.d("STT", "setup STT");
        if (initSTT() == false) {
            Toast.makeText(this.getActivity(), "STT Error: no authentication", Toast.LENGTH_LONG).show();
        }

        // setup button listeners
        Button button = (Button) getActivity().findViewById(R.id.sendText);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleSendText();
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

    private void handleSendText() {
        Log.d(TAG, ".handleSendText() entered");
        if (app.getConnectionType() != Constants.ConnectionType.QUICKSTART) {
            if (mState == ConnectionState.IDLE) {
                mState = ConnectionState.CONNECTING;
                displayButton("CONNECTING Audio");
                Log.d("STT", "onClickRecord: IDLE -> CONNECTING");
                mRecognitionResults = "";
                // start recognition
                new AsyncTask<Void, Void, Void>() {
                    @Override
                    protected Void doInBackground(Void... none) {
                        uiStop=false;
                        SpeechToText.sharedInstance().recognize(); //開始轉錄
                        return null;
                    }
                }.execute();

            } else if (mState == ConnectionState.CONNECTED) {
                uiStop=true;
                mState = ConnectionState.IDLE;
                Log.d("STT", "onClickRecord: CONNECTED -> IDLE");
                SpeechToText.sharedInstance().stopRecognition();

            }

            /*
            final EditText input = new EditText(context);
            new AlertDialog.Builder(getActivity())
                    .setTitle(getResources().getString(R.string.send_text_title))
                    .setMessage(getResources().getString(R.string.send_text_text))
                    .setView(input)
                    .setPositiveButton(getResources().getString(R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int whichButton) {
                            Editable value = input.getText();
                            String messageData = MessageFactory.getTextMessage(value.toString());
                            try {
                                // create ActionListener to handle message published results
                                MyIoTActionListener listener = new MyIoTActionListener(context, Constants.ActionStateStatus.PUBLISH);
                                IoTClient iotClient = IoTClient.getInstance(context);
                                iotClient.publishEvent(Constants.TEXT_EVENT, "json", messageData, 0, false, listener);

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
                    }).setNegativeButton(getResources().getString(R.string.cancel), new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Do nothing.
                }
            }).show();
            */

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
        Log.d(TAG, ".processIntent() entered: "+intent.getStringExtra(Constants.INTENT_DATA));

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
        }else if (data.equals(Constants.STOP_EVENT)) {
            Log.d(Constants.STOP_EVENT,"here");
            new QueryAddressTask().execute(intent.getStringExtra(Constants.INTENT_DATA_MESSAGE));
            /*
            if (initTTS() == false) {
                Log.d("tts", "initTTS() == false");

            }*/
            /*
            SpeechToText.sharedInstance().stopRecognition();
            TextToSpeech.sharedInstance().synthesize(intent.getStringExtra(Constants.INTENT_DATA_MESSAGE));
            */

        }
    }

    class QueryAddressTask extends AsyncTask<String, Integer, String> {
        @Override
        protected String doInBackground(String... params) {

            String wav = null;
            File file = new File(context.getCacheDir().getPath()+ "/" + params[0] + ".wav");
            if(file.exists()){
                SpeechToText.sharedInstance().stopRecognition();
                MediaPlayer mp = new MediaPlayer();
                mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        SpeechToText.sharedInstance().recognize();
                    }

                });
                wav=context.getCacheDir().getPath()+ "/" + params[0] + ".wav";
                try {
                    mp.setDataSource(context.getCacheDir().getPath()+ "/" + params[0] + ".wav");
                    mp.prepare();
                    mp.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }else {

                String methodName = "ConvertText";
                String nameSpace = "http://tts.itri.org.tw/TTSService/";
                String soapAction = nameSpace + methodName;
                SoapObject rpc = new SoapObject(nameSpace, methodName);
                rpc.addProperty("accountID", "mis101bird");
                rpc.addProperty("password", "abcd4723ccc");
                rpc.addProperty("TTStext", params[0]);
                rpc.addProperty("TTSSpeaker", "Theresa");
                rpc.addProperty("volume", "100");
                rpc.addProperty("speed", "0");
                rpc.addProperty("outType", "wav");
                SoapSerializationEnvelope envelope = new SoapSerializationEnvelope(SoapEnvelope.VER11);
                envelope.bodyOut = rpc;
                envelope.dotNet = true;
                envelope.setOutputSoapObject(rpc);
                HttpTransportSE transport = new HttpTransportSE("http://tts.itri.org.tw/TTSService/Soap_1_3.php?wsdl");
                transport.debug = true;
                Object obj = null;
                try {
                    transport.call(soapAction, envelope);
                    obj = envelope.getResponse();
                    String result = obj.toString();
                    Log.i("shiaukai", "result:" + obj.toString());
//resultTV.setText(result);
                    String[] retAry = result.split("&");
                    String status = retAry[0];
                    Log.i("shiaukai", status);
                    int convertID;
                    if (status.equals("0")) {
                        convertID = Integer.valueOf(retAry[2]);

                        while (true) {

                            Thread.sleep(2000);
                            String methodName1 = "GetConvertStatus";
                            String soapAction1 = nameSpace + methodName1;
                            SoapObject rpcs = new SoapObject(nameSpace, methodName1);
                            rpcs.addProperty("accountID", "mis101bird");
                            rpcs.addProperty("password", "abcd4723ccc");
                            rpcs.addProperty("convertID", convertID);
                            SoapSerializationEnvelope envelope1 = new SoapSerializationEnvelope(SoapEnvelope.VER11);
                            envelope1.bodyOut = rpcs;
                            envelope1.dotNet = true;
                            envelope1.setOutputSoapObject(rpcs);
                            HttpTransportSE transport1 = new HttpTransportSE("http://tts.itri.org.tw/TTSService/Soap_1_3.php?wsdl");
                            transport1.debug = true;
                            Object objs = null;
                            try {
                                transport1.call(soapAction1, envelope1);
                                objs = envelope1.getResponse();
                                wav = objs.toString();
                                String[] ret = wav.split("&");
                                String message = ret[2];
                                if (message.equals("2")) {
                                    wav = ret[4];
                                    break;
                                }
                                Log.i("shiaukai end", "result end:" + wav);

                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                    }


                } catch (Exception e) {
                    e.printStackTrace();
                }
                try {
                    SpeechToText.sharedInstance().stopRecognition();
                    MediaPlayer player = new MediaPlayer();
                    player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {

                        @Override
                        public void onCompletion(MediaPlayer mp) {
                            SpeechToText.sharedInstance().recognize();
                        }

                    });
                    player.setAudioStreamType(AudioManager.STREAM_MUSIC);
                    player.setDataSource(wav);
                    player.prepare();
                    player.start();

                } catch (Exception e) {
                    // TODO: handle exception
                }
                //store audio
                int count;
                try {
                    URL url = new URL(wav);
                    URLConnection conexion = url.openConnection();
                    conexion.connect();
                    // this will be useful so that you can show a tipical 0-100% progress bar
                    int lenghtOfFile = conexion.getContentLength();

                    // downlod the file
                    InputStream input = new BufferedInputStream(url.openStream());
                    OutputStream output = new FileOutputStream(context.getCacheDir().getPath()+ "/" + params[0] + ".wav");

                    byte data[] = new byte[1024];

                    long total = 0;

                    while ((count = input.read(data)) != -1) {
                        total += count;
                        // publishing the progress....
                        publishProgress((int)(total*100/lenghtOfFile));
                        output.write(data, 0, count);
                    }

                    output.flush();
                    output.close();
                    input.close();
                } catch (Exception e) { }
            }

            return wav;
        }

        @Override
        protected void onPostExecute(String result) {

            Log.d("result", "ui: " + result);

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
private boolean initTTS() {

        String username = getString(R.string.TTSdefaultUsername);
        String password = getString(R.string.TTSdefaultPassword);
        String tokenFactoryURL = getString(R.string.TTSdefaultTokenFactory);
        String serviceURL = "https://stream.watsonplatform.net/text-to-speech/api";
        TextToSpeech.sharedInstance().initWithContext(IoTPagerFragment.newInstance().getHost(serviceURL));
        TextToSpeech.sharedInstance().setCredentials(username, password);
        TextToSpeech.sharedInstance().setVoice("en-US_MichaelVoice");
        return true;
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
                            displayStatus("get "+mRecognitionResults);
                            String logMessage = "我:\n";
                            app.getMessageLog().add(logMessage + mRecognitionResults);
                            //send MQTT message
                            try {
                                // create ActionListener to handle message published results
                                MyIoTActionListener listener = new MyIoTActionListener(context, Constants.ActionStateStatus.PUBLISH);
                                IoTClient iotClient = IoTClient.getInstance(context);
                                String messageData = MessageFactory.getTextMessage(mRecognitionResults);
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


}
