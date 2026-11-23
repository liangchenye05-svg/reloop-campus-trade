"""Measure equivalent HTTP workloads against separate cached and uncached JVMs."""
import argparse
import concurrent.futures
import http.client
import json
import statistics
import time
import urllib.parse
from pathlib import Path


def measure(base, product_id, requests, workers):
    url = urllib.parse.urlparse(base)
    paths = [f"/api/products/{product_id}", "/api/products"]

    def send(worker):
        elapsed = []
        connection = http.client.HTTPConnection(url.hostname, url.port, timeout=15)
        try:
            for i in range(worker, requests, workers):
                start = time.perf_counter()
                connection.request("GET", paths[i % len(paths)])
                response = connection.getresponse()
                body = response.read()
                assert response.status == 200 and json.loads(body)["code"] == "OK", (response.status, body)
                elapsed.append((time.perf_counter() - start) * 1000)
        finally:
            connection.close()
        return elapsed

    start = time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(workers) as pool:
        results = sorted(value for group in pool.map(send, range(workers)) for value in group)
    seconds = time.perf_counter() - start
    return dict(n=len(results), req_per_s=round(len(results) / seconds, 2),
                p50_ms=round(statistics.median(results), 2), p95_ms=round(results[(len(results) * 95 + 99) // 100 - 1], 2))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", required=True)
    parser.add_argument("--redis", required=True)
    parser.add_argument("--product", required=True, type=int)
    parser.add_argument("--requests", type=int, default=1200)
    parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--output", type=Path)
    options = parser.parse_args()
    variants = {"mysql": options.baseline, "redis": options.redis}
    samples = {variant: [] for variant in variants}
    for base in variants.values():
        measure(base, options.product, 240, options.workers)
    for round_number in range(3):
        order = list(variants)
        if round_number % 2:
            order.reverse()
        for variant in order:
            samples[variant].append(measure(variants[variant], options.product, options.requests, options.workers))
    result = json.dumps(samples, indent=2)
    print(result)
    if options.output:
        options.output.write_text(result + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
