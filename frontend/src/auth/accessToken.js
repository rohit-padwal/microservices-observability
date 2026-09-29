/** @file Private module-scoped storage for the current access token; reload intentionally clears the session. */
let accessToken = null;

/**
 * Reads the token attached by the API client to protected requests.
 * @returns {string|null} Current process-memory bearer token, or null after sign-out/reload.
 */
export function getAccessToken() {
  return accessToken;
}

/**
 * Replaces the current access token; pass null to clear an expired or rejected session.
 * @param {string|null} token Backend-issued short-lived JWT.
 */
export function setAccessToken(token) {
  accessToken = token;
}