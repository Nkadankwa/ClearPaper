<script setup>
    import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue';
    import { api } from '../../../services/api.js';
    import {
        emptyGraph,
        finishDrawing,
        point,
        angleValue,
        autoAngles,
        movePoint,
        deletePoint,
        connectPoints,
        addManualAngle,
        neighbours,
        extendSide,
        addStarter,
    } from '../geometry.js';
    import GeometryCanvas from './GeometryCanvas.vue';
    import WorksheetDestination from '../../worksheets/components/WorksheetDestination.vue';
    import ModelCanvas from './ModelCanvas.vue';
    import ModelControls from './ModelControls.vue';
    import { defaultModel, buildModel, modelNames, triangleToGeometry } from '../models.js';
    const props = defineProps({
        route: Object,
        kind: String,
        embedded: Boolean,
        initialData: Object,
    });
    const emit = defineEmits(['apply', 'cancel']);
    const destinationFigure = ref(null);
    function preparedFigure() {
        if (strokes.value.length) throw Error('Select Done before using pending strokes.');
        return graph.value.model && modelControls.value?.hasPending()
            ? buildModel(modelControls.value.getDraft())
            : clone(graph.value);
    }
    function addToWorksheet() {
        try {
            destinationFigure.value = preparedFigure();
        } catch (e) {
            error.value = e.message;
        }
    }
    function applyPlaced() {
        try {
            emit('apply', preparedFigure());
        } catch (e) {
            error.value = e.message;
        }
    }
    const name = ref(''),
        collectionId = ref(''),
        collections = ref([]),
        resourceId = ref(null),
        graph = ref(emptyGraph()),
        questions = ref(''),
        baseWorksheet = ref({ schemaVersion: 1, blocks: [] }),
        strokes = ref([]),
        tool = ref('draw'),
        selected = ref(''),
        tolerance = ref(12),
        closed = ref(false),
        busy = ref(false),
        error = ref(''),
        notice = ref(''),
        canvas = ref(),
        x = ref(0),
        y = ref(0),
        label = ref(''),
        history = ref([]),
        dirty = ref(false);
    const hidden = ref(false),
        reflex = ref(false),
        from = ref(''),
        to = ref(''),
        vertex = ref(''),
        rayA = ref(''),
        rayB = ref('');
    const rays = computed(() =>
        neighbours(graph.value, vertex.value).map((key) => point(graph.value, key)),
    );
    watch(vertex, () => {
        rayA.value = '';
        rayB.value = '';
    });
    const future = ref([]),
        pointMenu = ref(null),
        menuMode = ref('actions'),
        connectionDetails = ref(),
        angleDetails = ref();
    const selectionTitle = computed(() =>
        selected.value.startsWith('point:')
            ? 'Point ' + selection.value?.name
            : selected.value.startsWith('segment:')
              ? 'Edit side'
              : 'Edit angle',
    );
    const modelDraftDirty = ref(false);
    const starter = ref('RECTANGLE'),
        polygonSides = ref(5),
        modelType = ref('TRIANGLE'),
        modelControls = ref(),
        extensionSide = ref(''),
        extensionEnd = ref(''),
        extensionLength = ref(80),
        extensionStyle = ref('dashed'),
        revision = ref(0),
        saving = ref(false),
        saveFailed = ref(false);
    const extensionEndpoints = computed(() => {
        const e = graph.value.segments.find((e) => e.id === extensionSide.value);
        return e ? [point(graph.value, e.a), point(graph.value, e.b)] : [];
    });
    watch(extensionSide, () => {
        extensionEnd.value = extensionEndpoints.value[1]?.id || '';
    });
    const saveState = computed(() =>
        saving.value
            ? 'Saving…'
            : busy.value
              ? 'Loading…'
              : saveFailed.value
                ? 'Save failed — changes kept'
                : dirty.value || (graph.value.model && modelDraftDirty.value)
                  ? 'Unsaved changes'
                  : resourceId.value
                    ? 'Saved'
                    : 'New resource',
    );
    let request = 0;
    const selection = computed(() => {
        const [kind, key] = selected.value.split(':');
        return kind === 'point'
            ? graph.value.points.find((p) => p.id === key)
            : kind === 'segment'
              ? graph.value.segments.find((e) => e.id === key)
              : graph.value.angles.find((a) => a.id === key);
    });
    const entries = computed(() => [
        ...graph.value.points.map((p) => ({ value: 'point:' + p.id, name: 'Point ' + p.name })),
        ...graph.value.segments.map((e) => ({
            value: 'segment:' + e.id,
            name: 'Side ' + point(graph.value, e.a).name + point(graph.value, e.b).name,
        })),
        ...graph.value.angles.map((a) => ({
            value: 'angle:' + a.id,
            name:
                'Angle ' +
                point(graph.value, a.a).name +
                point(graph.value, a.v).name +
                point(graph.value, a.b).name +
                (a.hidden ? ' (hidden)' : ''),
        })),
    ]);
    const clone = (value) => JSON.parse(JSON.stringify(value));
    async function load() {
        if (props.embedded) {
            graph.value = clone(props.initialData);
            tool.value = 'select';
            return;
        }
        const ticket = ++request;
        busy.value = true;
        error.value = '';
        notice.value = '';
        resourceId.value = null;
        name.value = props.kind === 'DIAGRAM' ? 'Untitled diagram' : 'Untitled worksheet';
        graph.value = emptyGraph();
        questions.value = '';
        baseWorksheet.value = { schemaVersion: 1, blocks: [] };
        strokes.value = [];
        selected.value = '';
        history.value = [];
        future.value = [];
        saving.value = false;
        saveFailed.value = false;
        revision.value = 0;
        extensionSide.value = '';
        extensionEnd.value = '';
        pointMenu.value = null;
        tool.value = 'draw';
        from.value = '';
        to.value = '';
        vertex.value = '';
        rayA.value = '';
        rayB.value = '';
        dirty.value = false;
        try {
            const cs = await api('/library/collections');
            if (ticket !== request) return;
            collections.value = cs;
            collectionId.value = '';
            const key = new URLSearchParams(props.route.query).get('id');
            if (key) {
                const r = await api('/library/resources/' + encodeURIComponent(key));
                if (ticket !== request) return;
                if (r.kind !== props.kind)
                    throw Error(
                        'This resource belongs in the other editor. Open it from your library.',
                    );
                resourceId.value = r.id;
                name.value = r.name;
                collectionId.value = r.collectionId || '';
                if (props.kind === 'DIAGRAM') {
                    graph.value = r.data;
                    tool.value = 'select';
                } else {
                    baseWorksheet.value = r.data;
                    questions.value = r.data.blocks.map((b) => b.text).join('\n');
                }
            }
        } catch (e) {
            if (ticket === request) error.value = e.message;
        } finally {
            if (ticket === request) busy.value = false;
        }
    }
    watch(() => [props.route.query, props.kind], load, { immediate: true });
    watch(selection, () => {
        const item = selection.value;
        if (!item) return;
        x.value = item.x ?? 0;
        y.value = item.y ?? 0;
        label.value = item.name ?? item.label ?? '';
        hidden.value = !!item.hidden;
        reflex.value = !!item.reflex;
    });
    function select(value) {
        selected.value = value;
        tool.value = 'select';
    }
    function setTool(value) {
        closeMenu();
        tool.value = value;
        if (value === 'draw') selected.value = '';
    }
    function markDirty() {
        dirty.value = true;
        revision.value++;
        saveFailed.value = false;
    }
    function snapshot() {
        return {
            graph: clone(graph.value),
            strokes: clone(strokes.value),
            selected: selected.value,
        };
    }
    function remember() {
        history.value.push(snapshot());
        if (history.value.length > 100) history.value.shift();
        future.value = [];
    }
    function restore(state) {
        graph.value = state.graph;
        strokes.value = state.strokes;
        selected.value = state.selected;
        tool.value = strokes.value.length ? 'draw' : 'select';
        markDirty();
        error.value = '';
        closeMenu();
    }
    function stroke(value) {
        if (strokes.value.length >= 20) {
            error.value = 'Finish or clear your 20 pending strokes before drawing more.';
            return;
        }
        remember();
        strokes.value.push(value);
        dirty.value = true;
    }
    function clearStrokes() {
        if (!strokes.value.length) return;
        remember();
        strokes.value = [];
        markDirty();
        notice.value = 'Pending strokes cleared. Undo can restore them.';
    }
    function done() {
        error.value = '';
        try {
            const next = finishDrawing(
                graph.value,
                strokes.value,
                Number(tolerance.value),
                closed.value,
            );
            remember();
            graph.value = next;
            strokes.value = [];
            tool.value = 'select';
            selected.value = '';
            markDirty();
            notice.value = 'Drawing ready. Click a part to edit it.';
        } catch (e) {
            error.value = e.message;
        }
    }
    function undo() {
        if (busy.value || !history.value.length) return;
        future.value.push(snapshot());
        restore(history.value.pop());
        notice.value = 'Last change undone.';
    }
    function redo() {
        if (busy.value || !future.value.length) return;
        history.value.push(snapshot());
        restore(future.value.pop());
        notice.value = 'Change restored.';
    }
    function commit(next, message) {
        remember();
        graph.value = next;
        markDirty();
        notice.value = message;
        error.value = '';
    }
    function edit() {
        const item = selection.value;
        if (!item) return;
        try {
            let next;
            if (selected.value.startsWith('point:'))
                next = movePoint(
                    graph.value,
                    item.id,
                    Number(x.value),
                    Number(y.value),
                    label.value,
                );
            else {
                next = clone(graph.value);
                const target = selected.value.startsWith('segment:')
                    ? next.segments.find((e) => e.id === item.id)
                    : next.angles.find((a) => a.id === item.id);
                target.label = label.value.slice(0, 40);
                if (selected.value.startsWith('angle:')) {
                    target.hidden = hidden.value;
                    if (target.reflex !== reflex.value) {
                        target.reflex = reflex.value;
                        target.manual = true;
                        target.sweep = false;
                    }
                }
            }
            commit(next, 'Selected part updated.');
        } catch (e) {
            error.value = e.message;
        }
    }
    function drag(value) {
        try {
            commit(
                movePoint(graph.value, value.id, value.x, value.y),
                'Point moved. Angles recalculated.',
            );
        } catch (e) {
            error.value = e.message;
        }
    }
    function removePoint() {
        try {
            commit(
                deletePoint(graph.value, selection.value.id),
                'Point and its connected sides/angles removed. Undo restores them.',
            );
            selected.value = '';
        } catch (e) {
            error.value = e.message;
        }
    }
    function connect() {
        try {
            commit(
                connectPoints(graph.value, from.value, to.value),
                'Points connected. Angles recalculated.',
            );
        } catch (e) {
            error.value = e.message;
        }
    }
    function manualAngle() {
        try {
            const next = addManualAngle(graph.value, vertex.value, rayA.value, rayB.value);
            commit(next, 'Angle added. Choose its label or visibility below.');
            tool.value = 'select';
            selected.value = 'angle:' + next.angles.at(-1).id;
        } catch (e) {
            error.value = e.message;
        }
    }
    function insertStarter() {
        try {
            if (strokes.value.length) throw Error('Finish or clear pending strokes first.');
            commit(
                addStarter(graph.value, starter.value, Number(polygonSides.value)),
                'Starting shape added. Select a part to edit it.',
            );
            tool.value = 'select';
            selected.value = '';
        } catch (e) {
            error.value = e.message;
        }
    }
    function extend() {
        try {
            commit(
                extendSide(
                    graph.value,
                    extensionSide.value,
                    extensionEnd.value,
                    Number(extensionLength.value),
                    extensionStyle.value,
                ),
                'Extension added. Its points and angles are editable.',
            );
        } catch (e) {
            error.value = e.message;
        }
    }
    function useModel() {
        try {
            if (strokes.value.length) throw Error('Finish or clear pending strokes first.');
            commit(
                buildModel(defaultModel(modelType.value)),
                'Model ready. Change the values to update it.',
            );
            selected.value = '';
            tool.value = 'select';
        } catch (e) {
            error.value = e.message;
        }
    }
    function updateModel(model) {
        try {
            commit(buildModel(model), 'Model updated.');
            selected.value = '';
        } catch (e) {
            error.value = e.message;
        }
    }
    function applyModelDraft() {
        if (graph.value.model && modelControls.value?.hasPending())
            commit(buildModel(modelControls.value.getDraft()), 'Model values applied.');
    }
    async function convertTriangle() {
        if (props.embedded) {
            try {
                applyModelDraft();
                commit(triangleToGeometry(graph.value), 'Converted to editable geometry.');
            } catch (e) {
                error.value = e.message;
            }
            return;
        }

        const original = await save();
        if (!original) return;
        const ticket = request;
        busy.value = true;
        error.value = '';
        try {
            const data = triangleToGeometry(graph.value),
                r = await api('/library/resources', {
                    method: 'POST',
                    body: {
                        name: name.value.slice(0, 86) + ' (geometry)',
                        kind: 'DIAGRAM',
                        collectionId: collectionId.value || null,
                        data,
                    },
                });
            if (ticket === request)
                location.hash = '/teacher/studio?id=' + encodeURIComponent(r.id);
        } catch (e) {
            if (ticket === request)
                error.value =
                    'The original triangle is saved. The geometry copy could not be created: ' +
                    e.message;
        } finally {
            if (ticket === request) busy.value = false;
        }
    }
    function regenerate() {
        try {
            const next = clone(graph.value);
            autoAngles(next);
            commit(next, 'Angle markers refreshed; surviving labels and visibility kept.');
        } catch (e) {
            error.value = e.message;
        }
    }
    async function save() {
        if (busy.value) return;
        if (!name.value.trim()) {
            error.value = 'Enter a resource name.';
            return;
        }
        if (strokes.value.length) {
            error.value = 'Select Done before saving pending strokes.';
            return;
        }
        try {
            applyModelDraft();
        } catch (e) {
            error.value = e.message;
            return;
        }
        const ticket = request,
            version = revision.value,
            data =
                props.kind === 'DIAGRAM'
                    ? clone(graph.value)
                    : {
                          ...clone(baseWorksheet.value),
                          blocks: questions.value
                              .split('\n')
                              .filter((s) => s.trim())
                              .map((text, i) => ({ ...baseWorksheet.value.blocks[i], text })),
                      };
        busy.value = true;
        saving.value = true;
        saveFailed.value = false;
        error.value = '';
        try {
            const body = {
                    name: name.value.trim(),
                    kind: props.kind,
                    collectionId: collectionId.value || null,
                    data,
                },
                r = await api(
                    '/library/resources' + (resourceId.value ? '/' + resourceId.value : ''),
                    {
                        method: resourceId.value ? 'PUT' : 'POST',
                        body,
                    },
                );
            if (ticket !== request) return;
            resourceId.value = r.id;
            dirty.value = revision.value !== version;
            notice.value = dirty.value
                ? 'Saved the earlier version. Your latest changes are still unsaved.'
                : 'Saved to your personal library.';
            window.history.replaceState(
                null,
                '',
                '#/teacher/' +
                    (props.kind === 'DIAGRAM' ? 'studio' : 'worksheets') +
                    '?id=' +
                    encodeURIComponent(r.id),
            );
            return r;
        } catch (e) {
            if (ticket === request) {
                saveFailed.value = true;
                error.value = 'Save failed. Your changes are still here. ' + e.message;
            }
        } finally {
            if (ticket === request) {
                busy.value = false;
                saving.value = false;
            }
        }
    }
    async function download() {
        try {
            applyModelDraft();
            await nextTick();
            const svg = canvas.value.exportSvg(),
                url = URL.createObjectURL(new Blob([svg], { type: 'image/svg+xml' })),
                a = document.createElement('a');
            a.href = url;
            a.download = (name.value.replace(/[^a-z0-9_-]/gi, '-') || 'diagram') + '.svg';
            a.click();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (e) {
            error.value = e.message;
        }
    }
    async function openPointMenu({ id, x: screenX, y: screenY }) {
        if (busy.value || !point(graph.value, id)) return;
        selected.value = 'point:' + id;
        tool.value = 'select';
        menuMode.value = 'actions';
        error.value = '';
        pointMenu.value = {
            id,
            x: Math.max(10, Math.min(screenX, window.innerWidth - 280)),
            y: Math.max(10, Math.min(screenY, window.innerHeight - 310)),
        };
        await nextTick();
        document.querySelector('.point-menu [role=menuitem]')?.focus();
    }
    function menuFromButton(event) {
        const r = event.currentTarget.getBoundingClientRect();
        openPointMenu({ id: selection.value.id, x: r.left, y: r.bottom + 6 });
    }
    function closeMenu(restoreFocus = false) {
        const key = pointMenu.value?.id;
        if (key && menuMode.value !== 'actions' && selection.value) {
            label.value = selection.value.name;
            x.value = selection.value.x;
            y.value = selection.value.y;
        }
        pointMenu.value = null;
        if (restoreFocus && key) nextTick(() => canvas.value?.focusPoint(key));
    }
    async function quickEdit(mode) {
        menuMode.value = mode;
        await nextTick();
        document.querySelector('.point-menu input')?.focus();
    }
    function applyQuickEdit() {
        edit();
        if (!error.value) closeMenu(true);
    }
    function removeFromMenu() {
        removePoint();
        closeMenu();
        nextTick(() => document.getElementById('selected-part')?.focus());
    }
    async function connectFromMenu() {
        from.value = pointMenu.value.id;
        to.value = '';
        closeMenu();
        connectionDetails.value.open = true;
        await nextTick();
        document.getElementById('connect-to')?.focus();
    }
    async function angleFromMenu() {
        vertex.value = pointMenu.value.id;
        closeMenu();
        angleDetails.value.open = true;
        await nextTick();
        document.getElementById('angle-ray-a')?.focus();
    }
    function menuKeys(event) {
        if (event.key === 'Escape') {
            event.preventDefault();
            event.stopPropagation();
            closeMenu(true);
            return;
        }
        if (
            menuMode.value !== 'actions' ||
            !['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)
        )
            return;
        event.preventDefault();
        const items = [...document.querySelectorAll('.point-menu [role=menuitem]:not(:disabled)')],
            index = items.indexOf(document.activeElement);
        const target =
            event.key === 'Home'
                ? 0
                : event.key === 'End'
                  ? items.length - 1
                  : (index + (event.key === 'ArrowDown' ? 1 : -1) + items.length) % items.length;
        items[target]?.focus();
    }
    function outsideMenu(event) {
        if (pointMenu.value && !event.target.closest('.point-menu')) closeMenu();
    }
    function shortcuts(event) {
        if (event.key === 'Escape' && pointMenu.value) {
            closeMenu(true);
            return;
        }
        if (
            props.kind !== 'DIAGRAM' ||
            busy.value ||
            event.target.closest('input,textarea,select,[contenteditable="true"]') ||
            !(event.ctrlKey || event.metaKey) ||
            event.altKey
        )
            return;
        if (event.key.toLowerCase() === 'z') {
            event.preventDefault();
            event.shiftKey ? redo() : undo();
        } else if (event.key.toLowerCase() === 'y') {
            event.preventDefault();
            redo();
        }
    }
    function repositionMenu() {
        closeMenu();
    }
    onMounted(() => {
        document.addEventListener('pointerdown', outsideMenu);
        window.addEventListener('keydown', shortcuts);
        window.addEventListener('resize', repositionMenu);
        window.addEventListener('scroll', repositionMenu, true);
    });
    onUnmounted(() => {
        request++;
        document.removeEventListener('pointerdown', outsideMenu);
        window.removeEventListener('keydown', shortcuts);
        window.removeEventListener('resize', repositionMenu);
        window.removeEventListener('scroll', repositionMenu, true);
    });
</script>
<template>
    <div class="editor">
        <div v-if="embedded" class="resource-bar">
            <button class="primary-action" @click="applyPlaced">Apply figure to question</button>
            <button @click="emit('cancel')">Cancel diagram editing</button>
        </div>
        <div v-else class="resource-bar">
            <a href="#/teacher/library">Back to library</a>
            <div class="resource-title">
                <label class="sr-only" for="resource-name">Resource name</label>
                <input
                    id="resource-name"
                    v-model="name"
                    maxlength="100"
                    @input="markDirty"
                    :disabled="busy"
                />
            </div>
            <span class="save-state" role="status">{{ saveState }}</span>
            <button class="primary-action" @click="save" :disabled="busy">
                {{ saving ? 'Saving…' : saveFailed ? 'Retry save' : 'Save to library' }}
            </button>
            <button v-if="kind === 'DIAGRAM'" @click="addToWorksheet" :disabled="busy">
                Add to worksheet
            </button>
            <button
                v-if="kind === 'DIAGRAM'"
                @click="download"
                :disabled="busy || !!strokes.length"
            >
                Download SVG
            </button>
        </div>
        <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
        <p v-if="notice" class="feedback" role="status">{{ notice }}</p>
        <div class="editor-grid">
            <section class="controls" aria-label="Diagram tools">
                <template v-if="kind === 'DIAGRAM'">
                    <ModelControls
                        v-if="graph.model"
                        ref="modelControls"
                        :model="graph.model"
                        :disabled="busy"
                        @draft="modelDraftDirty = $event"
                        @update="updateModel"
                        @convert="convertTriangle"
                    />
                    <template v-else>
                        <div class="panel-heading">
                            <h2>
                                {{
                                    tool === 'draw'
                                        ? 'Draw your figure'
                                        : selection
                                          ? selectionTitle
                                          : 'Edit your figure'
                                }}
                            </h2>
                            <p>
                                {{
                                    tool === 'draw'
                                        ? 'Draw one or more strokes, then choose Done.'
                                        : selection
                                          ? 'Changes apply to the highlighted part.'
                                          : 'Click a part on the canvas, or choose one below.'
                                }}
                            </p>
                        </div>
                        <template v-if="tool === 'draw'">
                            <label for="tolerance">How much should lines straighten?</label>
                            <select id="tolerance" v-model="tolerance">
                                <option :value="6">Keep small corners</option>
                                <option :value="12">Balanced</option>
                                <option :value="20">Smooth small bends</option>
                            </select>
                            <label class="check">
                                <input type="checkbox" v-model="closed" />
                                Join the ends of each stroke
                            </label>
                            <p class="help">Leave this off for an open L or K.</p>
                            <p class="pending">
                                {{ strokes.length }}
                                {{ strokes.length === 1 ? 'stroke' : 'strokes' }} waiting for Done
                            </p>
                            <button
                                class="primary-action full"
                                @click="done"
                                :disabled="!strokes.length || busy"
                            >
                                Done — calculate angles
                            </button>
                            <button
                                class="full"
                                @click="clearStrokes"
                                :disabled="!strokes.length || busy"
                            >
                                Clear pending strokes
                            </button>
                        </template>
                        <template v-else>
                            <label for="selected-part">Choose a part</label>
                            <select id="selected-part" v-model="selected">
                                <option value="">Select a point, side or angle…</option>
                                <option v-for="e in entries" :key="e.value" :value="e.value">
                                    {{ e.name }}
                                </option>
                            </select>
                            <p v-if="!selection" class="help">
                                Points glow green. Angles glow orange. Right-click a point for quick
                                edits.
                            </p>
                            <form v-if="selection" class="part-form" @submit.prevent="edit">
                                <label for="part-label">
                                    {{
                                        selected.startsWith('point:')
                                            ? 'Point name'
                                            : 'Label or expression'
                                    }}
                                </label>
                                <input
                                    id="part-label"
                                    v-model="label"
                                    :maxlength="selected.startsWith('point:') ? 20 : 40"
                                    :placeholder="
                                        selected.startsWith('point:') ? 'A' : 'e.g. x or 2x + 5'
                                    "
                                />
                                <template v-if="selected.startsWith('point:')">
                                    <p class="help">
                                        Drag this point on the canvas, or set its position here.
                                    </p>
                                    <div class="coordinate-fields">
                                        <div>
                                            <label for="point-x">Horizontal (X)</label>
                                            <input
                                                id="point-x"
                                                type="number"
                                                step="any"
                                                v-model.number="x"
                                                min="-10000"
                                                max="10000"
                                                required
                                            />
                                        </div>
                                        <div>
                                            <label for="point-y">Vertical (Y)</label>
                                            <input
                                                id="point-y"
                                                type="number"
                                                step="any"
                                                v-model.number="y"
                                                min="-10000"
                                                max="10000"
                                                required
                                            />
                                        </div>
                                    </div>
                                </template>
                                <template v-if="selected.startsWith('angle:')">
                                    <p class="measurement">
                                        {{ Math.round(angleValue(graph, selection)) }}°
                                        <span>measured from the figure</span>
                                    </p>
                                    <label class="check">
                                        <input id="angle-hidden" type="checkbox" v-model="hidden" />
                                        Hide this angle and label
                                    </label>
                                    <label class="check">
                                        <input id="angle-reflex" type="checkbox" v-model="reflex" />
                                        Use the larger (reflex) angle
                                    </label>
                                </template>
                                <button class="primary-action full" :disabled="busy">
                                    Apply changes
                                </button>
                            </form>
                            <div
                                v-if="selection && selected.startsWith('point:')"
                                class="point-actions"
                            >
                                <button
                                    class="full"
                                    @click="menuFromButton"
                                    :disabled="busy"
                                    aria-haspopup="menu"
                                    :aria-expanded="!!pointMenu"
                                >
                                    Point actions
                                </button>
                                <button class="danger full" @click="removePoint" :disabled="busy">
                                    Delete point
                                </button>
                            </div>
                        </template>
                        <details class="tool-details">
                            <summary>Start with a shape</summary>
                            <label for="starter-shape">Shape</label>
                            <select id="starter-shape" v-model="starter">
                                <option value="RECTANGLE">Rectangle</option>
                                <option value="TRAPEZIUM">Trapezium</option>
                                <option value="POLYGON">Regular polygon</option>
                                <option value="OPEN_L">Open L</option>
                                <option value="CONCAVE_L">Concave L outline</option>
                                <option value="K">K junction</option>
                            </select>
                            <template v-if="starter === 'POLYGON'">
                                <label for="polygon-sides">Number of sides</label>
                                <input
                                    id="polygon-sides"
                                    v-model.number="polygonSides"
                                    type="number"
                                    min="3"
                                    max="12"
                                    step="1"
                                />
                            </template>
                            <button class="full" @click="insertStarter" :disabled="busy">
                                Add shape
                            </button>
                            <p class="help">
                                Adds an editable figure without removing your current drawing.
                            </p>
                        </details>
                        <details class="tool-details">
                            <summary>Extend a side</summary>
                            <label for="extension-side">Side</label>
                            <select id="extension-side" v-model="extensionSide">
                                <option value="">Choose a side…</option>
                                <option v-for="e in graph.segments" :key="e.id" :value="e.id">
                                    {{ point(graph, e.a).name }}{{ point(graph, e.b).name }}
                                </option>
                            </select>
                            <label for="extension-end">Extend past point</label>
                            <select id="extension-end" v-model="extensionEnd">
                                <option value="">Choose an endpoint…</option>
                                <option v-for="p in extensionEndpoints" :key="p.id" :value="p.id">
                                    {{ p.name }}
                                </option>
                            </select>
                            <label for="extension-length">Length (canvas units)</label>
                            <input
                                id="extension-length"
                                v-model.number="extensionLength"
                                type="number"
                                min="1"
                                max="1000"
                            />
                            <label for="extension-style">Line style</label>
                            <select id="extension-style" v-model="extensionStyle">
                                <option value="dashed">Dashed</option>
                                <option value="solid">Solid</option>
                            </select>
                            <button
                                class="full"
                                @click="extend"
                                :disabled="busy || !extensionSide || !extensionEnd"
                            >
                                Add extension
                            </button>
                            <p class="help">
                                Starts in line with this side. Its endpoints can then move
                                independently.
                            </p>
                        </details>
                        <details ref="connectionDetails" class="tool-details">
                            <summary>Connect two points</summary>
                            <p class="help">Add a side between points already in your figure.</p>
                            <label for="connect-from">Start point</label>
                            <select id="connect-from" v-model="from">
                                <option value="">Choose a point…</option>
                                <option v-for="p in graph.points" :key="p.id" :value="p.id">
                                    {{ p.name }}
                                </option>
                            </select>
                            <label for="connect-to">End point</label>
                            <select id="connect-to" v-model="to">
                                <option value="">Choose a point…</option>
                                <option v-for="p in graph.points" :key="p.id" :value="p.id">
                                    {{ p.name }}
                                </option>
                            </select>
                            <button class="full" @click="connect" :disabled="busy || !from || !to">
                                Connect points
                            </button>
                        </details>
                        <details ref="angleDetails" class="tool-details">
                            <summary>Add an angle</summary>
                            <p class="help">
                                Choose a corner, then the two connected sides to measure.
                            </p>
                            <label for="angle-vertex">Corner point</label>
                            <select id="angle-vertex" v-model="vertex">
                                <option value="">Choose the corner…</option>
                                <option v-for="p in graph.points" :key="p.id" :value="p.id">
                                    {{ p.name }}
                                </option>
                            </select>
                            <label for="angle-ray-a">First side — towards</label>
                            <select id="angle-ray-a" v-model="rayA">
                                <option value="">Choose a connected point…</option>
                                <option v-for="p in rays" :key="p.id" :value="p.id">
                                    {{ p.name }}
                                </option>
                            </select>
                            <label for="angle-ray-b">Second side — towards</label>
                            <select id="angle-ray-b" v-model="rayB">
                                <option value="">Choose a connected point…</option>
                                <option v-for="p in rays" :key="p.id" :value="p.id">
                                    {{ p.name }}
                                </option>
                            </select>
                            <button
                                class="full"
                                @click="manualAngle"
                                :disabled="busy || !vertex || !rayA || !rayB"
                            >
                                Add angle
                            </button>
                        </details>
                    </template>
                    <details class="tool-details">
                        <summary>Maths &amp; physics models</summary>
                        <label for="model-type">Model</label>
                        <select id="model-type" v-model="modelType">
                            <option v-for="(title, type) in modelNames" :key="type" :value="type">
                                {{ title }}
                            </option>
                        </select>
                        <button class="full" @click="useModel" :disabled="busy">
                            {{
                                graph.points.length || graph.model
                                    ? 'Replace figure with model'
                                    : 'Create model'
                            }}
                        </button>
                        <p class="help">
                            Creates a figure from adjustable values. Undo restores the previous
                            figure.
                        </p>
                    </details>
                    <details class="tool-details">
                        <summary>More settings</summary>
                        <label for="resource-collection">Save in collection</label>
                        <select id="resource-collection" v-model="collectionId" @change="markDirty">
                            <option value="">Unfiled</option>
                            <option v-for="c in collections" :key="c.id" :value="c.id">
                                {{ c.name }}
                            </option>
                        </select>
                        <button
                            v-if="!graph.model"
                            class="full"
                            @click="regenerate"
                            :disabled="busy || !!strokes.length"
                        >
                            Refresh angle markers
                        </button>
                        <p class="help">
                            Angles display as whole numbers; rounding can change displayed totals.
                        </p>
                    </details>
                </template>
                <template v-else>
                    <h2>Worksheet details</h2>
                    <label for="resource-collection">Collection</label>
                    <select id="resource-collection" v-model="collectionId" @change="dirty = true">
                        <option value="">Unfiled</option>
                        <option v-for="c in collections" :key="c.id" :value="c.id">
                            {{ c.name }}
                        </option>
                    </select>
                    <p class="help">
                        Full page composition and printing follow in the worksheet tasks.
                    </p>
                </template>
            </section>
            <section
                class="surface"
                :aria-label="kind === 'DIAGRAM' ? 'Diagram canvas' : 'Worksheet content'"
            >
                <template v-if="kind === 'DIAGRAM'">
                    <div class="canvas-tools">
                        <div
                            v-if="!graph.model"
                            class="mode-tools"
                            role="group"
                            aria-label="Canvas mode"
                        >
                            <button
                                :aria-pressed="tool === 'draw'"
                                @click="setTool('draw')"
                                :disabled="busy"
                            >
                                Draw freehand
                            </button>
                            <button
                                :aria-pressed="tool === 'select'"
                                @click="setTool('select')"
                                :disabled="busy"
                            >
                                Select &amp; edit
                            </button>
                        </div>
                        <div class="history-tools" role="group" aria-label="Edit history">
                            <button
                                @click="undo"
                                :disabled="busy || !history.length"
                                title="Undo (Ctrl+Z)"
                            >
                                Undo
                            </button>
                            <button
                                @click="redo"
                                :disabled="busy || !future.length"
                                title="Redo (Ctrl+Shift+Z)"
                            >
                                Redo
                            </button>
                        </div>
                    </div>
                    <p class="canvas-hint">
                        {{
                            graph.model
                                ? 'Update the model values to change this figure.'
                                : tool === 'draw'
                                  ? 'Draw with your mouse or finger. Each stroke stays editable until Done.'
                                  : 'Click to select. Drag points to move them. Right-click a point for quick actions.'
                        }}
                    </p>
                    <button
                        v-if="tool !== 'draw' && strokes.length"
                        class="finish-pending"
                        @click="done"
                        :disabled="busy"
                    >
                        Finish {{ strokes.length }} pending strokes
                    </button>
                    <ModelCanvas v-if="graph.model" ref="canvas" :data="graph" :disabled="busy" />
                    <GeometryCanvas
                        v-else
                        ref="canvas"
                        :graph="graph"
                        :drawing="tool === 'draw'"
                        :strokes="strokes"
                        :selected="selected"
                        :disabled="busy"
                        @drag="drag"
                        @stroke="stroke"
                        @select="select"
                        @point-context="openPointMenu"
                    />
                    <p v-if="graph.model" class="help counts">
                        {{ modelNames[graph.model.type] }} · Parameter-driven diagram
                    </p>
                    <p v-else class="help counts">
                        {{ graph.points.length }} points · {{ graph.segments.length }} sides ·
                        {{ graph.angles.length }} angles
                    </p>
                </template>
                <template v-else>
                    <h2>{{ name }}</h2>
                    <label for="worksheet-questions">Questions — one per line</label>
                    <textarea
                        id="worksheet-questions"
                        v-model="questions"
                        rows="12"
                        maxlength="45000"
                        @input="dirty = true"
                    ></textarea>
                    <ol>
                        <li
                            v-for="(q, i) in questions.split('\n').filter((s) => s.trim())"
                            :key="i"
                        >
                            {{ q }}
                        </li>
                    </ol>
                </template>
            </section>
        </div>
        <WorksheetDestination
            v-if="destinationFigure"
            :figure="destinationFigure"
            @close="destinationFigure = null"
        />
        <Teleport to="body" :disabled="embedded">
            <div
                v-if="pointMenu"
                class="point-menu"
                :style="{ left: pointMenu.x + 'px', top: pointMenu.y + 'px' }"
                @keydown="menuKeys"
            >
                <template v-if="menuMode === 'actions'">
                    <div class="menu-heading">Point {{ selection?.name }}</div>
                    <div role="menu" aria-label="Point actions">
                        <button role="menuitem" @click="quickEdit('rename')">Rename point</button>
                        <button role="menuitem" @click="quickEdit('position')">
                            Edit position
                        </button>
                        <button role="menuitem" @click="connectFromMenu">
                            Connect from this point
                        </button>
                        <button
                            role="menuitem"
                            @click="angleFromMenu"
                            :disabled="neighbours(graph, pointMenu.id).length < 2"
                        >
                            Add angle at this point
                        </button>
                        <button role="menuitem" class="danger" @click="removeFromMenu">
                            Delete point
                        </button>
                    </div>
                </template>
                <form v-else @submit.prevent="applyQuickEdit" aria-label="Quick point edit">
                    <h2>{{ menuMode === 'rename' ? 'Rename point' : 'Edit position' }}</h2>
                    <template v-if="menuMode === 'rename'">
                        <label for="quick-point-name">Point name</label>
                        <input id="quick-point-name" v-model="label" maxlength="20" required />
                    </template>
                    <template v-else>
                        <div class="coordinate-fields">
                            <div>
                                <label for="quick-x">Horizontal (X)</label>
                                <input
                                    id="quick-x"
                                    type="number"
                                    step="any"
                                    v-model.number="x"
                                    min="-10000"
                                    max="10000"
                                    required
                                />
                            </div>
                            <div>
                                <label for="quick-y">Vertical (Y)</label>
                                <input
                                    id="quick-y"
                                    type="number"
                                    step="any"
                                    v-model.number="y"
                                    min="-10000"
                                    max="10000"
                                    required
                                />
                            </div>
                        </div>
                    </template>
                    <p v-if="error" class="quick-error" role="alert">{{ error }}</p>
                    <div class="quick-buttons">
                        <button type="submit" class="primary-action">Apply</button>
                        <button type="button" @click="closeMenu(true)">Cancel</button>
                    </div>
                </form>
            </div>
        </Teleport>
    </div>
</template>
<style scoped>
    .resource-bar {
        display: flex;
        align-items: center;
        gap: 12px;
        flex-wrap: wrap;
    }
    .resource-bar a {
        color: #235a43;
        text-underline-offset: 3px;
        font-size: 14px;
    }
    .resource-title {
        flex: 1;
        min-width: 180px;
    }
    .resource-title input {
        font-weight: 600;
        background: white;
    }
    .save-state {
        font-size: 13px;
        color: #56675e;
    }
    .primary-action {
        background: #235a43;
        color: white;
        border-color: #235a43;
    }
    .primary-action:hover {
        background: #174430;
    }
    .editor-grid {
        display: grid;
        grid-template-columns: 290px minmax(0, 1fr);
        gap: 22px;
        margin-top: 22px;
        align-items: start;
    }
    .controls,
    .surface {
        background: white;
        border: 1px solid #d6dcd5;
        border-radius: 12px;
        padding: 20px;
        min-width: 0;
    }
    .surface {
        position: sticky;
        top: 20px;
    }
    .panel-heading h2,
    .controls > h2 {
        margin: 0;
        font-size: 20px;
    }
    .panel-heading p {
        font-size: 13px;
        color: #56675e;
        margin: 8px 0 18px;
        line-height: 1.6;
    }
    .controls select {
        width: 100%;
        font: inherit;
        padding: 10px;
        border: 1px solid #89988d;
        border-radius: 6px;
        background: white;
    }
    .controls label {
        margin-top: 14px;
    }
    .full {
        display: block;
        width: 100%;
        margin-top: 12px;
    }
    .part-form {
        margin-top: 18px;
    }
    .coordinate-fields {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 12px;
    }
    .coordinate-fields input {
        min-width: 0;
    }
    .check {
        display: flex;
        align-items: center;
        gap: 8px;
        line-height: 1.5;
    }
    .check input {
        width: auto;
    }
    .help,
    .canvas-hint {
        font-size: 13px;
        color: #56675e;
        line-height: 1.65;
    }
    .pending {
        font-size: 13px;
        font-weight: 600;
        margin-top: 22px;
    }
    .measurement {
        font-size: 26px;
        margin: 20px 0 12px;
        font-variant-numeric: tabular-nums;
    }
    .measurement span {
        display: block;
        font-size: 12px;
        color: #56675e;
    }
    .danger {
        color: #912b20;
    }
    .tool-details {
        margin-top: 20px;
        padding-top: 16px;
        border-top: 1px solid #d6dcd5;
    }
    .tool-details summary {
        font-size: 14px;
        font-weight: 600;
        cursor: pointer;
        line-height: 1.5;
    }
    .canvas-tools {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        flex-wrap: wrap;
    }
    .mode-tools,
    .history-tools {
        display: flex;
        gap: 6px;
    }
    .canvas-tools button {
        font-size: 13px;
        padding: 10px 12px;
    }
    .mode-tools [aria-pressed='true'] {
        background: #dfe9dd;
        border-color: #235a43;
        color: #174430;
    }
    .canvas-hint {
        margin: 14px 0;
    }
    .counts {
        border-top: 1px solid #d6dcd5;
        padding-top: 12px;
        margin-bottom: 0;
    }
    .finish-pending {
        margin-bottom: 14px;
    }
    .feedback {
        background: #e5ede0;
        padding: 12px;
    }
    .feedback.error {
        background: #fff0eb;
        color: #8f2020;
    }
    .surface h2 {
        margin-top: 0;
        font-size: 22px;
    }
    .surface textarea {
        width: 100%;
        font: inherit;
        border: 1px solid #89988d;
        border-radius: 6px;
        padding: 12px;
        resize: vertical;
        line-height: 1.6;
    }
    .surface ol {
        line-height: 1.8;
    }
    .sr-only {
        position: absolute;
        width: 1px;
        height: 1px;
        margin: -1px;
        overflow: hidden;
        clip-path: inset(50%);
    }
    .point-menu {
        position: fixed;
        z-index: 1000;
        width: 270px;
        max-width: calc(100vw - 20px);
        background: white;
        box-shadow: 0 8px 28px #203b3233;
        border-radius: 10px;
        padding: 8px;
        color: #203b32;
    }
    .menu-heading {
        padding: 10px 12px;
        font-weight: 700;
        font-size: 14px;
        border-bottom: 1px solid #e3e8e1;
        margin-bottom: 4px;
    }
    .point-menu [role='menuitem'] {
        display: block;
        width: 100%;
        text-align: left;
        border: 0;
        border-radius: 4px;
        padding: 11px 12px;
        font-size: 14px;
    }
    .point-menu form {
        padding: 8px;
    }
    .point-menu h2 {
        font-size: 18px;
        margin: 0 0 12px;
    }
    .point-menu label {
        margin-top: 12px;
    }
    .quick-buttons {
        display: flex;
        gap: 8px;
        margin-top: 16px;
    }
    .quick-error {
        font-size: 13px;
        color: #912b20;
    }
    .point-menu input {
        padding: 10px;
    }
    @media (max-width: 1050px) {
        .editor-grid {
            grid-template-columns: 1fr;
        }
        .surface {
            position: static;
            grid-row: 1;
            padding: 15px;
        }
        .controls {
            grid-row: 2;
        }
        .resource-title {
            order: -1;
            flex-basis: 100%;
        }
        .resource-bar button {
            font-size: 13px;
        }
        .canvas-tools {
            gap: 10px;
        }
        .mode-tools button,
        .history-tools button {
            padding: 9px 10px;
        }
    }
    @media (prefers-reduced-motion: reduce) {
        * {
            scroll-behavior: auto;
        }
    }
</style>
