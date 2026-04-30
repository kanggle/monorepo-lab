package com.example.community.application;

import com.example.community.application.event.CommunityEventPublisher;
import com.example.community.domain.reaction.Reaction;
import com.example.community.domain.reaction.ReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AddReactionUseCase {

    private static final Set<String> ALLOWED_EMOJIS = Set.of("HEART", "FIRE", "CLAP", "WOW", "SAD");

    private final PostAccessGuard postAccessGuard;
    private final ReactionRepository reactionRepository;
    private final CommunityEventPublisher eventPublisher;

    public record ReactionResult(String postId, String emojiCode, long totalReactions) {}

    @Transactional
    public ReactionResult execute(String postId, String emojiCode, ActorContext actor) {
        if (emojiCode == null || !ALLOWED_EMOJIS.contains(emojiCode)) {
            throw new IllegalArgumentException("Unsupported emojiCode: " + emojiCode);
        }

        postAccessGuard.requirePublishedAccess(postId, actor);

        Optional<Reaction> existing = reactionRepository.find(postId, actor.accountId());
        boolean isNew;
        Reaction reaction;
        if (existing.isPresent()) {
            reaction = existing.get();
            isNew = false;
            if (!reaction.getEmojiCode().equals(emojiCode)) {
                reaction.changeEmoji(emojiCode);
                reactionRepository.save(reaction);
            }
        } else {
            reaction = Reaction.create(postId, actor.accountId(), emojiCode);
            reactionRepository.save(reaction);
            isNew = true;
        }

        long total = reactionRepository.countByPostId(postId);
        eventPublisher.publishReactionAdded(postId, actor.accountId(), emojiCode, isNew, reaction.getUpdatedAt());

        return new ReactionResult(postId, emojiCode, total);
    }
}
