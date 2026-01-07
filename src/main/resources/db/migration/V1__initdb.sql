CREATE TABLE merchants (
    merchant_id VARCHAR(255) PRIMARY KEY,
    site_id VARCHAR(5) NOT NULL UNIQUE,
    store_name VARCHAR(255) NOT NULL,
    callback_url VARCHAR(512) NOT NULL,
    commission_type VARCHAR(50) NOT NULL,
    commission_value NUMERIC(15,2) NOT NULL,
    min_commission NUMERIC(15,2),
    max_commission NUMERIC(15,2),
    bank_account_number VARCHAR(20) NOT NULL,
    bank_routing_number VARCHAR(9) NOT NULL,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);


CREATE INDEX idx_merchants_site_id ON merchants(site_id);