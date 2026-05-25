-- Remove FK constraints on User references (auth-service owns User, not loan-service)
ALTER TABLE property_assessments DROP CONSTRAINT IF EXISTS property_assessments_appraiser_id_fkey;
ALTER TABLE underwriting_decisions DROP CONSTRAINT IF EXISTS underwriting_decisions_underwriter_id_fkey;
ALTER TABLE loan_documents DROP CONSTRAINT IF EXISTS loan_documents_uploaded_by_fkey;
ALTER TABLE audit_logs DROP CONSTRAINT IF EXISTS audit_logs_changed_by_fkey;
ALTER TABLE loan_applications DROP CONSTRAINT IF EXISTS loan_applications_created_by_fkey;

-- Rename columns for clarity (no schema change, just column rename)
ALTER TABLE loan_documents RENAME COLUMN uploaded_by TO uploaded_by_id;
ALTER TABLE loan_applications RENAME COLUMN created_by TO created_by_id;
ALTER TABLE audit_logs RENAME COLUMN changed_by TO changed_by_id;
