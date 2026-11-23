"""Fault injection for a disposable database and an application running with RocketMQ enabled."""
import argparse
import subprocess
import uuid
from pathlib import Path

from events import await_value
from exercise import Api


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base", required=True)
    parser.add_argument("--mysql", required=True)
    parser.add_argument("--mysql-port", type=int, default=3306)
    parser.add_argument("--db", default="campus_trade")
    parser.add_argument("--log", type=Path, required=True)
    parser.add_argument("--java")
    parser.add_argument("--rocket-libs")
    parser.add_argument("--namesrv", default="127.0.0.1:9876")
    args = parser.parse_args()
    assert args.db == "campus_trade", "Run on a dedicated local campus_trade test instance"
    api = Api(args.base)
    _, seller = api.user("seller")
    _, buyer = api.user("buyer")
    buyer_id = api.data("/auth/me", token=buyer)["id"]
    item = api.listing(seller, "retry", 2)[0]["id"]
    api.cart(buyer, item, 1)
    order = api.checkout(buyer, [item], uuid.uuid4().hex)[1]["data"]["id"]

    def sql(command):
        cmd = [args.mysql, "--no-defaults", "--protocol=TCP", "-h127.0.0.1", f"-P{args.mysql_port}",
               "-uroot", f"--database={args.db}", "--batch", "--skip-column-names"]
        return subprocess.check_output(cmd, input="DELIMITER //\n" + command + "//\n", text=True).strip()

    def log_contents():
        return args.log.read_text(encoding="utf-8", errors="replace")

    name = "reject_notice_" + uuid.uuid4().hex[:10]
    sql(f"CREATE TRIGGER {name} BEFORE INSERT ON order_notices FOR EACH ROW "
        "BEGIN "
        f"IF NEW.buyer_id = {buyer_id} AND NEW.kind = 'PAID' THEN "
        "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'CampusTrade retry test'; END IF; END")
    try:
        api.call(f"/orders/{order}/pay", "POST", token=buyer)
        event = sql(f"SELECT id FROM order_outbox WHERE order_id='{order}' AND kind='PAID'")
        await_value(lambda: f"consumer will retry event {event} " in log_contents(), 35)
        assert sql(f"SELECT COUNT(*) FROM order_notices WHERE event_id={event}") == "0"
    finally:
        sql(f"DROP TRIGGER {name}")

    await_value(lambda: sql(f"SELECT COUNT(*) FROM order_notices WHERE event_id={event}") == "1", 45)
    print(f"CONSUMER_RETRY event={event}, failed insert retried, 1 durable notice")

    if args.java and args.rocket_libs:
        message = [f"-Duser.home={args.log.resolve().parent}", "-cp", args.rocket_libs, "org.apache.rocketmq.tools.command.MQAdminStartup",
                   "sendMessage", "-n", args.namesrv, "-t", "CampusTradeNotices",
                   "-k", str(event), "-p", str(event)]
        previous = log_contents().count(f"Consumed order event {event} PAID")
        subprocess.run([args.java, *message], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
        await_value(lambda: log_contents().count(f"Consumed order event {event} PAID") > previous, 35)
        assert sql(f"SELECT COUNT(*) FROM order_notices WHERE event_id={event}") == "1"
        print(f"DUPLICATE_DELIVERY event={event}, second broker delivery observed, 1 durable notice")


if __name__ == "__main__":
    main()
