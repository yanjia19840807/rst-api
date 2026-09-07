-- AD seed is Toolkit latest state; Exercise no longer records a source Exercise.
ALTER TABLE rst_exercise DROP COLUMN IF EXISTS initialized_from_exercise_id;
