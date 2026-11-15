-- Same schema on every shard. external_id is compared byte for byte, the same way it is hashed.

CREATE TABLE transactions (
    id           BIGINT         NOT NULL AUTO_INCREMENT PRIMARY KEY,
    external_id  VARCHAR(128)   CHARACTER SET utf8mb4 COLLATE utf8mb4_nopad_bin NOT NULL,
    type         VARCHAR(16)    NOT NULL,
    currency     CHAR(3)        NOT NULL,
    occurred_at  DATETIME(6)    NOT NULL,
    total        DECIMAL(19, 4) NOT NULL,
    version      BIGINT         NOT NULL,
    created_at   DATETIME(6)    NOT NULL,
    updated_at   DATETIME(6)    NOT NULL,
    CONSTRAINT ck_transactions_type CHECK (type IN ('flight', 'hotel', 'rail', 'navan_fee')),
    CONSTRAINT uq_transactions_external_id UNIQUE (external_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE INDEX ix_transactions_created_at_id ON transactions (created_at, id);

-- Lives on the key's owner shard and keeps external_id unique. Only these rows move when a shard is added.
CREATE TABLE external_ids (
    external_id    VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_nopad_bin NOT NULL,
    transaction_id BIGINT       NOT NULL,
    CONSTRAINT uq_external_ids_external_id UNIQUE (external_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE transaction_tax_lines (
    transaction_id BIGINT         NOT NULL,
    line_no        INT            NOT NULL,
    name           VARCHAR(128)   NOT NULL,
    rate           DECIMAL(9, 6),
    amount         DECIMAL(19, 4) NOT NULL,
    PRIMARY KEY (transaction_id, line_no),
    CONSTRAINT fk_tax_lines_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE transaction_fee_lines (
    transaction_id BIGINT         NOT NULL,
    line_no        INT            NOT NULL,
    name           VARCHAR(128)   NOT NULL,
    amount         DECIMAL(19, 4) NOT NULL,
    PRIMARY KEY (transaction_id, line_no),
    CONSTRAINT fk_fee_lines_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE flight_details (
    transaction_id BIGINT         NOT NULL PRIMARY KEY,
    origin         VARCHAR(64)    NOT NULL,
    destination    VARCHAR(64)    NOT NULL,
    fare           DECIMAL(19, 4) NOT NULL,
    CONSTRAINT fk_flight_details_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE flight_coupons (
    transaction_id BIGINT      NOT NULL,
    coupon_no      INT         NOT NULL,
    carrier        VARCHAR(8),
    flight_number  VARCHAR(16) NOT NULL,
    from_location  VARCHAR(64) NOT NULL,
    to_location    VARCHAR(64) NOT NULL,
    departure_at   DATETIME(6) NOT NULL,
    arrival_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (transaction_id, coupon_no),
    CONSTRAINT fk_flight_coupons_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE hotel_details (
    transaction_id      BIGINT         NOT NULL PRIMARY KEY,
    name                VARCHAR(256)   NOT NULL,
    address_street      VARCHAR(256)   NOT NULL,
    address_city        VARCHAR(128)   NOT NULL,
    address_country     VARCHAR(64)    NOT NULL,
    check_in            DATE           NOT NULL,
    check_out           DATE           NOT NULL,
    nights              INT            NOT NULL,
    room_rate_per_night DECIMAL(19, 4) NOT NULL,
    CONSTRAINT fk_hotel_details_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE hotel_extras (
    transaction_id BIGINT         NOT NULL,
    extra_no       INT            NOT NULL,
    name           VARCHAR(128)   NOT NULL,
    amount         DECIMAL(19, 4) NOT NULL,
    PRIMARY KEY (transaction_id, extra_no),
    CONSTRAINT fk_hotel_extras_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE rail_legs (
    transaction_id BIGINT       NOT NULL,
    leg_no         INT          NOT NULL,
    origin         VARCHAR(128) NOT NULL,
    destination    VARCHAR(128) NOT NULL,
    departure_at   DATETIME(6)  NOT NULL,
    arrival_at     DATETIME(6)  NOT NULL,
    train_number   VARCHAR(32),
    travel_class   VARCHAR(32),
    PRIMARY KEY (transaction_id, leg_no),
    CONSTRAINT fk_rail_legs_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE navan_fee_details (
    transaction_id      BIGINT       NOT NULL PRIMARY KEY,
    fee_kind            VARCHAR(32)  NOT NULL,
    description         VARCHAR(512),
    related_external_id VARCHAR(128),
    CONSTRAINT ck_navan_fee_kind CHECK (fee_kind IN ('trip_fee', 'agent_call_fee')),
    CONSTRAINT fk_navan_fee_details_transaction FOREIGN KEY (transaction_id) REFERENCES transactions (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
