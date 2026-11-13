// Графік історії ціни пропозиції: денний ряд з /api/offers/{id}/history, пунктиром — заявлена стара ціна.
(function () {
    'use strict';

    const canvas = document.getElementById('price-chart');
    if (!canvas || typeof Chart === 'undefined') {
        return;
    }
    const offerId = canvas.dataset.offerId;
    const emptyNote = document.getElementById('chart-empty');
    const oldPriceLegend = document.getElementById('legend-old-price');
    const unconfirmedLegend = document.getElementById('legend-unconfirmed');
    const buttons = document.querySelectorAll('#periods button');
    const priceFormat = new Intl.NumberFormat('uk-UA', {maximumFractionDigits: 0});
    let chart = null;

    // кольори беремо з CSS-змінних, щоб графік відповідав світлій / темній темі
    const css = getComputedStyle(document.documentElement);
    const color = name => css.getPropertyValue(name).trim();
    const theme = {
        accent: color('--accent'),
        bad: color('--bad'),
        text: color('--text'),
        muted: color('--muted'),
        border: color('--border'),
        surface: color('--surface'),
        font: color('--font')
    };
    Chart.defaults.font.family = theme.font;
    Chart.defaults.color = theme.muted;

    function formatDate(iso) {
        const [year, month, day] = iso.split('-');
        return `${day}.${month}`;
    }

    function fillGradient(context) {
        const {ctx, chartArea} = context.chart;
        if (!chartArea) {
            return 'transparent';
        }
        const gradient = ctx.createLinearGradient(0, chartArea.top, 0, chartArea.bottom);
        gradient.addColorStop(0, theme.accent + '33');
        gradient.addColorStop(1, theme.accent + '00');
        return gradient;
    }

    function render(points) {
        const hasData = points.some(p => p.price !== null);
        emptyNote.hidden = hasData;
        canvas.hidden = !hasData;
        if (chart) {
            chart.destroy();
            chart = null;
        }
        if (!hasData) {
            return;
        }
        const hasOldPrice = points.some(p => p.oldPrice !== null);
        oldPriceLegend.hidden = !hasOldPrice;
        // день не підтверджено обходом (ціна потім змінилась) — відрізок до нього пунктиром із крапок
        const unconfirmed = i => points[i].confirmed === false;
        unconfirmedLegend.hidden = !points.some((p, i) => unconfirmed(i));
        const dense = points.length > 31;
        const datasets = [{
            label: 'Ціна',
            data: points.map(p => p.price),
            borderColor: theme.accent,
            backgroundColor: fillGradient,
            fill: 'start',
            stepped: true,
            spanGaps: false,
            borderWidth: 2.5,
            pointRadius: dense ? 0 : 3.5,
            pointHoverRadius: 6,
            pointBackgroundColor: theme.surface,
            pointBorderColor: theme.accent,
            pointBorderWidth: 2,
            segment: {borderDash: ctx => unconfirmed(ctx.p1DataIndex) ? [2, 4] : undefined}
        }];
        if (hasOldPrice) {
            datasets.push({
                label: 'Стара ціна',
                data: points.map(p => p.oldPrice),
                borderColor: theme.bad,
                borderDash: [6, 5],
                borderWidth: 1.75,
                stepped: true,
                spanGaps: false,
                pointRadius: 0,
                pointHoverRadius: 4,
                fill: false
            });
        }
        chart = new Chart(canvas, {
            type: 'line',
            data: {labels: points.map(p => formatDate(p.date)), datasets},
            options: {
                maintainAspectRatio: false,
                interaction: {mode: 'index', intersect: false},
                layout: {padding: {top: 8}},
                scales: {
                    x: {
                        grid: {display: false},
                        border: {color: theme.border},
                        ticks: {maxRotation: 0, autoSkipPadding: 16}
                    },
                    y: {
                        grace: '8%',
                        grid: {color: theme.border},
                        border: {display: false},
                        ticks: {padding: 8, callback: value => priceFormat.format(value) + ' ₴'}
                    }
                },
                plugins: {
                    legend: {display: false},
                    tooltip: {
                        backgroundColor: theme.text,
                        titleColor: theme.surface,
                        bodyColor: theme.surface,
                        padding: 12,
                        cornerRadius: 10,
                        boxPadding: 4,
                        usePointStyle: true,
                        callbacks: {
                            title: items => points[items[0].dataIndex].date.split('-').reverse().join('.'),
                            label: ctx => ctx.parsed.y === null
                                ? ` ${ctx.dataset.label}: немає в наявності`
                                : ` ${ctx.dataset.label}: ${priceFormat.format(ctx.parsed.y)} ₴`
                                    + (unconfirmed(ctx.dataIndex) ? ' (не перевірено — магазин не обходили)' : '')
                        }
                    }
                }
            }
        });
    }

    function load(period) {
        buttons.forEach(b => b.classList.toggle('active', b.dataset.period === period));
        fetch(`/api/offers/${offerId}/history?period=${encodeURIComponent(period)}`)
            .then(response => {
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}`);
                }
                return response.json();
            })
            .then(history => render(history.points))
            .catch(error => {
                console.error('Failed to load price history', error);
                render([]);
            });
    }

    buttons.forEach(b => b.addEventListener('click', () => load(b.dataset.period)));
    load(canvas.dataset.period);
})();
