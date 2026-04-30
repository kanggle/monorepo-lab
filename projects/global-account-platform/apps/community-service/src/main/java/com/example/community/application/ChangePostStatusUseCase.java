package com.example.community.application;

import com.example.community.application.exception.PermissionDeniedException;
import com.example.community.application.exception.PostNotFoundException;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import com.example.community.domain.post.status.ActorType;
import com.example.community.domain.post.status.PostStatus;
import com.example.community.domain.post.status.PostStatusHistoryEntry;
import com.example.community.domain.post.status.PostStatusHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChangePostStatusUseCase {

    private final PostRepository postRepository;
    private final PostStatusHistoryRepository historyRepository;

    @Transactional
    public void execute(String postId, PostStatus target, ActorType actorType, String actorId, String reason) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (actorType == ActorType.AUTHOR && !post.getAuthorAccountId().equals(actorId)) {
            throw new PermissionDeniedException("Only the author can change the status of this post");
        }
        PostStatus previous = post.changeStatus(target, actorType);
        postRepository.save(post);
        historyRepository.save(PostStatusHistoryEntry.record(
                postId, previous, target, actorType, actorId, reason));
    }
}
