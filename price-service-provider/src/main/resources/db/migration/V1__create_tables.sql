CREATE TABLE prices (
    instrument_id VARCHAR(255) PRIMARY KEY,
    bid_price NUMERIC(19, 4) NOT NULL,
    ask_price NUMERIC(19, 4) NOT NULL,
    last_updated TIMESTAMP NOT NULL
);

CREATE TABLE order_books (
    instrument_id VARCHAR(255) PRIMARY KEY,
    last_updated TIMESTAMP NOT NULL
);

CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_book_id VARCHAR(255) NOT NULL,
    price NUMERIC(19, 4) NOT NULL,
    volume NUMERIC(19, 4) NOT NULL,
    order_type VARCHAR(3) NOT NULL,
    CONSTRAINT fk_order_book FOREIGN KEY (order_book_id) REFERENCES order_books(instrument_id) ON DELETE CASCADE
);

CREATE INDEX idx_orders_order_book_id ON orders(order_book_id);
CREATE INDEX idx_orders_order_type ON orders(order_type);
