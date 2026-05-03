package com.example.fanplatform.community.application.exception;

public class CommentNotFoundException extends RuntimeException {
    public CommentNotFoundException(String commentId) {
        super("Comment not found: " + commentId);
    }
}
