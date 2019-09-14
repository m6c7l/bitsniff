/*
 * Copyright (c) 2018, Manfred Constapel
 * This file is licensed under the terms of the MIT license.
 */

package de.m6c7l.sniffer.app;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;

public class PortReader implements SerialPortEventListener {

    private List<FrameListener> listeners = new ArrayList<FrameListener>();

    private InputStream in = null;

    public PortReader(InputStream in) {
        this.in = in;
    }

    public void addListener(FrameListener listener) {
        this.listeners.add(listener);
    }

    public void removeListener(FrameListener listener) {
        this.listeners.remove(listener);
    }

    private void process(byte[] buf) {
        if (buf.length < 4)
            return;
        if (buf[0] == Device.MESSAGE_START) {
            // exactly one frame
            if (buf[1] == buf.length - 2) {
                if (FrameUtility.checksum(buf) == buf[buf.length - 1]) {
                    // checksum seems ok
                    for (FrameListener fl : this.listeners) {
                        fl.receive(buf[2], Arrays.copyOfRange(buf, 3, buf.length - 1));
                    }
                }
            // more than one frame
            } else if (buf[1] < buf.length - 2) {
                byte[] fub = buf;
                while (fub.length > 0) {
                    process(Arrays.copyOfRange(fub, 0, fub[1] + 2)); // again, exactly one frame
                    fub = Arrays.copyOfRange(fub, fub[1] + 2, fub.length); // process of subsequent frame
                }
            }
        }
    }

    public void serialEvent(SerialPortEvent event) {
        switch (event.getEventType()) {
        case SerialPortEvent.OUTPUT_BUFFER_EMPTY:
        case SerialPortEvent.BI:
        case SerialPortEvent.OE:
        case SerialPortEvent.FE:
        case SerialPortEvent.PE:
        case SerialPortEvent.CD:
        case SerialPortEvent.CTS:
        case SerialPortEvent.DSR:
        case SerialPortEvent.RI: break;
        case SerialPortEvent.DATA_AVAILABLE:
            ByteBuffer buf = ByteBuffer.allocate(1024);
            try {
                while ((in.available()) > 0) buf.put((byte) in.read());
            } catch (IOException e) {}
            buf.flip();
            byte[] b = new byte[buf.limit()];
            buf.get(b);
            process(b);
            break;
        }
    }

}