-- Run in the SQL Editor after the migration. Fixtures are rolled back, including on success.
-- Failure is an exception, not a successful "empty result".
begin;
insert into auth.users(id, email, aud, role) values
('a1111111-1111-4111-8111-111111111111', 'animeflix-rls-a@example.invalid', 'authenticated', 'authenticated'),
('b2222222-2222-4222-8222-222222222222', 'animeflix-rls-b@example.invalid', 'authenticated', 'authenticated');
insert into public.viewing_profiles(id, account_id, name) values
('c3333333-3333-4333-8333-333333333333', 'a1111111-1111-4111-8111-111111111111', 'Niño'),
('d4444444-4444-4444-8444-444444444444', 'a1111111-1111-4111-8111-111111111111', 'Niña'),
('e5555555-5555-4555-8555-555555555555', 'b2222222-2222-4222-8222-222222222222', 'Otra cuenta');
insert into public.profile_entries(profile_id, account_id, kind, item_key, value) values
('c3333333-3333-4333-8333-333333333333', 'a1111111-1111-4111-8111-111111111111', 'watched', 'episode-1', '{"watched":true}'),
('e5555555-5555-4555-8555-555555555555', 'b2222222-2222-4222-8222-222222222222', 'watched', 'private-episode', '{"watched":true}');

set local role authenticated;
select set_config('request.jwt.claim.sub', 'a1111111-1111-4111-8111-111111111111', true);
select set_config('request.jwt.claims', '{"sub":"a1111111-1111-4111-8111-111111111111","role":"authenticated"}', true);
do $$
begin
    if (select count(*) from public.viewing_profiles) <> 2 then raise exception 'RLS: incorrect visible profiles'; end if;
    if exists(select 1 from public.profile_entries where item_key = 'private-episode') then raise exception 'RLS: other account data visible'; end if;
    if exists(select 1 from public.profile_entries where profile_id = 'd4444444-4444-4444-8444-444444444444') then raise exception 'Profiles mixed'; end if;
    begin
        insert into public.profile_entries(profile_id, account_id, kind, item_key, value)
        values ('e5555555-5555-4555-8555-555555555555', 'b2222222-2222-4222-8222-222222222222', 'watched', 'forged', '{}');
        raise exception 'RLS: cross-account insert succeeded';
    exception when insufficient_privilege then null; end;
    begin
        insert into public.profile_entries(profile_id, account_id, kind, item_key, value)
        values ('e5555555-5555-4555-8555-555555555555', 'a1111111-1111-4111-8111-111111111111', 'watched', 'forged-owner', '{}');
        raise exception 'RLS: foreign profile accepted with own account id';
    exception when insufficient_privilege or foreign_key_violation then null; end;
    begin
        update public.profile_entries set account_id = 'b2222222-2222-4222-8222-222222222222', profile_id = 'e5555555-5555-4555-8555-555555555555' where item_key = 'episode-1';
        raise exception 'RLS: reassignment to another account succeeded';
    exception when insufficient_privilege then null; end;
end $$;
-- Exercise the exact upsert shape used by the Android client.
insert into public.profile_entries(profile_id, account_id, kind, item_key, value)
values ('c3333333-3333-4333-8333-333333333333', 'a1111111-1111-4111-8111-111111111111', 'watched', 'episode-1', '{"watched":false}')
on conflict(profile_id, kind, item_key) do update set profile_id = excluded.profile_id,
account_id = excluded.account_id, kind = excluded.kind, item_key = excluded.item_key, value = excluded.value;
do $$ begin
    if (select value->>'watched' from public.profile_entries where item_key = 'episode-1') <> 'false' then raise exception 'Upsert failed'; end if;
end $$;
reset role;
set local role anon;
do $$ begin
    begin
        perform 1 from public.viewing_profiles;
        raise exception 'Anonymous profile access succeeded';
    exception when insufficient_privilege then null; end;
end $$;
reset role;
rollback;
