<script setup>
    import { ref, reactive, onMounted, onUnmounted, watch, nextTick } from 'vue';
    import { api } from './services/api.js';
    import { getSession, signIn, signUp, signOut, onAuthChange } from './features/auth/auth.js';
    import { clearDrafts } from './features/worksheets/worksheets.js';
    import { readRoute } from './app/routes.js';
    import TeacherShell from './app/layouts/TeacherShell.vue';
    import StudentShell from './app/layouts/StudentShell.vue';
    import SharedResource from './features/sharing/components/SharedResource.vue';
    const route = ref(readRoute(location.hash)),
        teacher = ref(null),
        mode = ref('login'),
        busy = ref(true),
        ready = ref(false),
        error = ref(''),
        notice = ref(''),
        fields = ref({});
    const form = reactive({ displayName: '', email: '', password: '' });
    function updateRoute() {
        route.value = readRoute(location.hash);
    }
    function focusMain() {
        document.getElementById('workspace-main')?.focus({ preventScroll: true });
    }
    function expired() {
        teacher.value = null;
        clearDrafts();
        error.value = 'Your session is no longer accepted. Sign in again.';
    }
    window.addEventListener('hashchange', updateRoute);
    window.addEventListener('clearpaper:auth-expired', expired);
    watch(
        () => route.value.path,
        async () => {
            if (!route.value.student && !ready.value && !busy.value) await initialise();
            await nextTick();
            focusMain();
            window.scrollTo(0, 0);
        },
    );
    async function initialise() {
        busy.value = true;
        error.value = '';
        ready.value = false;
        try {
            const session = await getSession();
            ready.value = true;
            teacher.value = session ? await api('/auth/me') : null;
            updateRoute();
        } catch (e) {
            error.value = e.message;
        } finally {
            busy.value = false;
        }
    }
    function chooseMode(value) {
        mode.value = value;
        form.password = '';
        error.value = '';
        notice.value = '';
        fields.value = {};
    }
    async function submit() {
        busy.value = true;
        error.value = '';
        notice.value = '';
        fields.value = {};
        try {
            if (mode.value === 'register') {
                const result = await signUp(
                    form.displayName.trim(),
                    form.email.trim(),
                    form.password,
                );
                form.password = '';
                if (result.session) teacher.value = await api('/auth/me');
                else {
                    chooseMode('login');
                    notice.value =
                        'Check your email to confirm your account, then sign in. If you already have an account, use Sign in.';
                }
            } else {
                await signIn(form.email.trim(), form.password);
                form.password = '';
                teacher.value = await api('/auth/me');
            }
            const returnShare = new URLSearchParams(route.value.query).get('returnShare');
            if (teacher.value && returnShare)
                location.hash = '/shared?token=' + encodeURIComponent(returnShare);
        } catch (e) {
            error.value = e.message;
            fields.value = e.fields || {};
        } finally {
            busy.value = false;
        }
    }
    async function logout() {
        busy.value = true;
        error.value = '';
        try {
            await signOut();
            teacher.value = null;
            clearDrafts();
            form.password = '';
            notice.value = 'You have signed out.';
        } catch (e) {
            error.value = e.message;
        } finally {
            busy.value = false;
        }
    }
    watch(teacher, async (value) => {
        if (value) {
            await nextTick();
            focusMain();
        }
    });
    let unsubscribe;
    onMounted(() => {
        unsubscribe = onAuthChange((event, session) => {
            if (event === 'SIGNED_OUT') {
                teacher.value = null;
                clearDrafts();
            } else if (
                event === 'SIGNED_IN' &&
                teacher.value?.id !== session?.user.id &&
                !busy.value
            ) {
                teacher.value = null;
                clearDrafts();
                setTimeout(initialise, 0);
            }
        });
        if (route.value.student) busy.value = false;
        else initialise();
    });
    onUnmounted(() => {
        unsubscribe?.();
        window.removeEventListener('hashchange', updateRoute);
        window.removeEventListener('clearpaper:auth-expired', expired);
    });
</script>

<template>
    <SharedResource v-if="route.path === '/shared'" :route="route" :teacher="teacher" />
    <StudentShell v-else-if="route.student" :route="route" />
    <TeacherShell
        v-else-if="teacher"
        :key="teacher.id"
        :teacher="teacher"
        :route="route"
        :busy="busy"
        :error="error"
        @logout="logout"
    />
    <div v-else class="auth-layout">
        <a class="auth-skip" href="#workspace-main" @click.prevent="focusMain">
            Skip to account form
        </a>
        <header class="auth-header">
            <a class="brand" href="/">ClearPaper</a>
            <span>Teacher workspace</span>
        </header>
        <main id="workspace-main" class="auth-main" tabindex="-1">
            <section class="intro">
                <h1>Clear questions start here.</h1>
                <p>Create diagrams, prepare worksheets and follow your pupils’ reading progress.</p>
                <p class="scope">
                    Sign in with your teacher account to open your library, diagram studio,
                    worksheets and reading workspace.
                </p>
            </section>
            <section class="account" aria-label="Teacher account">
                <p v-if="error" class="error" role="alert">{{ error }}</p>
                <p v-if="notice" class="notice" role="status">{{ notice }}</p>
                <template v-if="teacher">
                    <h2>Welcome, {{ teacher.displayName }}</h2>
                    <p>{{ teacher.email }}</p>
                    <p class="scope">You are signed in to your own teacher account.</p>
                    <button :disabled="busy" @click="logout">
                        {{ busy ? 'Please wait…' : 'Sign out' }}
                    </button>
                </template>
                <template v-else>
                    <div class="tabs" aria-label="Account action">
                        <button
                            :aria-pressed="mode === 'login'"
                            :disabled="busy"
                            @click="chooseMode('login')"
                        >
                            Sign in
                        </button>
                        <button
                            :aria-pressed="mode === 'register'"
                            :disabled="busy"
                            @click="chooseMode('register')"
                        >
                            Create account
                        </button>
                    </div>
                    <h2>
                        {{
                            mode === 'register'
                                ? 'Create your teacher account'
                                : 'Sign in to ClearPaper'
                        }}
                    </h2>
                    <p v-if="busy && !ready" role="status">Connecting to your workspace…</p>
                    <form @submit.prevent="submit">
                        <template v-if="mode === 'register'">
                            <label for="displayName">Your name</label>
                            <input
                                id="displayName"
                                v-model="form.displayName"
                                autocomplete="name"
                                required
                                maxlength="80"
                                :disabled="busy"
                                :aria-invalid="!!fields.displayName"
                                aria-describedby="name-error"
                            />
                            <small id="name-error" class="field-error">
                                {{ fields.displayName }}
                            </small>
                        </template>
                        <label for="email">Email address</label>
                        <input
                            id="email"
                            v-model="form.email"
                            type="email"
                            autocomplete="username"
                            required
                            maxlength="254"
                            :disabled="busy"
                            :aria-invalid="!!fields.email"
                            aria-describedby="email-error"
                        />
                        <small id="email-error" class="field-error">{{ fields.email }}</small>
                        <label for="password">Password</label>
                        <input
                            id="password"
                            v-model="form.password"
                            type="password"
                            :autocomplete="
                                mode === 'register' ? 'new-password' : 'current-password'
                            "
                            required
                            :minlength="mode === 'register' ? 12 : 1"
                            maxlength="128"
                            :disabled="busy"
                            :aria-invalid="!!fields.password"
                            aria-describedby="password-help password-error"
                        />
                        <small id="password-help">
                            {{
                                mode === 'register'
                                    ? 'Use 12–128 characters.'
                                    : 'Enter your account password.'
                            }}
                        </small>
                        <small id="password-error" class="field-error">{{ fields.password }}</small>
                        <button class="primary" type="submit" :disabled="busy || !ready">
                            {{
                                busy
                                    ? 'Please wait…'
                                    : mode === 'register'
                                      ? 'Create teacher account'
                                      : 'Sign in'
                            }}
                        </button>
                    </form>
                    <button v-if="!ready && !busy" @click="initialise">Retry connection</button>
                </template>
            </section>
        </main>
    </div>
</template>

<style>
    :root {
        font-family:
            Segoe UI,
            Arial,
            sans-serif;
        color: #203b32;
        background: #f2f3ed;
        font-synthesis: none;
    }
    * {
        box-sizing: border-box;
    }
    body {
        margin: 0;
    }
    .auth-header {
        height: 80px;
        background: white;
        border-bottom: 1px solid #d6dcd5;
        padding: 0 7%;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 20px;
    }
    .brand {
        font-family: Georgia, serif;
        font-size: 28px;
        color: #203b32;
        text-decoration: none;
    }
    .auth-header span {
        font-size: 14px;
    }
    .auth-main {
        max-width: 1150px;
        padding: 90px 30px;
        margin: auto;
        display: grid;
        grid-template-columns: 1fr 430px;
        gap: 80px;
        align-items: start;
    }
    h1 {
        font:
            48px/1.15 Georgia,
            serif;
        margin: 20px 0;
        letter-spacing: -0.025em;
    }
    h2 {
        font-size: 23px;
        margin: 24px 0 20px;
    }
    p {
        line-height: 1.7;
    }
    .intro p {
        max-width: 50ch;
        font-size: 18px;
    }
    .scope {
        color: #56675e;
        font-size: 14px !important;
        margin-top: 28px;
    }
    .account {
        background: white;
        padding: 30px;
        border: 1px solid #d6dcd5;
        border-radius: 12px;
    }
    button,
    input {
        font: inherit;
    }
    button {
        cursor: pointer;
        padding: 11px 16px;
        background: white;
        border: 1px solid #89988d;
        border-radius: 6px;
        color: #203b32;
        font-weight: 600;
    }
    button:hover {
        background: #e9eee7;
    }
    button:disabled {
        opacity: 0.6;
        cursor: wait;
    }
    .tabs {
        display: flex;
        gap: 8px;
    }
    .tabs [aria-pressed='true'],
    .primary {
        background: #235a43;
        color: white;
        border-color: #235a43;
    }
    .primary {
        display: block;
        margin-top: 25px;
        width: 100%;
    }
    label {
        display: block;
        margin: 18px 0 7px;
        font-weight: 600;
        font-size: 14px;
    }
    input {
        width: 100%;
        padding: 12px;
        border: 1px solid #89988d;
        border-radius: 6px;
    }
    small {
        display: block;
        color: #56675e;
        margin-top: 6px;
        font-size: 13px;
    }
    .field-error {
        color: #9c2525;
    }
    .error {
        color: #8f2020;
        background: #fff0eb;
        padding: 12px;
        font-size: 14px;
    }
    .notice {
        background: #e5ede0;
        padding: 12px;
        font-size: 14px;
    }
    :focus-visible {
        outline: 3px solid #aa6c00;
        outline-offset: 3px;
    }
    ::selection {
        background: #f0d491;
    }
    @media (max-width: 800px) {
        .auth-main {
            grid-template-columns: 1fr;
            gap: 25px;
            padding: 30px 20px;
        }
        h1 {
            font-size: 36px;
        }
        .account {
            max-width: 500px;
            width: 100%;
            padding: 24px;
        }
        .auth-header {
            padding: 0 20px;
        }
        .intro .scope {
            margin-top: 14px;
        }
    }
    .auth-skip {
        position: absolute;
        top: -70px;
        left: 18px;
        z-index: 20;
        padding: 12px;
        background: white;
        color: #203b32;
    }
    .auth-skip:focus {
        top: 12px;
    }
</style>
