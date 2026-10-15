<script setup>
    function focusMain() {
        document.getElementById('workspace-main')?.focus();
    }
    import { ref, computed, watch } from 'vue';
    import { studentRoutes } from '../routes.js';
    const props = defineProps({ route: { type: Object, required: true } });
    const code = ref(''),
        message = ref('');
    const page = computed(() => studentRoutes.find((r) => r.path === props.route.path));
    watch(
        () => props.route.query,
        (query) => {
            code.value = new URLSearchParams(query).get('code') || '';
            message.value = '';
        },
        { immediate: true },
    );
    function checkCode() {
        message.value = code.value.trim()
            ? 'Group joining is not connected yet. Your teacher will provide a live invitation when enrolment is available.'
            : 'Enter the group code supplied by your teacher.';
    }
</script>

<template>
    <div class="student-shell">
        <a class="skip" href="#workspace-main" @click.prevent="focusMain">Skip to student page</a>
        <header class="student-header">
            <a class="brand" href="#/student/join">ClearPaper</a>
            <span>Student reading</span>
        </header>
        <nav class="student-nav" aria-label="Student reading navigation">
            <a
                v-for="item in studentRoutes"
                :key="item.path"
                :href="'#' + item.path"
                :aria-current="route.path === item.path ? 'page' : undefined"
            >
                {{ item.title }}
            </a>
        </nav>
        <main id="workspace-main" class="student-main" tabindex="-1">
            <h1>{{ page?.title || 'Student page not found' }}</h1>
            <section
                v-if="route.path === '/student/join'"
                class="student-surface"
                aria-labelledby="join-title"
            >
                <h2 id="join-title">Your reading group starts with an invitation.</h2>
                <p>Enter the code your teacher shares with you.</p>
                <form @submit.prevent="checkCode" novalidate>
                    <label for="group-code">Group code</label>
                    <input
                        id="group-code"
                        v-model="code"
                        maxlength="120"
                        autocomplete="off"
                        aria-describedby="join-help join-message"
                        :aria-invalid="!!message && !code.trim()"
                    />
                    <small id="join-help">
                        Group enrolment is being prepared. Checking an invitation does not create
                        membership yet.
                    </small>
                    <button class="student-action" type="submit">Check invitation</button>
                    <p v-if="message" id="join-message" role="status">{{ message }}</p>
                </form>
            </section>
            <section v-else-if="route.path === '/student/assignments'" class="student-surface">
                <h2>Your passages will appear here.</h2>
                <p>
                    After you join a group, your teacher’s assigned passages and due dates will be
                    listed here.
                </p>
                <p class="implementation-note">
                    Your assignment list becomes available once group enrolment and passage
                    assignment are ready.
                </p>
                <a class="student-action" href="#/student/join">Open group invitation</a>
            </section>
            <section v-else-if="route.path === '/student/reading'" class="student-surface">
                <h2>Select an assignment before reading.</h2>
                <p>
                    The passage and recording controls will appear after your teacher assigns a
                    reading.
                </p>
                <p class="implementation-note">
                    Reading and recording controls are being prepared. Microphone access is not
                    requested by this preview.
                </p>
                <a class="student-action" href="#/student/assignments">View assignment screen</a>
            </section>
            <section v-else class="student-surface">
                <p>This student route does not exist.</p>
                <a class="student-action" href="#/student/join">Return to group joining</a>
            </section>
            <p class="teacher-entry">
                Are you a teacher?
                <a href="#/teacher/library">Open teacher workspace</a>
            </p>
        </main>
    </div>
</template>

<style scoped>
    .student-shell {
        min-height: 100vh;
    }
    .student-header {
        height: 78px;
        display: flex;
        justify-content: space-between;
        align-items: center;
        gap: 15px;
        background: white;
        border-bottom: 1px solid #d6dcd5;
        padding: 0 6%;
    }
    .student-header span {
        font-size: 14px;
    }
    .student-nav {
        display: flex;
        gap: 8px;
        max-width: 850px;
        margin: 24px auto 0;
        padding: 0 24px;
        overflow: auto;
    }
    .student-nav a {
        padding: 10px 14px;
        border-radius: 6px;
        color: #203b32;
        white-space: nowrap;
        text-decoration: none;
    }
    .student-nav a[aria-current='page'] {
        background: #dfe9dd;
        font-weight: 700;
    }
    .student-nav a:hover {
        background: #e9eee7;
    }
    .student-main {
        display: block;
        max-width: 850px;
        padding: 25px 24px 50px;
        margin: auto;
        outline-offset: -4px;
    }
    .student-main h1 {
        font:
            36px/1.2 Georgia,
            serif;
        margin: 10px 0 24px;
    }
    .student-surface {
        background: white;
        border: 1px solid #d6dcd5;
        border-radius: 12px;
        padding: 30px;
    }
    .student-surface h2 {
        font-size: 23px;
        margin: 0 0 15px;
        max-width: 45ch;
    }
    .student-surface p {
        max-width: 65ch;
    }
    .student-surface form {
        max-width: 450px;
    }
    .student-action {
        display: inline-block;
        width: auto;
        background: #235a43;
        color: white;
        text-decoration: none;
        border: 0;
        padding: 12px 18px;
        border-radius: 6px;
        font-weight: 600;
        margin-top: 20px;
    }
    .student-action:hover {
        background: #174430;
    }
    .implementation-note,
    .teacher-entry {
        font-size: 14px;
        color: #56675e;
    }
    .teacher-entry {
        margin-top: 24px;
    }
    .teacher-entry a {
        color: #235a43;
        text-underline-offset: 3px;
    }
    .skip {
        position: absolute;
        left: 18px;
        top: -70px;
        z-index: 10;
        background: white;
        padding: 12px;
    }
    .skip:focus {
        top: 12px;
    }
    @media (max-width: 600px) {
        .student-header {
            padding: 0 18px;
        }
        .student-main {
            padding: 20px 16px;
        }
        .student-nav {
            padding: 0 16px;
            flex-wrap: wrap;
        }
        .student-surface {
            padding: 23px;
        }
        .student-main h1 {
            font-size: 32px;
        }
    }
</style>
