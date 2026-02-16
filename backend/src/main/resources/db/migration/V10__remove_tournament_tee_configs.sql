-- Remove tournament tee configuration table
-- This configuration is no longer needed as tees are selected per player when creating scorecards
DROP TABLE IF EXISTS tournament_tee_configs CASCADE;
