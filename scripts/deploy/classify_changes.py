#!/usr/bin/env python3
import fnmatch
import json
import sys


def matches(path: str, patterns: list[str]) -> bool:
    return any(fnmatch.fnmatchcase(path, pattern) for pattern in patterns)


def main() -> int:
    changed_files = [line.strip().replace("\\", "/") for line in sys.stdin if line.strip()]

    backend_patterns = [
        "backend/src/**",
        "backend/pom.xml",
        "backend/mvnw",
        "backend/mvnw.cmd",
        "backend/.mvn/**",
        "backend/Dockerfile",
        "backend/src/main/resources/**",
        "backend/src/test/**",
        ".github/workflows/ci.yml",
        ".github/actions/**",
    ]
    frontend_patterns = [
        "frontend/src/**",
        "frontend/public/**",
        "frontend/package.json",
        "bun.lock",
        "frontend/angular.json",
        "frontend/tsconfig*.json",
        "frontend/.prettierrc",
        "frontend/.postcssrc.json",
        "frontend/eslint.config.*",
        "frontend/Dockerfile",
        ".github/workflows/ci.yml",
        ".github/actions/**",
    ]
    backend_runtime_patterns = [
        "backend/src/main/**",
        "backend/src/main/resources/**",
        "backend/pom.xml",
        "backend/mvnw",
        "backend/mvnw.cmd",
        "backend/.mvn/**",
        "backend/Dockerfile",
    ]
    frontend_runtime_patterns = [
        "frontend/src/**",
        "frontend/public/**",
        "frontend/package.json",
        "bun.lock",
        "frontend/angular.json",
        "frontend/tsconfig*.json",
        "frontend/.postcssrc.json",
        "frontend/Dockerfile",
    ]
    deploy_patterns = [
        ".github/workflows/deploy-production.yml",
        ".github/workflows/deploy-vps.yml",
        ".github/workflows/reusable-build-image.yml",
        ".github/workflows/reusable-deploy-region.yml",
        ".github/workflows/manual-infra-sync.yml",
        ".github/actions/**",
        "scripts/deploy/**",
        "infra/regions.json",
        "infra/kubernetes/**",
    ]
    infra_patterns = [
        "infra/cloudformation/**",
        "infra/kubernetes/**",
        "infra/regions.json",
        ".github/workflows/deploy-production.yml",
        ".github/workflows/deploy-vps.yml",
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
        and not matches(path, backend_patterns + frontend_patterns + deploy_patterns + infra_patterns)
        for path in changed_files
    )

    backend_verify_required = any(matches(path, backend_patterns) for path in changed_files)
    frontend_verify_required = any(matches(path, frontend_patterns) for path in changed_files)
    infra_apply_required = any(matches(path, infra_patterns) for path in changed_files)
    backend_runtime_changed = any(matches(path, backend_runtime_patterns) for path in changed_files)
    frontend_runtime_changed = any(matches(path, frontend_runtime_patterns) for path in changed_files)
    deploy_changed = any(matches(path, deploy_patterns) for path in changed_files)
    backend_rollout_required = backend_runtime_changed or deploy_changed or infra_apply_required
    frontend_rollout_required = frontend_runtime_changed or deploy_changed or infra_apply_required
    app_rollout_required = backend_rollout_required or frontend_rollout_required
    relevant_changed = backend_verify_required or frontend_verify_required or app_rollout_required or infra_apply_required

    result = {
        "changed_files": changed_files,
        "backend_verify_required": backend_verify_required,
        "frontend_verify_required": frontend_verify_required,
        "backend_rollout_required": backend_rollout_required,
        "frontend_rollout_required": frontend_rollout_required,
        "app_rollout_required": app_rollout_required,
        "infra_apply_required": infra_apply_required,
        "relevant_changed": relevant_changed,
        "docs_only": docs_only,
    }
    json.dump(result, sys.stdout)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
