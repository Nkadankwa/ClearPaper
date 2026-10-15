<script setup>
    import { ref, onMounted } from 'vue';
    import { api } from '../../../services/api.js';
    import ShareResource from '../../sharing/components/ShareResource.vue';
    const sharing = ref(),
        importing = ref();
    async function exportBackup(r) {
        error.value = '';
        try {
            const data = await api('/library/resources/' + r.id + '/export'),
                url = URL.createObjectURL(
                    new Blob([JSON.stringify(data)], { type: 'application/json' }),
                ),
                a = document.createElement('a');
            a.href = url;
            a.download = r.name.replace(/[^a-z0-9_-]/gi, '-') + '.clearpaper.json';
            a.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (e) {
            error.value = e.message;
        }
    }
    async function chooseBackup(event) {
        error.value = '';
        importing.value = null;
        try {
            const file = event.target.files[0];
            if (!file) return;
            if (file.size > 4100000) throw Error('Choose a backup smaller than 4 MB.');
            const data = JSON.parse(await file.text());
            if (
                data.format !== 'ClearPaper' ||
                data.backupVersion !== 1 ||
                !['DIAGRAM', 'WORKSHEET'].includes(data.resource?.kind) ||
                typeof data.resource?.name !== 'string'
            )
                throw Error('Choose a ClearPaper version 1 resource backup.');
            importing.value = data;
        } catch (e) {
            error.value = e.message;
        } finally {
            event.target.value = '';
        }
    }
    async function restoreBackup() {
        busy.value = true;
        error.value = '';
        try {
            await api('/library/import', { method: 'POST', body: importing.value });
            importing.value = null;
            notice.value = 'Backup imported as a new independent resource.';
            await load();
        } catch (e) {
            error.value = e.message;
            busy.value = false;
        }
    }

    const resources = ref([]),
        collections = ref([]),
        busy = ref(false),
        error = ref(''),
        notice = ref(''),
        search = ref(''),
        kind = ref(''),
        collectionId = ref(''),
        newCollection = ref('');
    let request = 0;
    async function load() {
        const ticket = ++request;
        busy.value = true;
        error.value = '';
        try {
            const params = new URLSearchParams();
            if (search.value) params.set('search', search.value);
            if (kind.value) params.set('kind', kind.value);
            if (collectionId.value) params.set('collectionId', collectionId.value);
            const [r, c] = await Promise.all([
                api('/library/resources?' + params),
                api('/library/collections'),
            ]);
            if (ticket === request) {
                resources.value = r;
                collections.value = c;
            }
        } catch (e) {
            if (ticket === request) error.value = e.message;
        } finally {
            if (ticket === request) busy.value = false;
        }
    }
    function open(r) {
        location.hash = `/teacher/${r.kind === 'DIAGRAM' ? 'studio' : 'worksheets'}?id=${encodeURIComponent(r.id)}`;
    }
    async function duplicate(r) {
        busy.value = true;
        error.value = '';
        try {
            await api('/library/resources/' + r.id + '/duplicate', { method: 'POST' });
            notice.value = 'Independent copy created.';
            await load();
        } catch (e) {
            error.value = e.message;
            busy.value = false;
        }
    }
    async function remove(r) {
        if (!window.confirm(`Delete “${r.name}” from your library? This cannot be undone.`)) return;
        busy.value = true;
        error.value = '';
        try {
            await api('/library/resources/' + r.id, { method: 'DELETE' });
            notice.value = 'Resource deleted.';
            await load();
        } catch (e) {
            error.value = e.message;
            busy.value = false;
        }
    }
    async function createCollection() {
        if (!newCollection.value.trim()) return;
        busy.value = true;
        error.value = '';
        try {
            const c = await api('/library/collections', {
                method: 'POST',
                body: { name: newCollection.value.trim() },
            });
            newCollection.value = '';
            notice.value = 'Collection created.';
            await load();
            collectionId.value = c.id;
            await load();
        } catch (e) {
            error.value = e.message;
            busy.value = false;
        }
    }
    onMounted(load);
</script>
<template>
    <div class="library">
        <div class="tools">
            <a class="action" href="#/teacher/studio?new=1">New diagram</a>
            <a class="action secondary" href="#/teacher/worksheets?new=1">New worksheet</a>
        </div>
        <details class="backup-tools">
            <summary>Import a resource backup</summary>
            <p>
                Import one diagram or worksheet as a new private resource. Existing resources are
                never overwritten.
            </p>
            <label for="backup-file">ClearPaper backup file</label>
            <input
                id="backup-file"
                type="file"
                accept=".json,application/json"
                @change="chooseBackup"
                :disabled="busy"
            />
            <div v-if="importing">
                <p>
                    Import “{{ importing.resource.name }}” ({{
                        importing.resource.kind === 'DIAGRAM' ? 'diagram' : 'worksheet'
                    }})?
                </p>
                <button @click="restoreBackup" :disabled="busy">Import as new resource</button>
                <button @click="importing = null">Cancel import</button>
            </div>
        </details>
        <p v-if="error" class="feedback error" role="alert">
            {{ error }}
            <button @click="load">Retry</button>
        </p>
        <p v-if="notice" class="feedback" role="status">{{ notice }}</p>
        <form class="filters" @submit.prevent="load">
            <div>
                <label for="library-search">Search names</label>
                <input id="library-search" v-model="search" type="search" maxlength="100" />
            </div>
            <div>
                <label for="kind-filter">Resource type</label>
                <select id="kind-filter" v-model="kind" @change="load">
                    <option value="">All resources</option>
                    <option value="DIAGRAM">Diagrams</option>
                    <option value="WORKSHEET">Worksheets</option>
                </select>
            </div>
            <div>
                <label for="collection-filter">Collection</label>
                <select id="collection-filter" v-model="collectionId" @change="load">
                    <option value="">All collections</option>
                    <option v-for="c in collections" :key="c.id" :value="c.id">{{ c.name }}</option>
                </select>
            </div>
            <button type="submit" :disabled="busy">Search</button>
        </form>
        <form class="collection-form" @submit.prevent="createCollection">
            <label for="new-collection">New collection</label>
            <input
                id="new-collection"
                v-model="newCollection"
                maxlength="80"
                placeholder="Maths, Physics…"
            />
            <button :disabled="busy || !newCollection.trim()">Create collection</button>
        </form>
        <p v-if="busy" role="status">Loading your library…</p>
        <section v-else-if="!error && !resources.length" class="empty">
            <h2>
                {{
                    search || kind || collectionId
                        ? 'No matching resources'
                        : 'Start your resource library'
                }}
            </h2>
            <p>
                {{
                    search || kind || collectionId
                        ? 'Try another search or collection.'
                        : 'Create a diagram or worksheet, then save it here for your next lesson.'
                }}
            </p>
        </section>
        <ul v-else-if="!error" class="resources" aria-label="Saved resources">
            <li v-for="r in resources" :key="r.id">
                <div>
                    <h2>{{ r.name }}</h2>
                    <p>
                        {{ r.kind === 'DIAGRAM' ? 'Diagram' : 'Worksheet' }} ·
                        {{ collections.find((c) => c.id === r.collectionId)?.name || 'Unfiled' }}
                    </p>
                </div>
                <div class="tools">
                    <button @click="open(r)">Open</button>
                    <button @click="duplicate(r)">Duplicate</button>
                    <button @click="exportBackup(r)">Export backup</button>
                    <button @click="sharing = r">Share</button>
                    <button @click="remove(r)">Delete</button>
                </div>
            </li>
        </ul>
        <ShareResource v-if="sharing" :resource="sharing" @close="sharing = null" />
    </div>
</template>
<style scoped>
    .backup-tools {
        margin: 24px 0;
        padding: 16px 0;
        border-top: 1px solid #d6dcd5;
        border-bottom: 1px solid #d6dcd5;
    }
    .backup-tools summary {
        cursor: pointer;
        font-weight: 600;
    }
    .tools {
        display: flex;
        gap: 9px;
        flex-wrap: wrap;
    }
    .action {
        display: inline-block;
        background: #235a43;
        color: white;
        text-decoration: none;
        padding: 12px 17px;
        border-radius: 6px;
        font-weight: 600;
    }
    .secondary {
        background: white;
        color: #203b32;
        border: 1px solid #89988d;
    }
    .filters {
        display: grid;
        grid-template-columns: 2fr 1fr 1fr auto;
        gap: 15px;
        align-items: end;
        margin: 20px 0;
    }
    .filters label,
    .collection-form label {
        margin-top: 0;
    }
    .filters input,
    .filters select {
        width: 100%;
    }
    select {
        font: inherit;
        border: 1px solid #89988d;
        border-radius: 6px;
        padding: 12px;
        background: white;
        color: #203b32;
    }
    .collection-form {
        display: flex;
        gap: 12px;
        align-items: center;
        flex-wrap: wrap;
        margin: 22px 0;
    }
    .collection-form input {
        width: 240px;
    }
    .resources {
        padding: 0;
        list-style: none;
        background: white;
        border: 1px solid #d6dcd5;
        border-radius: 12px;
    }
    .resources li {
        padding: 22px;
        display: flex;
        justify-content: space-between;
        gap: 18px;
        align-items: center;
        border-bottom: 1px solid #d6dcd5;
    }
    .resources li:last-child {
        border: 0;
    }
    .resources h2 {
        font-size: 19px;
        margin: 0;
        overflow-wrap: anywhere;
    }
    .resources p {
        font-size: 13px;
        margin: 6px 0 0;
        color: #56675e;
    }
    .empty {
        padding: 40px;
        background: white;
        border: 1px solid #d6dcd5;
        border-radius: 12px;
    }
    .empty h2 {
        margin-top: 0;
    }
    .feedback {
        background: #e5ede0;
        padding: 12px;
    }
    .feedback.error {
        background: #fff0eb;
        color: #8f2020;
    }
    @media (max-width: 900px) {
        .filters {
            grid-template-columns: 1fr 1fr;
        }
        .resources li {
            align-items: start;
            flex-direction: column;
        }
    }
    @media (max-width: 500px) {
        .filters {
            grid-template-columns: 1fr;
        }
        .collection-form {
            display: grid;
        }
        .collection-form input {
            width: 100%;
        }
    }
</style>
