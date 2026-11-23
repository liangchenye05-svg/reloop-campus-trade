"""Verify notification delivery, timeout and stock restore using a real RocketMQ broker."""
import argparse
import time
import uuid

from exercise import Api


def await_value(call, seconds):
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        result = call()
        if result:
            return result
        time.sleep(0.25)
    raise AssertionError(f"Condition not met after {seconds} seconds")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", default="http://127.0.0.1:8080")
    parser.add_argument("--expiry-seconds", type=int, default=8)
    args = parser.parse_args()
    api = Api(args.base)
    _, seller = api.user("seller")
    _, buyer = api.user("buyer")
    item = api.listing(seller, "mq events", 2)[0]["id"]

    def order():
        api.cart(buyer, item, 1)
        return api.checkout(buyer, [item], uuid.uuid4().hex)[1]["data"]["id"]

    def notices(order_id, kind):
        return [row for row in api.data("/orders/notices", token=buyer)["items"]
                if row["orderId"] == order_id and row["kind"] == kind]

    paid = order()
    _, payment = api.call(f"/orders/{paid}/pay", "POST", token=buyer)
    assert payment["data"]["status"] == "PAID"
    assert len(await_value(lambda: notices(paid, "PAID"), 25)) == 1

    start = time.monotonic()
    expiring = order()
    assert api.stock(item) == 0
    assert api.data(f"/orders/{expiring}", token=buyer)["status"] == "PENDING_PAYMENT"
    await_value(lambda: api.data(f"/orders/{expiring}", token=buyer)["status"] == "EXPIRED",
                args.expiry_seconds + 12)
    delay = time.monotonic() - start
    assert delay >= args.expiry_seconds - 0.5, delay
    assert api.stock(item) == 1
    assert len(await_value(lambda: notices(expiring, "EXPIRED"), 10)) == 1
    assert api.data(f"/orders/{paid}", token=buyer)["status"] == "PAID"
    print(f"ROCKETMQ_PAYMENT_TRACE={payment['traceId']}")
    print(f"EXPIRY elapsed_seconds={delay:.3f}, order={expiring}, stock_restored=1")
    print(f"PASS dataset={api.prefix}")


if __name__ == "__main__":
    main()
