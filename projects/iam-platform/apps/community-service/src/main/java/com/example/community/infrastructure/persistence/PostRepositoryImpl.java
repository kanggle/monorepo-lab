package com.example.community.infrastructure.persistence;

import com.example.community.domain.post.PageResult;
import com.example.community.domain.post.Post;
import com.example.community.domain.post.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

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
    public PageResult<Post> findFeedForFan(String fanAccountId, int page, int size) {
        Page<Post> jpaPage = postJpaRepository.findFeedForFan(fanAccountId, PageRequest.of(page, size));
        return new PageResult<>(
                jpaPage.getContent(),
                jpaPage.getNumber(),
                jpaPage.getSize(),
                jpaPage.getTotalElements(),
                jpaPage.getTotalPages()
        );
    }
}
