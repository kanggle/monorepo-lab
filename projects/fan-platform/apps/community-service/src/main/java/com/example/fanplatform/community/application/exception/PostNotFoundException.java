package com.example.fanplatform.community.application.exception;

/**
 * Thrown when a post does not exist or belongs to another tenant. The
 * cross-tenant case intentionally collapses to NOT_FOUND (not 403) so the
 * service does not leak the existence of posts in other tenants.
 */
public class PostNotFoundException extends RuntimeException {
    public PostNotFoundException(String postId) {
        super("Post not found: " + postId);
    }
}
