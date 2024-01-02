package com.micronet.dsc.vbs;

public class CanFrame {
    private int id;
    private byte[] data;

    public CanFrame(int id, byte[] data) {
        this.id = id;
        this.data = data;
    }

    public int getId() {
        return id;
    }

    public byte[] getData() {
        return data;
    }
}
