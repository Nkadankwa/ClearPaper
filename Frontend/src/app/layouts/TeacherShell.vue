<script setup>
    function focusMain() {
        document.getElementById('workspace-main')?.focus();
    }
    import { computed } from 'vue';
    import ResourceLibrary from '../../features/library/components/ResourceLibrary.vue';
    import ResourceEditor from '../../features/diagrams/components/ResourceEditor.vue';
    import WorksheetEditor from '../../features/worksheets/components/WorksheetEditor.vue';
    import { onUnmounted } from 'vue';
    import { clearDrafts } from '../../features/worksheets/worksheets.js';
    onUnmounted(clearDrafts);
    import { teacherRoutes } from '../routes.js';
    const props = defineProps({
        teacher: { type: Object, required: true },
        route: { type: Object, required: true },
        busy: Boolean,
        error: String,
    });
    defineEmits(['logout']);
    const page = computed(() => teacherRoutes.find((r) => r.path === props.route.path));
</script>

<template>
    <div class="teacher-shell">
        <a class="skip" href="#workspace-main" @click.prevent="focusMain">Skip to workspace</a>
        <header class="workspace-header">
            <a class="brand" href="#/teacher/library">ClearPaper</a>
            <div class="identity">
                <span>{{ teacher.displayName }}</span>
                <button :disabled="busy" @click="$emit('logout')">
                    {{ busy ? 'Signing out…' : 'Sign out' }}
                </button>
            </div>
        </header>
        <div class="workspace-grid">
            <aside class="sidebar">
                <nav aria-label="Teacher workspace">
                    <a
                        v-for="item in teacherRoutes"
                        :key="item.path"
                        :href="'#' + item.path"
                        :aria-current="route.path === item.path ? 'page' : undefined"
                    >
                        {{ item.title }}
                    </a>
                </nav>
                <p class="sidebar-note">
                    Clear diagrams.
                    <br />
                    Readable photocopies.
                    <br />
                    More time to teach.
                </p>
                <a class="student-link" href="#/student/join">Student screen preview</a>
            </aside>
            <main id="workspace-main" class="workspace-main" tabindex="-1" :aria-busy="busy">
                <p v-if="error" class="workspace-error" role="alert">{{ error }}</p>
                <p v-if="busy" role="status">Signing out of your workspace…</p>
                <template v-if="page">
                    <div class="page-heading">
                        <h1>{{ page.title }}</h1>
                        <p>{{ page.description }}</p>
                    </div>
                    <ResourceLibrary v-if="route.path === '/teacher/library'" />
                    <ResourceEditor
                        v-else-if="route.path === '/teacher/studio'"
                        :route="route"
                        kind="DIAGRAM"
                    />
                    <WorksheetEditor
                        v-else-if="route.path === '/teacher/worksheets'"
                        :route="route"
                    />
                    <section v-else class="workspace-surface" :aria-labelledby="'section-heading'">
                        <h2 id="section-heading">{{ page.heading }}</h2>
                        <div class="empty-state">
                            <svg viewBox="0 0 240 140" aria-hidden="true">
                                <rect x="68" y="12" width="100" height="116" rx="2" />
                                <path d="M86 32h63M86 43h44M88 103l29-40 30 40zM117 63v40" />
                            </svg>
                            <h3>{{ page.empty }}</h3>
                            <p>{{ page.detail }}</p>
                            <a class="route-action" :href="'#' + page.target">{{ page.action }}</a>
                        </div>
                    </section>
                    <p v-if="route.path === '/teacher/reading'" class="stage-note">
                        Workspace preview · Content creation will become available as each module is
                        completed.
                    </p>
                </template>
                <section v-else class="not-found">
                    <h1>Page not found</h1>
                    <p>This workspace route does not exist.</p>
                    <a class="route-action" href="#/teacher/library">Return to your library</a>
                </section>
            </main>
        </div>
    </div>
</template>

<style scoped>
    .teacher-shell {
        min-height: 100vh;
    }
    .workspace-header {
        height: 78px;
        padding: 0 32px;
        background: white;
        border-bottom: 1px solid #d6dcd5;
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 20px;
    }
    .identity {
        display: flex;
        gap: 18px;
        align-items: center;
        font-size: 14px;
        min-width: 0;
    }
    .identity span {
        max-width: 30ch;
        overflow-wrap: anywhere;
    }
    .workspace-grid {
        display: grid;
        grid-template-columns: 220px minmax(0, 1fr);
        min-height: calc(100vh - 78px);
    }
    .sidebar {
        background: #fafbf6;
        border-right: 1px solid #d6dcd5;
        padding: 28px 18px;
    }
    nav {
        display: grid;
        gap: 8px;
    }
    nav a {
        display: block;
        padding: 13px 14px;
        border-radius: 6px;
        color: #203b32;
        text-decoration: none;
    }
    nav a:hover {
        background: #e9eee7;
    }
    nav a[aria-current='page'] {
        background: #dfe9dd;
        font-weight: 700;
    }
    .sidebar-note {
        font-size: 13px;
        color: #56675e;
        line-height: 1.9;
        margin: 50px 14px 20px;
    }
    .student-link {
        font-size: 13px;
        margin: 14px;
        display: inline-block;
        color: #235a43;
        text-underline-offset: 3px;
    }
    .workspace-main {
        display: block;
        width: 100%;
        max-width: 1250px;
        margin: 0 auto;
        padding: 40px;
        outline-offset: -5px;
    }
    .page-heading {
        margin-bottom: 30px;
    }
    .page-heading h1 {
        font:
            38px/1.2 Georgia,
            serif;
        margin: 0 0 10px;
    }
    .page-heading p {
        color: #56675e;
        margin: 0;
        max-width: 65ch;
    }
    .workspace-surface {
        background: white;
        border: 1px solid #d6dcd5;
        border-radius: 12px;
        padding: 26px;
    }
    .workspace-surface h2 {
        font-size: 20px;
        margin: 0;
    }
    .empty-state {
        padding: 38px 20px 48px;
        text-align: center;
    }
    .empty-state svg {
        width: 210px;
        height: 130px;
        fill: none;
        stroke: #203b32;
        stroke-width: 1.8;
        margin-bottom: 18px;
    }
    .empty-state h3 {
        font-size: 22px;
        font-weight: 600;
        margin: 0 0 12px;
    }
    .empty-state p {
        color: #56675e;
        max-width: 60ch;
        margin: 0 auto 24px;
    }
    .route-action {
        display: inline-block;
        padding: 12px 18px;
        border-radius: 6px;
        background: #235a43;
        color: white;
        text-decoration: none;
        font-weight: 600;
    }
    .route-action:hover {
        background: #174430;
    }
    .stage-note {
        font-size: 13px;
        color: #56675e;
        line-height: 1.7;
        margin-top: 20px;
        max-width: 75ch;
    }
    .workspace-error {
        background: #fff0eb;
        color: #8f2020;
        padding: 12px;
    }
    .skip {
        position: absolute;
        left: 18px;
        top: -70px;
        z-index: 10;
        background: white;
        padding: 12px;
        color: #203b32;
    }
    .skip:focus {
        top: 12px;
    }
    .not-found h1 {
        font:
            36px Georgia,
            serif;
    }
    .not-found .route-action {
        margin-top: 14px;
    }
    @media (max-width: 760px) {
        .workspace-header {
            height: auto;
            min-height: 72px;
            padding: 16px 20px;
        }
        .identity {
            gap: 8px;
        }
        .identity span {
            display: none;
        }
        .workspace-grid {
            display: block;
        }
        .sidebar {
            padding: 12px;
            border-right: 0;
            border-bottom: 1px solid #d6dcd5;
        }
        nav {
            display: flex;
            flex-wrap: wrap;
            gap: 5px;
        }
        nav a {
            white-space: nowrap;
            padding: 11px;
        }
        .sidebar-note {
            display: none;
        }
        .student-link {
            margin: 8px 10px 0;
        }
        .workspace-main {
            padding: 26px 18px;
        }
        .page-heading h1 {
            font-size: 32px;
        }
        .workspace-surface {
            padding: 20px;
        }
        .empty-state {
            padding: 28px 5px;
        }
        .empty-state h3 {
            font-size: 20px;
        }
    }
</style>
