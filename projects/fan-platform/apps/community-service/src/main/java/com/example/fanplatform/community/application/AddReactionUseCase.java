package com.example.fanplatform.community.application;

import com.example.fanplatform.community.application.event.CommunityEventPublisher;
import com.example.fanplatform.community.domain.reaction.Reaction;
import com.example.fanplatform.community.domain.reaction.ReactionRepository;
import com.example.fanplatform.community.domain.reaction.ReactionType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AddReactionUseCase {

    private final PostAccessGuard postAccessGuard;
    private final ReactionRepository reactionRepository;
    private final CommunityEventPublisher eventPublisher;

    public record ReactionResult(String postId, ReactionType reactionType, long totalReactions) {}

    @Transactional
    public ReactionResult execute(String postId, ReactionType reactionType, ActorContext actor) {
        postAccessGuard.requirePublishedAccess(postId, actor);
        Optional<Reaction> existing = reactionRepository.find(postId, actor.accountId(), actor.tenantId());
        Reaction reaction;
        boolean changed;
        if (existing.isPresent()) {
            reaction = existing.get();
            if (reaction.getReactionType() != reactionType) {
                reaction.changeType(reactionType);
                reactionRepository.save(reaction);
                changed = true;
            } else {
                // Same (post, reactor, type) PUT again — a true no-op at the
                // database level. Skip the outbox write so consumers don't
                // see duplicate community.reaction.added events with distinct
                // event_ids that they cannot dedupe.
                changed = false;
            }
        } else {
            reaction = Reaction.create(postId, actor.accountId(), actor.tenantId(), reactionType);
            reactionRepository.save(reaction);
            changed = true;
        }
        long total = reactionRepository.countByPostId(postId, actor.tenantId());
        if (changed) {
            eventPublisher.publishReactionAdded(
                    postId, actor.tenantId(), actor.accountId(),
                    reactionType, reaction.getUpdatedAt());
        }
        return new ReactionResult(postId, reactionType, total);
    }
}
