// The Vite /api proxy keeps requests same-origin in development.
// Authentication uses an HttpOnly session cookie, never localStorage.
let csrfToken = null;
let unauthorizedHandler = null;

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}

// AuthProvider registers this once so any expired or revoked session immediately
// stops rendering protected client-side screens as authenticated.
export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
  return () => {
    if (unauthorizedHandler === handler) unauthorizedHandler = null;
  };
}

export async function refreshCsrf() {
  const response = await fetch('/api/auth/csrf', { credentials: 'include' });
  if (!response.ok) throw new ApiError('Cannot connect to the authentication server.', response.status);
  const data = await response.json();
  csrfToken = data.token;
  return csrfToken;
}

export async function api(path, options = {}) {
  const method = (options.method || 'GET').toUpperCase();
  const headers = new Headers(options.headers || {});
  const bodyIsForm = options.body instanceof URLSearchParams;
  const isBodyObject = options.body !== undefined && !(options.body instanceof FormData) && !bodyIsForm && typeof options.body !== 'string';
  if (isBodyObject) headers.set('Content-Type', 'application/json');
  if (bodyIsForm) headers.set('Content-Type', 'application/x-www-form-urlencoded;charset=UTF-8');
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    if (!csrfToken) await refreshCsrf();
    headers.set('X-CSRF-TOKEN', csrfToken);
  }
  const body = isBodyObject ? JSON.stringify(options.body) : options.body;
  let response;
  try {
    response = await fetch(path, { ...options, method, headers, body, credentials: 'include' });
  } catch {
    throw new ApiError('Cannot reach backend. Check that Spring Boot is running on port 8080.', 0);
  }
  const data = await response.json().catch(() => null);
  if (!response.ok) {
    if (response.status === 401) unauthorizedHandler?.();
    throw new ApiError(data?.error || `Request failed (${response.status})`, response.status);
  }
  return data;
}
