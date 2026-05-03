package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.application.ActorContext;

public interface RemoveGroupMemberUseCase {

    void removeMember(ActorContext actor, String groupId, String artistId);
}
