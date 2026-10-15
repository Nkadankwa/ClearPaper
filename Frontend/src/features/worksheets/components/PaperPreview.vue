<script setup>
    import { ref, computed, watch, nextTick, onMounted, onUnmounted } from 'vue';
    import { validatePaper, dimensions, paginate } from '../paper.js';
    import PrintedQuestion from './PrintedQuestion.vue';
    import { validate } from '../worksheets.js';
    const props = defineProps({ record: Object, readonly: Boolean });
    const measure = ref(),
        header = ref(),
        pages = ref([[]]),
        oversized = ref([]),
        smallLabels = ref(false),
        measuring = ref(true),
        preview = ref(props.readonly);
    const invalid = computed(() => {
        try {
            validate(props.record.data);
            validatePaper(props.record.data);
            if (!props.record.name.trim()) throw Error('Enter a worksheet name.');
            return '';
        } catch (e) {
            return e.message;
        }
    });
    let sequence = 0,
        style;
    const paper = computed(
        () => props.record.data.paper || { size: 'A4', orientation: 'portrait' },
    );
    const size = computed(() => dimensions(paper.value)),
        variables = computed(() => ({
            '--paper-width': size.value[0] + 'mm',
            '--paper-height': size.value[1] + 'mm',
        }));
    async function layout() {
        const ticket = ++sequence;
        measuring.value = true;
        await nextTick();
        if (!measure.value || ticket !== sequence) return;
        const pxPerMm = measure.value.getBoundingClientRect().width / (size.value[0] - 24),
            capacity =
                (size.value[1] - 24) * pxPerMm -
                header.value.getBoundingClientRect().height -
                8 * pxPerMm;
        const nodes = [...measure.value.querySelectorAll(':scope > .measure-question')],
            result = paginate(
                nodes.map((n) => n.getBoundingClientRect().height),
                capacity,
                props.record.data.blocks.map((b) => b.pageBreakBefore),
            );
        pages.value = result.pages;
        oversized.value = result.oversized;
        smallLabels.value = nodes.some((n) =>
            [...n.querySelectorAll('svg text')].some((t) => {
                const m = t.getScreenCTM();
                return m && (16 * Math.hypot(m.a, m.b) * 72) / 96 < 8;
            }),
        );
        measuring.value = false;
        if (style)
            style.textContent =
                '@page { size: ' + size.value[0] + 'mm ' + size.value[1] + 'mm; margin: 0; }';
    }
    watch(() => [props.record.name, props.record.data], layout, { deep: true, immediate: true });
    onMounted(() => {
        style = document.createElement('style');
        document.head.appendChild(style);
        document.fonts.ready.then(layout);
    });
    onUnmounted(() => {
        sequence++;
        style?.remove();
    });
    async function print() {
        await layout();
        if (invalid.value || oversized.value.length || measuring.value) return;
        window.print();
    }
    defineExpose({ layout });
</script>
<template>
    <section class="paper-panel" :style="variables" aria-label="Paper layout">
        <div class="paper-actions">
            <button @click="preview = !preview" :aria-expanded="preview">
                {{ preview ? 'Hide page preview' : 'Show page preview' }}
            </button>
            <button @click="print" :disabled="measuring || !!oversized.length || !!invalid">
                Print / Save as PDF
            </button>
            <span role="status">
                {{
                    measuring
                        ? 'Measuring pages…'
                        : pages.length + ' ' + (pages.length === 1 ? 'page' : 'pages')
                }}
            </span>
        </div>
        <p v-if="invalid" class="layout-warning" role="alert">Fix before printing: {{ invalid }}</p>
        <p class="print-help">
            Print at 100% scale, with browser headers and footers off. Choose the same paper in the
            printer dialog. Lines and diagrams print in black on white.
        </p>
        <p v-if="oversized.length" class="layout-warning" role="alert">
            Question {{ oversized.map((i) => i + 1).join(', ') }} is too tall for this paper. Reduce
            its figure height or answer lines, shorten or split the wording into another question,
            or choose larger paper. Printing is paused until it fits. The preview shows all content.
        </p>
        <p v-if="smallLabels" class="layout-warning">
            Some diagram labels are below 8 pt at this size. Increase figure height or simplify the
            figure before photocopying.
        </p>
        <div
            class="paper-measure"
            :style="{ width: size[0] - 24 + 'mm' }"
            ref="measure"
            aria-hidden="true"
            inert
        >
            <header ref="header" class="paper-heading">
                <h2>{{ record.name }}</h2>
                <p>Name: _________________________ &nbsp; Date: ______________</p>
            </header>
            <div v-for="(b, i) in record.data.blocks" :key="b.id" class="measure-question">
                <PrintedQuestion :block="b" :number="i + 1" />
            </div>
        </div>
        <div v-if="preview" class="paper-scroll">
            <div
                v-for="(page, p) in pages"
                :key="p"
                class="paper-page"
                :class="{ 'has-overflow': page.some((i) => oversized.includes(i)) }"
            >
                <header class="paper-heading">
                    <h2>{{ record.name }}</h2>
                    <p>Name: _________________________ &nbsp; Date: ______________</p>
                </header>
                <PrintedQuestion
                    v-for="i in page"
                    :key="record.data.blocks[i].id"
                    :block="record.data.blocks[i]"
                    :number="i + 1"
                />
                <footer>{{ p + 1 }} / {{ pages.length }}</footer>
            </div>
        </div>
        <Teleport to="body">
            <div class="clearpaper-print" :style="variables">
                <div v-for="(page, p) in pages" :key="p" class="paper-page">
                    <header class="paper-heading">
                        <h2>{{ record.name }}</h2>
                        <p>Name: _________________________ &nbsp; Date: ______________</p>
                    </header>
                    <PrintedQuestion
                        v-for="i in page"
                        :key="record.data.blocks[i].id"
                        :block="record.data.blocks[i]"
                        :number="i + 1"
                    />
                    <footer>{{ p + 1 }} / {{ pages.length }}</footer>
                </div>
            </div>
        </Teleport>
    </section>
</template>
<style>
    .clearpaper-print {
        display: none;
    }
    .paper-panel .paper-actions {
        display: flex;
        gap: 10px;
        align-items: center;
        flex-wrap: wrap;
    }
    .paper-panel {
        margin: 24px 0;
        padding: 20px 0;
        border-top: 1px solid #b6c4b8;
        border-bottom: 1px solid #b6c4b8;
    }
    .print-help {
        font-size: 13px;
        color: #46594e;
        max-width: 70ch;
    }
    .layout-warning {
        background: #fff2d6;
        color: #604000;
        padding: 12px;
        font-size: 14px;
    }
    .paper-scroll {
        max-width: 100%;
        overflow-x: auto;
        background: #dfe5dc;
        padding: 16px;
        margin-top: 18px;
    }
    .paper-page {
        position: relative;
        width: var(--paper-width);
        min-height: var(--paper-height);
        padding: 12mm;
        margin: 0 auto 18px;
        background: white;
        color: black;
        box-sizing: border-box;
        box-shadow: 0 3px 12px #203b3222;
    }
    .paper-heading {
        display: block !important;
        margin: 0 0 6mm;
    }
    .paper-heading h2 {
        font:
            bold 17pt/1.25 Arial,
            sans-serif !important;
        margin: 0 0 4mm !important;
        overflow-wrap: anywhere;
    }
    .paper-heading p {
        font:
            10pt/1.4 Arial,
            sans-serif;
        margin: 0;
        white-space: normal;
    }
    .paper-page footer {
        position: absolute;
        bottom: 5mm;
        left: 12mm;
        right: 12mm;
        text-align: center;
        font:
            9pt Arial,
            sans-serif;
    }
    .paper-page.has-overflow {
        padding-bottom: 20mm;
    }
    .paper-measure {
        position: fixed;
        left: -20000px;
        top: 0;
        pointer-events: none;
        visibility: hidden;
        font:
            11pt Arial,
            sans-serif;
    }
    .paper-measure .printed-question {
        width: 100%;
    }
    @media print {
        html:has(body > .clearpaper-print),
        body:has(> .clearpaper-print) {
            margin: 0 !important;
            padding: 0 !important;
            background: white !important;
        }
        body:has(> .clearpaper-print) > * {
            display: none !important;
        }
        body > .clearpaper-print {
            display: block !important;
        }
        .clearpaper-print .paper-page {
            width: var(--paper-width);
            min-height: var(--paper-height);
            margin: 0;
            box-shadow: none;
            border: 0;
            break-after: page;
            overflow: visible;
        }
        .clearpaper-print .paper-page:last-child {
            break-after: auto;
        }
        .clearpaper-print .printed-question {
            break-inside: avoid;
        }
        .clearpaper-print [data-editor] {
            display: none !important;
        }
        .clearpaper-print * {
            color: black !important;
            filter: none !important;
            text-shadow: none !important;
            box-shadow: none !important;
        }
    }
</style>
