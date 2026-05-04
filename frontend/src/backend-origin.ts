function trimTrailingSlash(value: string): string {
  return value.endsWith('/') ? value.slice(0, -1) : value;
}

export function resolveBackendOrigin(): string {
  const explicitOrigin = process.env['BACKEND_ORIGIN']?.trim();
  if (explicitOrigin) {
    return trimTrailingSlash(explicitOrigin);
  }

  const appName = process.env['APP_NAME']?.trim();
  if (appName) {
    const backendContainerName = appName.endsWith('-frontend')
      ? appName.replace(/-frontend$/, '-backend')
      : `${appName}-backend`;
    return `http://${backendContainerName}:8080`;
  }

  return 'http://localhost:8080';
}
