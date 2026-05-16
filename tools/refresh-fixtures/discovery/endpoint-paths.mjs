function shouldDiscoverHref(pathParts) {
    if (pathParts.length < 2) {
        return false;
    }
    const parent = pathParts.at(-2);
    const current = pathParts.at(-1);
    return parent === "key" && current === "href";
}

export function isExcludedEndpoint(endpointPath) {
    return endpointPath.startsWith("media/");
}

export function normalizeEndpointPath(input) {
    const url = typeof input === "string" ? new URL(input) : input;
    const prefix = "/data/wow/";

    if (!url.pathname.startsWith(prefix)) {
        return null;
    }

    return url.pathname.slice(prefix.length).replace(/^\/+|\/+$/g, "");
}

export function collectLinkedEndpointPaths(payload) {
    const discovered = new Set();

    function visit(value, pathParts) {
        if (Array.isArray(value)) {
            value.forEach((entry, index) => visit(entry, pathParts.concat(String(index))));
            return;
        }

        if (!value || typeof value !== "object") {
            return;
        }

        for (const [key, nested] of Object.entries(value)) {
            const nextPath = pathParts.concat(key);

            if (typeof nested === "string" && shouldDiscoverHref(nextPath) && !nextPath.includes("_links")) {
                const endpointPath = normalizeEndpointPath(nested);
                if (endpointPath && !isExcludedEndpoint(endpointPath)) {
                    discovered.add(endpointPath);
                }
            }

            visit(nested, nextPath);
        }
    }

    visit(payload, []);
    return [...discovered].sort();
}
