package com.example.fanplatform.artist.application.port.in;

import com.example.fanplatform.artist.application.ActorContext;
import com.example.fanplatform.artist.domain.group.GroupRole;

public interface AddGroupMemberUseCase {

    ArtistGroupView addMember(ActorContext actor, String groupId, String artistId, GroupRole role);
}
