/** Joins an API path onto the base path baked into the frontend bundle. */
export function apiUrl(path: string): string {
  return `${import.meta.env.BASE_URL.replace(/\/$/, '')}${path}`
}
