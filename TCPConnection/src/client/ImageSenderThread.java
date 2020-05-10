package client;

import utils.Constants;
import utils.SenderThread;
import utils.TCPSegment;

import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

public class ImageSenderThread extends Thread {

    private List<Long> sentSequenceNumbers;
    private Map<Long, SenderThread> threadMap;
    private Map<Long, Long> sendTimeMap;
    private final byte[] image;
    private long sequenceNumber;
    private int timeout;
    private int windowSize;
    private final DatagramSocket socket;
    private final InetAddress ip;
    private boolean done = false;

    public ImageSenderThread (List<Long> sentSequenceNumbers, Map<Long, SenderThread> threadMap, Map<Long, Long> sendTimeMap
            , byte[] image, long sequenceNumber, DatagramSocket socket, int timeout, int windowSize, InetAddress ip) {
        this.sentSequenceNumbers = sentSequenceNumbers;
        this.threadMap = threadMap;
        this.sendTimeMap = sendTimeMap;
        this.image = image;
        this.sequenceNumber = sequenceNumber;
        this.timeout = timeout;
        this.windowSize = windowSize;
        this.socket = socket;
        this.ip = ip;
    }

    @Override
    public void run() {
        TCPSegment sendSegment;
        SenderThread senderThread;
        byte[] data;
        int imageIndex = 0;
        int MSS = Constants.MSS;
        while (!done) {
            synchronized (sentSequenceNumbers) {
                if ((sentSequenceNumbers.size() == 0 && windowSize >= MSS)
                        || (sequenceNumber - sentSequenceNumbers.get(0) + 2 * MSS <= windowSize)) {
                    if (imageIndex >= image.length){
                        done = true;
                        break;
                    }
                    data = new byte[Constants.DATA_SIZE];
                    if (imageIndex + Constants.DATA_SIZE < image.length) {
                        System.arraycopy(image, imageIndex, data, 0, Constants.DATA_SIZE);
                    } else {
                        System.arraycopy(image, imageIndex, data, 0, image.length - imageIndex);
                    }
                    imageIndex += Constants.DATA_SIZE;
                    int DEST_PORT = Constants.RECEIVER_PORT;
                    int SOURCE_PORT = Constants.SENDER_PORT;
                    double DROP_RATE = Constants.DROP_RATE;
                    double CORRUPT_RATE = Constants.CORRUPT_RATE;
                    sendSegment = new TCPSegment(SOURCE_PORT, DEST_PORT, sequenceNumber, 0, (byte) 16, data);
                    senderThread = new SenderThread(socket, ip, sendSegment.getByteArray(), DEST_PORT, timeout, DROP_RATE, CORRUPT_RATE, sequenceNumber);
                    sequenceNumber += MSS;
                    sentSequenceNumbers.add(sequenceNumber);
                    synchronized (threadMap) {
                        threadMap.put(sequenceNumber, senderThread);
                    }
                    sendTimeMap.put(sequenceNumber, System.currentTimeMillis());
                    senderThread.start();
                }
            }
        }
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    public boolean isDone() {
        return done;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }
}