export const teacherRoutes = [
    {
        path: '/teacher/library',
        title: 'My library',
        description: 'Keep your diagrams and worksheets ready for the next lesson.',
        heading: 'Your lesson resources',
        empty: 'Your library is ready to take shape.',
        detail: 'Your diagrams and worksheets will appear here when resource saving is available.',
        action: 'Visit diagram studio',
        target: '/teacher/studio',
    },
    {
        path: '/teacher/studio',
        title: 'Diagram studio',
        description: 'Draw, adjust and label clear figures for your questions.',
        heading: 'A clearer diagram starts here',
        empty: 'The diagram canvas is coming next.',
        detail: 'Freehand drawing, point editing and angle controls are being prepared. Diagram creation is not available on this preview screen yet.',
        action: 'View worksheets',
        target: '/teacher/worksheets',
    },
    {
        path: '/teacher/worksheets',
        title: 'Worksheets',
        description: 'Bring your questions and diagrams together on a print-friendly page.',
        heading: 'Prepare your next worksheet',
        empty: 'Choose the page. Build the questions.',
        detail: 'Worksheet creation, answer choices and printing controls are being prepared.',
        action: 'Visit your library',
        target: '/teacher/library',
    },
    {
        path: '/teacher/reading',
        title: 'Reading fluency',
        description: 'Assign a passage, listen to a reading and give useful feedback.',
        heading: 'Listen to the learning',
        empty: 'Your reading groups will live here.',
        detail: 'Group invitations, assigned passages and reading reviews will appear here when those features are ready.',
        action: 'Preview student screens',
        target: '/student/join',
    },
];
export const studentRoutes = [
    { path: '/student/join', title: 'Join a group' },
    { path: '/student/assignments', title: 'My assignments' },
    { path: '/student/reading', title: 'Read a passage' },
];
export function readRoute(hash = '') {
    const raw = hash.replace(/^#/, '') || '/teacher/library';
    const [path, query = ''] = raw.split('?');
    return {
        path,
        query,
        student: path.startsWith('/student/'),
        known:
            path === '/shared' || [...teacherRoutes, ...studentRoutes].some((r) => r.path === path),
    };
}
