-- Add status field to tournaments table
ALTER TABLE tournaments ADD COLUMN IF NOT EXISTS estado VARCHAR(50) NOT NULL DEFAULT 'PENDING';

-- Create index for status queries
CREATE INDEX IF NOT EXISTS idx_tournaments_estado ON tournaments(estado);

-- Update existing tournaments to PENDING status
UPDATE tournaments SET estado = 'PENDING' WHERE estado IS NULL;
