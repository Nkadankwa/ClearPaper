export function publicAuthConfig(url, key) {
    if (!url || !key)
        return {
            error: 'Set VITE_SUPABASE_URL and VITE_SUPABASE_PUBLISHABLE_KEY in Frontend/.env, then restart the frontend.',
        };
    try {
        const parsed = new URL(url);
        if (
            parsed.protocol !== 'https:' &&
            !(
                parsed.protocol === 'http:' &&
                ['localhost', '127.0.0.1', '[::1]'].includes(parsed.hostname)
            )
        )
            throw Error();
        if (
            parsed.username ||
            parsed.password ||
            parsed.search ||
            parsed.hash ||
            !['', '/'].includes(parsed.pathname)
        )
            throw Error();
    } catch {
        return {
            error: 'Use the Supabase project URL, for example https://your-project.supabase.co.',
        };
    }
    if (!key.startsWith('sb_publishable_'))
        return {
            error: 'Use the Supabase publishable key in the frontend. Never use a secret key, database password or service-role key.',
        };
    return { url: url.replace(/\/+$/, ''), key };
}
