// Memory-only storage avoids leaving a reusable bearer token in persistent browser storage.
let accessToken = null;

export function getAccessToken() {
  return accessToken;
}

export function setAccessToken(token) {
  accessToken = token;
}