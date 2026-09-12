-- Issue #29: UserRouteWaypoint.photoUrl is optional, so the database column must be nullable.
-- Apply this statement explicitly to each existing PostgreSQL environment; Hibernate
-- ddl-auto=update must not be relied on to relax an existing NOT NULL constraint.
ALTER TABLE user_route_waypoints
    ALTER COLUMN photo_url DROP NOT NULL;
