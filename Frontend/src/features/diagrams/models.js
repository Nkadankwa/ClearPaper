import { emptyGraph, cloneGraph } from './geometry.js';

export const modelNames = {
    TRIANGLE: 'Triangle',
    BEARING: 'Bearing',
    INCLINE: 'Incline plane',
    SCALE: 'Measurement scale',
};
export const modelFields = {
    TRIANGLE: [
        ['a', 'Angle A (°)', 5, 170, 1],
        ['b', 'Angle B (°)', 5, 170, 1],
        ['base', 'Base AB (cm)', 1, 100, 'any'],
    ],
    BEARING: [
        ['angle', 'Bearing from north (°)', 0, 359, 1],
        ['distance', 'Distance OP (km)', 1, 100, 'any'],
    ],
    INCLINE: [
        ['angle', 'Incline angle (°)', 5, 75, 1],
        ['mass', 'Mass (kg)', 0.1, 100, 'any'],
    ],
    SCALE: [
        ['min', 'Minimum reading', -10000, 10000, 'any'],
        ['max', 'Maximum reading', -10000, 10000, 'any'],
        ['reading', 'Pointer reading', -10000, 10000, 'any'],
        ['divisions', 'Labelled intervals', 2, 20, 1],
    ],
};
const defaults = {
    TRIANGLE: { a: 50, b: 60, base: 8 },
    BEARING: { angle: 65, distance: 12 },
    INCLINE: { angle: 30, mass: 2 },
    SCALE: { min: 0, max: 100, reading: 37.5, divisions: 10, unit: 'cm' },
};
export function defaultModel(type) {
    if (!defaults[type]) throw Error('Choose a supported model.');
    return {
        type,
        parameters: { ...defaults[type] },
        showAnswers: true,
        showAngles: true,
        hiddenAngles: { a: false, b: false, c: false },
    };
}
export function validateModel(model) {
    if (!model || !modelFields[model.type] || !model.parameters)
        throw Error('Choose a supported model and enter its values.');
    for (const [key, label, min, max, step] of modelFields[model.type]) {
        const value = model.parameters[key];
        if (
            typeof value !== 'number' ||
            !Number.isFinite(value) ||
            value < min ||
            value > max ||
            (step === 1 && !Number.isInteger(value))
        )
            throw Error(
                `${label}: use ${step === 1 ? 'a whole number' : 'a number'} from ${min} to ${max}.`,
            );
    }
    if (typeof model.showAnswers !== 'boolean' || typeof model.showAngles !== 'boolean')
        throw Error('Choose valid answer and angle visibility settings.');
    if (
        model.hiddenAngles &&
        (typeof model.hiddenAngles !== 'object' ||
            Array.isArray(model.hiddenAngles) ||
            Object.values(model.hiddenAngles).some((v) => typeof v !== 'boolean'))
    )
        throw Error('Choose valid individual angle visibility settings.');
    const p = model.parameters;
    if (model.type === 'TRIANGLE' && p.a + p.b > 175)
        throw Error(
            'Angles A and B must total no more than 175° so the third angle is at least 5°.',
        );
    if (model.type === 'SCALE') {
        if (p.max <= p.min || p.reading < p.min || p.reading > p.max)
            throw Error('Maximum must exceed minimum and the pointer must stay within the range.');
        if (typeof p.unit !== 'string' || !p.unit.trim() || p.unit.length > 12)
            throw Error('Enter a unit of up to 12 characters.');
    }
}
const rad = (n) => (n * Math.PI) / 180;
export const formatNumber = (n) => Number(n.toFixed(2)).toString();
function triangleGraph(model) {
    const p = model.parameters,
        c = 180 - p.a - p.b,
        side = (330 * Math.sin(rad(p.b))) / Math.sin(rad(c)),
        vx = side * Math.cos(rad(p.a)),
        vy = side * Math.sin(rad(p.a)),
        minx = Math.min(0, vx),
        maxx = Math.max(330, vx),
        scale = Math.min(1, 210 / vy, 420 / (maxx - minx)),
        ax = 90 - minx * scale,
        ay = 285;
    return {
        schemaVersion: 1,
        points: [
            { id: 'A', name: 'A', x: ax, y: ay },
            { id: 'B', name: 'B', x: ax + 330 * scale, y: ay },
            { id: 'C', name: 'C', x: ax + vx * scale, y: ay - vy * scale },
        ],
        segments: [
            { id: 'AB', a: 'A', b: 'B', label: formatNumber(p.base) + ' cm', style: 'solid' },
            { id: 'BC', a: 'B', b: 'C', label: '', style: 'solid' },
            { id: 'CA', a: 'C', b: 'A', label: '', style: 'solid' },
        ],
        angles: [
            ['a', 'A', 'B', 'C', p.a],
            ['b', 'B', 'A', 'C', p.b],
            ['c', 'C', 'A', 'B', c],
        ].map(([id, v, a, b, value]) => ({
            id,
            v,
            a,
            b,
            label: id === 'c' && !model.showAnswers ? 'x' : Math.round(value) + '°',
            reflex: false,
            hidden: !model.showAngles || !!model.hiddenAngles?.[id],
        })),
    };
}
export function modelScene(model) {
    validateModel(model);
    const p = model.parameters,
        items = [],
        graph = model.type === 'TRIANGLE' ? triangleGraph(model) : emptyGraph();
    const add = (tag, attrs, text) => items.push({ tag, attrs, text });
    const line = (x1, y1, x2, y2, attrs = {}) => add('line', { x1, y1, x2, y2, ...attrs });
    const text = (x, y, value, attrs = {}) => add('text', { x, y, ...attrs }, value);
    const path = (d, attrs = {}) => add('path', { d, fill: 'none', ...attrs });
    const arrow = (x1, y1, x2, y2) => {
        line(x1, y1, x2, y2);
        const a = Math.atan2(y2 - y1, x2 - x1);
        path(
            `M${x2 - 8 * Math.cos(a - 0.4)} ${y2 - 8 * Math.sin(a - 0.4)} L${x2} ${y2} L${x2 - 8 * Math.cos(a + 0.4)} ${y2 - 8 * Math.sin(a + 0.4)}`,
        );
    };
    let results = {};
    if (model.type === 'TRIANGLE') results = { c: 180 - p.a - p.b };
    if (model.type === 'BEARING') {
        const cx = 280,
            cy = 195,
            a = rad(p.angle),
            x = cx + 155 * Math.sin(a),
            y = cy - 155 * Math.cos(a),
            r = 60;
        line(cx, 320, cx, 35, { 'stroke-dasharray': '6 6' });
        line(125, cy, 455, cy, { 'stroke-dasharray': '6 6' });
        arrow(cx, cy, x, y);
        add('circle', { cx, cy, r: 3, fill: 'black' });
        text(cx, 22, 'N');
        text(474, cy + 6, 'E');
        text(cx, 345, 'S');
        text(105, cy + 6, 'W');
        text(cx - 18, cy + 23, 'O');
        text(x + 18, y + 7, 'P');
        text((cx + x) / 2 + 24, (cy + y) / 2, formatNumber(p.distance) + ' km');
        if (model.showAngles) {
            if (p.angle > 0)
                path(
                    `M${cx} ${cy - r} A${r} ${r} 0 ${p.angle > 180 ? 1 : 0} 1 ${cx + r * Math.sin(a)} ${cy - r * Math.cos(a)}`,
                );
            text(
                450,
                45,
                model.showAnswers
                    ? 'Bearing ' + String(p.angle).padStart(3, '0') + '°'
                    : 'Bearing x',
            );
        }
        results = { bearing: p.angle };
    }
    if (model.type === 'INCLINE') {
        const theta = rad(p.angle),
            run = 300 * Math.cos(theta),
            rise = 300 * Math.sin(theta),
            ox = 115,
            oy = 290,
            mx = ox + run * 0.6,
            my = oy - rise * 0.6,
            W = p.mass * 9.81,
            N = W * Math.cos(theta),
            parallel = W * Math.sin(theta),
            maxlen = 35 + (95 * p.mass) / (p.mass + 3),
            normalLen = maxlen * Math.cos(theta),
            parallelLen = maxlen * Math.sin(theta),
            fx = mx - 20 * Math.sin(theta),
            fy = my - 20 * Math.cos(theta);
        path(`M${ox} ${oy} H${ox + run} V${oy - rise} Z`);
        add('rect', {
            x: -28,
            y: -38,
            width: 56,
            height: 36,
            fill: 'white',
            transform: `translate(${mx} ${my}) rotate(${-p.angle})`,
        });
        text(mx, my - 48, formatNumber(p.mass) + ' kg');
        arrow(fx, fy, fx, fy + maxlen);
        arrow(fx, fy, fx - normalLen * Math.sin(theta), fy - normalLen * Math.cos(theta));
        arrow(fx, fy, fx - parallelLen * Math.cos(theta), fy + parallelLen * Math.sin(theta));
        if (model.showAngles) {
            path(
                `M${ox + 38} ${oy} A38 38 0 0 0 ${ox + 38 * Math.cos(theta)} ${oy - 38 * Math.sin(theta)}`,
            );
            text(ox + 58, oy - 10, p.angle + '°');
        }
        text(mx + 55, my + 75, model.showAnswers ? 'W = ' + formatNumber(W) + ' N' : 'W = x');
        text(
            mx - 80,
            my - normalLen - 14,
            model.showAnswers ? 'N = ' + formatNumber(N) + ' N' : 'N = x',
        );
        text(
            405,
            50,
            model.showAnswers ? 'Down slope: ' + formatNumber(parallel) + ' N' : 'Down slope: x',
        );
        results = { weight: W, normal: N, parallel };
    }
    if (model.type === 'SCALE') {
        const left = 75,
            width = 450,
            step = (p.max - p.min) / p.divisions,
            values = Array.from({ length: p.divisions + 1 }, (_, i) =>
                formatNumber(p.min + i * step),
            ),
            size = Math.min(
                14,
                Math.max(
                    6,
                    (width / p.divisions - 3) / (Math.max(...values.map((v) => v.length)) * 0.6),
                ),
            );
        line(left, 205, left + width, 205);
        for (let i = 0; i <= p.divisions; i++) {
            const x = left + (i * width) / p.divisions;
            line(x, 190, x, 220);
            text(x, 248, values[i], {
                style: {
                    fontSize: size < 10 ? '12px' : size + 'px',
                    textAnchor: size < 10 ? 'start' : 'middle',
                },
                ...(size < 10 ? { transform: `rotate(90 ${x} 248)` } : {}),
            });
            if (i < p.divisions)
                line(x + width / p.divisions / 2, 198, x + width / p.divisions / 2, 214);
        }
        const x = left + ((p.reading - p.min) / (p.max - p.min)) * width;
        arrow(x, 122, x, 183);
        text(x, 103, model.showAnswers ? formatNumber(p.reading) + ' ' + p.unit : 'x ' + p.unit);
        text(
            300,
            size < 10 ? 340 : 293,
            'Each labelled interval = ' + formatNumber(step) + ' ' + p.unit,
            { style: { fontSize: '15px' } },
        );
        results = { reading: p.reading, interval: step };
    }
    const visible =
        model.type === 'TRIANGLE'
            ? [
                  ...graph.segments.map((e) => e.label),
                  ...graph.angles.filter((a) => !a.hidden).map((a) => a.v + ': ' + a.label),
              ]
            : items.filter((i) => i.tag === 'text').map((i) => i.text);
    return {
        graph,
        items,
        results,
        description: modelNames[model.type] + '. ' + visible.filter(Boolean).join('. ') + '.',
    };
}
export function buildModel(model) {
    const scene = modelScene(model);
    return { ...scene.graph, model: cloneGraph(model) };
}
export function triangleToGeometry(data) {
    if (data.model?.type !== 'TRIANGLE')
        throw Error('Only triangles can be converted to editable geometry.');
    return cloneGraph(modelScene(data.model).graph);
}
