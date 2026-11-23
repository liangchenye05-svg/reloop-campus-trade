#!/usr/bin/env bash
set -eu

create_topic() {
  local topic="$1"
  shift
  for attempt in {1..30}; do
    if output=$(sh mqadmin updateTopic -b broker:10911 -n namesrv:9876 -t "$topic" "$@" 2>&1) &&
        [[ "$output" == *"create topic to"*success* ]]; then
      echo "Ready: $topic"
      return 0
    fi
    sleep 2
  done
  printf '%s\n' "$output" >&2
  return 1
}

create_topic CampusTradeNotices
create_topic CampusTradeTimeouts -a '+message.type=DELAY'
