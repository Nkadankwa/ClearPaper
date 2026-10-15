import test from 'node:test';
import assert from 'node:assert/strict';
import { publicAuthConfig } from '../../src/features/auth/auth-config.js';
test('frontend configuration permits public keys and refuses secrets or unsafe project URLs', () => {
    assert.ok(publicAuthConfig('', '').error);
    for (const key of ['sb_secret_private', 'service_role', 'eyJlegacytoken'])
        assert.ok(publicAuthConfig('https://example.supabase.co', key).error);
    for (const url of [
        'http://remote.example',
        'https://user:password@example.com',
        'https://example.com?secret=1',
        'https://example.com/auth/v1',
    ])
        assert.ok(publicAuthConfig(url, 'sb_publishable_test').error);
    assert.deepEqual(publicAuthConfig('https://example.supabase.co/', 'sb_publishable_test'), {
        url: 'https://example.supabase.co',
        key: 'sb_publishable_test',
    });
    assert.equal(
        publicAuthConfig('http://127.0.0.1:54321', 'sb_publishable_test').url,
        'http://127.0.0.1:54321',
    );
});
