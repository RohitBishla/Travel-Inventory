-- An invoice and its snapshot live on the invoice's home shard. invoice_lines.transaction_id has no foreign key
-- because the transaction may live on another shard; invoice_links, on the transaction's own shard, carries the
-- ON DELETE RESTRICT guard instead.

CREATE TABLE invoices (
    id         BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    currency   CHAR(3)        NOT NULL,
    total      DECIMAL(19, 4) NOT NULL,
    line_count INT            NOT NULL,
    version    BIGINT         NOT NULL,
    created_at DATETIME(6)    NOT NULL,
    updated_at DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_invoices_created_at_id ON invoices (created_at, id);

CREATE TABLE invoice_lines (
    id                  BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    invoice_id          BIGINT         NOT NULL,
    line_no             INT            NOT NULL,
    transaction_id      BIGINT         NOT NULL,
    external_id         VARCHAR(128)   CHARACTER SET utf8mb4 COLLATE utf8mb4_nopad_bin NOT NULL,
    type                VARCHAR(16)    NOT NULL,
    category            VARCHAR(32)    NOT NULL,
    currency            CHAR(3)        NOT NULL,
    occurred_at         DATETIME(6)    NOT NULL,
    total               DECIMAL(19, 4) NOT NULL,
    transaction_version BIGINT         NOT NULL,
    metadata            TEXT           NOT NULL,
    CONSTRAINT uq_invoice_lines_transaction UNIQUE (invoice_id, transaction_id),
    CONSTRAINT uq_invoice_lines_line_no UNIQUE (invoice_id, line_no),
    CONSTRAINT ck_invoice_lines_type CHECK (type IN ('flight', 'hotel', 'rail', 'navan_fee')),
    CONSTRAINT ck_invoice_lines_category CHECK (category IN ('flight', 'hotel', 'rail', 'trip_fee', 'agent_call_fee')),
    CONSTRAINT fk_invoice_lines_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_invoice_lines_transaction_id ON invoice_lines (transaction_id);

CREATE TABLE invoice_line_tax_lines (
    invoice_line_id BIGINT         NOT NULL,
    line_no         INT            NOT NULL,
    name            VARCHAR(128)   NOT NULL,
    rate            DECIMAL(9, 6),
    amount          DECIMAL(19, 4) NOT NULL,
    PRIMARY KEY (invoice_line_id, line_no),
    CONSTRAINT fk_line_tax_lines_line FOREIGN KEY (invoice_line_id) REFERENCES invoice_lines (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE invoice_line_fee_lines (
    invoice_line_id BIGINT         NOT NULL,
    line_no         INT            NOT NULL,
    name            VARCHAR(128)   NOT NULL,
    amount          DECIMAL(19, 4) NOT NULL,
    PRIMARY KEY (invoice_line_id, line_no),
    CONSTRAINT fk_line_fee_lines_line FOREIGN KEY (invoice_line_id) REFERENCES invoice_lines (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE invoice_tax_rollups (
    invoice_id BIGINT         NOT NULL,
    line_no    INT            NOT NULL,
    name       VARCHAR(128)   NOT NULL,
    rate       DECIMAL(9, 6),
    amount     DECIMAL(19, 4) NOT NULL,
    PRIMARY KEY (invoice_id, line_no),
    CONSTRAINT fk_tax_rollups_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE invoice_fee_rollups (
    invoice_id BIGINT         NOT NULL,
    line_no    INT            NOT NULL,
    name       VARCHAR(128)   NOT NULL,
    amount     DECIMAL(19, 4) NOT NULL,
    PRIMARY KEY (invoice_id, line_no),
    CONSTRAINT fk_fee_rollups_invoice FOREIGN KEY (invoice_id) REFERENCES invoices (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE invoice_links (
    transaction_id BIGINT NOT NULL,
    invoice_id     BIGINT NOT NULL,
    PRIMARY KEY (transaction_id, invoice_id),
    CONSTRAINT fk_invoice_links_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
