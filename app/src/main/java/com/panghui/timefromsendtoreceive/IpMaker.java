package com.panghui.timefromsendtoreceive;

import android.util.Log;

import java.util.Random;

public class IpMaker {
    public static String getRandomIp(){
        String IP="192.168.43";
        Random randomGenerator = new Random();
        //int x = randomGenerator.nextInt(255);
        int y = makeRandomInteger(1,254,randomGenerator);
        Log.i("IP",IP + "." + y);
        return IP + "." + y;

    }
    private static int makeRandomInteger(int aStart, int aEnd, Random aRandom){
        if (aStart > aEnd) {
            throw new IllegalArgumentException("Start cannot exceed End.");
        }
        //get the range, casting to long to avoid overflow problems
        long range = (long)aEnd - (long)aStart + 1;
        // compute a fraction of the range, 0 <= frac < range
        long fraction = (long)(range * aRandom.nextDouble());
        int randomNumber =  (int)(fraction + aStart);
        return randomNumber;
    }
}
