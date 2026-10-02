package com.example.fanplatform.artist.application.port.in;

import com.example.common.page.PageResult;
import com.example.fanplatform.artist.application.ActorContext;

/**
 * Agency CRUD (TASK-MONO-748, ADR-MONO-079 D1). Writes are admin-tier — today's
 * {@code ADMIN_ROLES} gate; {@code TASK-MONO-750} opens the platform-operator path.
 * Reads are any authenticated tenant member.
 */
public interface ManageAgencyUseCase {

    AgencyView create(ActorContext actor, String name);

    AgencyView rename(ActorContext actor, String agencyId, String name);

    AgencyView archive(ActorContext actor, String agencyId);

    AgencyView getById(ActorContext actor, String agencyId);

    PageResult<AgencyView> list(ActorContext actor, int page, int size);
}
