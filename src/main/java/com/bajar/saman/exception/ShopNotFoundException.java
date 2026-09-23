package com.bajar.saman.exception;

public class ShopNotFoundException extends RuntimeException {
    public ShopNotFoundException(String identifier) { super("Shop not found: " + identifier); }
}
