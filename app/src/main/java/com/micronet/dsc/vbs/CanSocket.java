package com.micronet.dsc.vbs;

import java.lang.reflect.Method;
import java.net.Socket;

public class CanSocket {

    private int socketNum;
    private int idxNum;
    private Object canService;
    Method read, write;

    public CanSocket(int socket, int idx, Object service, Method send, Method recvMsg) {
        socketNum=socket;
        idxNum=idx;
        canService=service;
        read=recvMsg;
        write=send;
    }

    public void write(CanFrame frame){

    }

    public CanFrame read(){
        return new CanFrame(0,new byte[8]);
    }
}
