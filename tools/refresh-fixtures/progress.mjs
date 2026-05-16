const REQUEST_LOG_INTERVAL_MS = 5000;

export function createProgressReporter({ enabled = true } = {}) {
    let lastRequestLogAt = 0;

    return {
        log(message) {
            if (enabled) {
                console.error(message);
            }
        },
        logRequest(endpointPath, { current, total } = {}) {
            if (!enabled) {
                return;
            }
            const now = Date.now();
            const hasCounter = Number.isFinite(current) && Number.isFinite(total);
            const shouldLog =
                (hasCounter && (current === 1 || current === total || current % 50 === 0)) ||
                now - lastRequestLogAt >= REQUEST_LOG_INTERVAL_MS;
            if (!shouldLog) {
                return;
            }
            lastRequestLogAt = now;
            const prefix = hasCounter ? `[${current}/${total}] ` : "";
            console.error(`${prefix}GET ${endpointPath}`);
        },
    };
}
