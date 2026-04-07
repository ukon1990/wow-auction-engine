#!/usr/bin/env python3

import json
from pathlib import Path


def main() -> None:
    config = json.loads(Path("infra/regions.json").read_text(encoding="utf-8"))
    regions = [region for region in config["regions"] if region.get("enabled", False)]
    print(json.dumps({"include": regions}))


if __name__ == "__main__":
    main()
