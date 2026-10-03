CREATE TABLE marketplace_order_refund_operations (
    order_id UUID NOT NULL REFERENCES marketplace_orders(id),
    operation_id UUID NOT NULL,
    requested_cents INTEGER,
    reason VARCHAR(2000),
    PRIMARY KEY (order_id, operation_id)
);
