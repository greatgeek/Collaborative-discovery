package com.panghui.timefromsendtoreceive;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;

import java.io.BufferedWriter;
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
    int month = calendar.get(Calendar.MONTH)+1;
    int day = calendar.get(Calendar.DAY_OF_MONTH);
    int hour = calendar.get(Calendar.HOUR);
    int min = calendar.get(Calendar.MINUTE);

    String filename = "timedata"+year+month+day+"_"+hour+"_"+min;// File name consisting of date and time

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        wifiManager = (WifiManager)getApplicationContext().getSystemService(Context.WIFI_SERVICE);// get Wifi Manager

        // filter for the sending Broadcast receiver and the receiving Broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(ConnectivityManager.EXTRA_NO_CONNECTIVITY);

        //registerReceiver(broadcastReceiverSend,filter);
        registerReceiver(broadcastReceiverReceive,filter);

        enableWifi();
    }

    private void enableWifi() { wifiManager.setWifiEnabled(true); }
    private void disableWifi() {wifiManager.setWifiEnabled(false); }

    private BroadcastReceiver broadcastReceiverSend = new BroadcastReceiver() {
        private final String TAG = "broadcastReceiverSend";
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)){
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if(info.getState().equals(NetworkInfo.State.CONNECTED)){ // connect to wifi
                    new SendMessageThread().start();

                }else if(info.getState().equals(NetworkInfo.State.DISCONNECTED)){ // disconnect to wifi

                }
            }

            if(intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)){
                int wifistate = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,WifiManager.WIFI_STATE_DISABLED);
                if(wifistate == WifiManager.WIFI_STATE_DISABLED){
                    Log.i(TAG,"wifi is disabled");
                }else if(wifistate == WifiManager.WIFI_STATE_ENABLED){
                    Log.i(TAG,"wifi is enabled");
                }
            }
        }
    };

    private BroadcastReceiver broadcastReceiverReceive = new BroadcastReceiver() {
        private final String TAG = "broadcastReceiverSend";
        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent.getAction().equals(WifiManager.NETWORK_STATE_CHANGED_ACTION)){
                NetworkInfo info = intent.getParcelableExtra(WifiManager.EXTRA_NETWORK_INFO);
                if(info.getState().equals(NetworkInfo.State.CONNECTED)){// connect to  wifi
                    new ListenThread().start();

                }else if(info.getState().equals(NetworkInfo.State.DISCONNECTED)){

                }
            }

            if(intent.getAction().equals(WifiManager.WIFI_STATE_CHANGED_ACTION)){
                int wifistate = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,WifiManager.WIFI_STATE_DISABLED);
                if(wifistate == WifiManager.WIFI_STATE_DISABLED){
                    Log.i(TAG,"wifi is disabled");
                }else if(wifistate == WifiManager.WIFI_STATE_ENABLED){
                    Log.i(TAG,"wifi is enabled");
                }
            }
        }
    };

    /***
     *
     * @param str save str to a file
     */
    public void saveToFile(String str){
        FileOutputStream out = null;
        BufferedWriter writer = null;
        try{
            out = openFileOutput(filename,Context.MODE_APPEND);
            writer = new BufferedWriter(new OutputStreamWriter(out));
            writer.write(str);
        }catch(IOException e){
            e.printStackTrace();
        }finally{
            try{
                if(writer != null) writer.close();
            }catch(IOException e){
                e.printStackTrace();
            }
        }
    }

    private class ListenThread extends Thread{
        String TAG = "ListenThread";
        boolean Listenexit = false;
        DatagramSocket rds = null;
        int receiveCount =0;

        @Override
        public void run() {
            try{
                int receivePort=23000;
                byte[] inBuf = new byte[1024];
                rds = new DatagramSocket(receivePort);
                while(!Listenexit){
                    DatagramPacket inPacket = new DatagramPacket(inBuf,inBuf.length);
                    rds.setSoTimeout(5000); // 2000ms to timeout
                    //Log.i(TAG,"go into receive");
                    rds.receive(inPacket);
                    String rdata = new String(inPacket.getData());// parse content from UDP packet
                    Log.i(TAG,"receive "+rdata);
                    long timeReceiving = System.currentTimeMillis();
                    saveToFile(rdata+"timeReceiving: "+timeReceiving+"\n");// save every time to a file
                    //Log.i(TAG,"go out receive "+rdata);

                }
                rds.close();
                Log.i(TAG,"ListenThread is end");
            }catch(SocketTimeoutException e){
                e.printStackTrace();
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        public void killListenThread(){ Listenexit=true; }
    }

    private void sendMessage(String str){
        DatagramSocket ds=null;
        String TAG="sendMessage";
        try{
            int sendPort=23000;
            ds = new DatagramSocket();
            ds.setBroadcast(true);
            InetAddress broadcastAddress = InetAddress.getByName("192.168.1.255");
            DatagramPacket dp = new DatagramPacket(str.getBytes(),str.getBytes().length,broadcastAddress,sendPort);
            long timeSending=System.currentTimeMillis();
            ds.send(dp);
            saveToFile(str+"timeSending: "+timeSending+"\n"); // save every sending time to a file
        }catch(Exception e){
            e.printStackTrace();
        }finally{
            if(ds!=null) ds.close();
        }
    }

    private class SendMessageThread extends Thread{
        String TAG = "SendMessageThread";

        @Override
        public void run() {
            for(int i=0;i<100;i++){
                sendMessage(String.valueOf(i)+'\0');
                Log.i(TAG,"Packet sended "+i);
                try{
                    Thread.sleep(1000); // sleep 1000 ms to send next packet
                }catch(InterruptedException e){
                    e.printStackTrace();
                }
            }
        }
    }

}
