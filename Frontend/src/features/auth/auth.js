import { createClient } from '@supabase/supabase-js';
import { publicAuthConfig } from './auth-config.js';
const config = publicAuthConfig(
    import.meta.env.VITE_SUPABASE_URL,
    import.meta.env.VITE_SUPABASE_PUBLISHABLE_KEY,
);
const client = config.error
    ? null
    : createClient(config.url, config.key, {
          auth: {
              flowType: 'pkce',
              autoRefreshToken: true,
              persistSession: true,
              detectSessionInUrl: true,
          },
      });
function configured() {
    if (!client) throw Error(config.error);
    return client;
}
export async function getSession() {
    const { data, error } = await configured().auth.getSession();
    if (error) throw error;
    return data.session;
}
export async function accessToken() {
    if (!client) return null;
    return (await getSession())?.access_token || null;
}
export async function signIn(email, password) {
    const { data, error } = await configured().auth.signInWithPassword({ email, password });
    if (error) throw error;
    return data;
}
export async function signUp(displayName, email, password) {
    const { data, error } = await configured().auth.signUp({
        email,
        password,
        options: {
            data: { display_name: displayName },
            emailRedirectTo: location.origin + location.pathname,
        },
    });
    if (error) throw error;
    return data;
}
export async function signOut() {
    const { error } = await configured().auth.signOut({ scope: 'local' });
    if (error) throw error;
}
export function onAuthChange(callback) {
    const subscription = client?.auth.onAuthStateChange(callback).data.subscription;
    return () => subscription?.unsubscribe();
}
