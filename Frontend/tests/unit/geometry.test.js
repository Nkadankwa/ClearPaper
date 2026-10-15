import test from 'node:test';
import assert from 'node:assert/strict';
import {
    emptyGraph,
    finishDrawing,
    angleValue,
    simplify,
} from '../../src/features/diagrams/geometry.js';
const pts = (values) => values.map(([x, y]) => ({ x, y }));
test('open L waits for confirmation and yields one right angle', () => {
    const original = emptyGraph(),
        strokes = [
            pts([
                [100, 100],
                [100, 250],
                [250, 250],
            ]),
        ];
    assert.equal(original.points.length, 0);
    const g = finishDrawing(original, strokes);
    assert.equal(original.points.length, 0);
    assert.equal(g.points.length, 3);
    assert.equal(g.segments.length, 2);
    assert.equal(Math.round(angleValue(g, g.angles[0])), 90);
});
test('multi-stroke K connects near junctions and splits its spine', () => {
    const g = finishDrawing(emptyGraph(), [
        pts([
            [100, 100],
            [100, 300],
        ]),
        pts([
            [240, 100],
            [103, 200],
            [240, 300],
        ]),
    ]);
    assert.equal(g.points.length, 5);
    assert.equal(g.segments.length, 4);
    assert.equal(g.angles.length, 4);
    const junction = g.points.find((p) => p.y === 200);
    assert.equal(g.segments.filter((e) => e.a === junction.id || e.b === junction.id).length, 4);
});
test('closed concave L includes its reflex internal angle', () => {
    const g = finishDrawing(
        emptyGraph(),
        [
            pts([
                [100, 100],
                [200, 100],
                [200, 200],
                [300, 200],
                [300, 300],
                [100, 300],
            ]),
        ],
        12,
        true,
    );
    assert.equal(g.segments.length, 6);
    assert.equal(g.angles.length, 6);
    assert.deepEqual(
        g.angles.map((a) => Math.round(angleValue(g, a))).sort((a, b) => a - b),
        [90, 90, 90, 90, 90, 270],
    );
});
test('crossings become stable selectable junctions; labels survive', () => {
    const first = finishDrawing(emptyGraph(), [
        pts([
            [100, 100],
            [300, 300],
        ]),
    ]);
    first.segments[0].label = 'x';
    const g = finishDrawing(first, [
        pts([
            [100, 300],
            [300, 100],
        ]),
    ]);
    assert.equal(g.points.length, 5);
    assert.equal(g.segments.length, 4);
    assert.equal(g.points[0].id, first.points[0].id);
    assert.equal(g.segments.filter((e) => e.label === 'x').length, 1);
});
test('tolerance controls straightening', () => {
    const p = pts([
        [0, 0],
        [50, 9],
        [100, 0],
    ]);
    assert.equal(simplify(p, 6).length, 3);
    assert.equal(simplify(p, 12).length, 2);
});
