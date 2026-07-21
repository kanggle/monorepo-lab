package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.exception.CommentNotFoundException;
import com.example.fanplatform.community.application.exception.PermissionDeniedException;
import com.example.fanplatform.community.domain.comment.Comment;
import com.example.fanplatform.community.domain.comment.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteCommentUseCase {

    private final CommentRepository commentRepository;

    @Transactional
    public void execute(String postId, String commentId, ActorContext actor) {
        Comment comment = commentRepository.findById(commentId, actor.tenantId())
                .orElseThrow(() -> new CommentNotFoundException(commentId));
        if (!comment.getPostId().equals(postId)) {
            throw new CommentNotFoundException(commentId);
        }
        if (!actor.owns(comment.getAuthorAccountId())) {
            throw new PermissionDeniedException("Only the author or an operator can delete this comment");
        }
        comment.markDeleted();
        commentRepository.save(comment);
    }
}
