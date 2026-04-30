package com.example.community.infrastructure.persistence;

import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PostRepositoryAdapter implements PostRepository {

    private final PostJpaRepository postJpaRepository;

    @Override
    public Post save(Post post) {
        return postJpaRepository.save(post);
    }

    @Override
    public Optional<Post> findById(String id) {
        return postJpaRepository.findById(id);
    }

    @Override
    public Page<Post> findFeedForFan(String fanAccountId, Pageable pageable) {
        return postJpaRepository.findFeedForFan(fanAccountId, pageable);
    }
}
