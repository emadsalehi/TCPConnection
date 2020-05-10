package server;

import utils.Constants;
import utils.SenderThread;
import utils.TCPSegment;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Random;

public class Server {

    private static final int SOURCE_PORT = Constants.RECEIVER_PORT;
    private static final int DEST_PORT = Constants.SENDER_PORT;
    private static final int MSS = Constants.MSS;
    private static final double DROP_RATE = Constants.DROP_RATE;
    private static final double CORRUPT_RATE = Constants.CORRUPT_RATE;

    public static void main(String[] args) {
        try {
            byte[] image = null;
            long startSequence = 0;
            ServerState serverState = ServerState.LISTEN;
            DatagramSocket socket = new DatagramSocket(SOURCE_PORT);
            byte[] receive = new byte[MSS];
            InetAddress ip = InetAddress.getLocalHost();
            DatagramPacket receivePacket;
            DatagramPacket sendPacket;
            TCPSegment receiveSegment;
            TCPSegment sendSegment;
            SenderThread senderThread = null;
            Random rand = new Random();
            while (true) {
                receivePacket = new DatagramPacket(receive, receive.length);
                socket.receive(receivePacket);
                receiveSegment = new TCPSegment(receive);
                byte[] data = receiveSegment.getData();
                if (serverState == ServerState.LISTEN) {
                    if (receiveSegment.getCheckSum() == receiveSegment.calculateCheckSum() && receiveSegment.getSyn()) {
                        int imageLength = ((data[0] & 0xFF) << 16) + ((data[1] & 0xFF) << 8) + (data[3] & 0xFF);
                        image = new byte[imageLength];
                        startSequence = receiveSegment.getSequenceNumber() + MSS;
                        sendSegment = new TCPSegment(SOURCE_PORT, DEST_PORT, 0, receiveSegment.getSequenceNumber() + MSS, (byte) 18, null);
                        sendPacket = new DatagramPacket(sendSegment.getByteArray(), sendSegment.getByteArray().length, ip, DEST_PORT);
                        socket.send(sendPacket);
                        serverState = ServerState.SYN_RECEIVED;
                    }
                } else if (serverState == ServerState.SYN_RECEIVED) {
                    if (receiveSegment.getCheckSum() == receiveSegment.calculateCheckSum() && receiveSegment.getAck()) {
                        System.out.println("Connection stablished.");
                        serverState = ServerState.ESTAB;
                    }
                    else if (receiveSegment.getCheckSum() == receiveSegment.calculateCheckSum() && receiveSegment.getSyn()) {
                        sendSegment = new TCPSegment(SOURCE_PORT, DEST_PORT, 0, receiveSegment.getSequenceNumber() + MSS, (byte) 18, null);
                        sendPacket = new DatagramPacket(sendSegment.getByteArray(), sendSegment.getByteArray().length, ip, DEST_PORT);
                        socket.send(sendPacket);
                    }
                } else if (serverState == ServerState.ESTAB) {
                    if (receiveSegment.getCheckSum() == receiveSegment.calculateCheckSum() && (receiveSegment.getAck() || receiveSegment.getFin())) {
                        sendSegment = new TCPSegment(SOURCE_PORT, DEST_PORT, 0, receiveSegment.getSequenceNumber() + MSS, (byte) 16, null);
                        if (receiveSegment.getAck()) {
                            int packetIndex = (int) ((receiveSegment.getSequenceNumber() - startSequence) / MSS);
                            System.arraycopy(data, 0, image, packetIndex * Constants.DATA_SIZE, Constants.DATA_SIZE);
                            double randomNumber = rand.nextDouble();
                            sendPacket = new DatagramPacket(sendSegment.getByteArray(), sendSegment.getByteArray().length, ip, DEST_PORT);
                            if (randomNumber < DROP_RATE) {
                                System.out.println("Packet " + receiveSegment.getSequenceNumber() + " has dropped.");
                            } else if (randomNumber < DROP_RATE + CORRUPT_RATE) {
                                System.out.println("Packet " + receiveSegment.getSequenceNumber() + " has corrupted.");
                                byte[] corruptSegment = new byte[sendSegment.getByteArray().length];
                                corruptSegment[0] = (byte) (10 + sendSegment.getByteArray()[0]);
                                sendPacket.setData(corruptSegment);
                                socket.send(sendPacket);
                            } else {
                                System.out.println("Packet " + receiveSegment.getSequenceNumber() + " has been sent.");
                                socket.send(sendPacket);
                            }
                        } else {
                            sendPacket = new DatagramPacket(sendSegment.getByteArray(), sendSegment.getByteArray().length, ip, DEST_PORT);
                            socket.send(sendPacket);
                            sendSegment = new TCPSegment(SOURCE_PORT, DEST_PORT, 0, receiveSegment.getSequenceNumber() + MSS, (byte) 1, null);
                            senderThread = new SenderThread(socket, ip, sendSegment.getByteArray(), DEST_PORT, 1000, DROP_RATE, CORRUPT_RATE, 0);
                            senderThread.start();
                            serverState = ServerState.LAST_ACK;
                        }
                    }
                } else if (serverState == ServerState.LAST_ACK) {
                    if (receiveSegment.getCheckSum() == receiveSegment.calculateCheckSum() && receiveSegment.getAck()) {
                        senderThread.setDone(true);
                        serverState = ServerState.LISTEN;
                        System.out.println("Connection closed.");
                        FileOutputStream outputStream = new FileOutputStream("receivedImage.jpg");
                        try {
                            outputStream.write(image);
                            outputStream.flush();
                        }
                        finally {
                            outputStream.close();
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
