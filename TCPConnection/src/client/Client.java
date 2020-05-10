package client;

import utils.Constants;
import utils.SenderThread;
import utils.TCPSegment;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.util.*;

public class Client {

    private static final int SOURCE_PORT = Constants.SENDER_PORT;
    private static final int DEST_PORT = Constants.RECEIVER_PORT;
    private static final int MSS = Constants.MSS;
    private static final double DROP_RATE = Constants.DROP_RATE;
    private static final double CORRUPT_RATE = Constants.CORRUPT_RATE;
    private static final String PATH = "image.jpg";

    public static void main(String[] args) {
        int timeout = 1;
        int estimatedRTT = 1;
        int devRTT = 0;
        int windowSize = MSS;
        int ssthreshold = 8 * MSS;
        long sequenceNumber = 0;
        long receiveTime = 0;
        File imageFile = new File(PATH);
        ClientState clientState = ClientState.CLOSED;
        try {
            byte[] image = Files.readAllBytes(imageFile.toPath());
            DatagramSocket socket = new DatagramSocket(SOURCE_PORT);
            InetAddress ip = InetAddress.getLocalHost();
            byte[] receive = new byte[MSS];
            DatagramPacket receivePacket;
            TCPSegment receiveSegment;
            TCPSegment sendSegment;
            SenderThread senderThread = null;
            ImageSenderThread imageSenderThread = null;
            Map<Long, SenderThread> threadMap = new HashMap<>();
            Map<Long, Long> sendTimeMap = new HashMap<>();
            List<Long> sentSequenceNumbers = new ArrayList<>();
            while (true) {
                if (clientState == ClientState.CLOSED) {
                    System.out.println(image.length);
                    byte[] send = new byte[Constants.DATA_SIZE];
                    int length = (image.length / Constants.DATA_SIZE + 2) * Constants.DATA_SIZE;
                    send[0] = (byte) (length >> 16);
                    send[1] = (byte) (length >> 8);
                    send[2] = (byte) length;
                    sendSegment = new TCPSegment(SOURCE_PORT, DEST_PORT, sequenceNumber, 0, (byte) 2, send);
                    senderThread = new SenderThread(socket, ip, sendSegment.getByteArray(), DEST_PORT, timeout, DROP_RATE, CORRUPT_RATE, sequenceNumber);
                    sequenceNumber += MSS;
                    sentSequenceNumbers.add(sequenceNumber);
                    threadMap.put(sequenceNumber, senderThread);
                    senderThread.start();
                    clientState = ClientState.SYN_SENT;
                } else if (clientState == ClientState.SYN_SENT) {
                    receivePacket = new DatagramPacket(receive, receive.length);
                    socket.receive(receivePacket);
                    receiveSegment = new TCPSegment(receive);
                    if (receiveSegment.getCheckSum() == receiveSegment.calculateCheckSum() && receiveSegment.getAck() && receiveSegment.getSyn()
                            && receiveSegment.getAcknowledgmentNumber() == sentSequenceNumbers.get(0)) {
                        threadMap.get(receiveSegment.getAcknowledgmentNumber()).setDone(true);
                        threadMap.remove(receiveSegment.getAcknowledgmentNumber());
                        sentSequenceNumbers.remove(0);
                        sendSegment = new TCPSegment(SOURCE_PORT, DEST_PORT, 0, 0, (byte) 16, null);
                        DatagramPacket sendPacket = new DatagramPacket(sendSegment.getByteArray(), sendSegment.getByteArray().length, ip, DEST_PORT);
                        socket.send(sendPacket);
                        System.out.println("Connection stablished.");
                        imageSenderThread = new ImageSenderThread(sentSequenceNumbers, threadMap, sendTimeMap, image
                                , sequenceNumber, socket, timeout, windowSize, ip);
                        imageSenderThread.start();
                        clientState = ClientState.ESTAB;
                    }
                } else if (clientState == ClientState.ESTAB) {

                    receivePacket = new DatagramPacket(receive, receive.length);
                    socket.receive(receivePacket);
                    receiveSegment = new TCPSegment(receive);
                    if (receiveSegment.getCheckSum() == receiveSegment.calculateCheckSum() && sentSequenceNumbers.contains(receiveSegment.getAcknowledgmentNumber())
                     && receiveSegment.getAck()) {
                        System.out.println("Packet " + (receiveSegment.getAcknowledgmentNumber() - MSS) + " acked.");
                        synchronized (threadMap) {
                            threadMap.get(receiveSegment.getAcknowledgmentNumber()).setDone(true);
                            threadMap.remove(receiveSegment.getAcknowledgmentNumber());
                        }
                        if (windowSize < ssthreshold) {
                            windowSize *= 2;
                        } else {
                            windowSize += MSS;
                        }
                        long sendTime = sendTimeMap.get(receiveSegment.getAcknowledgmentNumber());
                        long sampleRTT = System.currentTimeMillis() - sendTime;
                        estimatedRTT = (int) ((1 - Constants.ALPHA) * estimatedRTT + Constants.ALPHA * sampleRTT);
                        devRTT = (int) ((1 - Constants.BETA) * devRTT + Constants.BETA * Math.abs(sampleRTT - estimatedRTT));
                        timeout = estimatedRTT + 4 * devRTT;
                        imageSenderThread.setTimeout(timeout);
                        synchronized (threadMap) {
                            for (Map.Entry<Long, SenderThread> entry : threadMap.entrySet()) {
                                entry.getValue().setTimeout(timeout);
                                if (entry.getValue().isTimedOut()) {
                                    ssthreshold = windowSize / 2;
                                    windowSize = MSS;
                                }
                            }
                        }
                        imageSenderThread.setWindowSize(windowSize);
                        synchronized (sentSequenceNumbers) {
                            sentSequenceNumbers.remove(receiveSegment.getAcknowledgmentNumber());
                        }
                        sendTimeMap.remove(receiveSegment.getAcknowledgmentNumber());
                    }
                    if (imageSenderThread.isDone() && sentSequenceNumbers.size() == 0) {
                        clientState = ClientState.FIN_WAIT_1;
                        sequenceNumber = imageSenderThread.getSequenceNumber();
                    }
                } else if (clientState == ClientState.FIN_WAIT_1) {
                    sendSegment = new TCPSegment(SOURCE_PORT, DEST_PORT, sequenceNumber, 0, (byte) 1, null);
                    senderThread = new SenderThread(socket, ip, sendSegment.getByteArray(), DEST_PORT, timeout, DROP_RATE, CORRUPT_RATE, sequenceNumber);
                    sequenceNumber += MSS;
                    sentSequenceNumbers.add(sequenceNumber);
                    threadMap.put(sequenceNumber, senderThread);
                    senderThread.start();
                    clientState = ClientState.FIN_WAIT_2;
                } else if (clientState == ClientState.FIN_WAIT_2) {
                    receivePacket = new DatagramPacket(receive, receive.length);
                    socket.receive(receivePacket);
                    receiveSegment = new TCPSegment(receive);
                    if (receiveSegment.getCheckSum() == receiveSegment.calculateCheckSum() && receiveSegment.getFin() && receiveSegment.getAcknowledgmentNumber() == sentSequenceNumbers.get(0)) {
                        threadMap.get(receiveSegment.getAcknowledgmentNumber()).setDone(true);
                        threadMap.remove(receiveSegment.getAcknowledgmentNumber());
                        sentSequenceNumbers.remove(0);
                        clientState = ClientState.TIMED_WAIT;
                    }
                } else {
                    receivePacket = new DatagramPacket(receive, receive.length);
                    socket.receive(receivePacket);
                    receiveSegment = new TCPSegment(receive);
                    if (receiveSegment.getCheckSum() == receiveSegment.calculateCheckSum() && receiveSegment.getFin()) {
                        sendSegment = new TCPSegment(SOURCE_PORT, DEST_PORT, 0, 0, (byte) 16, null);
                        DatagramPacket packet = new DatagramPacket(sendSegment.getByteArray(), sendSegment.getByteArray().length, ip, DEST_PORT);
                        socket.send(packet);
                    }
                    break;
                }
            }
            System.out.println("Connection closed.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
