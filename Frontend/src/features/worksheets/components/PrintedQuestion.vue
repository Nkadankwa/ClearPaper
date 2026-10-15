<script setup>
    import GeometryCanvas from '../../diagrams/components/GeometryCanvas.vue';
    import { computed } from 'vue';
    import { printFrame } from '../paper.js';
    import { modelScene } from '../../diagrams/models.js';
    import ModelCanvas from '../../diagrams/components/ModelCanvas.vue';
    const props = defineProps({ block: Object, number: Number });
    const frame = computed(() => {
        const g = props.block.diagram;
        if (!g) return undefined;
        if (g.model && g.model.type !== 'TRIANGLE') return undefined;
        return printFrame(g.model ? modelScene(g.model).graph : g);
    });
</script>
<template>
    <article class="printed-question">
        <p class="wording">
            <strong>{{ number }}.</strong>
            {{ block.text }}
        </p>
        <div
            v-if="block.diagram"
            class="printed-figure"
            :style="{ '--figure-height': (block.figureHeight || 65) + 'mm' }"
        >
            <ModelCanvas
                v-if="block.diagram.model"
                :data="block.diagram"
                :frame="frame"
                :interactive="false"
            />
            <GeometryCanvas
                v-else
                :graph="block.diagram"
                :frame="frame"
                :interactive="false"
                :editable="false"
            />
        </div>
        <ol v-if="block.type === 'MC'" class="printed-choices">
            <li v-for="(choice, i) in block.choices" :key="i">{{ choice }}</li>
        </ol>
        <template v-else>
            <div
                v-for="n in Math.min(10, Math.max(0, Math.trunc(block.answerLines) || 0))"
                :key="n"
                class="printed-answer-line"
            ></div>
        </template>
    </article>
</template>
<style scoped>
    .printed-question {
        padding-bottom: 6mm;
        break-inside: avoid;
        color: #000;
        font:
            11pt/1.45 Arial,
            sans-serif;
        overflow-wrap: anywhere;
    }
    .wording {
        white-space: pre-wrap;
        margin: 0 0 3mm;
    }
    .printed-choices {
        list-style-type: upper-alpha;
        padding-left: 9mm;
        margin: 3mm 0 0;
    }
    .printed-choices li {
        padding-left: 2mm;
        margin: 1.5mm 0;
        white-space: pre-wrap;
    }
    .printed-answer-line {
        height: 8mm;
        border-bottom: 0.25mm solid #333;
    }
    .printed-figure {
        margin: 2mm auto 3mm;
        width: 100%;
        height: var(--figure-height);
    }
    .printed-figure :deep(.canvas-viewport) {
        height: var(--figure-height);
        overflow: visible;
    }
    .printed-figure :deep(svg.geometry) {
        width: 100%;
        height: 100%;
        background: white;
    }
    .printed-figure :deep(line),
    .printed-figure :deep(path) {
        vector-effect: non-scaling-stroke;
        stroke-width: 1.2px;
    }
    .printed-figure :deep(text) {
        stroke-width: 2px;
    }
    .printed-figure :deep([data-part]) {
        cursor: default;
    }
</style>
