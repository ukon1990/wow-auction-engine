import { blizzardConfig } from "../config.mjs";

/**
 * @typedef {object} ApiClient
 * @property {(endpointPath: string) => Promise<unknown>} fetchJson
 */

export function createApiClient({ fetchImpl = fetch, tokenProvider = fetchAccessToken, onRequest } = {}) {
    let cachedTokenPromise;

    return {
        async fetchJson(endpointPath, requestMeta) {
            cachedTokenPromise ??= tokenProvider({ fetchImpl });
            const token = await cachedTokenPromise;
            return getJson(endpointPath, token, {
                fetchImpl,
                onRequest: (path) => onRequest?.(path, requestMeta),
            });
        },
    };
}

async function fetchAccessToken({ fetchImpl = fetch } = {}) {
    const directToken = process.env.BLIZZARD_ACCESS_TOKEN;
    if (directToken) {
        return directToken;
    }

    const clientId = process.env.BLIZZARD_CLIENT_ID;
    const clientSecret = process.env.BLIZZARD_CLIENT_SECRET;
    if (!clientSecret || !clientId) {
        throw new Error(
            "Missing Blizzard credentials. Set BLIZZARD_ACCESS_TOKEN or BLIZZARD_CLIENT_ID + BLIZZARD_CLIENT_SECRET.",
        );
    }

    const body = new URLSearchParams({
        grant_type: "client_credentials",
        client_id: clientId,
        client_secret: clientSecret,
    });

    const response = await fetchImpl(blizzardConfig.tokenUrl, {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded",
        },
        body,
    });
    if (!response.ok) {
        const text = await response.text();
        throw new Error(`Failed to refresh token (${response.status}): ${text}`);
    }

    const payload = await response.json();
    if (!payload.access_token) {
        throw new Error("Token response did not include access_token");
    }
    return payload.access_token;
}

const REQUEST_TIMEOUT_MS = parseInt(process.env.BLIZZARD_REQUEST_TIMEOUT_MS ?? "120000", 10);

async function getJson(endpointPath, token, { fetchImpl = fetch, onRequest } = {}) {
    const url = new URL(`${blizzardConfig.baseUrl}/${endpointPath.replace(/^\//, "")}`);
    url.searchParams.set("namespace", blizzardConfig.namespace);
    onRequest?.(endpointPath);

    const response = await fetchImpl(url, {
        headers: {
            Authorization: `Bearer ${token}`,
        },
        signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
    });
    if (!response.ok) {
        const text = await response.text();
        throw new Error(`GET ${url} failed (${response.status}): ${text}`);
    }

    return response.json();
}
