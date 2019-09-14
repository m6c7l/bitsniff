/*
 * Copyright (c) 2018, Manfred Constapel
 * This file is licensed under the terms of the MIT license.
 */

package de.m6c7l.sniffer.app;

import gnu.io.CommPort;
import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.WritableByteChannel;
import java.util.Arrays;
import java.util.TooManyListenersException;

public class Device implements FrameListener {

    final public static char LF = '\n';
    final public static char CR = '\r';

    // commands and their corresponding replies
    final public static byte RESET_DEVICE = 0x01;
    final public static byte REPLY_RESET_DEVICE = 0x02;
    final public static byte AQUIRE_STATUS = 0x05;
    final public static byte REPLY_AQUIRE_STATUS = 0x06;
    final public static byte SET_CHANNEL_CHANNELPAGE = 0x09;
    final public static byte REPLY_SET_CHANNEL_CHANNELPAGE = 0x0a;
    final public static byte ENABLE_CAPTURE = 0x0b;
    final public static byte REPLY_ENABLE_CAPTURE = 0x0c;
    final public static byte DISABLE_CAPTURE = 0x0d;
    final public static byte REPLY_DISABLE_CAPTURE = 0x0e;
    final public static byte SEND_MESSAGE = 0x11;
    final public static byte REPLY_SEND_MESSAGE = 0x12;

    // data packet identifiers
    final public static byte MESSAGE_START = 0x2a;
    final public static byte DATA_CAPTURED = 0x50;
    
    // supported platforms, identified by one byte of status response     
    private enum Type {
        
      DE((byte)0x01, "DE"),
      RZ((byte)0x02, "RZ");

      private byte id;
      private String name;

      private Type(byte id, String name) {
        this.id = id;
        this.name = name;
      }
      
      public String toString() {
          return this.name;
      }
      
    }

    // timeframe to be less than 16 s due to 3 bytes only for timing [us] @ RZ
    final private static int  PRECISION_TIMEFRAME = 10; // [s]
    final private static long PRECISION_BASE_uS = (int)Math.log10(PRECISION_TIMEFRAME) + 6;
    final private static long PRECISION_FACTOR_uS = (int)Math.pow(10, PRECISION_BASE_uS + 1);

    private SerialPort serial;
    
    private PortReader poread;
    private PipeReader piread;
    private Type type;
    
    private Integer channel;
    private boolean enabled = false;
    private InputStream poin;
    private OutputStream poout;
    
    private byte[] response;
    private long lasttiming = 0;
    private long laststamp = 0;
    
    public Device() {
        super();
    }

    // is device in capturing mode?
    public boolean enabled() {
        return this.enabled;
    }

    // perform data frame content processing, called by Reader.process()
    public void receive(byte id, byte[] frame) {
        
        this.response = frame;
        
        if (id == DATA_CAPTURED) {
            
            long stamp = System.currentTimeMillis();
            
            // header of data frame content
            byte[] hdr = Arrays.copyOfRange(frame, 0, 8);

            // get channel
            int rxch = Integer.valueOf(FrameUtility.hex(new byte[] {hdr[5]}).trim(), 16);

            // pipe channel
            System.out.print(String.format("%2d ", rxch));
            
            // pipe timestamp [ms] of hosts clock
            System.out.print(String.format("%13d ", stamp));  

            // extract timing [us] of capturing device
            long timing = 0;
            if (type == Type.DE) { 
                for (int i=0; i<4; i++) { // 4 bytes for DE devices
                    timing += ((long)(hdr[i] & 0xff) << (8*i));
                }
            } else if (type == Type.RZ) { 
                for (int i=0; i<3; i++) { // 3 bytes for RZ devices
                    timing += ((long)(hdr[i + 1] & 0xff) << (8*i));
                }                
            }
                        
            long dt = 0;

            // fast incoming packets detected, take care about overflows in timing            
            if (laststamp > stamp - (PRECISION_TIMEFRAME * 1000)) {                
                if (type == Type.DE) {
                    dt = 0xffffffffL;
                } else if (type == Type.RZ) {
                    dt = 0xffffffL;
                }
                dt = (timing > lasttiming ? timing - lasttiming : dt - lasttiming + timing + 1) % PRECISION_FACTOR_uS;                
            }

            // high precision delta time [us]
            System.out.print(String.format("%" + PRECISION_BASE_uS + "d ", dt));                                            

            // keep track of timing and timestamp for estimation of deltas
            lasttiming = timing; 
            laststamp = stamp;
            
            // payload of data frame content (its the 802.15.4 frame except the fcs)
            byte[] cnt = Arrays.copyOfRange(frame, 8, frame.length - 2); 
            System.out.print(FrameUtility.hex(cnt));

            // fcs of 802.15.4 frame
            byte[] fcs = Arrays.copyOfRange(frame, frame.length - 2, frame.length); // fcs
            System.out.print(FrameUtility.hex(fcs).trim());

            // check fcs (16-bit crc-ccitt, bytes were received in little endian)
            if (!Arrays.equals(FrameUtility.fcs(cnt), fcs)) {
                // bad fcs
                System.out.print(" *");                  
            }            
            
            System.out.print(Device.LF);

        } else if (id == REPLY_SET_CHANNEL_CHANNELPAGE) {
            
            // 0xfa in response indicates a non-supported channel
            if ((frame[0] & 0xff) != 0xfa) {
                // if channel set successfully, keep it
                this.channel = (int)frame[0];               
            }
            
        } else if (id == REPLY_AQUIRE_STATUS) {

            // some byte in response of status request indicates type of connected device
            if ((frame[0] & 0xff) == Type.DE.id) {
                this.type = Type.DE;               
            } else if ((frame[0] & 0xff) == Type.RZ.id) {
                this.type = Type.RZ;                               
            }

        } else if (id == REPLY_SEND_MESSAGE) {

        }
        
    }

    // get status info, e.g. device type
    public boolean status() {
        try {
            this.write(FrameUtility.prepare(AQUIRE_STATUS));
        } catch (Exception e) {}
        return this.response != null;
    }

    // set capturing on/off
    public boolean enable(boolean value) {
        try {
            if (value) {
                this.write(FrameUtility.prepare(ENABLE_CAPTURE));
            } else {
                this.write(FrameUtility.prepare(DISABLE_CAPTURE));
            }
            if (this.response != null) {
                this.enabled = value;
            }
        } catch (Exception e) {}
        return this.enabled;
    }

    public boolean send(String channel, String msg) {
        Integer ch = FrameUtility.toint(channel);
        if (ch != null) return this.send(ch, msg);
        return false;
    }

    public boolean send(int channel, String msg) {
        return this.send(SEND_MESSAGE, channel, msg.getBytes());
    }

    public boolean send(String channel, byte[] msg) {
        Integer ch = FrameUtility.toint(channel);
        if (ch != null) return this.send(ch, msg);
        return false;
    }
    
    public boolean send(int channel, byte[] msg) {
        if (this.send(SEND_MESSAGE, channel, msg)) {
            if (this.response != null) {
                // device will rest on the channel for monitoring
                this.channel = channel;                
            }
            return true;
        }
        return false;
    }

    // sends exactly 13 bytes (investigated firmware seems to limit frame length to 13 bytes for outgoing messages)
    private boolean send(byte cmd, int channel, byte[] msg) {
        ByteBuffer temp = ByteBuffer.allocate(4 + 13);
        temp.put((byte)255);
        temp.put((byte)channel);
        temp.put((byte)255);
        temp.put((byte)255);
        int len = Math.min(13, msg.length);
        for (int i=0; i<len; i++) temp.put(msg[i]);            
        for (int i=len; i<13; i++) temp.put((byte)0);            
        try {
            return this.write(FrameUtility.prepare(cmd, temp.array())) > 0;
        } catch (IOException e) {}
        return false;
    }

    // get value of channel
    public Integer channel() {
        return this.channel;
    }

    // set channel for sniffing
    public boolean channel(String channel) {
        Integer val = FrameUtility.toint(channel);
        if (val == null) return false; 
        return this.channel(val);
    }

    public boolean channel(int channel) {
        return this.channel(channel, 0);
    }

    private boolean channel(int channel, int page) {
        // channels on page 0
        //  0   : 868 MHz band
        //  1-10: 915 MHz band
        // 11-26: 2,4 GHz band
        boolean b = false;
        if (page==0) { 
            b = (channel >= 0) && (channel <= 26);
        } else if ((page == 1) || (page == 2)) {
            b = (channel >= 0) && (channel <= 10); 
        }
        if (b) {
            try {
                byte[] cnt = new byte[] { (byte) channel, (byte) page };
                this.write(FrameUtility.prepare(SET_CHANNEL_CHANNELPAGE, cnt));
                return this.response != null;
            } catch (Exception e) {}
        }
        return false;
    }

    // reset device
    public boolean reset() {
        try {
            if (this.connect(null)) {
                for (int i = 0; i < 2; i++) {
                    this.write(FrameUtility.prepare(RESET_DEVICE));
                    Thread.sleep(75L);
                }
                return this.response != null;
            }
        } catch (NoSuchPortException | PortInUseException |
                 UnsupportedCommOperationException | IOException |
                 InterruptedException e) {}
        return false;
    }

    private int write(ByteBuffer buf) throws IOException {
        if (poout == null) return -1;
        this.response = null;
        WritableByteChannel channel = Channels.newChannel(poout);
        int len = 0;
        // System.out.println(System.currentTimeMillis() + " " + Utility.hex(buf.array()));
        while (buf.hasRemaining()) {
            len += channel.write(buf);
            try {
                Thread.sleep(25L);
            } catch (InterruptedException ignored) {}
        }
        return len;
    }

    // is device connected?
    public boolean connect() {
        return this.serial != null;
    }

    // connect to device
    public boolean connect(String port)
            throws NoSuchPortException, PortInUseException, UnsupportedCommOperationException, IOException {
        if (port != null) {
            CommPortIdentifier portIdentifier = CommPortIdentifier.getPortIdentifier(port);
            if (portIdentifier.isCurrentlyOwned()) {
                throw new PortInUseException();
            } else {
                CommPort commPort = portIdentifier.open(this.getClass().getName(), 5000);
                if (commPort instanceof SerialPort) {
                    serial = (SerialPort) commPort;
                    serial.setSerialPortParams(460800, SerialPort.DATABITS_8, SerialPort.STOPBITS_1,
                            SerialPort.PARITY_NONE);
                    serial.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
                    serial.setDTR(false);
                    poin = serial.getInputStream();
                    poout = serial.getOutputStream();
                    poread = new PortReader(poin);
                    poread.addListener(this);
                    try {
                        serial.addEventListener(poread);
                        serial.notifyOnDataAvailable(true);
                    } catch (TooManyListenersException e) {
                        throw new IOException();
                    }
                    // accept input via stdin
                    this.piread = new PipeReader(this, System.in);
                    this.piread.start();
                } else {
                    throw new NoSuchPortException();
                }
            }
        }
        return this.serial != null;
    }

    // disconnect from device
    public void disconnect() throws IOException {
        if (poout != null) {
            this.poout.close();
            this.poout = null;
        }
        if (poin != null) {
            this.poin.close();
            this.poin = null;
        }
        if (serial != null) {
            this.channel = null;
            this.poread.removeListener(this);
            this.poread = null;
            this.serial.notifyOnDataAvailable(false);
            this.serial.removeEventListener();
            this.serial.close();
            this.serial = null;
            this.piread = null;
        }
    }

    public String toString() {
        String s = "{device={type=%s,channel=%s," +
                   "serial={port=%s,settings={baudate=%s,databits=%s,stopbits=%s,parity=%s}," +
                   "flowcontrol=%s}}}";
        if (serial == null)
            return String.format(s, "", "", "", "", "", "", "");
        return String.format(s,
                type!=null ? type : "",
                channel!=null ? channel : "",
                serial.getName(),
                serial.getBaudRate(), serial.getDataBits(),
                serial.getStopBits(),
                serial.getParity(), serial.getFlowControlMode()
               );
    }

}