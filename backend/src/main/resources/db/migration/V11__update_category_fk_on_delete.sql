-- Update foreign key constraint on tournament_inscriptions.category_id
-- to SET NULL when a category is deleted instead of preventing deletion

-- Remove existing foreign key constraint
ALTER TABLE tournament_inscriptions 
DROP CONSTRAINT IF EXISTS tournament_inscriptions_category_id_fkey;

-- Add foreign key with ON DELETE SET NULL
-- This allows categories to be deleted without deleting inscriptions
-- Inscriptions will simply have category_id set to NULL
ALTER TABLE tournament_inscriptions
ADD CONSTRAINT tournament_inscriptions_category_id_fkey 
FOREIGN KEY (category_id) 
REFERENCES tournament_categories(id) 
ON DELETE SET NULL;
