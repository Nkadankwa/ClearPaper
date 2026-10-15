import test from 'node:test';
import assert from 'node:assert/strict';
import { readRoute, teacherRoutes, studentRoutes } from '../../src/app/routes.js';

test('default route is the teacher library', () => {
    assert.deepEqual(readRoute(''), {
        path: '/teacher/library',
        query: '',
        student: false,
        known: true,
    });
});
test('all registered routes are unique and correctly separated', () => {
    const routes = [...teacherRoutes, ...studentRoutes];
    assert.equal(new Set(routes.map((r) => r.path)).size, routes.length);
    for (const route of teacherRoutes) {
        assert.equal(readRoute('#' + route.path).student, false);
        assert.equal(readRoute('#' + route.path).known, true);
    }
    for (const route of studentRoutes) {
        assert.equal(readRoute('#' + route.path).student, true);
        assert.equal(readRoute('#' + route.path).known, true);
    }
});
test('student invitation query does not alter route selection', () => {
    assert.deepEqual(readRoute('#/student/join?code=invitation-123'), {
        path: '/student/join',
        query: 'code=invitation-123',
        student: true,
        known: true,
    });
});
test('unknown routes preserve the audience for recovery screens', () => {
    assert.equal(readRoute('#/teacher/missing').known, false);
    assert.equal(readRoute('#/student/missing').student, true);
    assert.equal(readRoute('#/student/missing').known, false);
});
