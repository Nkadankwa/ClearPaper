<script setup>
    import { ref, computed, nextTick } from 'vue';
    import { point, bounds, arc, angleValue } from '../geometry.js';
    const props = defineProps({
        graph: { type: Object, required: true },
        drawing: Boolean,
        strokes: { type: Array, default: () => [] },
        selected: String,
        interactive: { type: Boolean, default: true },
        disabled: Boolean,
        editable: { type: Boolean, default: true },
        frame: String,
        description: String,
    });
    const emit = defineEmits(['select', 'stroke', 'drag', 'point-context']);
    const svg = ref(),
        ink = ref([]);
    const dragging = ref(null),
        frozenBounds = ref(null),
        zoom = ref(1),
        viewport = ref();
    const shownAngles = computed(() => props.graph.angles.filter((a) => !a.hidden));
    async function zoomBy(factor) {
        const area = viewport.value;
        if (!area || dragging.value || ink.value.length) return;
        const cx = (area.scrollLeft + area.clientWidth / 2) / area.scrollWidth,
            cy = (area.scrollTop + area.clientHeight / 2) / area.scrollHeight;
        zoom.value = Math.max(1, Math.min(4, zoom.value * factor));
        await nextTick();
        area.scrollLeft = cx * area.scrollWidth - area.clientWidth / 2;
        area.scrollTop = cy * area.scrollHeight - area.clientHeight / 2;
    }
    function fitCanvas() {
        if (dragging.value || ink.value.length) return;
        zoom.value = 1;
        viewport.value?.scrollTo(0, 0);
    }
    function pointContext(event, key) {
        if (!props.interactive || !props.editable || props.disabled) return;
        const r = event.currentTarget.getBoundingClientRect();
        emit('point-context', {
            id: key,
            x: event.clientX || r.left,
            y: event.clientY || r.bottom,
        });
    }
    function focusPoint(key) {
        [...svg.value.querySelectorAll('[data-part]')]
            .find((n) => n.getAttribute('data-part') === 'point:' + key)
            ?.focus();
    }
    function select(kind, id) {
        if (props.interactive && props.editable && !props.drawing) emit('select', kind + ':' + id);
    }
    function coordinate(event) {
        const p = svg.value.createSVGPoint();
        p.x = event.clientX;
        p.y = event.clientY;
        const q = p.matrixTransform(svg.value.getScreenCTM().inverse());
        return { x: q.x, y: q.y };
    }
    function startDrag(event, key) {
        if (
            !props.interactive ||
            !props.editable ||
            props.drawing ||
            props.disabled ||
            event.button !== 0
        )
            return;
        event.stopPropagation();
        select('point', key);
        dragging.value = { key, start: coordinate(event), position: null };
        frozenBounds.value = bounds(props.graph);
        svg.value.setPointerCapture(event.pointerId);
    }
    function begin(event) {
        if (props.disabled) return;
        if (props.drawing && event.button === 0) {
            ink.value = [coordinate(event)];
            svg.value.setPointerCapture(event.pointerId);
        }
    }
    function move(event) {
        if (dragging.value) {
            dragging.value.position = coordinate(event);
            return;
        }
        if (ink.value.length) {
            const p = coordinate(event),
                last = ink.value.at(-1);
            if (Math.hypot(p.x - last.x, p.y - last.y) > 2 && ink.value.length < 1500)
                ink.value.push(p);
        }
    }
    function finish() {
        if (dragging.value) {
            const d = dragging.value;
            dragging.value = null;
            frozenBounds.value = null;
            if (d.position && Math.hypot(d.position.x - d.start.x, d.position.y - d.start.y) > 0.5)
                emit('drag', { id: d.key, ...d.position });
            return;
        }
        if (ink.value.length > 1)
            emit(
                'stroke',
                ink.value.map((p) => ({ ...p })),
            );
        ink.value = [];
    }
    function cancel() {
        ink.value = [];
        dragging.value = null;
        frozenBounds.value = null;
    }
    function labelPosition(e) {
        const a = point(props.graph, e.a),
            b = point(props.graph, e.b),
            len = Math.hypot(b.x - a.x, b.y - a.y) || 1;
        return {
            x: (a.x + b.x) / 2 + ((b.y - a.y) / len) * 17,
            y: (a.y + b.y) / 2 - ((b.x - a.x) / len) * 17,
        };
    }
    function exportSvg() {
        const copy = svg.value.cloneNode(true);
        copy.removeAttribute('style');
        copy.querySelectorAll('[data-editor]').forEach((n) => n.remove());
        copy.querySelectorAll('[data-part]').forEach((n) => {
            ['tabindex', 'role', 'aria-label', 'aria-pressed', 'data-part'].forEach((a) =>
                n.removeAttribute(a),
            );
            n.removeAttribute('class');
        });
        copy.querySelectorAll('[stroke]').forEach((n) => {
            if (n.getAttribute('stroke') !== 'none') n.setAttribute('stroke', 'black');
        });
        copy.setAttribute('role', 'img');
        copy.setAttribute('aria-label', 'Geometry diagram');
        copy.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
        const style = document.createElementNS('http://www.w3.org/2000/svg', 'style');
        style.textContent =
            'text{font:16px Arial,sans-serif;fill:black;stroke:white;stroke-width:4px;paint-order:stroke;text-anchor:middle}';
        copy.appendChild(style);
        return new XMLSerializer().serializeToString(copy);
    }
    defineExpose({ exportSvg, focusPoint });
</script>
<template>
    <div v-if="interactive" class="zoom-controls" role="group" aria-label="Canvas zoom">
        <button
            type="button"
            aria-label="Zoom out"
            @click="zoomBy(1 / 1.25)"
            :disabled="disabled || zoom <= 1 || !!dragging || !!ink.length"
        >
            −
        </button>
        <output aria-live="polite">{{ Math.round(zoom * 100) }}%</output>
        <button
            type="button"
            aria-label="Zoom in"
            @click="zoomBy(1.25)"
            :disabled="disabled || zoom >= 4 || !!dragging || !!ink.length"
        >
            +
        </button>
        <button type="button" @click="fitCanvas" :disabled="disabled || !!dragging || !!ink.length">
            Fit diagram
        </button>
        <span>Scroll to move around the enlarged canvas.</span>
    </div>
    <div
        ref="viewport"
        class="canvas-viewport"
        :tabindex="interactive ? 0 : undefined"
        role="region"
        aria-label="Diagram canvas viewport"
    >
        <svg
            ref="svg"
            class="geometry"
            :style="{ '--canvas-zoom': zoom }"
            :viewBox="frozenBounds || frame || bounds(graph)"
            :role="interactive ? 'group' : 'img'"
            :aria-label="
                description || (editable ? 'Geometry diagram with editable parts' : 'Diagram')
            "
            @pointerdown="begin"
            @pointermove="move"
            @pointerup="finish"
            @pointercancel="cancel"
            @keydown.esc="cancel"
        >
            <title v-if="description">{{ description }}</title>
            <title v-else>
                {{ graph.points.length }} points and {{ graph.segments.length }} segments.
                {{
                    shownAngles
                        .map((a) => a.label || Math.round(angleValue(graph, a)) + ' degrees')
                        .join(', ')
                }}
            </title>
            <g
                v-for="e in graph.segments"
                :key="e.id"
                :data-part="'segment:' + e.id"
                :role="interactive && editable ? 'button' : undefined"
                :tabindex="interactive && editable ? 0 : undefined"
                :aria-label="'Select side ' + point(graph, e.a).name + point(graph, e.b).name"
                :aria-pressed="selected === 'segment:' + e.id"
                @click="select('segment', e.id)"
                @keydown.enter.prevent="select('segment', e.id)"
                @keydown.space.prevent="select('segment', e.id)"
            >
                <line
                    :x1="point(graph, e.a).x"
                    :y1="point(graph, e.a).y"
                    :x2="point(graph, e.b).x"
                    :y2="point(graph, e.b).y"
                    :stroke="interactive && selected === 'segment:' + e.id ? '#235a43' : 'black'"
                    stroke-width="2"
                    :stroke-dasharray="e.style === 'dashed' ? '7 5' : undefined"
                />
                <line
                    v-if="interactive && editable"
                    data-editor="hit"
                    :x1="point(graph, e.a).x"
                    :y1="point(graph, e.a).y"
                    :x2="point(graph, e.b).x"
                    :y2="point(graph, e.b).y"
                    stroke="transparent"
                    stroke-width="18"
                />
                <text v-if="e.label" :x="labelPosition(e).x" :y="labelPosition(e).y">
                    {{ e.label }}
                </text>
            </g>
            <g
                v-for="a in shownAngles"
                :key="a.id"
                :data-part="'angle:' + a.id"
                :role="interactive && editable ? 'button' : undefined"
                :tabindex="interactive && editable ? 0 : undefined"
                :aria-label="'Select angle at ' + point(graph, a.v).name"
                @click="select('angle', a.id)"
                @keydown.enter.prevent="select('angle', a.id)"
                @keydown.space.prevent="select('angle', a.id)"
            >
                <path
                    v-if="interactive && selected === 'angle:' + a.id"
                    data-editor="selection-glow"
                    class="angle-glow"
                    :d="arc(graph, a).path"
                    fill="none"
                    stroke="#f39a35"
                    stroke-width="7"
                    stroke-opacity="0.55"
                    pointer-events="none"
                />
                <path
                    :d="arc(graph, a).path"
                    fill="none"
                    :stroke="interactive && selected === 'angle:' + a.id ? '#235a43' : 'black'"
                    stroke-width="1.5"
                />
                <path
                    v-if="interactive && editable"
                    data-editor="hit"
                    :d="arc(graph, a).path"
                    fill="none"
                    stroke="transparent"
                    stroke-width="16"
                />
                <text
                    v-if="interactive && selected === 'angle:' + a.id"
                    data-editor="selection-glow"
                    class="angle-label-glow"
                    :x="arc(graph, a).x"
                    :y="arc(graph, a).y"
                    aria-hidden="true"
                    pointer-events="none"
                >
                    {{ a.label || Math.round(angleValue(graph, a)) + '°' }}
                </text>
                <text :x="arc(graph, a).x" :y="arc(graph, a).y">
                    {{ a.label || Math.round(angleValue(graph, a)) + '°' }}
                </text>
            </g>
            <g
                v-for="p in graph.points"
                :key="p.id"
                :data-part="'point:' + p.id"
                :role="interactive && editable ? 'button' : undefined"
                :tabindex="interactive && editable ? 0 : undefined"
                :aria-label="'Select point ' + p.name"
                :aria-pressed="selected === 'point:' + p.id"
                @contextmenu.prevent.stop="pointContext($event, p.id)"
                @keydown.shift.f10.prevent.stop="pointContext($event, p.id)"
                @keydown.context-menu.prevent.stop="pointContext($event, p.id)"
                @pointerdown="startDrag($event, p.id)"
                @click="select('point', p.id)"
                @keydown.enter.prevent="select('point', p.id)"
                @keydown.space.prevent="select('point', p.id)"
            >
                <circle
                    v-if="interactive && selected === 'point:' + p.id"
                    data-editor="selection-glow"
                    class="point-glow"
                    :cx="p.x"
                    :cy="p.y"
                    r="11"
                    fill="#75df9b"
                    fill-opacity="0.25"
                    stroke="#27834b"
                    stroke-width="2"
                    pointer-events="none"
                />
                <circle
                    v-if="interactive && editable"
                    data-editor="handle"
                    :cx="p.x"
                    :cy="p.y"
                    r="14"
                    fill="transparent"
                />
                <circle :cx="p.x" :cy="p.y" r="2" fill="black" />
                <circle
                    v-if="interactive && editable"
                    data-editor="handle"
                    :cx="p.x"
                    :cy="p.y"
                    r="5"
                    :fill="selected === 'point:' + p.id ? '#235a43' : 'black'"
                    stroke="white"
                />
                <text
                    v-if="interactive && selected === 'point:' + p.id"
                    data-editor="selection-glow"
                    class="point-label-glow"
                    :x="p.x - 14"
                    :y="p.y - 13"
                    aria-hidden="true"
                    pointer-events="none"
                >
                    {{ p.name }}
                </text>
                <text :x="p.x - 14" :y="p.y - 13">{{ p.name }}</text>
            </g>
            <slot />
            <circle
                v-if="dragging?.position"
                data-editor="drag-preview"
                :cx="dragging.position.x"
                :cy="dragging.position.y"
                r="7"
                fill="none"
                stroke="#235a43"
                stroke-width="2"
            />
            <g data-editor="ink" fill="none" stroke="#235a43" stroke-width="2.5">
                <polyline
                    v-for="(stroke, i) in [...strokes, ...(ink.length ? [ink] : [])]"
                    :key="i"
                    :points="stroke.map((p) => p.x + ',' + p.y).join(' ')"
                />
            </g>
        </svg>
    </div>
</template>
<style scoped>
    .zoom-controls {
        display: flex;
        align-items: center;
        gap: 10px;
        flex-wrap: wrap;
        margin-bottom: 14px;
    }
    .zoom-controls output {
        min-width: 48px;
        text-align: center;
        font-size: 14px;
    }
    .zoom-controls span {
        font-size: 12px;
        color: #56675e;
    }
    .canvas-viewport {
        height: 360px;
        overflow: auto;
        width: 100%;
        min-width: 0;
    }
    .geometry {
        display: block;
        width: calc(100% * var(--canvas-zoom, 1));
        height: calc(360px * var(--canvas-zoom, 1));
        touch-action: none;
        user-select: none;
        background: #fcfcf9;
    }
    .geometry text {
        font:
            16px Arial,
            sans-serif;
        fill: black;
        stroke: white;
        stroke-width: 4px;
        paint-order: stroke;
        text-anchor: middle;
    }
    .geometry .angle-glow,
    .geometry .angle-label-glow {
        filter: drop-shadow(0 0 5px #f39a35);
    }
    .geometry .angle-label-glow {
        fill: #f39a35;
        stroke: #f39a35;
        stroke-width: 6px;
        stroke-opacity: 0.65;
    }
    .geometry .point-glow,
    .geometry .point-label-glow {
        filter: drop-shadow(0 0 5px #57ce83);
    }
    .geometry .point-label-glow {
        fill: #57ce83;
        stroke: #57ce83;
        stroke-width: 6px;
        stroke-opacity: 0.65;
    }
    .geometry [data-part] {
        cursor: pointer;
    }
    .geometry [data-part]:focus-visible {
        outline: 2px solid #aa6c00;
        outline-offset: 4px;
    }
    @media (max-width: 650px) {
        .canvas-viewport {
            height: 290px;
        }
        .geometry {
            height: calc(290px * var(--canvas-zoom, 1));
        }
    }
</style>
