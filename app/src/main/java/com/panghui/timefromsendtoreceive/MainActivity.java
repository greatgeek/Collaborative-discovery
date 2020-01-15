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
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Calendar;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

import static android.os.SystemClock.setCurrentTimeMillis;

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
    String debugFilename = "debug" + year + month + day + "_" + hour + "_" + min;
    String localIp = IpMaker.getRandomIp();

    /**
     * UI components
     */
    private TextView IPaddress;
    private EditText BeaconSendTime;
    private EditText ListeningTime;
    private EditText WorkingPeriod;
    private EditText PhaseDifference;
    private Button StartSim;
    private Button Reset;
    private Button ReceiveTime;
    private Switch FreeModel;
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
    long delayTime = 2500;

    /**
     * Timer to control working period
     */
    Timer periodTimer = null;
    TimerTask periodTimerTask = null;

    /**
     * BroadcastReceiver
     */
    myTickBroadcastReceiver mTickReceiver = null;

    /**
     * Free Mode
     */
    boolean isFreeMode = false;

    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case UPDATE_TEXT:
                    LogMessage.append(logMessage);
                    break;
            }
        }
    };

    void displayToUI(String str){
        logMessage = str;
        handler.obtainMessage(UPDATE_TEXT).sendToTarget();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        /**UI components*/
        IPaddress=findViewById(R.id.ipAddress);
        BeaconSendTime = findViewById(R.id.beaconSendTime);
        ListeningTime = findViewById(R.id.listeningTime);
        WorkingPeriod = findViewById(R.id.workingPeriod);
        PhaseDifference = findViewById(R.id.phaseDifference);
        StartSim = findViewById(R.id.startSim);
        Reset = findViewById(R.id.reset);
        ReceiveTime = findViewById(R.id.receiveTime);
        FreeModel = findViewById(R.id.freeModel);
        LogMessage = findViewById(R.id.logMessage);

        // display IP address
        IPaddress.setText(localIp);

        // Initialization start and reset button
        StartSim.setEnabled(true);
        Reset.setEnabled(false);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);// get Wifi Manager

        // filter for the sending Broadcast receiver and the receiving Broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.EXTRA_NO_CONNECTIVITY);

        final IntentFilter tickFilter = new IntentFilter(); // a tick broadcast receiver
        tickFilter.addAction(Intent.ACTION_TIME_TICK);

        mRegisterTickReceiver(mTickReceiver, tickFilter);

        registerReceiver(broadcastReceiver, filter);

        // construct a broadcast address
        try {
            broadcastAddress = InetAddress.getByName("192.168.1.255");
        } catch (UnknownHostException uhe) {
            uhe.printStackTrace();
        }

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

                    if (isFreeMode) {
                        pushStart = false;
                        startTimer();
                    } else {
                        pushStart = true;
                        displayToUI("push the start button\n");
                    }
                    iFindYou = false;
                    StartSim.setEnabled(false); // Do not allow click the button
                    Reset.setEnabled(true);
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
                stopTimer();
                FreeModel.setChecked(false);
                periodCount = 0;
            }
        });

        ReceiveTime.setOnClickListener(new View.OnClickListener(){

            @Override
            public void onClick(View v) {
                new ReceiveTimeStamp().start();
            }
        });

        FreeModel.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    isFreeMode = true;
                    Toast.makeText(MainActivity.this, "free Mode is on", Toast.LENGTH_SHORT).show();
                    mUnRegisterTickReceiver(mTickReceiver);

                } else {
                    isFreeMode = false;
                    mRegisterTickReceiver(mTickReceiver, tickFilter);
                    stopTimer();
                    Toast.makeText(MainActivity.this, "free Mode is off", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void enableWifi() {
        wifiManager.setWifiEnabled(true);
    }

    private void disableWifi() {
        wifiManager.setWifiEnabled(false);
    }

    void mRegisterTickReceiver(BroadcastReceiver receiver, final IntentFilter filter) {
        if (receiver == null) {
            receiver = new myTickBroadcastReceiver();
            registerReceiver(receiver, filter);
        }
    }

    void mUnRegisterTickReceiver(BroadcastReceiver receiver) {
        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }
    }

    /**
     * Timer Starter and Stopper
     */
    int periodCount = 0;

    private void startTimer() {
        if (periodTimer == null) {
            periodTimer = new Timer();
        }

        if (periodTimerTask == null) {
            periodTimerTask = new PeriodTask();
        }

        Log.i("Test", "Start at " + System.currentTimeMillis());

        timeStart = System.currentTimeMillis() + delayTime; // get current system time,2000 is the time required to switch wifi to connect to the network
        displayToUI("Start at " + timeStart + "\n");

        if (periodTimer != null && periodTimerTask != null) {
            periodTimer.scheduleAtFixedRate(periodTimerTask, phaseDifference + beaconSendTime, workingPeriod);
        }
    }

    private void stopTimer() {
        if (periodTimer != null) {
            periodTimer.cancel();
            periodTimer = null;
        }
        if (periodTimerTask != null) {
            periodTimerTask.cancel();
            periodTimerTask = null;
        }
    }

    /**
     * PeriodTask controls the working period
     */
    private class PeriodTask extends TimerTask {
        @Override
        public void run() {
            Log.i("Test", "Period start at " + System.currentTimeMillis());
            displayToUI("ActiveTask - " + periodCount++ + "\n");

            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    long nowTime = System.currentTimeMillis();
                    Log.i("Test", "Send udp at " + nowTime);
                    saveToFile(debugFilename,"listen="+System.currentTimeMillis()+" ");//debug
                    listen();
                    Log.i("Test", "Stop listen at " + System.currentTimeMillis());
                    sendMessage("beacon"+":"+nowTime+":"+timeStart+" ", broadcastAddress);
                    //saveToFile(debugFilename,"sendUDPEnd="+System.currentTimeMillis()); // debug
                    saveToFile(debugFilename,"End="+System.currentTimeMillis()+"\n"); // debug
                    disableWifi();
                }
            }, delayTime);

            enableWifi();
        }
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
                        //new SendMessageThread("beacon", broadcastAddress).start();
                        //new ListenThread().start();
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

    private class myTickBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (pushStart && isFreeMode == false) {
                startTimer();
            }
            pushStart = false; // mTickReceiver only start once
        }
    }

    ;


    private void sendMessage(String str, InetAddress ipAddress) {
        DatagramSocket ds = null;
        String TAG = "sendMessage";
        try {
            int sendPort = 23000;
            ds = new DatagramSocket();
            ds.setBroadcast(true);
            DatagramPacket dp = new DatagramPacket(str.getBytes(), str.getBytes().length, ipAddress, sendPort);
            ds.send(dp);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (ds != null) ds.close();
        }
    }

    private class SendTimeStampThread extends Thread {
        @Override
        public void run() {
            long time = System.currentTimeMillis();
            sendMessage(String.valueOf(time),broadcastAddress);
        }
    }

    private void listen() {
        String TAG = "Listen";
        DatagramSocket rds = null;
        long stopListenTime = System.currentTimeMillis() + listeningTime;
        do {
            try {
                int receivePort = 23000;
                byte[] inBuf = new byte[1024];
                rds = new DatagramSocket(receivePort);
                DatagramPacket inPacket = new DatagramPacket(inBuf, inBuf.length);
                int timeout = (int) (stopListenTime - System.currentTimeMillis());
                if (timeout < 0) timeout = 0;

                rds.setSoTimeout(timeout); // listeningTime to timeout

                // listen util timeout even receive a packet
                rds.receive(inPacket);

                new ParsePacket(inPacket).start(); /**parse the UDP Packet*/

                /*

                // Filter local UDP packets
                InetAddress ipAddress = inPacket.getAddress();
                String realLocalIp = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
                InetAddress localIpAddress = InetAddress.getByName(realLocalIp);
                if (!ipAddress.toString().equals(localIpAddress.toString())) {
                    String rdata = new String(inPacket.getData());// parse content from UDP packet
                    String myrdata = rdata.trim();
                    if (myrdata.contains("beacon")) {
                        //new SendMessageThread("ack", ipAddress).start(); // send a ACK back
                        sendMessage("ack", ipAddress);
                        timeFind = System.currentTimeMillis();

                        String[] mySubstring = myrdata.split(":");
                        long newTimeStart = Long.parseLong(mySubstring[1]);
                        saveToFile("receive beacon @ " + (timeFind - newTimeStart) + " from "+ipAddress.toString()+"\n");
                        displayToUI("receive beacon @ " + (timeFind - newTimeStart) + " from "+ipAddress.toString()+"\n");

                    } else if (rdata.trim().equals("ack")) {
                        Log.i(TAG, localIpAddress + "receive " + rdata + ipAddress);
                        timeFind = System.currentTimeMillis(); // get the time of discovery
                        // iFindYou = true; // i find you
                        displayToUI("receive ack @ " + (timeFind - timeStart) + " from "+ipAddress.toString()+"\n");
                    }else{ // receive time stamp and set System time
                        long time = Long.parseLong(rdata);
                        setCurrentTimeMillis(time);
                        displayToUI("receive time stamp");
                    }
                }

                */
            } catch (SocketTimeoutException e) {
                Log.e(TAG, "listen timeout");
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (rds != null && !rds.isClosed()) {
                    rds.close();
                }
            }
        } while (stopListenTime > System.currentTimeMillis());
    }

    private class ReceiveTimeStamp extends Thread {
        @Override
        public void run() {
            String TAG = "ReceiveTimeStamp";
            DatagramSocket rds = null;
                try {
                    int receivePort = 23000;
                    byte[] inBuf = new byte[1024];
                    rds = new DatagramSocket(receivePort);
                    DatagramPacket inPacket = new DatagramPacket(inBuf, inBuf.length);

                    rds.setSoTimeout(10000); // listeningTime to timeout

                    // listen util timeout even receive a packet
                    rds.receive(inPacket);
                    displayToUI("receive @ "+System.currentTimeMillis()+"\n");

                    // Filter local UDP packets
                    InetAddress ipAddress = inPacket.getAddress();
                    String realLocalIp = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
                    InetAddress localIpAddress = InetAddress.getByName(realLocalIp);
                    if (!ipAddress.toString().equals(localIpAddress.toString())) {
                        String rdata = new String(inPacket.getData());// parse content from UDP packet
                       // receive time stamp and set System time
                            long time = Long.parseLong(rdata.trim());
                            displayToUI("difference @ "+ (time-System.currentTimeMillis()) +"\n");
                            boolean res = setCurrentTimeMillis(time+beaconSendTime);
                            if(res) {
                                new SendTimeStampThread().start();
                                displayToUI("set time successfully"+"\n");
                            }
                    }
                } catch (SocketTimeoutException e) {
                    Log.e(TAG, "listen timeout");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    if (rds != null && !rds.isClosed()) {
                        rds.close();
                    }
                }
        }
    }

    /***
     *
     * @param str save str to a file
     */
    public void saveToFile(String filename,String str) {
        FileOutputStream out = null;
        BufferedWriter writer = null;
        try {
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


    /**
     * This thread is used to parse UDP packet
     */
    private class ParsePacket extends Thread {
        DatagramPacket packet;

        ParsePacket(DatagramPacket packet) {
            this.packet = packet;
        }

        @Override
        public void run() {
            try{
                // Filter local UDP packets
                InetAddress ipAddress = packet.getAddress();
                String realLocalIp = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
                InetAddress localIpAddress = InetAddress.getByName(realLocalIp);
                if(!ipAddress.toString().equals(localIpAddress.toString())){
                    String rdata = new String(packet.getData()); // parse content from UDP packet
                    String myrdata = rdata.trim();

                    if(myrdata.contains("beacon")){
                        sendMessage("ack",ipAddress);
                        timeFind = System.currentTimeMillis();

                        String[] mySubstring = myrdata.split(":");
                        if(mySubstring.length ==3){
                            long standardTime = Long.parseLong(mySubstring[1]);
                            long newTimeStart = Long.parseLong(mySubstring[2]);
                            saveToFile(filename,"receive beacon @ "+ (timeFind - newTimeStart) + " from "+ipAddress.toString()+"\n");
                            saveToFile(debugFilename,"receive beacon @ "+ (timeFind - newTimeStart) + " from "+ipAddress.toString()+"\n");

                            displayToUI("receive beacon @ "+ (timeFind - newTimeStart) + " from "+ipAddress.toString()+"\n");

                            // set SystemClock
                            //boolean flag = SystemClock.setCurrentTimeMillis(standardTime+beaconSendTime);
                            //if(flag) displayToUI("set SystemClock successfully"+"\n");

                        }

                    }else if(myrdata.contains("ack")){
                        String[] mySubstring = myrdata.split(":");
                        if(mySubstring.length ==2){
                            long standardTime = Long.parseLong(mySubstring[1]);
                            //boolean flag = SystemClock.setCurrentTimeMillis(standardTime+beaconSendTime);
                            //if(flag) displayToUI("set SystemClock successfully"+"\n");
                        }

                        timeFind = System.currentTimeMillis(); // get the discovery time
                        displayToUI("receive ack @ "+(timeFind - timeStart) + " from "+ipAddress.toString()+"\n");


                    }
                }
            }catch(UnknownHostException uhe){
                uhe.printStackTrace();
            }
        }
    }
}
