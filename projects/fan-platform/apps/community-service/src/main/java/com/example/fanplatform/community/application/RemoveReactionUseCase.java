package com.example.fanplatform.community.application;

import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import com.example.fanplatform.community.domain.reaction.Reaction;
import com.example.fanplatform.community.domain.reaction.ReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RemoveReactionUseCase {

    private final PostRepository postRepository;
    private final ReactionRepository reactionRepository;

    @Transactional
    public void execute(String postId, ActorContext actor) {
        Post post = PostLookup.requireById(postRepository, postId, actor.tenantId());
        Optional<Reaction> existing = reactionRepository.find(post.getId(), actor.accountId(), actor.tenantId());
        existing.ifPresent(reactionRepository::delete);
    }
}
