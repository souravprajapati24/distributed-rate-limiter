CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_quota_tiers_updated_at
    BEFORE UPDATE ON quota_tiers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_tenants_updated_at
    BEFORE UPDATE ON tenants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_overrides_updated_at
    BEFORE UPDATE ON tenant_quota_overrides
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER trg_usage_summaries_updated_at
    BEFORE UPDATE ON usage_summaries
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
