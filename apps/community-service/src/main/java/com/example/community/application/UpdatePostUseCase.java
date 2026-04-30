package com.example.community.application;

import com.example.community.application.exception.PermissionDeniedException;
import com.example.community.application.exception.PostNotFoundException;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UpdatePostUseCase {

    private final PostRepository postRepository;
    private final PostMediaUrlsSerializer mediaUrlsSerializer;

    @Transactional
    public UpdatePostResponse execute(String postId, ActorContext actor, String title, String body, List<String> mediaUrls) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new PostNotFoundException(postId));
        if (!post.getAuthorAccountId().equals(actor.accountId())) {
            throw new PermissionDeniedException("Only the author can update this post");
        }
        String mediaUrlsJson = mediaUrlsSerializer.serialize(mediaUrls);
        post.updateContent(title, body, mediaUrlsJson);
        postRepository.save(post);
        return new UpdatePostResponse(
                post.getId(),
                post.getTitle(),
                post.getBody(),
                mediaUrls,
                post.getUpdatedAt()
        );
    }
}
