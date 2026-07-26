package com.bajar.saman.exception;

public class InsufficientStockException extends RuntimeException {
    public InsufficientStockException(String productName, int available, int requested) {
        super("Insufficient stock for '" + productName + "': " + available +
                " available, " + requested + " requested");
    }
}