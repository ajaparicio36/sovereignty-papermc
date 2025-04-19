-- Update vault_npcs table to add missing columns
ALTER TABLE vault_npcs ADD COLUMN world TEXT;
ALTER TABLE vault_npcs ADD COLUMN x DOUBLE;
ALTER TABLE vault_npcs ADD COLUMN y DOUBLE;
ALTER TABLE vault_npcs ADD COLUMN z DOUBLE;
ALTER TABLE vault_npcs ADD COLUMN created_by TEXT;
