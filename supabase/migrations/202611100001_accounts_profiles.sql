-- One Supabase Auth user is a household account; viewing profiles have no passwords.
begin;
create table public.viewing_profiles (
    id uuid primary key default gen_random_uuid(),
    account_id uuid not null references auth.users(id) on delete cascade default auth.uid(),
    name text not null check (length(trim(name)) between 1 and 32),
    created_at timestamptz not null default now(),
    unique (id, account_id)
);
create index viewing_profiles_account_idx on public.viewing_profiles(account_id);

create table public.profile_entries (
    profile_id uuid not null,
    account_id uuid not null default auth.uid(),
    kind text not null check (kind in ('favorite', 'opened', 'watched', 'progress', 'preference')),
    item_key text not null check (length(item_key) between 1 and 2048),
    value jsonb not null check (jsonb_typeof(value) = 'object' and octet_length(value::text) <= 32768),
    updated_at timestamptz not null default now(),
    primary key (profile_id, kind, item_key),
    foreign key (profile_id, account_id) references public.viewing_profiles(id, account_id) on delete cascade
);
create index profile_entries_account_idx on public.profile_entries(account_id);

alter table public.viewing_profiles enable row level security;
alter table public.profile_entries enable row level security;
revoke all on public.viewing_profiles, public.profile_entries from public, anon, authenticated;
grant select, insert, delete on public.viewing_profiles to authenticated;
grant update (name) on public.viewing_profiles to authenticated;
grant select, insert on public.profile_entries to authenticated;
grant update on public.profile_entries to authenticated;

create policy own_profiles_select on public.viewing_profiles for select to authenticated using ((select auth.uid()) = account_id);
create policy own_profiles_insert on public.viewing_profiles for insert to authenticated with check ((select auth.uid()) = account_id);
create policy own_profiles_update on public.viewing_profiles for update to authenticated using ((select auth.uid()) = account_id) with check ((select auth.uid()) = account_id);
create policy own_profiles_delete on public.viewing_profiles for delete to authenticated using ((select auth.uid()) = account_id);
create policy own_entries_select on public.profile_entries for select to authenticated using ((select auth.uid()) = account_id);
create policy own_entries_insert on public.profile_entries for insert to authenticated with check (
    (select auth.uid()) = account_id and exists (
        select 1 from public.viewing_profiles p where p.id = profile_id and p.account_id = (select auth.uid())
    )
);
create policy own_entries_update on public.profile_entries for update to authenticated
    using ((select auth.uid()) = account_id) with check ((select auth.uid()) = account_id);

create function public.touch_profile_entry() returns trigger language plpgsql set search_path = '' as $$
begin new.updated_at = now(); return new; end;
$$;
revoke all on function public.touch_profile_entry() from public;
create trigger profile_entry_updated before update on public.profile_entries for each row execute function public.touch_profile_entry();
commit;
