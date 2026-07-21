ALTER TABLE rate_limit_audit_log
ALTER COLUMN client_ip TYPE VARCHAR(45) USING client_ip::VARCHAR;