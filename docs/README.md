# IEEE 802.15.4 Packet Sniffer for BitCatcher firmware 

This repository contains a lightweight Java-based command-line tool able to speak to BitCatcher-driven devices for IEEE 802.15.4 network and protocol analysis.

## Introduction

Bitsniff has been realized by the intense use of USB sniffers and Java decompilers, and probably most of the magic hidden in the proprietary firmware is exposed in the implementation of Bitsniff.

### Supported hardware

* [Atmel RZUSBSTICK](http://www.microchip.com/developmenttools/productdetails.aspx?partno=atavrrzusbstick)
* [Dresden Elektronik deRFusb](https://www.dresden-elektronik.de/funktechnik/products/usb-radio-sticks/?L=1)

### Supported platforms

* GNU/Linux: amd64, i386, arm (Raspberry Pi)
* Windows: amd64, i386

### BitCatcher firmware

The BitCatcher firmware was made by [Luxoft](https://digital.luxoft.com/bitcatcher/) as part of the BitCatcher tooling for network and protocol analysis. Firmware binaries for devices stated above are included in the Luxoft BitCatcher ZigBee Network Analyze tool, which is available for download on [Dropbox](https://www.dropbox.com/s/rj4x9s1f0z9iiax/Sniffer_1.0.1_public.zip).

## Application

In the examples shown below, a USB port that a device is attached to is passed through the argument of **-p**, while **-c** provides the initial IEEE 802.15.4 channel to be monitored.

### Packet capturing

Presumably, employing Bitsniff as sniffer on a preset channel for just-in-time inspection of the conducted traffic is the most common use case. All captured packets on the monitored channel are piped to stdout, thus they will normally pop up in the current terminal.

```bash
$ java -jar bitsniff.jar -p /dev/ttyACM0 -c 26
```

Of course, the concept of piping makes creating log files incredibly easy.

```bash
$ java -jar bitsniff.jar -p /dev/ttyACM0 -c 11 > bitsniff.log
```

To run Bitsniff in the background just for capturing traffic on a preset channel, in a bash nohup is used to ignore the signal HUP (hangup) when its parent terminal is closed.

```bash
$ nohup java -jar bitsniff.jar -p /dev/ttyACM0 -c 26 1> bitsniff.out 2> bitsniff.err &
```

In the output, the first column exhibits the channel on which the packet was captured. The second column indicates the epoch timestamp of the computer's clock in milliseconds at the moment of starting the processing of a received packet. In the third column, the elapsed time of the device's clock since the last receipt is indicated in microseconds, which is very accurate in terms of precision. This comes in handy when receiving a lot of data in the time domain from a few milliseconds to hundreds of microseconds. The very first byte of the captured packet can be seen in the fourth column. All packets having bad field control sequences (FCS) shown by the two bytes on the far right are starred.

```no-highlight
26 1515938245825       0 68 65 6c 6c 6f 2c 20 77 6f 72 6c 64 2e 09 9c
26 1515938248698 2873201 68 65 6c 6d 6f 2c 20 77 6f 72 6c 64 2e 09 9c *
26 1515938250778 2080103 68 65 6c 6c 6f 2c 20 77 6f 72 6c 64 2e 09 9c
26 1515938257186 6407763 68 65 6c 6c 6f 2c 20 77 6f 72 6c 64 2e 09 9c
```

The monitored channel can be changed during runtime. For this purpose, the channel number (an integer value) has to be piped to stdin. In the simplest case, this can be done just by typing a valid channel number and pressing enter in the terminal where Bitsniff is running.

```bash
$ java -jar bitsniff.jar -p /dev/ttyACM0 -c 19
{device={type=RZ,channel=19,serial={port=/dev/ttyACM0,settings={baudate=460800,databits=8,stopbits=1,parity=0},flowcontrol=0}}}
20
{channel={time=1516529573971,value=20}}
```

### Message sending

The BitCatcher firmware limits the sending of packets to a maximum of 13 bytes, therefore any string consisting of 13 characters, e.g. "Hello, World.", or six 16-bit integers plus one byte, is the highest of highs for the payload. A message starts with a channel for transmission (a decimal value, does not need to be identical to the monitored channel) followed by one blank space and some hexified bytes for payload. The FCS is calculated and appended to the payload on the device through a 16-bit CRC-CCITT routine. All messages have to be piped to stdin. The channel for transmission remains on the device, thus it becomes the new monitoring channel.

```bash
$ java -jar bitsniff.jar -p /dev/ttyACM1 -c 21
{device={type=DE,channel=21,serial={port=/dev/ttyACM1,settings={baudate=460800,databits=8,stopbits=1,parity=0},flowcontrol=0}}}
26 68656c6c6f2c20776f726c642e
{message={time=1516529575972,channel=26,value=68656c6c6f2c20776f726c642e}}
```

## Interprocess communication

As previously addressed, pipes are applied for IPC to make sure everything is lightweight, reliable and fast - no further stack or protocol can hamper the communication between bitsniff and any other process. This is particularly important for applications with real-time constraints. Bitsniff makes use of three pipes:

* **stdout** to output captured packets for display or further processing
* **stdin** for message input and change of the monitored channel at runtime
* **stderr** to show user messages and control feedback

To show how Bitsniff can be integrated into an application via IPC two examples are listed below. Those examples can be adapted to a huge variety of programming languages.

### Bash

All standard pipes are going to be detached running a process as daemon in a shell. To cure the problem of loosing pipes, output piped to stdout and stderr can be redirected to files. To stay in touch with input while running bitsniff in background, a named pipe is needed acccording to the following example:

```bash
$ mkfifo bitsniff.in  # create a named pipe
$ tail -f bitsniff.in | java -jar bitsniff.jar -p /dev/ttyACM0 -c 26 1> bitsniff.out 2> bitsniff.err &
$ bitsniff_pid=${!}  # keep process id of bitsniff
$ echo "20" > "/proc/${bitsniff_pid}/fd/0"  # change monitoring to channel 20
$ echo "26 68656c6c6f2c20776f726c642e" > "/proc/${bitsniff_pid}/fd/0"  # send message on channel 26
```

### Python

In Python the subprocess module can take care about everything with regard to piping.

```python
import subprocess, time
p = subprocess.Popen(["java", "-jar", "bitsniff.jar", "-p", "/dev/ttyACM0", "-c", "26"], stdin=subprocess.PIPE)
time.sleep(2)
p.stdin.write("20\n")  # change monitoring to channel 20
time.sleep(2)
p.stdin.write("26 68656c6c6f2c20776f726c642e\n")  # send message on channel 26
time.sleep(2)
p.kill()
```

Python 3 needs an array of bytes instead of a string for piping to stdin. Beyond that stdin needs to be flushed most times.

## Building

To get a runnable JAR file, simply run in the directory that contains the **build.xml**:

```bash
$ ant
```
To get started immediately, download the binary of Bitsniff available on the [release page](https://github.com/m6c7l/bitsniff/releases).

## Troubleshooting

**RXTX fhs_lock() Error: opening lock file: /var/lock/LCK ... FAILED TO OPEN ... testRead() Lock file failed**

In case of error messages from nrjavaserial due to failing attempts to create lock files in "/run/lock", one option would be to add the user to the "lock" group (on Arch Linux derivates).

```bash
$ sudo usermod -a -G lock $USER
```

However, members of the "lock" group need to have write access to "/run/lock". This can be achieved by changing just one line in "/usr/lib/tmpfiles.d/legacy.conf", which becomes active after reboot.

```
# d /run/lock 0755 root root -
d /run/lock 0775 root lock -
```

**no such port: /dev/tty ...**

This error message pops up due to missing permissions for accessing serial ports. This error will vanish after assigning the user to "uucp" on Arch-based systems or "dialout" on Debian-based systems and a reboot.

```bash
$ sudo usermod -a -G uucp $USER
```

## Credits

* NeuronRobotics -- [nrjavaserial - A Java Serial Port system (fork of the RXTX project)](https://github.com/NeuronRobotics/nrjavaserial)
* RXTX -- [rxtx - a Java cross platform wrapper library for the serial port](http://rxtx.qbang.org/wiki)
* George Oikonomou -- [sensniff - Contiki Live Traffic Capture and Sniffer](https://github.com/g-oikonomou/sensniff)
