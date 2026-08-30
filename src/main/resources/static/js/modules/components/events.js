// ─── Corporate Events Warning ────────────────────────────────────────────────
// Depends on: modules/state.js

function updateEventsWarning(data) {
    const warning = document.getElementById('eventsWarning');
    const none = document.getElementById('eventsNone');
    const list = document.getElementById('eventsList');
    if (!warning || !none || !list) return;
    if (!data || data.status !== 'success' || !data.data || !data.data.length) {
        warning.classList.add('hidden');
        none.classList.remove('hidden');
        return;
    }
    const events = data.data;
    const now = new Date();
    const fiveDays = new Date(now.getTime() + 5 * 24 * 60 * 60 * 1000);
    const upcoming = events.filter(e => {
        const d = new Date(e.eventDate + 'T00:00:00');
        return d >= now && d <= fiveDays;
    });
    if (!upcoming.length) {
        warning.classList.add('hidden');
        none.classList.remove('hidden');
        return;
    }
    warning.classList.remove('hidden');
    none.classList.add('hidden');
    list.textContent = upcoming.map(e => (e.purpose || '') + ' on ' + (e.eventDate || '')).join(' · ');
}
