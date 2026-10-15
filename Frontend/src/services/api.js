import { accessToken } from '../features/auth/auth.js';
const base = import.meta.env.VITE_API_BASE || '/api';
if (!base.startsWith('/') || base.startsWith('//'))
    throw Error('VITE_API_BASE must be a same-origin path.');
export class ApiError extends Error {
    constructor(status, payload) {
        super(payload.message || 'Request failed.');
        this.status = status;
        this.fields = payload.fields || {};
        this.code = payload.code;
    }
}
export async function api(path, { method = 'GET', body } = {}) {
    const headers = {},
        token = await accessToken();
    if (token) headers.Authorization = 'Bearer ' + token;
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    let response;
    try {
        response = await fetch(base + path, {
            method,
            headers,
            credentials: 'omit',
            cache: 'no-store',
            body: body === undefined ? undefined : JSON.stringify(body),
        });
    } catch {
        throw Error('Connection failed. Check the backend and try again.');
    }
    if (response.status === 204) return null;
    const payload = await response
        .json()
        .catch(() => ({ message: 'Unexpected server response. Please try again.' }));
    if (!response.ok) {
        if (response.status === 401 && token)
            window.dispatchEvent(new Event('clearpaper:auth-expired'));
        throw new ApiError(response.status, payload);
    }
    return payload;
}
