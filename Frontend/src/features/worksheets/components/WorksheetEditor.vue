<script setup>
    import { ref, watch, onUnmounted, nextTick } from 'vue';
    import { api } from '../../../services/api.js';
    import PaperPreview from './PaperPreview.vue';
    import { validatePaper, sizes } from '../paper.js';
    import {
        drafts,
        normalize,
        question,
        validate,
        clone,
        applyFigure,
        setCurrent,
    } from '../worksheets.js';
    import ResourceEditor from '../../diagrams/components/ResourceEditor.vue';
    import GeometryCanvas from '../../diagrams/components/GeometryCanvas.vue';
    import ModelCanvas from '../../diagrams/components/ModelCanvas.vue';
    const props = defineProps({ route: Object });
    const record = ref(),
        items = ref([]),
        busy = ref(false),
        error = ref(''),
        notice = ref(''),
        editing = ref(),
        dialog = ref();
    let generation = 0;
    function navigate(key) {
        location.hash =
            '/teacher/worksheets' + (key === 'new' ? '?new=1' : '?id=' + encodeURIComponent(key));
    }
    function remember() {
        if (record.value) drafts.set(record.value.id || 'new', clone(record.value));
    }
    function changed() {
        record.value.dirty = true;
        notice.value = '';
        remember();
    }
    async function load() {
        remember();
        const ticket = ++generation;
        busy.value = true;
        error.value = '';
        try {
            const list = await api('/library/resources?kind=WORKSHEET');
            if (ticket !== generation) return;
            items.value = list;
            const key = new URLSearchParams(props.route.query).get('id') || 'new';
            let r =
                drafts.get(key) ||
                (key === 'new'
                    ? {
                          name: 'Untitled worksheet',
                          kind: 'WORKSHEET',
                          collectionId: null,
                          data: { schemaVersion: 1, blocks: [] },
                          dirty: true,
                      }
                    : await api('/library/resources/' + encodeURIComponent(key)));
            if (ticket !== generation) return;
            if (r.kind !== 'WORKSHEET') throw Error('Open this diagram in the diagram studio.');
            record.value = { ...clone(r), data: normalize(r.data) };
            setCurrent(key);
        } catch (e) {
            if (ticket === generation) error.value = e.message;
        } finally {
            if (ticket === generation) busy.value = false;
        }
    }
    watch(() => props.route.query, load, { immediate: true });
    onUnmounted(() => {
        remember();
        generation++;
    });
    async function save() {
        const ticket = generation;
        busy.value = true;
        error.value = '';
        try {
            validate(record.value.data);
            validatePaper(record.value.data);
            if (!record.value.name.trim()) throw Error('Enter a worksheet name.');
            const r = await api(
                '/library/resources' + (record.value.id ? '/' + record.value.id : ''),
                {
                    method: record.value.id ? 'PUT' : 'POST',
                    body: {
                        name: record.value.name.trim(),
                        kind: 'WORKSHEET',
                        collectionId: record.value.collectionId || null,
                        data: clone(record.value.data),
                    },
                },
            );
            if (ticket !== generation) return;
            drafts.delete(record.value.id || 'new');
            record.value = { ...r, data: normalize(r.data), dirty: false };
            remember();
            setCurrent(r.id);
            location.hash = '/teacher/worksheets?id=' + encodeURIComponent(r.id);
            notice.value = 'Worksheet saved.';
            items.value = await api('/library/resources?kind=WORKSHEET');
        } catch (e) {
            if (ticket === generation) error.value = 'Changes kept. ' + e.message;
        } finally {
            if (ticket === generation) busy.value = false;
        }
    }
    function add() {
        record.value.data.blocks.push(question());
        changed();
    }
    function move(i, d) {
        const list = record.value.data.blocks;
        [list[i], list[i + d]] = [list[i + d], list[i]];
        changed();
    }
    function remove(i) {
        record.value.data.blocks.splice(i, 1);
        changed();
    }
    async function edit(b, i) {
        editing.value = {
            id: b.id,
            figure: clone(b.diagram),
            caption: record.value.name + ' · Question ' + (i + 1),
        };
        await nextTick();
        dialog.value.showModal();
    }
    function close() {
        editing.value = null;
    }
    function apply(figure) {
        record.value.data = applyFigure(record.value.data, editing.value.id, figure);
        changed();
        close();
    }
</script>
<template>
    <section class="worksheet-editor">
        <div class="bar">
            <div class="worksheet-picker">
                <label for="open-worksheet">Open worksheet</label>
                <select
                    id="open-worksheet"
                    :value="record?.id || 'new'"
                    :disabled="busy"
                    @change="navigate($event.target.value)"
                >
                    <option value="new">New worksheet</option>
                    <option v-for="w in items" :key="w.id" :value="w.id">{{ w.name }}</option>
                </select>
            </div>
            <button @click="save" :disabled="busy || !record">Save worksheet</button>
            <span role="status">
                {{ busy ? 'Working…' : record?.dirty ? 'Unsaved changes' : notice || 'Saved' }}
            </span>
        </div>
        <p v-if="error" role="alert" class="error">{{ error }}</p>
        <template v-if="record">
            <fieldset :disabled="busy">
                <label for="worksheet-name">Worksheet name</label>
                <input id="worksheet-name" v-model="record.name" maxlength="100" @input="changed" />
                <div class="paper-settings">
                    <div>
                        <label for="paper-size">Paper size</label>
                        <select id="paper-size" v-model="record.data.paper.size" @change="changed">
                            <option v-for="(_, key) in sizes" :key="key">{{ key }}</option>
                        </select>
                    </div>
                    <div>
                        <label for="paper-orientation">Orientation</label>
                        <select
                            id="paper-orientation"
                            v-model="record.data.paper.orientation"
                            @change="changed"
                        >
                            <option value="portrait">Portrait</option>
                            <option value="landscape">Landscape</option>
                        </select>
                    </div>
                </div>
                <PaperPreview :record="record" />
                <p>
                    Drafts stay available while you switch worksheets in this session. Save to keep
                    them after closing or signing out.
                </p>
                <div v-if="!record.data.blocks.length" class="empty">
                    No questions yet. Add a question, or add a figure from the diagram studio.
                </div>
                <article v-for="(b, i) in record.data.blocks" :key="b.id" class="question">
                    <header>
                        <h2>Question {{ i + 1 }}</h2>
                        <div>
                            <button
                                :aria-label="'Move question ' + (i + 1) + ' up'"
                                :disabled="i === 0"
                                @click="move(i, -1)"
                            >
                                ↑
                            </button>
                            <button
                                :aria-label="'Move question ' + (i + 1) + ' down'"
                                :disabled="i === record.data.blocks.length - 1"
                                @click="move(i, 1)"
                            >
                                ↓
                            </button>
                            <button :aria-label="'Remove question ' + (i + 1)" @click="remove(i)">
                                Remove
                            </button>
                        </div>
                    </header>
                    <label :for="'wording-' + b.id">Question wording</label>
                    <textarea
                        :id="'wording-' + b.id"
                        v-model="b.text"
                        maxlength="1500"
                        rows="3"
                        @input="changed"
                    ></textarea>
                    <label :for="'type-' + b.id">Answer format</label>
                    <select :id="'type-' + b.id" v-model="b.type" @change="changed">
                        <option value="WRITTEN">Written answer</option>
                        <option value="MC">Multiple choice</option>
                    </select>
                    <template v-if="b.type === 'MC'">
                        <div v-for="(_, j) in b.choices" :key="j" class="choice">
                            <label :for="'choice-' + b.id + j">
                                Option {{ String.fromCharCode(65 + j) }}
                            </label>
                            <input
                                :id="'choice-' + b.id + j"
                                v-model="b.choices[j]"
                                maxlength="300"
                                @input="changed"
                            />
                            <button
                                :disabled="b.choices.length <= 2"
                                :aria-label="
                                    'Remove option ' +
                                    String.fromCharCode(65 + j) +
                                    ' from question ' +
                                    (i + 1)
                                "
                                @click="
                                    b.choices.splice(j, 1);
                                    changed();
                                "
                            >
                                Remove
                            </button>
                        </div>
                        <button
                            :disabled="b.choices.length >= 6"
                            @click="
                                b.choices.push('');
                                changed();
                            "
                        >
                            Add option ({{ b.choices.length }} of 6)
                        </button>
                    </template>
                    <template v-else>
                        <label :for="'lines-' + b.id">Answer lines (0–10)</label>
                        <input
                            :id="'lines-' + b.id"
                            type="number"
                            min="0"
                            max="10"
                            step="1"
                            v-model.number="b.answerLines"
                            @input="changed"
                        />
                    </template>
                    <template v-if="b.diagram">
                        <label :for="'figure-height-' + b.id">
                            Printed figure height (30–150 mm)
                        </label>
                        <input
                            :id="'figure-height-' + b.id"
                            type="number"
                            min="30"
                            max="150"
                            :value="b.figureHeight || 65"
                            @input="
                                b.figureHeight = Number($event.target.value);
                                changed();
                            "
                        />
                        <button class="edit-figure" @click="edit(b, i)">
                            Edit diagram for question {{ i + 1 }}
                        </button>
                        <ModelCanvas
                            v-if="b.diagram.model"
                            :data="b.diagram"
                            :interactive="false"
                        />
                        <GeometryCanvas
                            v-else
                            :graph="b.diagram"
                            :interactive="false"
                            :editable="false"
                        />
                    </template>
                    <label class="page-break">
                        <input type="checkbox" v-model="b.pageBreakBefore" @change="changed" />
                        Start this question on a new page
                    </label>
                    <details>
                        <summary>Question preview</summary>
                        <p>{{ i + 1 }}. {{ b.text }}</p>
                        <ol v-if="b.type === 'MC'" class="options">
                            <li v-for="(c, j) in b.choices" :key="j">{{ c }}</li>
                        </ol>
                        <div
                            v-else
                            v-for="n in Math.min(10, Math.max(0, Math.trunc(b.answerLines) || 0))"
                            :key="n"
                            class="answer-line"
                        ></div>
                    </details>
                </article>
                <button @click="add" :disabled="record.data.blocks.length >= 30">
                    Add question
                </button>
            </fieldset>
        </template>
        <dialog v-if="editing" ref="dialog" aria-labelledby="placed-title" @cancel.prevent="close">
            <h2 id="placed-title">Edit diagram — {{ editing.caption }}</h2>
            <p>Apply replaces this figure only. Your question and choices stay as they are.</p>
            <ResourceEditor
                :route="{ query: '' }"
                kind="DIAGRAM"
                :initial-data="editing.figure"
                :embedded="true"
                @apply="apply"
                @cancel="close"
            />
        </dialog>
    </section>
</template>
<style scoped>
    .paper-settings {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 16px;
    }
    .page-break {
        display: flex;
        gap: 8px;
        align-items: center;
    }
    .page-break input {
        width: auto;
    }
    .bar,
    header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        flex-wrap: wrap;
    }
    .worksheet-picker {
        flex: 1;
    }
    fieldset {
        border: 0;
        padding: 0;
        min-width: 0;
    }
    select,
    textarea {
        width: 100%;
        font: inherit;
        padding: 12px;
        border: 1px solid #89988d;
        border-radius: 6px;
        background: white;
    }
    textarea {
        resize: vertical;
    }
    .question {
        background: white;
        border: 1px solid #d6dcd5;
        border-radius: 10px;
        padding: 22px;
        margin: 22px 0;
    }
    .question h2 {
        font:
            22px Georgia,
            serif;
    }
    .question label {
        margin-top: 16px;
    }
    header div {
        display: flex;
        gap: 6px;
    }
    .choice {
        display: grid;
        grid-template-columns: 1fr auto;
        gap: 8px;
    }
    .choice label {
        grid-column: 1/-1;
    }
    .edit-figure {
        margin: 20px 0 12px;
    }
    details {
        margin-top: 20px;
    }
    .options {
        list-style-type: upper-alpha;
    }
    .answer-line {
        height: 32px;
        border-bottom: 1px solid #555;
    }
    .error {
        background: #fff0eb;
        padding: 12px;
        color: #8f2020;
    }
    .empty {
        padding: 30px;
        background: white;
        margin: 20px 0;
    }
    dialog {
        width: 1200px;
        max-width: calc(100vw - 24px);
        max-height: 94vh;
        overflow: auto;
        border: 1px solid #89988d;
        border-radius: 12px;
        color: #203b32;
        padding: 22px;
    }
    dialog::backdrop {
        background: #203b3277;
    }
    p {
        line-height: 1.6;
    }
    button {
        max-width: 100%;
    }
    @media (max-width: 600px) {
        .question {
            padding: 14px;
        }
        dialog {
            padding: 12px;
        }
    }
</style>
