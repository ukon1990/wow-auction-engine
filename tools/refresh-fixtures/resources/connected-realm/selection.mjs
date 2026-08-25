/**
 * Parse a connected-realm id from an index href such as
 * https://eu.api.blizzard.com/data/wow/connected-realm/1084?namespace=dynamic-eu
 */
export function parseConnectedRealmIdFromHref(href) {
    if (typeof href !== "string" || href.length === 0) {
        return null;
    }

    try {
        const pathname = new URL(href).pathname;
        const match = pathname.match(/\/connected-realm\/(\d+)\/?$/);
        if (!match) {
            return null;
        }
        const id = parseInt(match[1], 10);
        return Number.isFinite(id) ? id : null;
    } catch {
        return null;
    }
}

export function collectConnectedRealmIdsFromIndex(indexPayload) {
    const ids = [];
    for (const entry of indexPayload?.connected_realms ?? []) {
        const id = parseConnectedRealmIdFromHref(entry?.href);
        if (id != null) {
            ids.push(id);
        }
    }
    return [...new Set(ids)].sort((left, right) => left - right);
}

/**
 * @param {object} indexPayload
 * @param {{ sampleSize?: number, connectedRealmIds?: number[] | null, full?: boolean }} options
 */
export function pickConnectedRealmIds(indexPayload, { sampleSize = 40, connectedRealmIds = null, full = false } = {}) {
    const availableIds = collectConnectedRealmIdsFromIndex(indexPayload);
    if (availableIds.length === 0) {
        throw new Error("Connected realm index did not contain any parseable connected-realm hrefs.");
    }

    if (connectedRealmIds?.length) {
        const available = new Set(availableIds);
        const missing = connectedRealmIds.filter((id) => !available.has(id));
        if (missing.length > 0) {
            throw new Error(
                `Connected realm id(s) not present in regional index: ${missing.join(", ")}`,
            );
        }
        return [...new Set(connectedRealmIds)].sort((left, right) => left - right);
    }

    if (full) {
        return availableIds;
    }

    return availableIds.slice(0, sampleSize);
}

export function filterConnectedRealmIndex(indexPayload, selectedIds) {
    const allowed = new Set(selectedIds);
    const filtered = (indexPayload?.connected_realms ?? []).filter((entry) => {
        const id = parseConnectedRealmIdFromHref(entry?.href);
        return id != null && allowed.has(id);
    });

    filtered.sort((left, right) => {
        const leftId = parseConnectedRealmIdFromHref(left.href) ?? 0;
        const rightId = parseConnectedRealmIdFromHref(right.href) ?? 0;
        return leftId - rightId;
    });

    return {
        ...indexPayload,
        connected_realms: filtered,
    };
}
