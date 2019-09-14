/*
 * Copyright (c) 2018, Manfred Constapel
 * This file is licensed under the terms of the MIT license.
 */

package de.m6c7l.sniffer.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class PipeReader extends Thread {
    
    private BufferedReader in;
    private Device device;

    public PipeReader(Device dev, InputStream in) {
        this.device = dev;
        this.in = new BufferedReader(new InputStreamReader(System.in));            
    }

    // process text input of stdin  
    public void run() {
        String s = null;      
        while (device.connect()) {
            try {
                s = this.in.readLine();                
                String[] args = (s!=null ? s.split(" ") : new String[] {});
                // a new channel (in decimal) to sniff on?
                if (args.length == 1) {
                    // just an enter, pipe status of device
                    if (args[0].length() == 0) {
                        System.err.println(device);
                    } else {
                        // try to set new channel for capturing
                        if (device.channel(s)) {
                            // give some feedback
                            System.err.println("{channel={time=" + System.currentTimeMillis() + ",value=" + s + "}}");
                        }                        
                    }
                // most likely a channel and some hexified content to send
                } else if (args.length == 2) {
                    byte[] buf = FrameUtility.arr(args[1]);
                    if (device.send(args[0], buf)) {
                        // give some feedback
                        System.err.println("{message={time=" + System.currentTimeMillis() + ",channel=" + args[0] + ",value=" + args[1] + "}}");                        
                    }                  
                }
            } catch (IOException e) {}        
        }
    }

}