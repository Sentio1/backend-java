create sequence if not exists organization_invite_seq_gen start with 1 increment by 50;

create table organization_invites (
    id bigint primary key,
    org_id bigint not null
          references organizations(id)
          on delete cascade,
    email citext not null,
    role org_role not null,
    token_hash varchar(255) not null unique,
    invited_by bigint not null
            references users(id)
            on delete cascade,
    expires_at timestamptz not null,
    accepted_at timestamptz,
    revoked_at timestamptz,
    created_at timestamptz not null
);

create unique index organization_invites_token_hash_idx     on organization_invites(token_hash);

create unique index organization_invites_org_id_email_idx   on organization_invites(org_id, email)
    where revoked_at is null and accepted_at is null;