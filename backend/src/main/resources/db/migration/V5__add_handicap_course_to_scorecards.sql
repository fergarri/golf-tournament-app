-- Add handicap_course column to scorecards table
ALTER TABLE scorecards ADD COLUMN handicap_course DECIMAL(4,2);

-- Add comment to explain the column
COMMENT ON COLUMN scorecards.handicap_course IS 'Handicap course entered manually by the player at the start of the scorecard';
