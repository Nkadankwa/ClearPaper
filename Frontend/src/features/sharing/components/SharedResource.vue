<script setup>
    import { ref, watch } from 'vue';
    import { api } from '../../../services/api.js';
    import { normalize } from '../../worksheets/worksheets.js';
    import GeometryCanvas from '../../diagrams/components/GeometryCanvas.vue';
    import ModelCanvas from '../../diagrams/components/ModelCanvas.vue';
    import PaperPreview from '../../worksheets/components/PaperPreview.vue';
    const props = defineProps({ route: Object, teacher: Object });
    const item = ref(),
        busy = ref(false),
        error = ref('');
    let request = 0;
    const token = () => new URLSearchParams(props.route.query).get('token') || '';
    watch(
        () => props.route.query,
        async () => {
            const ticket = ++request;
            item.value = null;
            busy.value = true;
            error.value = '';
            try {
                const r = await api('/shared/' + encodeURIComponent(token()));
                if (ticket === request)
                    item.value = {
                        ...r,
                        data: r.kind === 'WORKSHEET' ? normalize(r.data) : r.data,
                    };
            } catch (e) {
                if (ticket === request)
                    error.value =
                        e.status === 404
                            ? 'This link is unavailable or has been revoked. Ask its owner for a new link.'
                            : e.message;
            } finally {
                if (ticket === request) busy.value = false;
            }
        },
        { immediate: true },
    );
    async function copy() {
        busy.value = true;
        error.value = '';
        try {
            const r = await api('/shared/' + encodeURIComponent(token()) + '/copy', {
                method: 'POST',
            });
            location.hash =
                '/teacher/' +
                (r.kind === 'DIAGRAM' ? 'studio' : 'worksheets') +
                '?id=' +
                encodeURIComponent(r.id);
        } catch (e) {
            error.value = e.message;
        } finally {
            busy.value = false;
        }
    }
</script>
<template>
    <div class="shared-shell">
        <header>
            <a class="brand" href="#/teacher/library">ClearPaper</a>
            <a href="#/teacher/library">{{ teacher ? 'My library' : 'Sign in' }}</a>
        </header>
        <main id="workspace-main" tabindex="-1">
            <p v-if="busy" role="status">Loading shared resource…</p>
            <p v-if="error" class="error" role="alert">{{ error }}</p>
            <template v-if="item">
                <h1>{{ item.name }}</h1>
                <p>
                    This is a published snapshot.
                    {{
                        item.permission === 'COPY'
                            ? 'Teachers may save and edit an independent copy.'
                            : 'This link permits viewing only.'
                    }}
                </p>
                <template v-if="item.permission === 'COPY'">
                    <button v-if="teacher" @click="copy" :disabled="busy">
                        Make a copy in my library
                    </button>
                    <a
                        v-else
                        class="sign-in-copy"
                        :href="'#/teacher/library?returnShare=' + encodeURIComponent(token())"
                    >
                        Sign in to make a copy
                    </a>
                </template>
                <PaperPreview v-if="item.kind === 'WORKSHEET'" :record="item" :readonly="true" />
                <section v-else class="shared-diagram" aria-label="Shared diagram">
                    <ModelCanvas v-if="item.data.model" :data="item.data" :interactive="false" />
                    <GeometryCanvas
                        v-else
                        :graph="item.data"
                        :interactive="false"
                        :editable="false"
                    />
                </section>
                <p v-if="item.kind === 'WORKSHEET'">
                    Use Show page preview to read the worksheet, or Print / Save as PDF for a paper
                    copy.
                </p>
            </template>
        </main>
    </div>
</template>
<style scoped>
    .shared-shell header {
        display: flex;
        justify-content: space-between;
        align-items: center;
        background: white;
        padding: 24px 6%;
        border-bottom: 1px solid #d6dcd5;
    }
    .shared-shell main {
        max-width: 1050px;
        margin: auto;
        padding: 30px 24px;
    }
    .shared-shell h1 {
        font:
            36px/1.2 Georgia,
            serif;
        overflow-wrap: anywhere;
    }
    .shared-shell a {
        color: #235a43;
    }
    .shared-diagram {
        margin: 24px 0;
        background: white;
        padding: 20px;
    }
    .sign-in-copy {
        display: inline-block;
        padding: 12px;
        border: 1px solid #235a43;
        border-radius: 6px;
    }
    .shared-shell p {
        max-width: 70ch;
    }
</style>
