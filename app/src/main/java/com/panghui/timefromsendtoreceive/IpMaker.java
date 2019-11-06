package com.panghui.timefromsendtoreceive;

import java.util.Random;

public class IpMaker {
    public static String getRandomIp(){
        String Ip="192.168.1";
        Random randomGenerator = new Random();
        int y=makeRandomInteger(1,254,randomGenerator);
        return Ip+"."+y;
    }

    private static int makeRandomInteger(int aStart, int aEnd, Random aRandom){
        if(aStart > aEnd) throw new IllegalArgumentException("Start cannot exceed End.");
        long range = (long)aEnd - (long)aStart+1;
        long fraction = (long)(range * aRandom.nextDouble());// compute a fraction of the range, 0<= frac <range
        int randomNumber = (int)(fraction + aStart);
        return randomNumber;
    }
}
