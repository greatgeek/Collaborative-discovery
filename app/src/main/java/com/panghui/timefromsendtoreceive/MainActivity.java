package com.panghui.timefromsendtoreceive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
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
import java.net.UnknownHostException;
import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    WifiManager wifiManager;

    Calendar calendar = Calendar.getInstance();// get the system data
    int year = calendar.get(Calendar.YEAR);
    int month = calendar.get(Calendar.MONTH) + 1;
    int day = calendar.get(Calendar.DAY_OF_MONTH);
    int hour = calendar.get(Calendar.HOUR);
    int min = calendar.get(Calendar.MINUTE);
    boolean alreadyConfigureIp = false;

    String filename = "timedata" + year + month + day + "_" + hour + "_" + min;// File name consisting of date and time
    String localIp = IpMaker.getRandomIp();

    public enum SendorListen {send, listen}

    /**
     * UI components
     */
    private EditText BeaconSendTime;
    private EditText ListeningTime;
    private EditText WorkingPeriod;
    private EditText PhaseDifference;
    private Button StartSim;
    private Button Reset;
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
    static long timeStart = 0;
    static long timeFind = 0;

    /**
     * Network communication related
     */
    InetAddress broadcastAddress;

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
        Reset = findViewById(R.id.reset);
        LogMessage = findViewById(R.id.logMessage);

        StartSim.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (BeaconSendTime.getText().toString() == null || ListeningTime.getText().toString() == null ||
                        WorkingPeriod.getText().toString() == null || PhaseDifference.getText().toString() == null) {
                    Toast.makeText(MainActivity.this, "Experimental parameters cannot be null",
                            Toast.LENGTH_SHORT).show();
                } else {
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
                    iFindYou = false;
                    StartSim.setEnabled(false); // Do not allow click the button
                    Reset.setEnabled(true);

                    logMessage = "push the start button\n";
                    handler.obtainMessage(UPDATE_TEXT).sendToTarget(); // update UI
                }
            }
        });

        Reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // allow changes to experiment parameter
                BeaconSendTime.setEnabled(true);
                ListeningTime.setEnabled(true);
                WorkingPeriod.setEnabled(true);
                PhaseDifference.setEnabled(true);

                LogMessage.setText(""); // clear log output
                pushStart = false; // set pushStart is false
                iFindYou = true;
                StartSim.setEnabled(true); // allow click the button

                wifiControllerStartCount = 0;
            }
        });

        // Initialization start and reset button
        StartSim.setEnabled(true);
        Reset.setEnabled(false);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);// get Wifi Manager

        // filter for the sending Broadcast receiver and the receiving Broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.EXTRA_NO_CONNECTIVITY);

        IntentFilter tickFilter = new IntentFilter(); // a tick broadcast receiver
        tickFilter.addAction(Intent.ACTION_TIME_TICK);

        registerReceiver(mTickReceiver, tickFilter);

        registerReceiver(broadcastReceiver, filter);

        // construct a broadcast address
        try {
            broadcastAddress = InetAddress.getByName("192.168.1.255");
        } catch (UnknownHostException uhe) {
            uhe.printStackTrace();
        }


        wifiManager.setWifiEnabled(true);
    }

    private void enableWifi() {
        wifiManager.setWifiEnabled(true);
    }

    private void disableWifi() {
        wifiManager.setWifiEnabled(false);
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

    /**
     * set static ip address for device
     */
    public void configureAdhocNetwork(boolean check, String localIp) {
        String TAG = "configureAdhocNetwork";
        try {
            WifiConfigurationNew wifiConfig = new WifiConfigurationNew();

            /*Set the SSID and security as normal */
            wifiConfig.SSID = "\"asd\"";
            wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.OPEN);
            wifiConfig.allowedAuthAlgorithms.set(WifiConfiguration.AuthAlgorithm.SHARED);
            wifiConfig.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);

            /* Use reflection until API is official */
            wifiConfig.setIsIBSS(true);
            wifiConfig.setFrequency(2412);

            /* Use reflection to configure static IP addresses*/
            wifiConfig.setIpAssignment("STATIC");
            //wifiConfig.setIpAddress(InetAddress.getByName(ip),16);
            wifiConfig.setIpAddress(InetAddress.getByName(localIp), 24);

            wifiConfig.setDNS(InetAddress.getByName("8.8.8.8"));

            /* Add , enable and save network as normal */
            int id = wifiManager.addNetwork(wifiConfig);

            if (id < 0) {
                Log.i(TAG, "configureAdhocNetwork: failed");
            } else {
                if (check == true) {
                    boolean success = wifiManager.enableNetwork(id, true);
                } else {
                    wifiManager.disableNetwork(id);
                    wifiManager.removeNetwork(id);
                }
                wifiManager.saveConfiguration();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        private final String TAG = "broadcastReceiverSend";
        boolean isTickTrack = false;// checkout if it's go into tick track

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info.getState().equals(NetworkInfo.State.CONNECTED)) { // connect to wifi
                    String realLocalIp = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
                    Log.i("realLocalIp: ", realLocalIp);
                    if (realLocalIp.equals(localIp)) isTickTrack = true; // go into tick track
                    if (isTickTrack) {
                        new SendMessageThread("beacon", broadcastAddress).start();
                        new ListenThread().start();
                    }

                } else if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) { // disconnect to wifi

                }
            }

            if (intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {
                int wifistate = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, WifiManager.WIFI_STATE_DISABLED);
                if (wifistate == WifiManager.WIFI_STATE_DISABLED) {
                    Log.i(TAG, "wifi is disabled");
                } else if (wifistate == WifiManager.WIFI_STATE_ENABLED) { // Wait until the wifi is turned on and then configure the ip address.
                    Log.i(TAG, "wifi is enabled");
                    if (!alreadyConfigureIp) {
                        configureAdhocNetwork(true, localIp);
                        alreadyConfigureIp = true;
                    }
                }
            }
        }
    };

    private BroadcastReceiver mTickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (pushStart) {
                timeStart = System.currentTimeMillis() + 2000; // get current system time,2000 is the time required to switch wifi to connect to the network
                logMessage = "Tick Start\n";
                handler.obtainMessage(UPDATE_TEXT).sendToTarget();
                try {
                    Thread.sleep(phaseDifference); // Set the phase difference from the whole minute
                    new PeriodController().start(); // if we push the Start button , turn on Period Controller
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }
            }
            pushStart = false; // mTickReceiver only start once
        }
    };

    /**
     * WifiController controls the turn-on and off of wifi
     */
    int wifiControllerStartCount = 0;

    private class WifiController extends Thread {
        @Override
        public void run() {
            long time = System.currentTimeMillis();
            logMessage = "WifiController - " + wifiControllerStartCount + "\n";
            handler.obtainMessage(UPDATE_TEXT).sendToTarget();
            wifiControllerStartCount++;
            try {
                enableWifi();
                Thread.sleep(beaconSendTime + listeningTime + 2000); // set the On period, 2000ms is the time from opening wifi to connecting to the network
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
    public void saveToFile(SendorListen flag, String str) {
        FileOutputStream out = null;
        BufferedWriter writer = null;
        try {
            if (flag == SendorListen.send)
                out = openFileOutput("send_" + filename, Context.MODE_APPEND);
            if (flag == SendorListen.listen)
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
        boolean isLocalpacket = true; // checkout if it's a local package

        @Override
        public void run() {
            try {
                int receivePort = 23000;
                byte[] inBuf = new byte[1024];
                rds = new DatagramSocket(receivePort);
                DatagramPacket inPacket = new DatagramPacket(inBuf, inBuf.length);
                rds.setSoTimeout(1000); // 1000ms to timeout

                while (isLocalpacket) {
                    rds.receive(inPacket);

                    // Filter local UDP packets
                    InetAddress ipAddress = inPacket.getAddress();
                    String realLocalIp = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
                    InetAddress localIpAddress = InetAddress.getByName(realLocalIp);
                    if (!ipAddress.toString().equals(localIpAddress.toString())) {
                        isLocalpacket = false;
                        String rdata = new String(inPacket.getData());// parse content from UDP packet
                        if (rdata.trim().equals("beacon")) {
                            new SendMessageThread("ack", ipAddress).start(); // send a ACK back
                            timeFind = System.currentTimeMillis();
                            logMessage = "receive beacon - " + (timeFind - timeStart) + "\n";
                            handler.obtainMessage(UPDATE_TEXT).sendToTarget();
                        } else if (rdata.trim().equals("ack")) {
                            Log.i(TAG, localIpAddress + "receive " + rdata + ipAddress);
                            timeFind = System.currentTimeMillis(); // get the time of discovery
                            saveToFile(SendorListen.listen, rdata + "timeReceiving: " + (timeFind - timeStart) + "\n");// save every time to a file

                            // iFindYou = true; // i find you
                            logMessage = "receive ack - " + (timeFind - timeStart) + "\n";
                            handler.obtainMessage(UPDATE_TEXT).sendToTarget();
                        }
                    }
                }

                rds.close();
                Log.i(TAG, "ListenThread is end");
            } catch (SocketTimeoutException e) {
                isLocalpacket = false;
                e.printStackTrace();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                isLocalpacket = false;
                rds.close();
            }
        }
    }

    private void sendMessage(String str, InetAddress ipAddress) {
        DatagramSocket ds = null;
        String TAG = "sendMessage";
        try {
            int sendPort = 23000;
            ds = new DatagramSocket();
            ds.setBroadcast(true);
            //InetAddress broadcastAddress = InetAddress.getByName("192.168.1.255");
            DatagramPacket dp = new DatagramPacket(str.getBytes(), str.getBytes().length, ipAddress, sendPort);
            long timeSending = System.currentTimeMillis();
            ds.send(dp);
            saveToFile(SendorListen.send, str + "timeSending: " + timeSending + "\n"); // save every sending time to a file
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (ds != null) ds.close();
        }
    }

    /**
     * used to send beacon or ack,msg can contain beacon or ack
     */
    private class SendMessageThread extends Thread {
        String msg;
        InetAddress ipAddress;

        SendMessageThread(String msg, InetAddress ipAddress) {
            this.msg = msg;
            this.ipAddress = ipAddress;
        }

        @Override
        public void run() {
            sendMessage(msg, ipAddress);
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
