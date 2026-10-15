import test from 'node:test';
import assert from 'node:assert/strict';
import {
    emptyGraph,
    cloneGraph,
    starterShape,
    addStarter,
    extendSide,
    movePoint,
    angleValue,
} from '../../src/features/diagrams/geometry.js';
import {
    defaultModel,
    buildModel,
    modelScene,
    triangleToGeometry,
    validateModel,
} from '../../src/features/diagrams/models.js';
test('starting shapes have expected topology and preserve existing geometry when added', () => {
    for (const [type, points, edges] of [
        ['RECTANGLE', 4, 4],
        ['TRAPEZIUM', 4, 4],
        ['POLYGON', 7, 7],
        ['OPEN_L', 3, 2],
        ['CONCAVE_L', 6, 6],
        ['K', 5, 4],
    ]) {
        const g = starterShape(type, 7);
        assert.equal(g.points.length, points);
        assert.equal(g.segments.length, edges);
    }
    const l = starterShape('CONCAVE_L');
    assert(l.angles.some((a) => Math.round(angleValue(l, a)) === 270));
    const original = starterShape('RECTANGLE'),
        next = addStarter(original, 'TRAPEZIUM');
    assert.equal(next.points.length, 8);
    assert.deepEqual(next.points.slice(0, 4), original.points);
    assert.equal(new Set(next.points.map((p) => p.name)).size, 8);
    assert.throws(() => starterShape('POLYGON', 2), /3–12/);
});
test('extension is collinear at chosen endpoint, has exact length/style and later moves independently', () => {
    const g = starterShape('RECTANGLE'),
        e = g.segments[0],
        endpoint = g.points.find((p) => p.id === e.b),
        other = g.points.find((p) => p.id === e.a);
    const extended = extendSide(g, e.id, e.b, 80, 'dashed'),
        tip = extended.points.at(-1),
        newEdge = extended.segments.at(-1);
    assert.equal(newEdge.style, 'dashed');
    assert(Math.abs(Math.hypot(tip.x - endpoint.x, tip.y - endpoint.y) - 80) < 1e-8);
    assert(
        Math.abs(
            (tip.x - endpoint.x) * (endpoint.y - other.y) -
                (tip.y - endpoint.y) * (endpoint.x - other.x),
        ) < 1e-8,
    );
    assert(
        extended.angles.some(
            (a) => a.v === endpoint.id && Math.round(angleValue(extended, a)) === 180,
        ),
    );
    const moved = movePoint(extended, endpoint.id, endpoint.x + 10, endpoint.y + 15);
    assert.deepEqual(
        moved.points.find((p) => p.id === tip.id),
        tip,
    );
    assert.equal(g.points.length, 4);
    assert.throws(() => extendSide(g, e.id, 'missing', 80), /endpoint/);
    assert.throws(() => extendSide(g, e.id, e.a, 0), /length/);
    const opposite = extendSide(g, e.id, e.a, 25, 'solid');
    assert.equal(opposite.segments.at(-1).style, 'solid');
    assert(opposite.points.at(-1).x < other.x);
});
test('triangle geometry measures its input angles; conversion retains known labels and flags without mutating model', () => {
    const model = defaultModel('TRIANGLE');
    model.parameters = { a: 40, b: 65, base: 12 };
    model.showAnswers = false;
    model.hiddenAngles.a = true;
    const data = buildModel(model),
        before = cloneGraph(data),
        converted = triangleToGeometry(data);
    assert.equal(modelScene(model).results.c, 75);
    assert.equal(
        Math.round(
            angleValue(
                data,
                data.angles.find((a) => a.id === 'a'),
            ),
        ),
        40,
    );
    assert.equal(
        Math.round(
            angleValue(
                data,
                data.angles.find((a) => a.id === 'b'),
            ),
        ),
        65,
    );
    assert.equal(converted.segments[0].label, '12 cm');
    assert.equal(converted.angles.find((a) => a.id === 'b').label, '65°');
    assert.equal(converted.angles.find((a) => a.id === 'c').label, 'x');
    assert(converted.angles[0].hidden);
    assert(!converted.model);
    assert.deepEqual(data, before);
});
test('bearing direction follows clockwise north, inclines recompute forces and scale moves the pointer', () => {
    const bearing = defaultModel('BEARING');
    bearing.parameters.angle = 90;
    const b = modelScene(bearing);
    assert.equal(
        b.items.find((i) => i.tag === 'line' && i.attrs.x1 === 280 && i.attrs.y1 === 195).attrs.x2,
        435,
    );
    const incline = defaultModel('INCLINE');
    incline.parameters = { angle: 30, mass: 2 };
    let result = modelScene(incline).results;
    assert(Math.abs(result.weight - 19.62) < 1e-8);
    assert(Math.abs(result.parallel - 9.81) < 1e-8);
    assert(Math.abs(result.normal - 19.62 * Math.cos(Math.PI / 6)) < 1e-8);
    incline.parameters.mass = 4;
    assert.equal(modelScene(incline).results.weight, 39.24);
    const scale = defaultModel('SCALE');
    const old = modelScene(scale).items.find((i) => i.tag === 'line' && i.attrs.y1 === 122).attrs
        .x1;
    scale.parameters.reading = 75;
    assert(
        modelScene(scale).items.find((i) => i.tag === 'line' && i.attrs.y1 === 122).attrs.x1 > old,
    );
});
test('answer and angle hiding removes displayed answers and descriptions, not model values', () => {
    for (const type of ['TRIANGLE', 'BEARING', 'INCLINE', 'SCALE']) {
        const m = defaultModel(type);
        m.showAnswers = false;
        m.showAngles = false;
        const s = modelScene(m);
        assert(!s.description.includes('Bearing 065°'));
        assert(!s.description.includes('19.62'));
        assert(!s.description.includes('37.5'));
        if (type === 'TRIANGLE') assert(s.graph.angles.every((a) => a.hidden));
        assert.equal(buildModel(m).model.showAnswers, false);
    }
});
test('invalid parameters are rejected and boundary values round trip', () => {
    for (const type of ['TRIANGLE', 'BEARING', 'INCLINE', 'SCALE']) {
        const m = defaultModel(type);
        assert.deepEqual(buildModel(m).model, m);
        const key = Object.keys(m.parameters)[0];
        m.parameters[key] = '';
        assert.throws(() => validateModel(m), /number/);
    }
    const tri = defaultModel('TRIANGLE');
    tri.parameters.a = 120;
    tri.parameters.b = 100;
    assert.throws(() => buildModel(tri), /total/);
    tri.parameters.a = 50.5;
    assert.throws(() => buildModel(tri), /whole number/);
    const bearing = defaultModel('BEARING');
    bearing.parameters.angle = 359;
    assert.doesNotThrow(() => buildModel(bearing));
    bearing.parameters.angle = 360;
    assert.throws(() => buildModel(bearing), /359/);
    const scale = defaultModel('SCALE');
    scale.parameters.reading = 101;
    assert.throws(() => buildModel(scale), /within/);
    scale.parameters.reading = 50;
    scale.parameters.max = scale.parameters.min;
    assert.throws(() => buildModel(scale), /Maximum/);
});

test('an off-line nearby point does not bend a newly created collinear extension', () => {
    const g = starterShape('RECTANGLE'),
        e = g.segments[0],
        p = g.points.find((p) => p.id === e.b);
    g.points.push({ id: 'nearby', name: 'Near', x: p.x + 80, y: p.y + 0.005 });
    const extended = extendSide(g, e.id, e.b, 80);
    assert.notEqual(extended.segments.at(-1).b, 'nearby');
    assert.equal(extended.points.at(-1).y, p.y);
});
