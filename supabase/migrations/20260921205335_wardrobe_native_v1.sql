-- One versioned owner document makes clothing counts and laundry changes atomic.
-- All code runs as the signed-in caller; no privileged key or SECURITY DEFINER.
create table public.wardrobes (
  id uuid primary key,
  workspace_id uuid not null references public.workspaces(id) on delete cascade,
  owner_user_id uuid not null references public.profiles(id),
  state jsonb not null,
  revision bigint not null default 1,
  change_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique(workspace_id,owner_user_id),
  check (octet_length(state::text) <= 500000),
  check (jsonb_typeof(state) = 'object')
);
alter table public.wardrobes enable row level security;
revoke all on public.wardrobes from anon,authenticated;
grant select,insert,update on public.wardrobes to authenticated;
create policy wardrobe_read on public.wardrobes for select to authenticated
using (owner_user_id=(select auth.uid()) and public.is_workspace_member(workspace_id));
create policy wardrobe_create on public.wardrobes for insert to authenticated
with check (owner_user_id=(select auth.uid()) and public.is_workspace_member(workspace_id));
create policy wardrobe_update on public.wardrobes for update to authenticated
using (owner_user_id=(select auth.uid()) and public.is_workspace_member(workspace_id))
with check (owner_user_id=(select auth.uid()) and public.is_workspace_member(workspace_id));

alter table public.tasks add column wardrobe_id uuid references public.wardrobes(id);
create index tasks_wardrobe_id_idx on public.tasks(wardrobe_id) where wardrobe_id is not null;
create function public.wardrobe_task_identity() returns trigger language plpgsql set search_path='' as $$
begin
  if old.wardrobe_id is not null and (new.wardrobe_id is distinct from old.wardrobe_id
    or new.owner_user_id<>old.owner_user_id or new.workspace_id<>old.workspace_id
    or new.original_date<>old.original_date or new.created_by<>old.created_by) then
    raise exception 'A laundry task keeps its wardrobe, owner and original date';
  end if;
  return new;
end $$;
create trigger tasks_wardrobe_identity before update on public.tasks for each row execute function public.wardrobe_task_identity();

create function public.validate_wardrobe() returns trigger language plpgsql
set search_path='' as $$
declare b jsonb; old_batch jsonb; garment jsonb; piece jsonb; piece_ids text[]='{}'; ids text[]='{}'; active integer=0;
begin
  if new.id <> md5('wardrobe:'||new.workspace_id::text||':'||new.owner_user_id::text)::uuid then
    raise exception 'Invalid wardrobe identity';
  end if;
  if tg_op='UPDATE' then
    if new.id<>old.id or new.workspace_id<>old.workspace_id or new.owner_user_id<>old.owner_user_id then
      raise exception 'Wardrobe identity cannot change';
    end if;
    new.revision=old.revision+1;
  else new.revision=1;
  end if;
  new.updated_at=now();
  if jsonb_typeof(new.state->'items') is distinct from 'array'
    or jsonb_typeof(new.state->'categories') is distinct from 'array'
    or jsonb_typeof(new.state->'outfits') is distinct from 'array'
    or jsonb_typeof(new.state->'batches') is distinct from 'array'
    or coalesce((new.state->>'threshold')::integer,-1) not between 0 and 1000
    or coalesce((new.state->>'sequence')::integer,-1)<0 then raise exception 'Invalid wardrobe structure'; end if;
  for b in select value from jsonb_array_elements(new.state->'categories') loop
    if coalesce(length(b->>'id'),0) not between 1 and 80 or b->>'id'=any(ids)
      or coalesce(length(btrim(b->>'name')),0) not between 1 and 50
      or coalesce(b->>'kind','') not in ('top','shirt','bottom','shorts','layer','one_piece','shoes','other') then raise exception 'Invalid category'; end if;
    ids=array_append(ids,b->>'id');
  end loop;
  ids='{}';
  for garment in select value from jsonb_array_elements(new.state->'items') loop
    if coalesce(length(btrim(garment->>'name')),0) not between 1 and 120
      or coalesce(garment->>'color','') !~ '^#[0-9A-Fa-f]{6}$'
      or coalesce(garment->>'use','') not in ('home','outdoor','both')
      or jsonb_typeof(garment->'pieces') is distinct from 'array'
      or not exists(select 1 from jsonb_array_elements(new.state->'categories') c where c->>'id'=garment->>'category')
      or coalesce(garment->>'id','')='' or garment->>'id'=any(ids) then raise exception 'Invalid clothing item'; end if;
    perform (garment->>'id')::uuid; ids=array_append(ids,garment->>'id');
    if jsonb_array_length(garment->'pieces') not between 1 and 100 then raise exception 'Invalid item quantity'; end if;
    for piece in select value from jsonb_array_elements(garment->'pieces') loop
      if coalesce(piece->>'status','') not in ('available','in_use','laundry') or coalesce(piece->>'id','')='' or piece->>'id'=any(piece_ids) then raise exception 'Invalid or duplicated clothing piece'; end if;
      perform (piece->>'id')::uuid;
      if piece->>'status'='laundry' then
        if coalesce(piece->>'laundry_token','')='' then raise exception 'Laundry token is required'; end if;
        perform (piece->>'laundry_token')::uuid;
      end if;
      piece_ids=array_append(piece_ids,piece->>'id');
    end loop;
  end loop;
  if cardinality(piece_ids)>1000 then raise exception 'Maximum wardrobe size is 1000 pieces'; end if;
  ids='{}';
  for b in select value from jsonb_array_elements(new.state->'batches') loop
    if coalesce((b->>'sequence')::integer,0)<1 or (b->>'sequence')::integer>(new.state->>'sequence')::integer
      or coalesce(b->>'task_id','')<>md5(new.id::text||':laundry:'||(b->>'sequence'))::uuid::text
      or b->>'task_id'=any(ids)
      or jsonb_typeof(b->'entries') is distinct from 'array' then raise exception 'Invalid laundry batch'; end if;
    ids=array_append(ids,b->>'task_id');
    if coalesce(b->>'date','')='' then raise exception 'Laundry date is required'; end if;
    perform (b->>'date')::date;
    for piece in select value from jsonb_array_elements(b->'entries') loop
      if coalesce(piece->>'piece_id','')='' or coalesce(piece->>'token','')='' then raise exception 'Invalid laundry entry'; end if;
      perform (piece->>'piece_id')::uuid;perform (piece->>'token')::uuid;
    end loop;
    if coalesce(b->>'completed_at','')='' and coalesce(b->>'cancelled_at','')='' then active=active+1; end if;
    if tg_op='UPDATE' then
      select value into old_batch from jsonb_array_elements(old.state->'batches') where value->>'task_id'=b->>'task_id';
      if old_batch is not null and (
        b->>'date' is distinct from old_batch->>'date'
        or (coalesce(old_batch->>'completed_at','')<>'' and b is distinct from old_batch)
        or (coalesce(old_batch->>'cancelled_at','')<>'' and b is distinct from old_batch)) then
        raise exception 'A finished laundry batch cannot be changed';
      end if;
    end if;
  end loop;
  if active>1 then raise exception 'Only one laundry task may be active'; end if;
  if (new.state->>'sequence')::integer <> coalesce((select max((value->>'sequence')::integer) from jsonb_array_elements(new.state->'batches')),0)
    then raise exception 'Invalid laundry sequence'; end if;
  for b in select value from jsonb_array_elements(new.state->'outfits') loop
    if coalesce(b->>'id','')='' or coalesce(length(btrim(b->>'name')),0) not between 1 and 80
      or jsonb_typeof(b->'items') is distinct from 'array' then raise exception 'Invalid saved outfit'; end if;
    perform (b->>'id')::uuid;
    for piece in select value from jsonb_array_elements(b->'items') loop perform (piece#>>'{}')::uuid; end loop;
  end loop;
  if tg_op='UPDATE' and exists(
    select 1 from jsonb_array_elements(old.state->'batches') b
    where not exists(select 1 from jsonb_array_elements(new.state->'batches') n where n->>'task_id'=b->>'task_id')
  ) then raise exception 'Laundry history cannot be removed'; end if;
  return new;
end $$;
create trigger wardrobes_validate before insert or update on public.wardrobes
for each row execute function public.validate_wardrobe();

-- Permit an offline-created laundry task to retain its original board date.
-- Normal tasks retain the existing closed-board guard.
create or replace function public.validate_new_task_schedule() returns trigger language plpgsql
set search_path='public' as $$
declare workspace_tz text; workspace_rollover smallint; current_board_date date;
begin
  select timezone,rollover_hour into workspace_tz,workspace_rollover from public.workspaces where id=new.workspace_id;
  current_board_date:=((now() at time zone workspace_tz)-make_interval(hours=>workspace_rollover))::date;
  if new.original_date<current_board_date and not exists(
    select 1 from public.wardrobes w, jsonb_array_elements(w.state->'batches') b
    where w.workspace_id=new.workspace_id and w.owner_user_id=new.owner_user_id
      and new.created_by=w.owner_user_id and b->>'task_id'=new.id::text
      and (b->>'date')::date=new.original_date
  ) then raise exception 'A new task cannot be added to a closed historical board'; end if;
  return new;
end $$;

create function public.wardrobe_tasks_changed() returns trigger language plpgsql
set search_path='' as $$
declare b jsonb; previous jsonb; task_owner uuid; task_workspace uuid;
begin
  for b in select value from jsonb_array_elements(new.state->'batches') loop
    previous=null;
    if tg_op='UPDATE' then select value into previous from jsonb_array_elements(old.state->'batches') where value->>'task_id'=b->>'task_id'; end if;
    if previous is null then
      insert into public.tasks(id,workspace_id,owner_user_id,created_by,title,description,original_date,completed_at,deleted_at,wardrobe_id)
      values((b->>'task_id')::uuid,new.workspace_id,new.owner_user_id,new.owner_user_id,'Do laundry',
        'Wardrobe laundry. Complete this task when the clothes are washed and ready to wear.',
        (b->>'date')::date,nullif(b->>'completed_at','')::timestamptz,nullif(b->>'cancelled_at','')::timestamptz,new.id)
      on conflict(id) do nothing;
      select owner_user_id,workspace_id into task_owner,task_workspace from public.tasks where id=(b->>'task_id')::uuid;
      if task_owner is distinct from new.owner_user_id or task_workspace is distinct from new.workspace_id then raise exception 'Laundry task identity conflict'; end if;
    else
      if coalesce(previous->>'completed_at','')='' and coalesce(b->>'completed_at','')<>'' then
        update public.tasks set completed_at=(b->>'completed_at')::timestamptz
        where id=(b->>'task_id')::uuid and owner_user_id=new.owner_user_id and workspace_id=new.workspace_id and completed_at is null;
      end if;
      if coalesce(previous->>'cancelled_at','')='' and coalesce(b->>'cancelled_at','')<>'' then
        update public.tasks set deleted_at=(b->>'cancelled_at')::timestamptz
        where id=(b->>'task_id')::uuid and owner_user_id=new.owner_user_id and workspace_id=new.workspace_id and deleted_at is null;
      end if;
    end if;
  end loop;
  return new;
end $$;
create trigger wardrobes_sync_tasks after insert or update on public.wardrobes
for each row execute function public.wardrobe_tasks_changed();

create function public.wardrobe_task_completed() returns trigger language plpgsql
set search_path='' as $$
declare w public.wardrobes; s jsonb; b jsonb; bi integer; garment jsonb; piece jsonb; gi integer; pi integer;
begin
  if not ((old.completed_at is null and new.completed_at is not null) or (old.deleted_at is null and new.deleted_at is not null)) then return new; end if;
  select * into w from public.wardrobes where workspace_id=new.workspace_id and owner_user_id=new.owner_user_id for update;
  if not found then return new; end if;
  s=w.state;
  for b,bi in select value,(ordinality-1)::integer from jsonb_array_elements(s->'batches') with ordinality loop
    if b->>'task_id'=new.id::text and coalesce(b->>'completed_at','')='' and coalesce(b->>'cancelled_at','')='' then
      if old.completed_at is null and new.completed_at is not null then
        for garment,gi in select value,(ordinality-1)::integer from jsonb_array_elements(s->'items') with ordinality loop
          for piece,pi in select value,(ordinality-1)::integer from jsonb_array_elements(garment->'pieces') with ordinality loop
            if piece->>'status'='laundry' and exists(select 1 from jsonb_array_elements(b->'entries') e
              where e->>'piece_id'=piece->>'id' and e->>'token'=piece->>'laundry_token') then
              piece=piece||jsonb_build_object('status','available','wears',0,'washed_at',new.completed_at);
              s=jsonb_set(s,array['items',gi::text,'pieces',pi::text],piece);
            end if;
          end loop;
        end loop;
        s=jsonb_set(s,array['batches',bi::text,'completed_at'],to_jsonb(new.completed_at));
      else s=jsonb_set(s,array['batches',bi::text,'cancelled_at'],to_jsonb(new.deleted_at));
      end if;
      update public.wardrobes set state=s,change_id=null where id=w.id;
      return new;
    end if;
  end loop;
  return new;
end $$;
create trigger tasks_wardrobe_completion after update of completed_at,deleted_at on public.tasks
for each row execute function public.wardrobe_task_completed();

create function public.sync_wardrobe(document jsonb,expected_revision bigint)
returns setof public.wardrobes language plpgsql set search_path='' as $$
declare current_row public.wardrobes; target uuid=(document->>'id')::uuid;
begin
  if auth.uid() is null or auth.uid() is distinct from (document->>'owner_user_id')::uuid
    or not public.is_workspace_member((document->>'workspace_id')::uuid) then raise exception 'Wardrobe access denied' using errcode='42501'; end if;
  -- Serialise first inserts as well as updates; the identifier is owner scoped.
  perform pg_advisory_xact_lock(hashtextextended(target::text,0));
  select * into current_row from public.wardrobes where id=target for update;
  if found then
    if current_row.change_id=(document->>'change_id')::uuid and current_row.state=document->'state' then return next current_row; return; end if;
    if current_row.revision<>expected_revision then raise exception 'Wardrobe changed on another device. Review both copies.' using errcode='PT409'; end if;
    update public.wardrobes set state=document->'state',change_id=(document->>'change_id')::uuid where id=target returning * into current_row;
  else
    if expected_revision<>0 then raise exception 'Wardrobe was removed remotely' using errcode='PT409'; end if;
    insert into public.wardrobes(id,workspace_id,owner_user_id,state,change_id)
    values(target,(document->>'workspace_id')::uuid,auth.uid(),document->'state',(document->>'change_id')::uuid) returning * into current_row;
  end if;
  return next current_row;
end $$;
revoke all on function public.wardrobe_task_identity(),public.validate_wardrobe(),public.wardrobe_tasks_changed(),public.wardrobe_task_completed(),public.sync_wardrobe(jsonb,bigint) from public,anon;
grant execute on function public.sync_wardrobe(jsonb,bigint) to authenticated;
