/*
 * Copyright (c) 2018, Manfred Constapel
 * This file is licensed under the terms of the MIT license.
 */

package de.m6c7l.sniffer.app;

public interface FrameListener {

    public void receive(byte id, byte[] frame);

}
