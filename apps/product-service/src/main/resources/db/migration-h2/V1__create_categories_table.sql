CREATE TABLE categories (
    id        UUID        NOT NULL,
    name      VARCHAR(100) NOT NULL,
    parent_id UUID,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES categories (id)
);

CREATE INDEX idx_categories_parent_id ON categories (parent_id);
