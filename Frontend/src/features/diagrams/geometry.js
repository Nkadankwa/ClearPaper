export const id = () => crypto.randomUUID();
export const emptyGraph = () => ({ schemaVersion: 1, points: [], segments: [], angles: [] });
export const point = (g, key) => g.points.find((p) => p.id === key);
export const pointName = (n) => (n < 26 ? String.fromCharCode(65 + n) : `P${n + 1}`);
export function angleValue(g, a) {
    const v = point(g, a.v),
        p = point(g, a.a),
        q = point(g, a.b);
    let delta = Math.atan2(q.y - v.y, q.x - v.x) - Math.atan2(p.y - v.y, p.x - v.x);
    delta = (Math.abs(Math.atan2(Math.sin(delta), Math.cos(delta))) * 180) / Math.PI;
    return a.reflex ? 360 - delta : delta;
}
export function autoAngles(g) {
    const old = g.angles.filter((a) => validAngle(g, a)),
        result = [];
    for (const v of g.points) {
        const rays = neighbours(g, v.id).sort(
            (a, b) =>
                Math.atan2(point(g, a).y - v.y, point(g, a).x - v.x) -
                Math.atan2(point(g, b).y - v.y, point(g, b).x - v.x),
        );
        const pairs =
            rays.length === 2
                ? [[...rays]]
                : rays.length > 2
                  ? rays.map((a, i) => [a, rays[(i + 1) % rays.length]])
                  : [];
        for (const [a, b] of pairs) {
            const previous = old.find((angle) => sameRays(angle, v.id, a, b));
            const item = previous || {
                id: id(),
                v: v.id,
                a,
                b,
                label: '',
                reflex: false,
                hidden: false,
            };
            if (!item.manual && rays.length > 2) {
                const p = point(g, a),
                    q = point(g, b);
                const gap =
                    (Math.atan2(q.y - v.y, q.x - v.x) -
                        Math.atan2(p.y - v.y, p.x - v.x) +
                        2 * Math.PI) %
                    (2 * Math.PI);
                item.a = a;
                item.b = b;
                item.sweep = true;
                item.reflex = gap > Math.PI;
            }
            result.push(item);
        }
    }
    for (const a of old)
        if ((a.manual || a.hidden || a.label) && !result.some((t) => t.id === a.id)) {
            // Keep annotated ray pairs when a new ray subdivides their original sector.
            a.manual = true;
            a.sweep = false;
            result.push(a);
        }
    if (result.length > 500) throw Error('Drawing limit exceeded. Simplify this drawing.');
    g.angles = result;
}
export function projection(p, a, b) {
    const dx = b.x - a.x,
        dy = b.y - a.y,
        l = dx * dx + dy * dy,
        t = l ? Math.max(0, Math.min(1, ((p.x - a.x) * dx + (p.y - a.y) * dy) / l)) : 0;
    return {
        t,
        x: a.x + t * dx,
        y: a.y + t * dy,
        distance: Math.hypot(p.x - a.x - t * dx, p.y - a.y - t * dy),
    };
}
export function simplify(points, tolerance) {
    if (points.length <= 2) return points;
    let max = 0,
        index = 0;
    for (let i = 1; i < points.length - 1; i++) {
        const d = projection(points[i], points[0], points.at(-1)).distance;
        if (d > max) {
            max = d;
            index = i;
        }
    }
    return max > tolerance
        ? [
              ...simplify(points.slice(0, index + 1), tolerance).slice(0, -1),
              ...simplify(points.slice(index), tolerance),
          ]
        : [points[0], points.at(-1)];
}
export function finishDrawing(original, strokes, tolerance = 12, closed = false) {
    const g = JSON.parse(JSON.stringify(original)),
        loops = [],
        snap = 12;
    function addPoint(p) {
        let n = g.points.find((v) => Math.hypot(v.x - p.x, v.y - p.y) < snap);
        if (!n) {
            n = { id: id(), name: pointName(g.points.length), x: p.x, y: p.y };
            g.points.push(n);
        }
        return n.id;
    }
    function edge(a, b, source) {
        if (
            a !== b &&
            !g.segments.some((e) => (e.a === a && e.b === b) || (e.a === b && e.b === a))
        )
            g.segments.push({
                id: source?.id || id(),
                a,
                b,
                label: source?.label || '',
                style: source?.style || 'solid',
            });
    }
    for (const stroke of strokes) {
        const keys = simplify(stroke, tolerance).map(addPoint);
        for (let i = 1; i < keys.length; i++) edge(keys[i - 1], keys[i]);
        if (closed && keys.length > 2) {
            edge(keys.at(-1), keys[0]);
            loops.push(keys.at(-1) === keys[0] ? keys.slice(0, -1) : keys);
        }
    }
    if (g.points.length > 300 || g.segments.length > 400)
        throw Error('Too many corners. Use fewer strokes or increase straightening tolerance.');
    for (const p of g.points) {
        let best;
        for (const e of g.segments) {
            if (e.a === p.id || e.b === p.id) continue;
            const q = projection(p, point(g, e.a), point(g, e.b));
            if (
                q.t > 0.02 &&
                q.t < 0.98 &&
                q.distance < snap &&
                (!best || q.distance < best.distance)
            )
                best = q;
        }
        if (best) {
            p.x = best.x;
            p.y = best.y;
        }
    }
    const edges = [...g.segments];
    for (let i = 0; i < edges.length; i++)
        for (let j = i + 1; j < edges.length; j++) {
            const a = point(g, edges[i].a),
                b = point(g, edges[i].b),
                c = point(g, edges[j].a),
                d = point(g, edges[j].b),
                dx = b.x - a.x,
                dy = b.y - a.y,
                ex = d.x - c.x,
                ey = d.y - c.y,
                den = dx * ey - dy * ex;
            if (Math.abs(den) < 0.0001) continue;
            const t = ((c.x - a.x) * ey - (c.y - a.y) * ex) / den,
                u = ((c.x - a.x) * dy - (c.y - a.y) * dx) / den;
            if (t > 0.001 && t < 0.999 && u >= 0 && u <= 1) {
                addPoint({ x: a.x + t * dx, y: a.y + t * dy });
                if (g.points.length > 300)
                    throw Error('Too many intersections. Simplify this drawing.');
            }
        }
    g.segments = [];
    for (const e of edges) {
        const a = point(g, e.a),
            b = point(g, e.b),
            parts = g.points
                .map((p) => ({ p, ...projection(p, a, b) }))
                .filter((q) => q.distance < 1)
                .sort((a, b) => a.t - b.t);
        for (let i = 1; i < parts.length; i++)
            edge(parts[i - 1].p.id, parts[i].p.id, {
                ...e,
                id: i === 1 ? e.id : undefined,
                label: i === 1 ? e.label : '',
            });
    }
    autoAngles(g);
    for (const loop of loops) {
        const area = loop.reduce((sum, key, i) => {
            const a = point(g, key),
                b = point(g, loop[(i + 1) % loop.length]);
            return sum + a.x * b.y - b.x * a.y;
        }, 0);
        for (let i = 0; i < loop.length; i++) {
            const p = point(g, loop[(i + loop.length - 1) % loop.length]),
                v = point(g, loop[i]),
                q = point(g, loop[(i + 1) % loop.length]),
                cross = (v.x - p.x) * (q.y - v.y) - (v.y - p.y) * (q.x - v.x),
                angle = g.angles.find(
                    (a) => a.v === v.id && [a.a, a.b].includes(p.id) && [a.a, a.b].includes(q.id),
                );
            if (angle) angle.reflex = cross * area < 0;
        }
    }
    if (g.segments.length > 400 || g.angles.length > 500)
        throw Error('Drawing limit exceeded. Simplify this drawing.');
    return g;
}
export function bounds(g) {
    if (!g.points.length) return '0 0 600 420';
    const xs = g.points.map((p) => p.x),
        ys = g.points.map((p) => p.y),
        w = Math.max(600, Math.max(...xs) - Math.min(...xs) + 120),
        h = Math.max(420, Math.max(...ys) - Math.min(...ys) + 120);
    return `${(Math.min(...xs) + Math.max(...xs) - w) / 2} ${(Math.min(...ys) + Math.max(...ys) - h) / 2} ${w} ${h}`;
}
export function arc(g, a) {
    const v = point(g, a.v),
        p = point(g, a.a),
        q = point(g, a.b),
        r = Math.max(
            10,
            Math.min(
                30,
                Math.hypot(p.x - v.x, p.y - v.y) * 0.25,
                Math.hypot(q.x - v.x, q.y - v.y) * 0.25,
            ),
        ),
        t = Math.atan2(p.y - v.y, p.x - v.x);
    let delta = Math.atan2(q.y - v.y, q.x - v.x) - t;
    delta = Math.atan2(Math.sin(delta), Math.cos(delta));
    if (a.sweep) {
        delta = (delta + 2 * Math.PI) % (2 * Math.PI);
        if (!a.reflex && delta > Math.PI + 0.00001) delta -= 2 * Math.PI;
        else if (a.reflex && delta < Math.PI - 0.00001) delta -= 2 * Math.PI;
    } else if (a.reflex) delta += delta > 0 ? -2 * Math.PI : 2 * Math.PI;
    const middle = t + delta / 2;
    return {
        path: `M${v.x + r * Math.cos(t)} ${v.y + r * Math.sin(t)} A${r} ${r} 0 ${Math.abs(delta) > Math.PI ? 1 : 0} ${delta > 0 ? 1 : 0} ${v.x + r * Math.cos(t + delta)} ${v.y + r * Math.sin(t + delta)}`,
        x: v.x + (r + 23) * Math.cos(middle),
        y: v.y + (r + 23) * Math.sin(middle) + 5,
    };
}

export const cloneGraph = (g) => JSON.parse(JSON.stringify(g));
export const neighbours = (g, v) => [
    ...new Set(
        g.segments.filter((e) => e.a === v || e.b === v).map((e) => (e.a === v ? e.b : e.a)),
    ),
];
const connected = (g, a, b) =>
    g.segments.some((e) => (e.a === a && e.b === b) || (e.a === b && e.b === a));
const validAngle = (g, a) => connected(g, a.v, a.a) && connected(g, a.v, a.b);
const sameRays = (angle, v, a, b) =>
    angle.v === v && ((angle.a === a && angle.b === b) || (angle.a === b && angle.b === a));

export function movePoint(original, key, x, y, name) {
    if (!Number.isFinite(x) || !Number.isFinite(y) || Math.abs(x) > 10000 || Math.abs(y) > 10000)
        throw Error('Use coordinates between −10,000 and 10,000.');
    const g = cloneGraph(original),
        p = point(g, key);
    if (!p) throw Error('Select an existing point.');
    for (const other of neighbours(g, key)) {
        const q = point(g, other);
        if (Math.hypot(q.x - x, q.y - y) <= 0.01)
            throw Error(
                'A connected side must have length. Move this point away from its neighbour.',
            );
    }
    p.x = x;
    p.y = y;
    if (name !== undefined) p.name = name.slice(0, 20);
    autoAngles(g);
    return g;
}

export function deletePoint(original, key) {
    if (!point(original, key)) throw Error('Select an existing point.');
    const g = cloneGraph(original);
    g.points = g.points.filter((p) => p.id !== key);
    g.segments = g.segments.filter((e) => e.a !== key && e.b !== key);
    g.angles = g.angles.filter((a) => ![a.v, a.a, a.b].includes(key));
    autoAngles(g);
    return g;
}

export function connectPoints(original, a, b) {
    const p = point(original, a),
        q = point(original, b);
    if (!p || !q) throw Error('Choose two existing points.');
    if (a === b || Math.hypot(p.x - q.x, p.y - q.y) <= 0.01)
        throw Error('A connection needs two points at different positions.');
    if (connected(original, a, b)) throw Error('These points are already connected.');
    if (original.segments.length >= 400) throw Error('This drawing already has 400 sides.');
    const g = cloneGraph(original);
    g.segments.push({ id: id(), a, b, label: '', style: 'solid' });
    autoAngles(g);
    return g;
}

export function addManualAngle(original, v, a, b, reflex = false) {
    if (new Set([v, a, b]).size !== 3 || !connected(original, v, a) || !connected(original, v, b))
        throw Error('Choose two different connected rays at the vertex.');
    if (original.angles.some((angle) => sameRays(angle, v, a, b)))
        throw Error('This ray pair already has an angle. Select it in the part list to edit it.');
    if (original.angles.length >= 500) throw Error('This drawing already has 500 angles.');
    const g = cloneGraph(original);
    g.angles.push({
        id: id(),
        v,
        a,
        b,
        reflex,
        manual: true,
        label: '',
        hidden: false,
        sweep: false,
    });
    return g;
}

export function extendSide(original, segmentId, endpointId, length, style = 'dashed') {
    const e = original.segments.find((s) => s.id === segmentId);
    if (!e || ![e.a, e.b].includes(endpointId))
        throw Error('Choose a side and one of its endpoints.');
    if (
        !Number.isFinite(length) ||
        length < 1 ||
        length > 1000 ||
        !['solid', 'dashed'].includes(style)
    )
        throw Error('Use a length from 1 to 1,000 and a solid or dashed style.');
    const g = cloneGraph(original),
        p = point(g, endpointId),
        q = point(g, e.a === endpointId ? e.b : e.a),
        distance = Math.hypot(p.x - q.x, p.y - q.y);
    if (distance <= 0.01) throw Error('The source side must have length.');
    const x = p.x + ((p.x - q.x) / distance) * length,
        y = p.y + ((p.y - q.y) / distance) * length;
    if (Math.abs(x) > 10000 || Math.abs(y) > 10000)
        throw Error('The extension goes beyond the canvas coordinate limit.');
    let end = g.points.find((p) => Math.hypot(p.x - x, p.y - y) < 1e-8);
    if (!end) {
        if (g.points.length >= 300) throw Error('This drawing already has 300 points.');
        let index = 0;
        while (g.points.some((p) => p.name === pointName(index))) index++;
        end = { id: id(), name: pointName(index), x, y };
        g.points.push(end);
    }
    const next = connectPoints(g, endpointId, end.id);
    next.segments.at(-1).style = style;
    return next;
}
export function starterShape(type, sides = 5) {
    let strokes,
        closed = true;
    if (type === 'RECTANGLE')
        strokes = [
            [
                [100, 100],
                [380, 100],
                [380, 280],
                [100, 280],
            ],
        ];
    else if (type === 'TRAPEZIUM')
        strokes = [
            [
                [170, 100],
                [310, 100],
                [400, 290],
                [80, 290],
            ],
        ];
    else if (type === 'POLYGON') {
        if (!Number.isInteger(sides) || sides < 3 || sides > 12)
            throw Error('Choose 3–12 polygon sides.');
        strokes = [
            Array.from({ length: sides }, (_, i) => [
                280 + 145 * Math.cos((i * 2 * Math.PI) / sides - Math.PI / 2),
                220 + 145 * Math.sin((i * 2 * Math.PI) / sides - Math.PI / 2),
            ]),
        ];
    } else if (type === 'CONCAVE_L')
        strokes = [
            [
                [100, 100],
                [200, 100],
                [200, 200],
                [300, 200],
                [300, 300],
                [100, 300],
            ],
        ];
    else if (type === 'OPEN_L') {
        strokes = [
            [
                [120, 100],
                [120, 290],
                [320, 290],
            ],
        ];
        closed = false;
    } else if (type === 'K') {
        strokes = [
            [
                [130, 100],
                [130, 310],
            ],
            [
                [300, 100],
                [130, 205],
                [300, 310],
            ],
        ];
        closed = false;
    } else throw Error('Choose a supported starting shape.');
    return finishDrawing(
        emptyGraph(),
        strokes.map((s) => s.map(([x, y]) => ({ x, y }))),
        6,
        closed,
    );
}
export function addStarter(original, type, sides) {
    const added = starterShape(type, sides),
        g = cloneGraph(original),
        offset = g.points.length
            ? Math.max(...g.points.map((p) => p.x)) +
              100 -
              Math.min(...added.points.map((p) => p.x))
            : 0;
    if (
        g.points.length + added.points.length > 300 ||
        g.segments.length + added.segments.length > 400 ||
        g.angles.length + added.angles.length > 500
    )
        throw Error('Drawing limit exceeded.');
    const usedNames = new Set(g.points.map((p) => p.name));
    for (const p of added.points) {
        p.x += offset;
        if (p.x > 10000) throw Error('This shape would exceed the canvas coordinate limit.');
        let index = 0;
        while (usedNames.has(pointName(index))) index++;
        p.name = pointName(index);
        usedNames.add(p.name);
    }
    g.points.push(...added.points);
    g.segments.push(...added.segments);
    g.angles.push(...added.angles);
    return g;
}
