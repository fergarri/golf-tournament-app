-- 1. Add status column to scorecards
ALTER TABLE scorecards ADD COLUMN status VARCHAR(20);

-- 2. Migrate existing data
UPDATE scorecards SET status = 'DELIVERED' WHERE delivered = true;
UPDATE scorecards SET status = 'CANCELLED' WHERE canceled = true;
UPDATE scorecards SET status = 'IN_PROGRESS' WHERE status IS NULL;

-- 3. Set NOT NULL and default after migration
ALTER TABLE scorecards ALTER COLUMN status SET NOT NULL;
ALTER TABLE scorecards ALTER COLUMN status SET DEFAULT 'IN_PROGRESS';

-- 4. Drop old columns and indexes
DROP INDEX IF EXISTS idx_scorecards_delivered;
DROP INDEX IF EXISTS idx_scorecards_canceled;
ALTER TABLE scorecards DROP COLUMN delivered;
ALTER TABLE scorecards DROP COLUMN canceled;

-- 5. New index for status
CREATE INDEX idx_scorecards_status ON scorecards(status);

-- 6. Add double_points to tournaments
ALTER TABLE tournaments ADD COLUMN double_points BOOLEAN NOT NULL DEFAULT FALSE;

-- 7. Create frutales_scores table
CREATE TABLE frutales_scores (
    id BIGSERIAL PRIMARY KEY,
    tournament_id BIGINT NOT NULL REFERENCES tournaments(id) ON DELETE CASCADE,
    scorecard_id BIGINT NOT NULL REFERENCES scorecards(id) ON DELETE CASCADE,
    player_id BIGINT NOT NULL REFERENCES players(id),
    position INTEGER,
    position_points INTEGER NOT NULL DEFAULT 0,
    birdie_count INTEGER NOT NULL DEFAULT 0,
    birdie_points INTEGER NOT NULL DEFAULT 0,
    eagle_count INTEGER NOT NULL DEFAULT 0,
    eagle_points INTEGER NOT NULL DEFAULT 0,
    ace_count INTEGER NOT NULL DEFAULT 0,
    ace_points INTEGER NOT NULL DEFAULT 0,
    participation_points INTEGER NOT NULL DEFAULT 0,
    total_points INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tournament_id, scorecard_id)
);

CREATE INDEX idx_frutales_scores_tournament ON frutales_scores(tournament_id);
