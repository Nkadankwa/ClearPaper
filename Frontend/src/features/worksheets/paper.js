import { arc, angleValue } from '../diagrams/geometry.js';
export const sizes = {
    A4: [210, 297],
    A5: [148, 210],
    A3: [297, 420],
    Letter: [215.9, 279.4],
    Legal: [215.9, 355.6],
};
export function dimensions(paper = { size: 'A4', orientation: 'portrait' }) {
    if (!sizes[paper.size] || !['portrait', 'landscape'].includes(paper.orientation))
        throw Error('Choose a supported paper size and orientation.');
    const [w, h] = sizes[paper.size];
    return paper.orientation === 'landscape' ? [h, w] : [w, h];
}
export function paginate(heights, capacity, breaks = []) {
    const pages = [],
        oversized = [];
    let page = [],
        used = 0;
    heights.forEach((height, i) => {
        if (height > capacity + 0.5) oversized.push(i);
        if (page.length && (breaks[i] || used + height > capacity)) {
            pages.push(page);
            page = [];
            used = 0;
        }
        page.push(i);
        used += height;
    });
    if (page.length || !pages.length) pages.push(page);
    return { pages, oversized };
}
export function validatePaper(data) {
    dimensions(data.paper);
    for (const b of data.blocks) {
        if (
            b.figureHeight !== undefined &&
            (!Number.isFinite(b.figureHeight) || b.figureHeight < 30 || b.figureHeight > 150)
        )
            throw Error('Figure height must be between 30 and 150 mm.');
        if (b.pageBreakBefore !== undefined && typeof b.pageBreakBefore !== 'boolean')
            throw Error('Invalid page break.');
    }
    return data;
}

// Fit printed figures to their content rather than the editor's minimum canvas size.
export function printFrame(g) {
    if (!g.points.length) return undefined;
    const xs = [],
        ys = [],
        add = (x, y, rx = 3, ry = 3) => {
            xs.push(x - rx, x + rx);
            ys.push(y - ry, y + ry);
        },
        label = (x, y, text) => {
            if (text) add(x, y - 6, text.length * 8.5, 12);
        };
    for (const p of g.points) {
        add(p.x, p.y);
        label(p.x - 14, p.y - 13, p.name);
    }
    for (const e of g.segments) {
        const a = g.points.find((p) => p.id === e.a),
            b = g.points.find((p) => p.id === e.b),
            len = Math.hypot(b.x - a.x, b.y - a.y) || 1;
        label(
            (a.x + b.x) / 2 + ((b.y - a.y) / len) * 17,
            (a.y + b.y) / 2 - ((b.x - a.x) / len) * 17,
            e.label,
        );
    }
    for (const a of g.angles.filter((a) => !a.hidden)) {
        const v = g.points.find((p) => p.id === a.v),
            pos = arc(g, a);
        add(v.x, v.y, 40, 40);
        label(pos.x, pos.y, a.label || Math.round(angleValue(g, a)) + '°');
    }
    const x = Math.min(...xs) - 16,
        y = Math.min(...ys) - 16;
    return [x, y, Math.max(...xs) - x + 16, Math.max(...ys) - y + 16].join(' ');
}
