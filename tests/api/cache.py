"""Verify actual Redis cache entries and MySQL-backed product invalidation."""
import argparse
import subprocess
import uuid

from exercise import Api


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--redis-cli", default="redis-cli")
    parser.add_argument("--redis-port", type=int, default=6379)
    args = parser.parse_args()
    api = Api(args.base)

    def redis(*command):
        output = subprocess.check_output([args.redis_cli, "-p", str(args.redis_port), "--raw", *command], text=True)
        return output.strip()

    _, seller = api.user("seller")
    _, buyer = api.user("buyer")
    product, spec = api.listing(seller, "cache", 2)
    product_id = product["id"]
    path = f"/products/{product_id}"
    detail_key = f"campus-trade:v2:product-details::{product_id}"
    hot_key = "campus-trade:v2:product-hot::all"
    missing_id = 2000000000 + product_id
    miss_key = f"campus-trade:v2:product-details::{missing_id}"

    assert api.stock(product_id) == 2 and api.data(path)["title"] == spec["title"]
    api.data("/products")
    assert 0 < int(redis("PTTL", detail_key)) <= 60000
    assert 0 < int(redis("PTTL", hot_key)) <= 30000
    api.call(f"/products/{missing_id}", status=404)
    api.call(f"/products/{missing_id}", status=404)
    assert 0 < int(redis("PTTL", miss_key)) <= 10000
    api.call(path, "PUT", {**spec, "price": 8.90}, seller)
    assert redis("EXISTS", detail_key) == "0" and redis("EXISTS", hot_key) == "0"
    assert api.data(path)["price"] == 8.9
    api.cart(buyer, product_id, 2)
    order = api.checkout(buyer, [product_id], uuid.uuid4().hex)[1]["data"]
    assert api.stock(product_id) == 0
    api.call("/orders/" + order["id"] + "/cancel", "POST", token=buyer)
    assert api.stock(product_id) == 2
    api.call(path + "/status", "PATCH", {"status": "INACTIVE"}, seller)
    api.call(path, status=404)
    api.call(path + "/status", "PATCH", {"status": "ACTIVE"}, seller)
    assert api.stock(product_id) == 2
    print("REDIS: hot/detail keys, positive/negative TTL, edit/status/checkout/cancel invalidation passed")
    print(f"BENCH_PRODUCT={product_id}")


if __name__ == "__main__":
    main()
