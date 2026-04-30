package com.example.community.domain.post;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface PostRepository {

    Post save(Post post);

    Optional<Post> findById(String id);

    Page<Post> findFeedForFan(String fanAccountId, Pageable pageable);
}
