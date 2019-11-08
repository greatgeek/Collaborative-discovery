package com.panghui.timefromsendtoreceive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.util.Log.*;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);// get Wifi Manager

        // filter for the sending Broadcast receiver and the receiving Broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.EXTRA_NO_CONNECTIVITY);

        IntentFilter tickFilter = new IntentFilter(); // a tick broadcast receiver
        tickFilter.addAction(Intent.ACTION_TIME_TICK);

        registerReceiver(mTickReceiver, tickFilter);

        registerReceiver(broadcastReceiverSend,filter); // device 38347D
        //registerReceiver(broadcastReceiverReceive,filter); // device 383541
        wifiManager.setWifiEnabled(true);
    }

    private void enableWifi() {
        runRootCommand("/system/bin/ifconfig wlan0 " + localIp + " netmask 255.255.255.0");
        Log.v("localIp",localIp);
        Log.i("enableWifi:", "enable Wifi");
        new SendMessageThread().start();
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

    private BroadcastReceiver broadcastReceiverSend = new BroadcastReceiver() {
        private final String TAG = "broadcastReceiverSend";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info.getState().equals(NetworkInfo.State.CONNECTED)) { // connect to wifi
                    String realLocalIp = Formatter.formatIpAddress(wifiManager.getConnectionInfo().getIpAddress());
                    Log.i("realLocalIp",realLocalIp);

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

    private BroadcastReceiver broadcastReceiverReceive = new BroadcastReceiver() {
        private final String TAG = "broadcastReceiverSend";

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)) {
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if (info.getState().equals(NetworkInfo.State.CONNECTED)) {// connect to  wifi
                    new ListenThread().start();

                } else if (info.getState().equals(NetworkInfo.State.DISCONNECTED)) {

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
            new WifiController().start(); // turn on Wifi Controller
        }
    };

    /**
     * WifiController controls the turn-on and off of wifi
     */
    private class WifiController extends Thread {
        @Override
        public void run() {
            try {
                enableWifi();
                Thread.sleep(55000); // set the On period to 1 seconds
                disableWifi();
            } catch (InterruptedException ie) {
                ie.printStackTrace();
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
                rds.setSoTimeout(2000); // 2000ms to timeout
                while (isLocalPacket) {
                    rds.receive(inPacket);

                    // Filter local UDP packets
                    InetAddress IpAddress = inPacket.getAddress();
                    InetAddress localIpAddress = InetAddress.getByName(localIp);
                    if (!IpAddress.toString().equals(localIpAddress.toString())) {
                        isLocalPacket = false;
                        String rdata = new String(inPacket.getData());// parse content from UDP packet
                        Log.i(TAG, localIpAddress+"receive " + rdata+IpAddress);
                        long timeReceiving = System.currentTimeMillis();
                        saveToFile(SendOrListen.listen,rdata + "timeReceiving: " + timeReceiving + "\n");// save every time to a file
                    }
                }
                rds.close();
                Log.i(TAG, "ListenThread is end");
            } catch (SocketTimeoutException e) {
                isLocalPacket = false;
                rds.close();
                e.printStackTrace();
            } catch (Exception e) {
                isLocalPacket = false;
                rds.close();
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
            saveToFile(SendOrListen.send,str + "timeSending: " + timeSending + "\n"); // save every sending time to a file
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (ds != null) ds.close();
        }
    }

    int sendMessageCount = 0;

    private class SendMessageThread extends Thread {
        String TAG = "SendMessageThread";

        @Override
        public void run() {
            try {
                Thread.sleep(500); // The sender delays sending for 0.5 second to fall into the receiving area
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            sendMessage(String.valueOf(sendMessageCount) + '\0');
            Log.i(TAG, "Packet sended " + sendMessageCount);
            sendMessageCount++;
        }
    }

}
