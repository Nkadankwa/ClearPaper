export const clone = (value) => JSON.parse(JSON.stringify(value));
export const drafts = new Map();
export let currentWorksheet = '';
export function setCurrent(key) {
    currentWorksheet = key;
}
export function clearDrafts() {
    drafts.clear();
    currentWorksheet = '';
}
export function question(diagram) {
    return {
        id: crypto.randomUUID(),
        text: '',
        type: 'WRITTEN',
        choices: ['', ''],
        answerLines: 3,
        ...(diagram ? { diagram: clone(diagram) } : {}),
    };
}
export function normalize(data) {
    return {
        paper: { size: 'A4', orientation: 'portrait' },
        ...clone(data),
        blocks: data.blocks.map((b) => ({ ...question(), ...clone(b) })),
    };
}
export function validate(data) {
    if (data.blocks.length > 30) throw Error('A worksheet can contain at most 30 questions.');
    const ids = new Set();
    for (const b of data.blocks) {
        if (!b.id || ids.has(b.id)) throw Error('Question identities must be unique.');
        ids.add(b.id);
        if (typeof b.text !== 'string' || b.text.length > 1500)
            throw Error('Question wording must be at most 1,500 characters.');
        if (!['WRITTEN', 'MC'].includes(b.type)) throw Error('Choose a valid question type.');
        if (
            b.type === 'MC' &&
            (!Array.isArray(b.choices) ||
                b.choices.length < 2 ||
                b.choices.length > 6 ||
                b.choices.some((s) => typeof s !== 'string' || !s.trim() || s.length > 300))
        )
            throw Error('Multiple choice needs 2–6 non-empty options, each up to 300 characters.');
        if (!Number.isInteger(b.answerLines) || b.answerLines < 0 || b.answerLines > 10)
            throw Error('Answer lines must be a whole number from 0 to 10.');
    }
    return data;
}
export function applyFigure(data, key, figure) {
    const next = clone(data),
        b = next.blocks.find((b) => b.id === key);
    if (!b) throw Error('This question no longer exists.');
    b.diagram = clone(figure);
    return next;
}
