package com.bajar.saman.exception;

public class CartItemNotFoundException extends RuntimeException {
    public CartItemNotFoundException(String identifier) {
        super("Cart item not found: " + identifier);
    }
}