-- Auth-service owns users. Loan-service stores auth user ids as scalar values only.
ALTER TABLE borrowers DROP CONSTRAINT IF EXISTS borrowers_user_id_fkey;
