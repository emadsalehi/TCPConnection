package utils;

import utils.Constants;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;

public class SenderThread extends Thread {

    private final DatagramSocket socket;
    private final InetAddress ip;
    private final byte[] segment;
    private final int destPort;
    private int timeout;
    private final double dropRate;
    private final double corruptRate;
    private final long sequenceNumber;
    private boolean isDone = false;
    private boolean isTimedOut = false;

    public SenderThread(DatagramSocket socket, InetAddress ip, byte[] segment, int destPort, int timeout, double dropRate, double corruptRate, long sequenceNumber) {
        this.socket = socket;
        this.ip = ip;
        this.segment = segment;
        this.destPort = destPort;
        this.timeout = timeout;
        this.dropRate = dropRate;
        this.corruptRate = corruptRate;
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public void run() {
        Random rand = new Random();
        byte[] corruptSegment = new byte[segment.length];
        corruptSegment[0] = (byte) (10 + segment[0]);
        try {
            DatagramPacket packet = new DatagramPacket(segment, segment.length, ip, destPort);
            boolean isFirstTime = true;
            while (!isDone) {
                if (!isFirstTime)
                    isTimedOut = true;
                double randomNumber = rand.nextDouble();
                if (randomNumber >= dropRate + corruptRate) {
                    System.out.println("Packet " + sequenceNumber + " has been sent.");
                    packet.setData(segment);
                    socket.send(packet);
                } else if (randomNumber < dropRate) {
                    System.out.println("Packet " + sequenceNumber + " has dropped.");
                } else if (randomNumber <= corruptRate + dropRate) {
                    System.out.println("Packet " + sequenceNumber + " has corrupted.");
                    packet.setData(corruptSegment);
                    socket.send(packet);
                }
                isFirstTime = false;
                if (isDone)
                    break;
                sleep(timeout);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void setDone(boolean done) {
        isDone = done;
    }

    public boolean isTimedOut() {
        boolean temp = isTimedOut;
        isTimedOut = false;
        return temp;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }
}
