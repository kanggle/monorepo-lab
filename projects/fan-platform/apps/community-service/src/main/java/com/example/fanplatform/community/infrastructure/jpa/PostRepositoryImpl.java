package com.example.fanplatform.community.infrastructure.jpa;

import com.example.fanplatform.community.domain.post.PageResult;
import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

    private final PostJpaRepository jpa;

    @Override
    public Post save(Post post) {
        return jpa.save(post);
    }

    @Override
    public Optional<Post> findById(String id, String tenantId) {
        return jpa.findByIdAndTenantId(id, tenantId);
    }

    @Override
    public PageResult<Post> findFeedForFan(String fanAccountId, String tenantId, int page, int size) {
        Page<Post> jpaPage = jpa.findFeedForFan(fanAccountId, tenantId, PageRequest.of(page, size));
        return new PageResult<>(
                jpaPage.getContent(),
                jpaPage.getNumber(),
                jpaPage.getSize(),
                jpaPage.getTotalElements(),
                jpaPage.getTotalPages());
    }
}
