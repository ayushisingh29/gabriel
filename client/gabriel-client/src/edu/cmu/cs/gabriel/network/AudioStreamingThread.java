package edu.cmu.cs.gabriel.network;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

import android.graphics.Bitmap;
import android.hardware.Camera.Parameters;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import edu.cmu.cs.gabriel.token.TokenController;

public class AudioStreamingThread extends Thread {

    private static final String LOG_TAG = "AudioStreaming";

    private boolean isRunning = false;
    private boolean isPing = true;

    // image files for experiments (test and compression)
    private File[] audioFiles = null;
    private File[] audioFilesCompress = null;
    private Bitmap[] audioBitmapsCompress = new Bitmap[30];
    private int audioImageFile = 0;
    private int audioImageFileCompress = 0;
    private int audioFileCompressLength = -1;

    // TCP connection
    private InetAddress remoteIP;
    private int remotePort;
    private Socket tcpSocket = null;
    private DataOutputStream networkWriter = null;
    private DataInputStream networkReader = null;
    private AudioControlThread networkReceiver = null;

    // frame data shared between threads
    private long frameID = 0;
    private byte[] frameBuffer = null;
    private Object frameLock = new Object();

    private Handler networkHander = null;
    private TokenController tokenController = null;

    public AudioStreamingThread(String serverIP, int port, Handler handler,
            TokenController tokenController) {
        isRunning = false;
        this.networkHander = handler;
        this.tokenController = tokenController;

        try {
            remoteIP = InetAddress.getByName(serverIP);
        } catch (UnknownHostException e) {
            Log.e(LOG_TAG, "unknown host: " + e.getMessage());
        }

        remotePort = port;
    }

    /**
     * @return a String representing the received message from @reader
     */
    private String receiveMsg(DataInputStream reader) throws IOException {
        int retLength = reader.readInt();
        byte[] recvByte = new byte[retLength];
        int readSize = 0;
        while (readSize < retLength) {
            int ret = reader.read(recvByte, readSize, retLength - readSize);
            if (ret <= 0) {
                break;
            }
            readSize += ret;
        }
        String receivedString = new String(recvByte);
        return receivedString;
    }

    public void run() {
        this.isRunning = true;
        Log.i(LOG_TAG, "Streaming thread running");

        // Init of TCP connection
        try {
            tcpSocket = new Socket();
            tcpSocket.setTcpNoDelay(true);
            tcpSocket.connect(new InetSocketAddress(remoteIP, remotePort),
                    5 * 1000);
            networkWriter = new DataOutputStream(tcpSocket.getOutputStream());
            networkReader = new DataInputStream(tcpSocket.getInputStream());

        } catch (IOException e) {
            Log.e(LOG_TAG, "Error in initializing network socket: " + e);
            this.notifyError(e.getMessage());
            this.isRunning = false;
            return;
        }

        while (this.isRunning) {
            try {
                // check token
                if (this.tokenController.getCurrentToken() <= 0) {
                    // this shouldn't happen since getCurrentToken will block
                    // until there is token
                    Log.w(LOG_TAG, "no token available");
                    continue;
                }

                byte[] data = null;
                long dataTime = 0;
                long sendingFrameID = 0;
                long compressedTime = 0;
                synchronized (this.frameLock) {
                    while (this.frameBuffer == null) {
                        try {
                            this.frameLock.wait();
                        } catch (InterruptedException e) {
                        }
                    }

                    data = this.frameBuffer;
                    dataTime = System.currentTimeMillis();

                    sendingFrameID = this.frameID;
                    Log.v(LOG_TAG, "sending" + sendingFrameID);
                    this.frameBuffer = null;

                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                DataOutputStream dos = new DataOutputStream(baos);
                byte[] header = ("{\""
                        + NetworkProtocol.HEADER_MESSAGE_FRAME_ID + "\":"
                        + sendingFrameID + "}").getBytes();
                dos.writeInt(header.length);
                dos.write(header);
                dos.writeInt(data.length);
                dos.write(data);

                // send packet and consume tokens
                this.tokenController.logSentPacket(sendingFrameID, dataTime,
                        compressedTime);
                this.tokenController.decreaseToken();
                networkWriter.write(baos.toByteArray());
                networkWriter.flush();

            } catch (IOException e) {
                Log.e(LOG_TAG, "Error in sending packet: " + e);
                this.notifyError(e.getMessage());
                this.isRunning = false;
                return;
            }

        }

        this.isRunning = false;

    }

    /**
     * Called whenever a new frame is generated Puts the new frame into the @frameBuffer
     */
    public void push(byte[] frame, Parameters parameters) {
        Log.v(LOG_TAG, "push");

        synchronized (this.frameLock) {
            ByteArrayOutputStream tmpStream = new ByteArrayOutputStream();
            try {
                tmpStream.write(frame);
            } catch (IOException e) {
                System.out.println("ERROROREOREREROEORERORO");
            }
            this.frameBuffer = tmpStream.toByteArray();
            this.frameID++;
            this.frameLock.notify();
        }
    }

    public void stopStreaming() {
        isRunning = false;
        if (tcpSocket != null) {
            try {
                tcpSocket.close();
            } catch (IOException e) {
            }
        }

        if (networkWriter != null) {
            try {
                networkWriter.close();
            } catch (IOException e) {
            }
        }
        if (networkReceiver != null) {
            networkReceiver.close();
        }
    }

    /**
     * Notifies error to the main thread
     */
    private void notifyError(String message) {
        // callback
        Message msg = Message.obtain();
        msg.what = NetworkProtocol.NETWORK_RET_FAILED;
        Bundle data = new Bundle();
        data.putString("message", message);
        msg.setData(data);
        this.networkHander.sendMessage(msg);
    }

}
