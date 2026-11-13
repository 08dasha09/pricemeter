// Графік цін товару: лінія на кожен магазин, дані — /api/products/{id}/history; колір — фірмовий колір магазину.
(function () {
    'use strict';

    const canvas = document.getElementById('product-chart');
    if (!canvas || typeof Chart === 'undefined') {
        return;
    }
    const productId = canvas.dataset.productId;
    const emptyNote = document.getElementById('chart-empty');
    const unconfirmedNote = document.getElementById('chart-unconfirmed');
    const buttons = document.querySelectorAll('#periods button');
    const priceFormat = new Intl.NumberFormat('uk-UA', {maximumFractionDigits: 0});
    let chart = null;

    // кольори беремо з CSS-змінних, щоб графік відповідав світлій / темній темі
    const css = getComputedStyle(document.documentElement);
    const color = name => css.getPropertyValue(name).trim();
    const theme = {
        text: color('--text'),
        muted: color('--muted'),
        border: color('--border'),
        surface: color('--surface'),
        font: color('--font')
    };
    Chart.defaults.font.family = theme.font;
    Chart.defaults.color = theme.muted;

    function shopColor(shopCode) {
        return color('--shop-' + shopCode.toLowerCase()) || color('--shop-other');
    }

    function formatDate(iso) {
        const [, month, day] = iso.split('-');
        return `${day}.${month}`;
    }

    function render(history) {
        const hasData = history.series.some(s => s.prices.some(p => p !== null));
        emptyNote.hidden = hasData;
        canvas.hidden = !hasData;
        if (chart) {
            chart.destroy();
            chart = null;
        }
        if (!hasData) {
            return;
        }
        const dense = history.dates.length > 31;
        // confirmed[i]: false — магазин того дня не обходили (остання відома ціна), null — даних пропозиції немає
        unconfirmedNote.hidden = !history.series.some(s => s.confirmed.some(c => c === false));
        const unconfirmedDash = s => ctx => s.confirmed[ctx.p1DataIndex] === false ? [2, 4] : undefined;
        const seenShops = new Set();
        const datasets = history.series.map(s => {
            const shopColorValue = shopColor(s.shopCode);
            // друга пропозиція того самого магазину — пунктиром
            const repeated = seenShops.has(s.shopCode);
            seenShops.add(s.shopCode);
            return {
                label: s.shopName,
                data: s.prices,
                borderColor: shopColorValue,
                backgroundColor: shopColorValue,
                borderDash: repeated ? [6, 5] : [],
                stepped: true,
                spanGaps: false,
                borderWidth: 2.5,
                pointRadius: dense ? 0 : 3,
                pointHoverRadius: 6,
                pointBackgroundColor: theme.surface,
                pointBorderColor: shopColorValue,
                pointBorderWidth: 2,
                fill: false,
                segment: {borderDash: unconfirmedDash(s)}
            };
        });
        chart = new Chart(canvas, {
            type: 'line',
            data: {labels: history.dates.map(formatDate), datasets},
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
                    legend: {
                        position: 'bottom',
                        labels: {usePointStyle: true, pointStyle: 'circle', boxWidth: 8, boxHeight: 8, padding: 16}
                    },
                    tooltip: {
                        backgroundColor: theme.text,
                        titleColor: theme.surface,
                        bodyColor: theme.surface,
                        padding: 12,
                        cornerRadius: 10,
                        boxPadding: 4,
                        usePointStyle: true,
                        itemSort: (a, b) => (a.parsed.y ?? Infinity) - (b.parsed.y ?? Infinity),
                        callbacks: {
                            title: items => history.dates[items[0].dataIndex].split('-').reverse().join('.'),
                            label: ctx => {
                                const confirmed = history.series[ctx.datasetIndex].confirmed[ctx.dataIndex];
                                if (ctx.parsed.y === null) {
                                    return ` ${ctx.dataset.label}: ` + (confirmed === null ? 'немає даних' : 'немає в наявності');
                                }
                                return ` ${ctx.dataset.label}: ${priceFormat.format(ctx.parsed.y)} ₴`
                                    + (confirmed === false ? ' (не перевірено)' : '');
                            }
                        }
                    }
                }
            }
        });
    }

    function load(period) {
        buttons.forEach(b => b.classList.toggle('active', b.dataset.period === period));
        fetch(`/api/products/${productId}/history?period=${encodeURIComponent(period)}`)
            .then(response => {
                if (!response.ok) {
                    throw new Error(`HTTP ${response.status}`);
                }
                return response.json();
            })
            .then(render)
            .catch(error => {
                console.error('Failed to load product price history', error);
                render({dates: [], series: []});
            });
    }

    buttons.forEach(b => b.addEventListener('click', () => load(b.dataset.period)));
    load(canvas.dataset.period);
})();
