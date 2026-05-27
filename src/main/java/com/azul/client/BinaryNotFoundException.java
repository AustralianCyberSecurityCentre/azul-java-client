package com.azul.client;

public class BinaryNotFoundException extends Exception {
    public BinaryNotFoundException(String sha256) {
        super("Binary not found in Azul: " + sha256);
    }
}
