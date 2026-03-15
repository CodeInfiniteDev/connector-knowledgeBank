create view auth_user_account as
select
    u.user_login_id,
    (elem ->> 'resource')   as resource_name,
    (elem ->> 'accountId')  as resource_account_id,
    u.updated_at
from auth_user as u
         cross join lateral jsonb_array_elements(u.accounts::jsonb) as elem;