-- Remove borrower_id FK constraint from loan_applications
-- Borrower table is being phased out. Loan-service stores borrower_id as scalar userId only.
ALTER TABLE loan_applications DROP CONSTRAINT IF EXISTS loan_applications_borrower_id_fkey;
