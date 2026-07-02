-- TASK-FE-085 — capture the request User-Agent at Web Push subscription registration
-- so a "push device list" UI can label each browser/device a user has subscribed from.
--
-- Nullable on purpose: existing rows (registered before this column existed) and any
-- caller that omits the User-Agent header must keep working, and the send path never
-- reads this column. It is captured once at register time and never rotated on
-- re-registration (key rotation only), so a legacy row simply shows a null device label.

ALTER TABLE push_subscriptions ADD COLUMN user_agent VARCHAR(512);
