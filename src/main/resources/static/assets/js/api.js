// API client for the Spring backend. Pages live under the same context path as the API
// (e.g. http://localhost:8080/api/v1/login.html), so relative URLs like "auth/login" hit the API.

import { t } from './i18n.js';

const STORE_KEY = 'naseej.session';

export class ApiError extends Error {
  constructor(status, message, fieldErrors = null) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors;
  }
}

// ---------- Session (tokens + user) ----------

export const session = {
  get() {
    try {
      const raw = localStorage.getItem(STORE_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  },
  save(auth) {
    const current = this.get() || {};
    const next = {
      accessToken: auth.accessToken ?? current.accessToken,
      refreshToken: auth.refreshToken ?? current.refreshToken,
      user: auth.user ?? current.user ?? null,
      roleSelectionRequired: auth.roleSelectionRequired ?? current.roleSelectionRequired ?? false,
    };
    try { localStorage.setItem(STORE_KEY, JSON.stringify(next)); } catch { /* private mode: session lives in memory only */ }
    memory = next;
    return next;
  },
  setUser(user) {
    return this.save({ user });
  },
  clear() {
    memory = null;
    try { localStorage.removeItem(STORE_KEY); } catch { /* ignore */ }
  },
  user() {
    return (this.get() || memory || {}).user || null;
  },
  isLoggedIn() {
    return !!(this.get() || memory)?.accessToken;
  },
};

// Fallback when localStorage is blocked
let memory = null;

function tokens() {
  return session.get() || memory || {};
}

// ---------- Requests ----------

let refreshing = null;

async function refreshTokens() {
  const { refreshToken } = tokens();
  if (!refreshToken) throw new ApiError(401, t('api.loginAgain'));
  // One refresh at a time even when several requests expire together
  refreshing ??= fetch('auth/refresh', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ refreshToken }),
  }).then(async (res) => {
    if (!res.ok) throw new ApiError(401, t('api.sessionEnded'));
    return session.save(await res.json());
  }).finally(() => { refreshing = null; });
  return refreshing;
}

function query(params) {
  if (!params) return '';
  const entries = Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '');
  return entries.length ? '?' + new URLSearchParams(entries).toString() : '';
}

/**
 * request('GET', 'discover/map', { query: { lat, lng } })
 * Adds the Bearer token, refreshes it once on 401, and turns API errors into ApiError with a readable message.
 */
export async function request(method, path, { body, query: params, auth = true, retry = true } = {}) {
  const headers = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const { accessToken } = tokens();
  if (auth && accessToken) headers.Authorization = `Bearer ${accessToken}`;

  let res;
  try {
    res = await fetch(path + query(params), {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    throw new ApiError(0, t('api.offline'));
  }

  if (res.status === 401 && auth && retry && tokens().refreshToken) {
    try {
      await refreshTokens();
    } catch (err) {
      session.clear();
      redirectToLogin();
      throw err;
    }
    return request(method, path, { body, query: params, auth, retry: false });
  }

  const text = await res.text();
  let data = null;
  if (text) {
    try { data = JSON.parse(text); } catch { data = text; }
  }

  if (!res.ok) {
    if (res.status === 401 && auth) {
      session.clear();
      redirectToLogin();
    }
    const message = (data && typeof data === 'object' && data.message) || defaultMessage(res.status);
    throw new ApiError(res.status, message, data && data.errors ? data.errors : null);
  }
  return data;
}

function defaultMessage(status) {
  if (status === 403) return t('api.forbidden');
  if (status === 404) return t('api.notFound');
  if (status === 502 || status === 503) return t('api.unavailable');
  return t('common.error');
}

export function redirectToLogin() {
  const here = location.pathname.split('/').pop() || 'index.html';
  if (/^(login|register|index|oauth-callback)\.html$/.test(here)) return;
  location.href = 'login.html?next=' + encodeURIComponent(here + location.search);
}

export const api = {
  get: (path, params, opts = {}) => request('GET', path, { ...opts, query: params }),
  post: (path, body, opts = {}) => request('POST', path, { ...opts, body }),
  put: (path, body, opts = {}) => request('PUT', path, { ...opts, body }),
  del: (path, opts = {}) => request('DELETE', path, opts),
};
