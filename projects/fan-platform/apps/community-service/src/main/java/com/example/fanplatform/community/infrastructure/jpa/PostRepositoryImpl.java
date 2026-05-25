package com.example.fanplatform.community.infrastructure.jpa;

import com.example.fanplatform.community.domain.post.Post;
import com.example.fanplatform.community.domain.post.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    public Page<Post> findFeedForFan(String fanAccountId, String tenantId, Pageable pageable) {
        return jpa.findFeedForFan(fanAccountId, tenantId, pageable);
    }
}
