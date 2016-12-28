package com.example.raducazacu.speedometer.interfaces;

public interface Connectable {

    boolean isConnected();

    void connect();

    void disconnect();

    void close();

    void sendData(CharSequence data);

    void sendData(int type, byte[] data);

    void signalToUi(int type, Object data);

}
