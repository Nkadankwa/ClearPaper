import test from 'node:test';
import assert from 'node:assert/strict';
import {
    emptyGraph,
    finishDrawing,
    movePoint,
    deletePoint,
    connectPoints,
    addManualAngle,
    angleValue,
    autoAngles,
    cloneGraph,
    point,
} from '../../src/features/diagrams/geometry.js';
const pts = (values) => values.map(([x, y]) => ({ x, y }));
const openL = () =>
    finishDrawing(emptyGraph(), [
        pts([
            [100, 100],
            [100, 250],
            [250, 250],
        ]),
    ]);
test('coordinate/name changes recalculate exact angles; collapsed edges reject without mutation', () => {
    const g = openL(),
        before = cloneGraph(g),
        key = g.points[2].id;
    const moved = movePoint(g, key, 250, 100, 'C2');
    assert.equal(point(moved, key).name, 'C2');
    assert.equal(Math.round(angleValue(moved, moved.angles[0])), 45);
    assert.equal(moved.angles[0].id, g.angles[0].id);
    assert.deepEqual(g, before);
    assert.throws(() => movePoint(g, key, 100, 250), /length/);
    assert.throws(() => movePoint(g, key, Infinity, 0), /coordinates/);
});
test('deleting junction removes every reference, unrelated geometry stays and snapshot restores all labels/flags', () => {
    const g = finishDrawing(openL(), [
        pts([
            [500, 100],
            [500, 250],
            [650, 250],
        ]),
    ]);
    g.segments[0].label = '2x + 5';
    g.angles[0].hidden = true;
    const undo = cloneGraph(g),
        key = g.points[1].id;
    const result = deletePoint(g, key);
    assert.equal(result.points.length, g.points.length - 1);
    assert(!result.segments.some((e) => e.a === key || e.b === key));
    assert(!result.angles.some((a) => [a.v, a.a, a.b].includes(key)));
    assert.deepEqual(result.segments, g.segments.slice(2));
    assert.deepEqual(result.angles, g.angles.slice(1));
    assert.deepEqual(g, undo);
    assert.equal(undo.segments[0].label, '2x + 5');
    assert(undo.angles[0].hidden);
    // A removed ray endpoint must also remove a manual marker at another vertex.
    const cross = finishDrawing(emptyGraph(), [
        pts([
            [100, 100],
            [300, 300],
        ]),
        pts([
            [100, 300],
            [300, 100],
        ]),
    ]);
    const v = cross.points.find((p) => p.x === 200 && p.y === 200),
        p = cross.points[0],
        q = cross.points[1];
    const manual = addManualAngle(cross, v.id, p.id, q.id),
        removed = deletePoint(manual, p.id);
    assert(!removed.angles.some((a) => [a.v, a.a, a.b].includes(p.id)));
});
test('new connections are independent, reject duplicates in either direction and reject zero length', () => {
    const g = openL(),
        a = g.points[0].id,
        b = g.points[2].id,
        added = connectPoints(g, a, b);
    assert.equal(added.segments.length, 3);
    assert.equal(g.segments.length, 2);
    assert.equal(added.angles.length, 3);
    assert.throws(() => connectPoints(added, b, a), /already connected/);
    assert.throws(() => connectPoints(g, a, a), /different positions/);
    const coincident = cloneGraph(g);
    coincident.points[2].x = 100;
    coincident.points[2].y = 100;
    assert.throws(() => connectPoints(coincident, a, b), /different positions/);
});
test('manual opposite ray angle, expression and visibility survive regeneration and JSON round trip', () => {
    const g = finishDrawing(emptyGraph(), [
        pts([
            [100, 100],
            [300, 300],
        ]),
        pts([
            [100, 300],
            [300, 100],
        ]),
    ]);
    const v = g.points.find((p) => p.x === 200 && p.y === 200),
        a = g.points[0].id,
        b = g.points[1].id;
    const added = addManualAngle(g, v.id, a, b),
        angle = added.angles.at(-1);
    angle.label = '2x + 5';
    angle.hidden = true;
    assert.equal(Math.round(angleValue(added, angle)), 180);
    const reopened = cloneGraph(added);
    autoAngles(reopened);
    assert.deepEqual(
        reopened.angles.find((t) => t.id === angle.id),
        angle,
    );
    assert.throws(() => addManualAngle(added, v.id, b, a), /already has/);
    assert.throws(() => addManualAngle(g, v.id, a, a), /different connected rays/);
});
test('adjacent junction sectors total 360 and hidden sector IDs/flags survive moving and refreshing', () => {
    const g = finishDrawing(emptyGraph(), [
        pts([
            [100, 100],
            [300, 300],
        ]),
        pts([
            [100, 300],
            [300, 100],
        ]),
    ]);
    const hidden = g.angles[0];
    hidden.hidden = true;
    hidden.label = 'y';
    const moved = movePoint(g, g.points[0].id, 90, 70);
    assert(Math.abs(moved.angles.reduce((sum, a) => sum + angleValue(moved, a), 0) - 360) < 1e-8);
    autoAngles(moved);
    assert.equal(moved.angles.find((a) => a.id === hidden.id).hidden, true);
    assert.equal(moved.angles.find((a) => a.id === hidden.id).label, 'y');
});

test('subdividing a hidden labelled sector preserves the recoverable ray-pair marker', () => {
    const g = openL();
    g.angles[0].hidden = true;
    g.angles[0].label = 'x';
    const saved = cloneGraph(g.angles[0]);
    g.points.push({ id: 'new-ray', name: 'D', x: 200, y: 150 });
    const connected = connectPoints(g, g.points[1].id, 'new-ray');
    const marker = connected.angles.find((a) => a.id === saved.id);
    assert(marker);
    assert(marker.hidden);
    assert.equal(marker.label, 'x');
    autoAngles(connected);
    assert(connected.angles.some((a) => a.id === saved.id && a.hidden));
});

test('subdividing a junction sector keeps the old hidden ray pair even when no longer adjacent', () => {
    const g = finishDrawing(emptyGraph(), [
        pts([
            [100, 100],
            [300, 300],
        ]),
        pts([
            [100, 300],
            [300, 100],
        ]),
    ]);
    const v = g.points.find((p) => p.x === 200 && p.y === 200),
        hidden = g.angles[0];
    hidden.hidden = true;
    hidden.label = 'x';
    g.points.push({ id: 'north', name: 'N', x: 200, y: 100 });
    const next = connectPoints(g, v.id, 'north'),
        retained = next.angles.find((a) => a.id === hidden.id);
    assert(retained);
    assert(retained.hidden);
    assert.equal(retained.label, 'x');
    assert(retained.manual);
    autoAngles(next);
    assert(next.angles.some((a) => a.id === hidden.id && a.hidden));
});
