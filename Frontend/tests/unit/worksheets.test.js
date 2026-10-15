import test from 'node:test';
import assert from 'node:assert/strict';
import {
    question,
    normalize,
    validate,
    applyFigure,
} from '../../src/features/worksheets/worksheets.js';
test('placed figure is copied on insertion and apply preserves question identity and choices', () => {
    const figure = { points: [{ id: 'a', x: 1 }] };
    const b = question(figure);
    b.type = 'MC';
    b.text = 'Find x';
    b.choices = ['One', 'Two'];
    const data = { schemaVersion: 1, blocks: [b, question()] };
    figure.points[0].x = 99;
    assert.equal(b.diagram.points[0].x, 1);
    const updated = applyFigure(data, b.id, { points: [] });
    assert.deepEqual(updated.blocks[0].choices, ['One', 'Two']);
    assert.equal(updated.blocks[0].text, 'Find x');
    assert.equal(updated.blocks[0].id, b.id);
    assert.equal(data.blocks[0].diagram.points.length, 1);
    assert.deepEqual(updated.blocks[1], data.blocks[1]);
    assert.throws(() => applyFigure(data, 'missing', {}));
});
test('multiple choice rejects invalid option counts, blank options and fractional answer lines', () => {
    const b = question();
    b.type = 'MC';
    const data = { blocks: [b] };
    assert.throws(() => validate(data));
    for (const count of [1, 7]) {
        b.choices = Array(count).fill('Choice');
        assert.throws(() => validate(data));
    }
    for (const count of [2, 6]) {
        b.choices = Array(count).fill('Choice');
        assert.equal(validate(data), data);
    }
    b.answerLines = 1.5;
    assert.throws(() => validate(data));
});
test('legacy worksheets retain figures and gain independent stable question identities', () => {
    const old = {
        schemaVersion: 1,
        blocks: [{ text: 'First', diagram: { points: [] } }, { text: 'Second' }],
    };
    const data = normalize(old);
    assert.notEqual(data.blocks[0].id, data.blocks[1].id);
    assert.deepEqual(normalize(data), data);
    data.blocks[0].diagram.points.push({});
    assert.equal(old.blocks[0].diagram.points.length, 0);
    validate(data);
});
