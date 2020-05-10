package utils;

public class TCPSegment {

    private final int HEADER_SIZE = Constants.HEADER_SIZE;
    private final int DATA_SIZE = Constants.DATA_SIZE;
    private final int MSS = Constants.MSS;

    int sourcePort;
    int destinationPort;
    long sequenceNumber;
    long acknowledgmentNumber;
    int headerLength;
    int reservedBits;
    byte flags;
    int windowSize;
    int checkSum;
    int urgentPoint;
    byte[] data;

    public TCPSegment(int sourcePort, int destinationPort, long sequenceNumber, long acknowledgmentNumber,
                      int headerLength, int reservedBits, byte flags, int windowSize, int checkSum, int urgentPoint, byte[] data) {
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.sequenceNumber = sequenceNumber;
        this.acknowledgmentNumber = acknowledgmentNumber;
        this.headerLength = headerLength;
        this.reservedBits = reservedBits;
        this.flags = flags;
        this.windowSize = windowSize;
        this.checkSum = checkSum;
        this.urgentPoint = urgentPoint;
        this.data = fillDataField(data);
        this.checkSum = calculateCheckSum();
    }

    public TCPSegment(int sourcePort, int destinationPort, long sequenceNumber, long acknowledgmentNumber, byte flags, byte[] data) {
        this.sourcePort = sourcePort;
        this.destinationPort = destinationPort;
        this.sequenceNumber = sequenceNumber;
        this.acknowledgmentNumber = acknowledgmentNumber;
        this.headerLength = HEADER_SIZE;
        this.reservedBits = 0;
        this.flags = flags;
        this.windowSize = 0;
        this.urgentPoint = 0;
        this.data = fillDataField(data);
        this.checkSum = calculateCheckSum();
    }

    public TCPSegment(byte[] segment) {
        sourcePort = ((segment[0] & 0xFF) << 8) + (segment[1] & 0xFF);
        destinationPort = ((segment[2] & 0xFF) << 8) + (segment[3] & 0xFF);
        sequenceNumber = ((long)(segment[4] & 0xFF) << 24) + ((long)(segment[5] & 0xFF) << 16) + ((long)(segment[6] & 0xFF) << 8) + ((long)(segment[7] & 0xFF));
        acknowledgmentNumber = ((long)(segment[8] & 0xFF) << 24) + ((long)(segment[9] & 0xFF) << 16) + ((long)(segment[10] & 0xFF) << 8) + ((long)(segment[11] & 0xFF));
        headerLength = (segment[12] & 0xFF) >> 4;
        reservedBits = (((segment[12] & 0xFF) - (((segment[12] & 0xFF) >> 4) << 4)) << 2) + ((segment[13] & 0xFF) >> 6);
        flags = (byte) ((segment[13] & 0xFF) - (((segment[13] & 0xFF) >> 6) << 6));
        windowSize = ((segment[14] & 0xFF) << 8) + (segment[15] & 0xFF);
        checkSum = ((segment[16] & 0xFF) << 8) + (segment[17] & 0xFF);
        urgentPoint = ((segment[18] & 0xFF) << 8) + (segment[19] & 0xFF);
        data = new byte[DATA_SIZE];
        System.arraycopy(segment, 20, data, 0, DATA_SIZE);
    }

    public byte[] getByteArray() {
        byte[] segment = new byte[HEADER_SIZE + DATA_SIZE];
        segment[0] = (byte) (sourcePort >> 8);
        segment[1] = (byte) (sourcePort);
        segment[2] = (byte) (destinationPort >> 8);
        segment[3] = (byte) (destinationPort);
        segment[4] = (byte) (sequenceNumber >> 24);
        segment[5] = (byte) (sequenceNumber >> 16);
        segment[6] = (byte) (sequenceNumber >> 8);
        segment[7] = (byte) (sequenceNumber);
        segment[8] = (byte) (acknowledgmentNumber >> 24);
        segment[9] = (byte) (acknowledgmentNumber >> 16);
        segment[10] = (byte) (acknowledgmentNumber >> 8);
        segment[11] = (byte) (acknowledgmentNumber);
        segment[12] = (byte) ((headerLength << 4) + (reservedBits >> 2));
        segment[13] = (byte) (((reservedBits - ((reservedBits >> 2) << 2)) << 6) + flags);
        segment[14] = (byte) (windowSize >> 8);
        segment[15] = (byte) (windowSize);
        segment[16] = (byte) (checkSum >> 8);
        segment[17] = (byte) (checkSum);
        segment[18] = (byte) (urgentPoint >> 8);
        segment[19] = (byte) (urgentPoint);
        System.arraycopy(data, 0, segment, 20, DATA_SIZE);
        return segment;
    }

    byte[] fillDataField (byte[] data) {
        byte[] outData = new byte[DATA_SIZE];
        if (data == null)
            data = new byte[0];
        System.arraycopy(data, 0, outData, 0, data.length);
        return outData;
    }

    public int calculateCheckSum() {
        byte[] segment = this.getByteArray();
        int sum = 0;
        int i = 0;
        for (i = 0; i < MSS - 1; i+=2) {
            if (i == 16) continue;
            sum += ((int)segment[i] << 8) + (int)segment[i + 1];
            sum = (sum & 65535) + (sum >> 16);
        }
        if (i == MSS - 1) {
            sum += ((int)segment[i] << 8);
            sum = (sum & 65535) + (sum >> 16);
        }
        return sum;
    }

    public boolean getSyn() {
        return (flags & 2) == 2;
    }

    public boolean getFin() {
        return (flags & 1) == 1;
    }

    public boolean getAck() {
        return (flags & 16) == 16;
    }

    public int getHEADER_SIZE() {
        return HEADER_SIZE;
    }

    public int getDATA_SIZE() {
        return DATA_SIZE;
    }

    public long getSequenceNumber() {
        return sequenceNumber;
    }

    public long getAcknowledgmentNumber() {
        return acknowledgmentNumber;
    }

    public byte getFlags() {
        return flags;
    }

    public int getCheckSum() {
        return checkSum;
    }

    public byte[] getData() {
        return data;
    }
}
