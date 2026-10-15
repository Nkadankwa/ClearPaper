<script setup>
    import { ref, computed, watch } from 'vue';
    import { modelFields, modelNames, validateModel } from '../models.js';
    const props = defineProps({ model: Object, disabled: Boolean }),
        emit = defineEmits(['update', 'convert', 'draft']);
    const draft = ref(),
        error = ref('');
    watch(
        () => props.model,
        (m) => {
            draft.value = {
                ...JSON.parse(JSON.stringify(m)),
                hiddenAngles: { a: false, b: false, c: false, ...m.hiddenAngles },
            };
            error.value = '';
        },
        { immediate: true },
    );
    const pending = computed(() => JSON.stringify(draft.value) !== JSON.stringify(props.model));
    watch(pending, (value) => emit('draft', value), { immediate: true });
    function apply() {
        try {
            validateModel(draft.value);
            error.value = '';
            emit('update', JSON.parse(JSON.stringify(draft.value)));
        } catch (e) {
            error.value = e.message;
        }
    }
    defineExpose({
        getDraft: () => JSON.parse(JSON.stringify(draft.value)),
        hasPending: () => pending.value,
    });
</script>
<template>
    <form class="model-controls" @submit.prevent="apply">
        <h2>{{ modelNames[model.type] }}</h2>
        <p class="help">Change the values, then update your diagram.</p>
        <template v-for="[key, label, min, max, step] in modelFields[model.type]" :key="key">
            <label :for="'model-' + key">{{ label }}</label>
            <input
                :id="'model-' + key"
                v-model.number="draft.parameters[key]"
                type="number"
                :min="min"
                :max="max"
                :step="step"
                required
                :disabled="disabled"
            />
        </template>
        <template v-if="model.type === 'SCALE'">
            <label for="model-unit">Unit</label>
            <input
                id="model-unit"
                v-model="draft.parameters.unit"
                maxlength="12"
                required
                :disabled="disabled"
            />
        </template>
        <label class="check">
            <input type="checkbox" v-model="draft.showAnswers" :disabled="disabled" />
            Show computed answers
        </label>
        <label v-if="model.type !== 'SCALE'" class="check">
            <input type="checkbox" v-model="draft.showAngles" :disabled="disabled" />
            Show angles
        </label>
        <details v-if="model.type === 'TRIANGLE'">
            <summary>Individual angles</summary>
            <label v-for="key in ['a', 'b', 'c']" :key="key" class="check">
                <input type="checkbox" v-model="draft.hiddenAngles[key]" :disabled="disabled" />
                Hide angle {{ key.toUpperCase() }}
            </label>
        </details>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <p v-if="pending" class="help" role="status">
            Values changed. Update the diagram to preview them.
        </p>
        <button class="update" :disabled="disabled">Update diagram</button>
        <p v-if="model.type === 'INCLINE'" class="help">
            Frictionless model; g = 9.81 m/s². Force arrows share a visual scale.
        </p>
        <p v-if="model.type === 'BEARING'" class="help">Bearings run clockwise from north.</p>
        <button
            v-if="model.type === 'TRIANGLE'"
            type="button"
            class="convert"
            :disabled="disabled"
            @click="emit('convert')"
        >
            Save editable geometry copy
        </button>
        <p v-if="model.type === 'TRIANGLE'" class="help">
            Keeps this triangle in your library and opens a separate copy with editable points and
            labels.
        </p>
    </form>
</template>
<style scoped>
    h2 {
        margin: 0;
        font-size: 20px;
    }
    .help {
        font-size: 13px;
        color: #56675e;
        line-height: 1.6;
    }
    .check {
        display: flex;
        align-items: center;
        gap: 8px;
    }
    .check input {
        width: auto;
    }
    .update,
    .convert {
        width: 100%;
        margin-top: 14px;
    }
    .update {
        background: #235a43;
        color: white;
        border-color: #235a43;
    }
    .update:hover {
        background: #174430;
    }
    summary {
        cursor: pointer;
        font-size: 14px;
        font-weight: 600;
        margin-top: 16px;
    }
    .error {
        font-size: 13px;
        color: #912b20;
    }
</style>
