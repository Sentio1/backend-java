alter table organization_members add is_default boolean not null
    default false;

create unique index idx_organization_member_user_default
    ON organization_members(user_id) WHERE is_default = true;