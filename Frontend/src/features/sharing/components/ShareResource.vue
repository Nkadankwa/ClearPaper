<script setup>
    import { ref, onMounted } from 'vue';
    import { api } from '../../../services/api.js';
    const props = defineProps({ resource: Object });
    const emit = defineEmits(['close']);
    const dialog = ref(),
        links = ref([]),
        permission = ref('VIEW'),
        busy = ref(false),
        error = ref(''),
        notice = ref(''),
        fresh = ref({});
    async function load() {
        busy.value = true;
        error.value = '';
        try {
            links.value = await api('/library/resources/' + props.resource.id + '/shares');
        } catch (e) {
            error.value = e.message;
        } finally {
            busy.value = false;
        }
    }
    onMounted(() => {
        dialog.value.showModal();
        load();
    });
    async function publish() {
        busy.value = true;
        error.value = '';
        try {
            const result = await api('/library/resources/' + props.resource.id + '/shares', {
                method: 'POST',
                body: { permission: permission.value },
            });
            const url = new URL(location.href);
            url.hash = '/shared?token=' + encodeURIComponent(result.token);
            fresh.value[result.id] = url.href;
            notice.value = 'Link created. Share it yourself with the people you choose.';
            await load();
        } catch (e) {
            error.value = e.message;
        } finally {
            busy.value = false;
        }
    }
    async function revoke(link) {
        busy.value = true;
        error.value = '';
        try {
            await api('/library/resources/' + props.resource.id + '/shares/' + link.id, {
                method: 'DELETE',
            });
            delete fresh.value[link.id];
            notice.value =
                'Link revoked. Independent copies already saved by other teachers remain available.';
            await load();
        } catch (e) {
            error.value = e.message;
        } finally {
            busy.value = false;
        }
    }
    async function copy(link) {
        try {
            await navigator.clipboard.writeText(fresh.value[link.id]);
            notice.value = 'Link copied.';
        } catch {
            notice.value = 'Select the link text and copy it manually.';
        }
    }
</script>
<template>
    <dialog
        ref="dialog"
        class="sharing-dialog"
        aria-labelledby="share-title"
        @cancel.prevent="emit('close')"
    >
        <h2 id="share-title">Share {{ resource.name }}</h2>
        <p>
            Publish a snapshot of the saved resource. Later edits stay private. Anyone with the link
            can view it; choose whether signed-in teachers can also save an independent copy.
        </p>
        <label for="share-permission">Link permission</label>
        <select id="share-permission" v-model="permission" :disabled="busy">
            <option value="VIEW">View only</option>
            <option value="COPY">View and make a copy</option>
        </select>
        <button @click="publish" :disabled="busy">Create sharing link</button>
        <p v-if="error" role="alert">{{ error }}</p>
        <p v-if="notice" role="status">{{ notice }}</p>
        <p v-if="busy" role="status">Updating links…</p>
        <ul aria-label="Active sharing links">
            <li v-for="link in links" :key="link.id">
                <strong>{{ link.permission === 'COPY' ? 'View and copy' : 'View only' }}</strong>
                <span>· {{ new Date(link.createdAt).toLocaleString() }}</span>
                <template v-if="fresh[link.id]">
                    <label :for="'share-url-' + link.id">Sharing link (keep this address)</label>
                    <input
                        :id="'share-url-' + link.id"
                        readonly
                        :value="fresh[link.id]"
                        @focus="$event.target.select()"
                    />
                    <button @click="copy(link)">Copy link</button>
                    <a :href="fresh[link.id]" target="_blank" rel="noopener noreferrer">
                        Open shared view
                    </a>
                </template>
                <p v-else class="hint">
                    The address is shown only when created. Revoke this link or create a new one.
                </p>
                <button @click="revoke(link)" :disabled="busy">Revoke link</button>
            </li>
        </ul>
        <p v-if="!links.length && !busy">No active sharing links.</p>
        <p class="hint">
            Publishing does not send a message. View-only restricts the app’s copy action; readers
            can still print or reproduce visible content.
        </p>
        <button @click="emit('close')">Close sharing</button>
    </dialog>
</template>
<style scoped>
    .sharing-dialog {
        width: 620px;
        max-width: calc(100vw - 24px);
        max-height: 90vh;
        overflow: auto;
        border: 1px solid #89988d;
        border-radius: 12px;
        padding: 24px;
        color: #203b32;
    }
    .sharing-dialog::backdrop {
        background: #203b3277;
    }
    select {
        width: 100%;
        padding: 12px;
        font: inherit;
        margin-bottom: 12px;
    }
    ul {
        list-style: none;
        padding: 0;
    }
    li {
        border-top: 1px solid #d6dcd5;
        padding: 20px 0;
    }
    li button,
    li a {
        margin: 10px 10px 0 0;
    }
    .hint,
    li span {
        font-size: 13px;
        color: #46594e;
    }
    p {
        line-height: 1.65;
    }
    a {
        color: #235a43;
    }
    h2 {
        overflow-wrap: anywhere;
    }
</style>
