<script setup>
    import { ref, computed } from 'vue';
    import { modelScene } from '../models.js';
    import GeometryCanvas from './GeometryCanvas.vue';
    const props = defineProps({
        data: Object,
        frame: String,
        disabled: Boolean,
        interactive: { type: Boolean, default: true },
    });
    const scene = computed(() => modelScene(props.data.model)),
        canvas = ref();
    defineExpose({ exportSvg: () => canvas.value.exportSvg() });
</script>
<template>
    <GeometryCanvas
        ref="canvas"
        :graph="scene.graph"
        :interactive="interactive"
        :editable="false"
        :disabled="disabled"
        :frame="frame || (data.model.type === 'TRIANGLE' ? undefined : '0 0 600 360')"
        :description="scene.description"
    >
        <g
            class="model-primitives"
            fill="none"
            stroke="black"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
        >
            <component v-for="(item, i) in scene.items" :key="i" :is="item.tag" v-bind="item.attrs">
                {{ item.text }}
            </component>
        </g>
    </GeometryCanvas>
</template>
<style scoped>
    .model-primitives text {
        font:
            16px Arial,
            sans-serif;
        fill: black;
        stroke: white;
        stroke-width: 4px;
        paint-order: stroke;
        text-anchor: middle;
    }
</style>
