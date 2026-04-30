package com.example.community.application.exception;

public class PostNotFoundException extends RuntimeException {
    public PostNotFoundException(String postId) {
        super("Post not found: " + postId);
    }
}
