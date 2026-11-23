CREATE TABLE IF NOT EXISTS users (
  id BIGINT NOT NULL AUTO_INCREMENT,
  username VARCHAR(32) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  display_name VARCHAR(40) NOT NULL,
  password_hash VARCHAR(60) CHARACTER SET ascii NOT NULL,
  role VARCHAR(12) NOT NULL DEFAULT 'USER',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_users_username (username),
  CONSTRAINT ck_users_role CHECK (role IN ('USER','ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS products (
  id BIGINT NOT NULL AUTO_INCREMENT,
  seller_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  description VARCHAR(2000) NOT NULL,
  category VARCHAR(24) NOT NULL,
  image_url VARCHAR(600),
  price DECIMAL(12,2) NOT NULL,
  stock INT NOT NULL,
  reserved INT NOT NULL DEFAULT 0,
  status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY ix_products_status_id (status,id DESC),
  KEY ix_products_category_status_id (category,status,id DESC),
  KEY ix_products_seller_id (seller_id,id DESC),
  CONSTRAINT ck_products_stock CHECK (stock >= 0 AND reserved >= 0 AND stock + reserved <= 100000),
  CONSTRAINT ck_products_price CHECK (price > 0),
  CONSTRAINT ck_products_status CHECK (status IN ('ACTIVE','INACTIVE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS cart_items (
  buyer_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  PRIMARY KEY (buyer_id,product_id),
  CONSTRAINT fk_cart_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT ck_cart_quantity CHECK (quantity BETWEEN 1 AND 99)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS trade_orders (
  id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  buyer_id BIGINT NOT NULL,
  idempotency_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  request_hash CHAR(64) CHARACTER SET ascii NOT NULL,
  status VARCHAR(16) NOT NULL,
  total DECIMAL(16,2) NOT NULL,
  created_at DATETIME(3) NOT NULL,
  expires_at DATETIME(3) NOT NULL,
  paid_at DATETIME(3),
  finished_at DATETIME(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_orders_buyer_key (buyer_id,idempotency_key),
  KEY ix_orders_buyer_created (buyer_id,created_at DESC,id),
  KEY ix_orders_expiry (status,expires_at,id),
  CONSTRAINT ck_orders_status CHECK (status IN ('PENDING_PAYMENT','PAID','COMPLETED','CANCELLED','EXPIRED')),
  CONSTRAINT ck_orders_total CHECK (total >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_items (
  order_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  product_id BIGINT NOT NULL,
  seller_id BIGINT NOT NULL,
  title VARCHAR(100) NOT NULL,
  unit_price DECIMAL(12,2) NOT NULL,
  quantity INT NOT NULL,
  PRIMARY KEY (order_id,product_id),
  CONSTRAINT fk_item_order FOREIGN KEY (order_id) REFERENCES trade_orders(id),
  CONSTRAINT fk_item_product FOREIGN KEY (product_id) REFERENCES products(id),
  CONSTRAINT ck_item_quantity CHECK (quantity BETWEEN 1 AND 99),
  CONSTRAINT ck_item_price CHECK (unit_price > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_outbox (
  id BIGINT NOT NULL AUTO_INCREMENT,
  order_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  kind VARCHAR(24) NOT NULL,
  trace_id CHAR(32) CHARACTER SET ascii NOT NULL,
  deliver_at DATETIME(3) NOT NULL,
  next_attempt_at DATETIME(3) NOT NULL,
  attempts INT NOT NULL DEFAULT 0,
  sent_at DATETIME(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_outbox_order_kind (order_id,kind),
  KEY ix_outbox_pending (sent_at,next_attempt_at,id),
  CONSTRAINT fk_outbox_order FOREIGN KEY (order_id) REFERENCES trade_orders(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_notices (
  event_id BIGINT NOT NULL,
  buyer_id BIGINT NOT NULL,
  order_id CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  kind VARCHAR(24) NOT NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (event_id),
  KEY ix_notices_buyer (buyer_id,event_id DESC),
  CONSTRAINT fk_notice_outbox FOREIGN KEY (event_id) REFERENCES order_outbox(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
