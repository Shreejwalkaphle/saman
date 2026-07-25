package com.bajar.saman.exception;

public class CategoryNotFoundException extends RuntimeException {
    public CategoryNotFoundException(String identifier) {
        super("Category not found: " + identifier);
    }
}