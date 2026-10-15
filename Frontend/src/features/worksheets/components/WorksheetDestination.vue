<script setup>
    import { ref, onMounted, onUnmounted } from 'vue';
    import { api } from '../../../services/api.js';
    import {
        drafts,
        currentWorksheet,
        normalize,
        question,
        validate,
        clone,
        setCurrent,
    } from '../worksheets.js';
    const props = defineProps({ figure: Object });
    const emit = defineEmits(['close']);
    let alive = true;
    onUnmounted(() => {
        alive = false;
    });
    const dialog = ref(),
        items = ref([]),
        destination = ref(
            currentWorksheet === 'new' && drafts.has('new')
                ? 'new-draft'
                : currentWorksheet || 'new',
        ),
        name = ref('Untitled worksheet'),
        busy = ref(true),
        error = ref('');
    onMounted(async () => {
        dialog.value.showModal();
        try {
            items.value = await api('/library/resources?kind=WORKSHEET');
        } catch (e) {
            error.value = e.message;
        } finally {
            busy.value = false;
        }
    });
    async function insert() {
        busy.value = true;
        error.value = '';
        try {
            let record;
            if (destination.value === 'new-draft') record = clone(drafts.get('new'));
            else if (destination.value === 'new')
                record = {
                    name: name.value.trim(),
                    kind: 'WORKSHEET',
                    collectionId: null,
                    data: { schemaVersion: 1, blocks: [] },
                };
            else
                record = clone(
                    drafts.get(destination.value) ||
                        (await api('/library/resources/' + encodeURIComponent(destination.value))),
                );
            if (!record.name) throw Error('Enter a worksheet name.');
            record.data = normalize(record.data);
            record.data.blocks.push(question(props.figure));
            validate(record.data);
            const saved = await api('/library/resources' + (record.id ? '/' + record.id : ''), {
                method: record.id ? 'PUT' : 'POST',
                body: {
                    name: record.name,
                    kind: 'WORKSHEET',
                    collectionId: record.collectionId || null,
                    data: record.data,
                },
            });
            if (!alive) return;
            if (destination.value === 'new-draft') drafts.delete('new');
            drafts.set(saved.id, { ...saved, data: normalize(saved.data), dirty: false });
            setCurrent(saved.id);
            emit('close');
            location.hash = '/teacher/worksheets?id=' + encodeURIComponent(saved.id);
        } catch (e) {
            error.value = e.message;
        } finally {
            busy.value = false;
        }
    }
</script>
<template>
    <dialog ref="dialog" aria-labelledby="destination-title" @cancel.prevent="emit('close')">
        <h2 id="destination-title">Add figure to a worksheet</h2>
        <p>
            A separate, editable copy will be added as a new question. Adding also saves the
            selected worksheet, including any changes in its current draft.
        </p>
        <label for="worksheet-destination">Destination worksheet</label>
        <select id="worksheet-destination" v-model="destination" :disabled="busy">
            <option value="new">Create a new worksheet</option>
            <option v-for="w in items" :key="w.id" :value="w.id">
                {{ w.name }}{{ w.id === currentWorksheet ? ' (current worksheet)' : '' }}
            </option>
            <option v-if="drafts.has('new')" value="new-draft">Current unsaved worksheet</option>
        </select>
        <template v-if="destination === 'new'">
            <label for="destination-name">New worksheet name</label>
            <input id="destination-name" v-model="name" maxlength="100" />
        </template>
        <p v-if="error" role="alert">{{ error }}</p>
        <p v-if="busy" role="status">Working…</p>
        <footer>
            <button @click="emit('close')" :disabled="busy">Cancel</button>
            <button @click="insert" :disabled="busy">Add figure to selected worksheet</button>
        </footer>
    </dialog>
</template>
<style scoped>
    dialog {
        width: 520px;
        max-width: calc(100vw - 32px);
        border: 1px solid #89988d;
        border-radius: 12px;
        padding: 24px;
        color: #203b32;
    }
    dialog::backdrop {
        background: #203b3277;
    }
    select {
        width: 100%;
        padding: 12px;
        font: inherit;
    }
    footer {
        display: flex;
        gap: 12px;
        justify-content: flex-end;
        margin-top: 24px;
    }
    p {
        line-height: 1.6;
    }
</style>
