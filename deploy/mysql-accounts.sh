#!/usr/bin/env bash
set -euo pipefail

for secret in "$CT_IDENTITY_DB_PASSWORD" "$CT_COMMERCE_DB_PASSWORD"; do
  [[ "$secret" =~ ^[A-Za-z0-9_-]{24,128}$ ]] || { echo 'Invalid application database password format' >&2; exit 1; }
done

MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot <<SQL
CREATE USER 'campus_identity'@'%' IDENTIFIED BY '$CT_IDENTITY_DB_PASSWORD';
CREATE USER 'campus_commerce'@'%' IDENTIFIED BY '$CT_COMMERCE_DB_PASSWORD';
GRANT SELECT, INSERT ON campus_trade.users TO 'campus_identity'@'%';
GRANT SELECT, INSERT, UPDATE ON campus_trade.products TO 'campus_commerce'@'%';
GRANT SELECT, INSERT, UPDATE, DELETE ON campus_trade.cart_items TO 'campus_commerce'@'%';
GRANT SELECT, INSERT, UPDATE ON campus_trade.trade_orders TO 'campus_commerce'@'%';
GRANT SELECT, INSERT ON campus_trade.order_items TO 'campus_commerce'@'%';
GRANT SELECT, INSERT, UPDATE ON campus_trade.order_outbox TO 'campus_commerce'@'%';
GRANT SELECT, INSERT, UPDATE ON campus_trade.order_notices TO 'campus_commerce'@'%';
SQL
