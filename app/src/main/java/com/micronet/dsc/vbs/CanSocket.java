package com.micronet.dsc.vbs;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.LongBuffer;

public class CanSocket {

    private final IntBuffer idBuffer;
    private final IntBuffer dlcBuffer;
    private final IntBuffer droppedBuffer;
    private final LongBuffer tsBuffer;
    private final ByteBuffer plBuffer;
    private final int socketNum;
    private final int idxNum;
    private final Object canService;
    Method read, write;

    public CanSocket(int socket, int idx, Object service, Method send, Method recvMsg) {
        socketNum=socket;
        idxNum=idx;
        canService=service;
        read=recvMsg;
        write=send;
        idBuffer = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer();
        dlcBuffer =ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer();
        droppedBuffer =ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).asIntBuffer();
        tsBuffer=ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder()).asLongBuffer();
        plBuffer=ByteBuffer.allocateDirect(8).order(ByteOrder.nativeOrder());
    }

    public void write(CanFrame frame){
        try {
            write.invoke(canService, socketNum, frame.getId(), frame.getData().length, frame.getData());
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    public CanFrame read() throws InvocationTargetException, IllegalAccessException {
        idBuffer.rewind();
        dlcBuffer.rewind();
        droppedBuffer.rewind();
        tsBuffer.rewind();
        plBuffer.rewind();
        read.invoke(canService, socketNum, idxNum, idBuffer, dlcBuffer, tsBuffer, droppedBuffer, plBuffer);
        int dlcInt = dlcBuffer.get();
        byte[] data = new byte[dlcInt];
        System.arraycopy(plBuffer.array(), plBuffer.arrayOffset(), data,0,dlcInt);
        return new CanFrame(idBuffer.get(),data);
    }
}
