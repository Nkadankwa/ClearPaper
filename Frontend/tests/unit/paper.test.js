import test from 'node:test';
import assert from 'node:assert/strict';
import {
    sizes,
    dimensions,
    paginate,
    validatePaper,
    printFrame,
} from '../../src/features/worksheets/paper.js';
test('all paper formats swap exact millimetres for landscape', () => {
    for (const [size, [w, h]] of Object.entries(sizes)) {
        assert.deepEqual(dimensions({ size, orientation: 'portrait' }), [w, h]);
        assert.deepEqual(dimensions({ size, orientation: 'landscape' }), [h, w]);
    }
    assert.throws(() => dimensions({ size: 'A0', orientation: 'portrait' }));
    assert.throws(() => dimensions({ size: 'A4', orientation: 'sideways' }));
});
test('pagination keeps question identities, honours forced breaks and flags oversized content without dropping it', () => {
    assert.deepEqual(paginate([40, 50, 30], 100).pages, [[0, 1], [2]]);
    assert.deepEqual(paginate([20, 20, 20], 100, [false, true, false]).pages, [[0], [1, 2]]);
    const result = paginate([150, 30], 100);
    assert.deepEqual(result, { pages: [[0], [1]], oversized: [0] });
    assert.deepEqual(paginate([], 100).pages, [[]]);
    assert.deepEqual(paginate([100], 100).oversized, []);
});
test('figure dimensions and page-break settings reject malformed values', () => {
    for (const figureHeight of [0, 151, NaN, '60'])
        assert.throws(() => validatePaper({ blocks: [{ figureHeight }] }));
    assert.throws(() => validatePaper({ blocks: [{ pageBreakBefore: 1 }] }));
    validatePaper({ blocks: [{ figureHeight: 30, pageBreakBefore: true }, { figureHeight: 150 }] });
});

test('print frame fits the drawing without the editor minimum; hidden labels do not enlarge it', () => {
    const g = {
        points: [
            { id: 'a', name: 'A', x: 100, y: 100 },
            { id: 'b', name: 'B', x: 100, y: 250 },
            { id: 'c', name: 'C', x: 330, y: 250 },
        ],
        segments: [{ id: 'ab', a: 'a', b: 'b', label: 'x' }],
        angles: [{ id: 'abc', v: 'b', a: 'a', b: 'c', label: 'HIDDEN'.repeat(6), hidden: true }],
    };
    const first = printFrame(g).split(' ').map(Number);
    assert.ok(first[2] < 600);
    assert.ok(first[3] < 420);
    g.angles[0].label = 'different hidden label';
    assert.deepEqual(printFrame(g).split(' ').map(Number), first);
    g.angles[0].hidden = false;
    g.angles[0].reflex = false;
    assert.ok(printFrame(g).split(' ').map(Number)[2] > first[2]);
    assert.equal(printFrame({ points: [] }), undefined);
});
