"""Run API acceptance and concurrent checkout against a disposable CampusTrade database."""
import argparse
import concurrent.futures
import json
import secrets
import urllib.error
import urllib.request
import uuid
from decimal import Decimal


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/") + "/api"
        self.prefix = "ct_" + secrets.token_hex(5)
        self.password = secrets.token_urlsafe(16)

    def call(self, path, method="GET", payload=None, token=None, status=200, headers=None):
        trace = uuid.uuid4().hex
        request_headers = {"X-Trace-Id": trace, **(headers or {})}
        if token:
            request_headers["Authorization"] = "Bearer " + token
        if payload is not None:
            request_headers["Content-Type"] = "application/json"
        request = urllib.request.Request(
            self.base + path,
            data=json.dumps(payload).encode("utf-8") if payload is not None else None,
            headers=request_headers, method=method,
        )
        try:
            response = urllib.request.urlopen(request, timeout=20)
        except urllib.error.HTTPError as error:
            response = error
        with response:
            body = json.load(response)
            expected = status if isinstance(status, tuple) else (status,)
            assert response.code in expected, (method, path, response.code, body)
            assert response.headers.get("X-Trace-Id") == trace == body["traceId"], (trace, body)
            return response.code, body

    def data(self, *args, **kwargs):
        return self.call(*args, **kwargs)[1]["data"]

    def user(self, label):
        username = self.prefix + "_" + label
        credentials = {"username": username, "password": self.password}
        account = self.data("/auth/register", "POST", {**credentials, "displayName": label})
        session = self.data("/auth/login", "POST", credentials)
        assert account == session["user"] and account["role"] == "USER"
        assert "password" not in account and "passwordHash" not in account
        return username, session["token"]

    def listing(self, seller, label, stock):
        spec = {"title": self.prefix + " " + label, "description": "校园闲置物品",
                "category": "BOOKS", "price": 12.30, "stock": stock}
        return self.data("/products", "POST", spec, seller), spec

    def cart(self, token, product, amount):
        self.call(f"/cart/{product}", "PUT", {"quantity": amount}, token)

    def checkout(self, token, products, key, status=200):
        return self.call("/orders", "POST", {"productIds": products}, token, status,
                         {"Idempotency-Key": key})

    def stock(self, product):
        return self.data(f"/products/{product}")["stock"]


def verify_product_and_auth(api, seller, buyer, username):
    api.call("/auth/me", status=401)
    api.call("/auth/me", token="invalid-token", status=401)
    api.call("/auth/login", "POST", {"username": username, "password": "wrong-password"}, status=401)
    api.call("/auth/register", "POST", {"username": username.upper(), "password": api.password,
                                       "displayName": "duplicate"}, status=409)
    assert api.data("/auth/me", token=seller)["username"] == username
    product, spec = api.listing(seller, "book", 4)
    product_id = product["id"]
    api.call("/products", "POST", {**spec, "price": -1}, seller, 400)
    api.call(f"/products/{product_id}", "PUT", spec, buyer, 403)
    api.call(f"/cart/{product_id}", "PUT", {"quantity": 1}, seller, 403)
    api.call(f"/products/{product_id}/status", "PATCH", {"status": "INACTIVE"}, buyer, 403)
    api.call(f"/products/{product_id}/status", "PATCH", {"status": "INACTIVE"}, seller)
    api.call(f"/products/{product_id}", status=404)
    assert api.data("/products/mine", token=seller)["items"][0]["status"] == "INACTIVE"
    api.call(f"/products/{product_id}/status", "PATCH", {"status": "ACTIVE"}, seller)
    api.call(f"/products/{product_id}", "PUT", {**spec, "price": 13.20}, seller)
    page = api.data(f"/products?q={api.prefix}&category=BOOKS&size=1")
    assert page["total"] == 1 and page["items"][0]["id"] == product_id
    api.call("/products?page=0", status=400)
    return product_id, spec


def verify_orders(api, seller, buyer, stranger, product, spec):
    api.cart(buyer, product, 1)
    api.cart(buyer, product, 2)
    assert api.data("/cart", token=buyer)[0]["quantity"] == 2
    key = uuid.uuid4().hex
    order = api.checkout(buyer, [product], key)[1]["data"]
    order_path = "/orders/" + order["id"]
    assert order["status"] == "PENDING_PAYMENT" and Decimal(str(order["total"])) == Decimal("26.40")
    assert api.stock(product) == 2 and not api.data("/cart", token=buyer)
    assert api.checkout(buyer, [product], key)[1]["data"]["id"] == order["id"]
    api.checkout(buyer, [product + 1], key, 409)
    api.call(order_path, token=stranger, status=404)
    api.call(order_path + "/complete", "POST", token=buyer, status=409)
    api.call(f"/products/{product}", "PUT", {**spec, "title": spec["title"] + " edited", "stock": 100000},
             seller, 409)
    api.call(f"/products/{product}", "PUT", {**spec, "title": spec["title"] + " edited", "stock": 2}, seller)
    assert api.data(order_path, token=buyer)["items"][0]["title"] == spec["title"]
    for _ in range(2):
        cancelled = api.data(order_path + "/cancel", "POST", token=buyer)
        assert cancelled["status"] == "CANCELLED" and api.stock(product) == 4
    api.cart(buyer, product, 2)
    new_order = api.checkout(buyer, [product], uuid.uuid4().hex)[1]["data"]
    order_path = "/orders/" + new_order["id"]
    for _ in range(2):
        assert api.data(order_path + "/pay", "POST", token=buyer)["status"] == "PAID"
    api.call(order_path + "/cancel", "POST", token=buyer, status=409)
    for _ in range(2):
        assert api.data(order_path + "/complete", "POST", token=buyer)["status"] == "COMPLETED"
    assert api.data("/orders", token=buyer)["total"] == 2 and api.stock(product) == 2

    second, second_spec = api.listing(seller, "rollback", 1)
    api.cart(buyer, product, 1)
    api.cart(buyer, second["id"], 1)
    api.call(f"/products/{second['id']}", "PUT", {**second_spec, "stock": 0}, seller)
    api.checkout(buyer, [product, second["id"]], uuid.uuid4().hex, 409)
    assert api.stock(product) == 2 and len(api.data("/cart", token=buyer)) == 2
    api.call(f"/cart/{product}", "DELETE", token=buyer)
    api.call(f"/cart/{second['id']}", "DELETE", token=buyer)


def verify_concurrency(api, seller, buyer, workers):
    product = api.listing(seller, "race", 1)[0]["id"]
    contenders = [api.user("race" + str(n))[1] for n in range(workers)]
    for token in contenders:
        api.cart(token, product, 1)

    def race(token):
        code, body = api.checkout(token, [product], uuid.uuid4().hex, (200, 409))
        if code == 409:
            assert body["code"] == "OUT_OF_STOCK", body
        return code, body, token

    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        results = list(pool.map(race, contenders))
    winners = [(body, token) for code, body, token in results if code == 200]
    assert len(winners) == 1 and api.stock(product) == 0, results
    order, token = winners[0]
    api.call("/orders/" + order["data"]["id"] + "/cancel", "POST", token=token)
    assert api.stock(product) == 1

    api.cart(buyer, product, 1)
    key = uuid.uuid4().hex
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
        retries = list(pool.map(lambda _: api.checkout(buyer, [product], key)[1]["data"]["id"], range(workers)))
    assert len(set(retries)) == 1 and api.stock(product) == 0
    api.call("/orders/" + retries[0] + "/cancel", "POST", token=buyer)
    assert api.stock(product) == 1
    print(f"LAST_UNIT: {workers} requests, 1 success, {workers - 1} conflicts, stock restored once")
    print(f"IDEMPOTENCY: {workers} requests, 1 order UUID")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--workers", type=int, default=12)
    args = parser.parse_args()
    api = Api(args.base)
    username, seller = api.user("seller")
    _, buyer = api.user("buyer")
    _, stranger = api.user("stranger")
    product, spec = verify_product_and_auth(api, seller, buyer, username)
    print("AUTH_PRODUCT: passed")
    verify_orders(api, seller, buyer, stranger, product, spec)
    print("CART_ORDER: paid, completed, cancelled, rollback verified")
    verify_concurrency(api, seller, buyer, args.workers)
    print(f"PASS dataset={api.prefix}")


if __name__ == "__main__":
    main()
