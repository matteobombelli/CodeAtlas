/**
 * Joins an absolute API path onto the base path the app was built with, so the
 * same bundle works at the site root and under a reverse-proxy prefix.
 */
export function apiUrl(path: string): string {
  return `${import.meta.env.BASE_URL.replace(/\/$/, '')}${path}`
}
