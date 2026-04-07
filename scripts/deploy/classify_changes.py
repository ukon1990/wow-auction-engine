#!/usr/bin/env python3
import fnmatch
import json
import sys


def matches(path: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatchcase(path, pattern) for pattern in patterns)


def main() -> int:
    changed_files = [line.strip().replace("\\", "/") for line in sys.stdin if line.strip()]

    backend_patterns = [
        "src/**",
        "pom.xml",
        "mvnw",
        ".mvn/**",
        "Dockerfile",
        "src/main/resources/**",
        "src/test/**",
        ".github/workflows/backend-ci.yml",
        ".github/actions/**",
    ]
    runtime_patterns = [
        "src/main/**",
        "src/main/resources/**",
        "pom.xml",
        "mvnw",
        ".mvn/**",
        "Dockerfile",
    ]
    deploy_patterns = [
        ".github/workflows/deploy-production.yml",
        ".github/workflows/reusable-build-image.yml",
        ".github/workflows/reusable-deploy-region.yml",
        ".github/workflows/manual-infra-sync.yml",
        ".github/actions/**",
        "scripts/deploy/**",
        "infra/regions.json",
    ]
    infra_patterns = [
        "infra/cloudformation/**",
        "infra/regions.json",
        ".github/workflows/deploy-production.yml",
        ".github/workflows/reusable-deploy-region.yml",
        ".github/workflows/manual-infra-sync.yml",
        ".github/actions/**",
        "scripts/deploy/**",
    ]

    docs_only = bool(changed_files) and all(
        matches(
            path,
            [
                "README.md",
                "docs/**",
                "*.md",
                ".gitignore",
                ".editorconfig",
                ".idea/**",
            ],
        )
        and not matches(path, backend_patterns + deploy_patterns + infra_patterns)
        for path in changed_files
    )

    backend_verify_required = any(matches(path, backend_patterns) for path in changed_files)
    infra_apply_required = any(matches(path, infra_patterns) for path in changed_files)
    runtime_changed = any(matches(path, runtime_patterns) for path in changed_files)
    deploy_changed = any(matches(path, deploy_patterns) for path in changed_files)
    app_rollout_required = runtime_changed or deploy_changed or infra_apply_required
    relevant_changed = backend_verify_required or app_rollout_required or infra_apply_required

    result = {
        "changed_files": changed_files,
        "backend_verify_required": backend_verify_required,
        "app_rollout_required": app_rollout_required,
        "infra_apply_required": infra_apply_required,
        "relevant_changed": relevant_changed,
        "docs_only": docs_only,
    }
    json.dump(result, sys.stdout)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
