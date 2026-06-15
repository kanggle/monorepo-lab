package com.example.community.domain.post;

import java.util.Optional;

public interface PostRepository {

    Post save(Post post);

    Optional<Post> findById(String id);

    PageResult<Post> findFeedForFan(String fanAccountId, int page, int size);
}
