/*
 * Copyright (c) 2018, Manfred Constapel
 * This file is licensed under the terms of the MIT license.
 */

package de.m6c7l.sniffer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;

import de.m6c7l.sniffer.app.Device;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;

public class Start {
   
    public static void main(String[] args) throws UnsupportedEncodingException, MalformedURLException {

        /*
        if (jniLink != null) {
            System.err.println(jniLink.getMessage());
            System.exit(1);
        }
        */
        
        byte status = -4;
        
        Object[] param = Utility.parse(args);
        if ((param[0] != null) && (param[1] != null)) {
            
            String port = (String)param[0];
            Device device = new Device();

            try {
                // connect
                if (device.connect(port)) {
                    Thread.sleep(50L);                    
                    status++; // -3
                    
                    // try to reset
                    if (device.reset()) {
                        Thread.sleep(50L);
                        status++; // -2
                        
                        // get device info
                        device.status();
                        Thread.sleep(50L);
                        
                        // try to set a channel
                        if (device.channel((String)param[1])) {
                            Thread.sleep(50L);
                            status++; // -1
                                                        
                            // channel is accepted by transceiver
                            if (device.channel() != null) { 
                                Thread.sleep(50L);
                                status++; // 0
                                
                                // enable device (start sniffing on given channel)
                                device.enable(true);
                                Thread.sleep(50L);

//                                // example 1: send a string message on monitored channel
//                                device.send(device.channel(), "hello, world.");
//
//                                // example 2: send a byte message on channel 26
//                                device.send(26, Utility.arr(Utility.hex("hello, world.".getBytes())));
//
//                                // example 3: send aother byte message on channel 11
//                                device.send(11, Utility.arr("68 65 6c 6c 6f 2c 20 77 6f 72 6c 64 2e"));

                            }
                        }
                    }    

                    // catch SIGINT (e.g. ctrl-c) and shutdown gently
                    Runtime.getRuntime().addShutdownHook(new Thread() {
                        public void run() {
                            try {
                                device.enable(false);
                                Thread.sleep(50L);
                                device.disconnect();
                            } catch (IOException | InterruptedException e) {
                                System.err.println(e.getMessage());
                            }
                        }
                    });

                    // device info to output
                    System.err.println(device);

                    if (status != 0) {
                        device.disconnect();
                        System.exit(1);
                    } 
                            
                }
            } catch (IOException e) {
                error("device not responding");
            } catch (NoSuchPortException e) {
                error("no such port: " + port);
            } catch (PortInUseException e) {
                error("port in use: " + port);
            } catch (UnsupportedCommOperationException e) {
                error("");
            } catch (InterruptedException e) {
                error("");
            }
            
        } else {
            
            usage();
            
        }
        
    }

    /*    
    //private static String jniLib = null;
    private static UnsatisfiedLinkError jniLink = null;
    
    static {
        
        final String sep = System.getProperty("file.separator");
        final String libPath = "lib/rxtx/";

        String osName = System.getProperty("os.name").toLowerCase();
        String osArch = System.getProperty("os.arch").toLowerCase();
        
        StringBuilder lib = new StringBuilder(libPath);
        lib.append(osName.split(" ")[0] + sep);
        lib.append(osArch + sep);

        File libdir = new File(lib.toString());
        if (!libdir.exists()) {
            jniLink = new UnsatisfiedLinkError("platform is not supported (" + osArch + "/" + osName + ")");
        }

        if (jniLink==null) {
            
            File[] libfile = libdir.listFiles();
            if (libfile.length > 0) {
                
                try {
                    
                    // convert URI to file to avoid percent-sign/hex notation
                    String root = new File(Utility.jar(Start.class).toURI()).getParent();
                    File f = new File(root + sep + libfile[0].toString());
                    
                    // Runtime.getRuntime().load(f.toString());
                    // since System.load() does not work for this version of gnu.io.rxtx,
                    // the directory is put into library path at runtime
                    // Utility.add_lib_path(f.getParent().toString());
                    Utility.add_lib_path(f.getParent().toString());

                } catch (URISyntaxException e) {
                    jniLink = new UnsatisfiedLinkError(e.getMessage());
                } catch (UnsupportedEncodingException e) {
                    jniLink = new UnsatisfiedLinkError(e.getMessage());
                } catch (MalformedURLException e) {
                    jniLink = new UnsatisfiedLinkError(e.getMessage());
                } catch (IOException e) {
                    jniLink = new UnsatisfiedLinkError(e.getMessage());
                }
            } else {
                jniLink = new UnsatisfiedLinkError("library is missing (" + osArch + "/" + osName + ")");                
            }
            
        }
        
    }
    */
    
    private static void usage() throws UnsupportedEncodingException, MalformedURLException {
        System.err.println(
                new StringBuilder("usage: ")
                .append(new java.io.File(Utility.jar(Start.class).getFile()).getName())
                .append(" -p [port]")
                .append(" -c [channel]").toString());
        System.exit(1);
    }

    private static void error(String msg) {
        System.err.println(msg);
        System.exit(1);
    }
    
}
