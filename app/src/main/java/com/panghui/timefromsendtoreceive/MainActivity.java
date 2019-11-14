package com.panghui.timefromsendtoreceive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    WifiManager wifiManager;

    Calendar calendar = Calendar.getInstance();// get the system data
    int year = calendar.get(Calendar.YEAR);
    int month = calendar.get(Calendar.MONTH) + 1;
    int day = calendar.get(Calendar.DAY_OF_MONTH);
    int hour = calendar.get(Calendar.HOUR);
    int min = calendar.get(Calendar.MINUTE);

    String filename = "timedata" + year + month + day + "_" + hour + "_" + min;// File name consisting of date and time
    String localIp = IpMaker.getRandomIp(); // Get random Ip address

    public enum SendOrListen {send, listen}

    /**
     * UI components
     */
    private EditText BeaconSendTime;
    private EditText ListeningTime;
    private EditText WorkingPeriod;
    private EditText PhaseDifference;
    private Button StartSim;
    private EditText LogMessage;

    static final int UPDATE_TEXT = 1;
    String logMessage = "log:\n";

    /**
     * Experimental parameters
     */
    private long beaconSendTime;
    private long listeningTime;
    private long workingPeriod;
    private long phaseDifference;

    /**
     * some judgement flags
     */
    boolean iFindYou = false;
    boolean pushStart = false;

    /**
     * Variables are used to record the length of time from startup to discovery
     */
    long timeStart = 0;
    long timeFind = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /**UI components*/
        BeaconSendTime = findViewById(R.id.beaconSendTime);
        ListeningTime = findViewById(R.id.listeningTime);
        WorkingPeriod = findViewById(R.id.workingPeriod);
        PhaseDifference = findViewById(R.id.phaseDifference);
        StartSim = findViewById(R.id.startSim);
        LogMessage = findViewById(R.id.logMessage);

        StartSim.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (BeaconSendTime.getText().toString() == null || ListeningTime.getText().toString() == null ||
                        WorkingPeriod.getText().toString() == null || PhaseDifference.getText().toString() == null) {
                    Toast.makeText(MainActivity.this, "Experimental parameters cannot be null",
                            Toast.LENGTH_SHORT).show();
                }

                beaconSendTime = Long.parseLong(BeaconSendTime.getText().toString());
                listeningTime = Long.parseLong(ListeningTime.getText().toString());
                workingPeriod = Long.parseLong(WorkingPeriod.getText().toString());
                phaseDifference = Long.parseLong(PhaseDifference.getText().toString());

                // Do not allow changes to experiment parameters after clicking Start
                BeaconSendTime.setEnabled(false);
                ListeningTime.setEnabled(false);
                WorkingPeriod.setEnabled(false);
                PhaseDifference.setEnabled(false);

                pushStart = true;
                StartSim.setEnabled(false); // Do not allow click the button

                logMessage = "push the start button\n";
                handler.obtainMessage(UPDATE_TEXT).sendToTarget(); // update UI
            }
        });

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);// get Wifi Manager

        // filter for the sending Broadcast receiver and the receiving Broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.EXTRA_NO_CONNECTIVITY);

        IntentFilter tickFilter = new IntentFilter(); // a tick broadcast receiver
        tickFilter.addAction(Intent.ACTION_TIME_TICK);

        registerReceiver(mTickReceiver, tickFilter);

        //registerReceiver(broadcastReceiver, filter); // broadcast receiver

        wifiManager.setWifiEnabled(true);
    }

    private void enableWifi() {
        runRootCommand("/system/bin/ifconfig wlan0 " + localIp + " netmask 255.255.255.0");
        Log.v("localIp", localIp);
        Log.i("enableWifi:", "enable Wifi");
        new SendBeaconThread().start();
        new ListenThread().start();
    }

    private void disableWifi() {
        runRootCommand("/system/bin/ifconfig wlan0 " + "192.168.2.4" + " netmask 255.255.255.0");
        Log.i("disableWifi:", "disable wifi");
    }

    /**
     * This function is used to execute shell commands
     *
     * @param command
     * @return
     */
    public boolean runRootCommand(String command) {
        Process process = null;
        DataOutputStream os = null;
        try {
            process = Runtime.getRuntime().exec("su");
            os = new DataOutputStream(process.getOutputStream());
            os.writeBytes(command + "\n");
            os.writeBytes("exit\n");
            os.flush();
            process.waitFor();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        } finally {
            try {
                if (os != null) {
                    os.close();
                }
                process.destroy();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return true;
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        private final String TAG = "broadcastReceiverSend";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info.getState().equals(NetworkInfo.State.CONNECTED)) { // connect to wifi
                    String realLocalIp = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
                    Log.i("realLocalIp", realLocalIp);

                } else if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) { // disconnect to wifi

                }
            }

            if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                int wifistate = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED);
                if (wifistate == WifiManager.WIFI_STATE_DISABLED) {
                    Log.i(TAG, "wifi is disabled");
                } else if (wifistate == WifiManager.WIFI_STATE_ENABLED) {
                    Log.i(TAG, "wifi is enabled");
                }
            }
        }
    };


    private BroadcastReceiver mTickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (pushStart) {
                logMessage = "Tick Start\n";
                handler.obtainMessage(UPDATE_TEXT).sendToTarget();
                timeStart = System.currentTimeMillis(); // get the start time
                try {
                    Thread.sleep(phaseDifference); // Set the phase difference from the whole minute
                    new PeriodController().start(); // if we push the Start button,turn on Period Controller
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
                pushStart = false; // mTickReceiver only start once
            }
        }
    };

    /**
     * WifiController controls the turn-on and off of wifi
     */
    int wifiControllerStartCount = 0;

    private class WifiController extends Thread {
        @Override
        public void run() {
            logMessage = "WifiController: " + wifiControllerStartCount + "\n";
            handler.obtainMessage(UPDATE_TEXT).sendToTarget();
            wifiControllerStartCount++;
            try {
                enableWifi();
                Thread.sleep(beaconSendTime + listeningTime); // set the On period
                disableWifi();
            } catch (InterruptedException ie) {
                ie.printStackTrace();
            }
        }
    }

    /**
     * PeriodController controls the working period
     */
    private class PeriodController extends Thread {
        @Override
        public void run() {
            while (!iFindYou) {
                try {
                    new WifiController().start();
                    Thread.sleep(workingPeriod); // set the working period
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
        }
    }

    /***
     *
     * @param str save str to a file
     */
    public void saveToFile(SendOrListen sl, String str) {
        FileOutputStream out = null;
        BufferedWriter writer = null;
        try {
            if (sl == SendOrListen.send)
                out = openFileOutput("send_" + filename, Context.MODE_APPEND);
            if (sl == SendOrListen.listen)
                out = openFileOutput("receive_" + filename, Context.MODE_APPEND);
            writer = new BufferedWriter(new OutputStreamWriter(out));
            writer.write(str);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (writer != null) writer.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private class ListenThread extends Thread {
        String TAG = "ListenThread";
        DatagramSocket rds = null;
        boolean isLocalPacket = true; // checkout if it's a local package

        @Override
        public void run() {
            try {
                int receivePort = 23000;
                byte[] inBuf = new byte[1024];
                rds = new DatagramSocket(receivePort);
                DatagramPacket inPacket = new DatagramPacket(inBuf, inBuf.length);
                rds.setSoTimeout(1000); // 1000ms to timeout
                while (isLocalPacket) {
                    rds.receive(inPacket);

                    // Filter local UDP packets
                    InetAddress IpAddress = inPacket.getAddress();
                    InetAddress localIpAddress = InetAddress.getByName(localIp);
                    if (!IpAddress.toString().equals(localIpAddress.toString())) {
                        isLocalPacket = false;
                        String rdata = new String(inPacket.getData());// parse content from UDP packet
                        if (rdata.trim().equals("beacon")) {
                            new SendAckThread().start();// send a ACK back
                        } else if (rdata.trim().equals("ack")) {
                            Log.i(TAG, localIpAddress + "receive " + rdata + IpAddress);
                            timeFind = System.currentTimeMillis();// get the time of discovery
                            saveToFile(SendOrListen.listen, rdata + "timeReceiving: " + (timeFind - timeStart) + "\n");// save every time to a file

                            //iFindYou = true;// i find you
                            logMessage = "I find you \n";
                            handler.obtainMessage(UPDATE_TEXT).sendToTarget();
                        }
                    }
                }
                rds.close();
                Log.i(TAG, "ListenThread is end");
            } catch (SocketTimeoutException e) {
                isLocalPacket = false;
                e.printStackTrace();
            } catch (Exception e) {
                isLocalPacket = false;
                e.printStackTrace();
            } finally {
                isLocalPacket = false;
                rds.close();
            }
        }
    }

    private void sendMessage(String str) {
        DatagramSocket ds = null;
        String TAG = "sendMessage";
        try {
            int sendPort = 23000;
            ds = new DatagramSocket();
            ds.setBroadcast(true);
            InetAddress broadcastAddress = InetAddress.getByName("192.168.1.255");
            DatagramPacket dp = new DatagramPacket(str.getBytes(), str.getBytes().length, broadcastAddress, sendPort);
            long timeSending = System.currentTimeMillis();
            ds.send(dp);
            saveToFile(SendOrListen.send, str + "timeSending: " + timeSending + "\n"); // save every sending time to a file
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (ds != null) ds.close();
        }
    }

    int sendMessageCount = 0;

    private class SendBeaconThread extends Thread {
        String TAG = "SendBeaconThread";

        @Override
        public void run() {
            sendMessage("beacon");
            Log.i(TAG, "Packet sended " + sendMessageCount);
            sendMessageCount++;
        }
    }

    private class SendAckThread extends Thread {
        String TAG = "SendAckThread";

        @Override
        public void run() {
            sendMessage("ack");
            Log.i(TAG, "Packet sended " + sendMessageCount);
            sendMessageCount++;
        }
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_TEXT:
                    LogMessage.append(logMessage);
                    break;
            }
        }
    };

}
