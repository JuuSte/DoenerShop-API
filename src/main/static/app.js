'use strict';
// Döner-Shop Web-Frontend – spricht direkt mit der REST-API unter /api

// ---------- Hilfsfunktionen ----------
const $ = (sel, el = document) => el.querySelector(sel);
const $$ = (sel, el = document) => [...el.querySelectorAll(sel)];
const esc = v => String(v ?? '').replace(/[&<>"']/g, c =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const eur = n => new Intl.NumberFormat('de-AT', { style: 'currency', currency: 'EUR' }).format(Number(n) || 0);
const num = n => new Intl.NumberFormat('de-AT', { maximumFractionDigits: 2 }).format(Number(n) || 0);
const fmtDate = s => s ? new Date(s).toLocaleString('de-AT', { dateStyle: 'short', timeStyle: 'short' }) : '–';
const sum = (arr, fn) => arr.reduce((a, x) => a + (Number(fn(x)) || 0), 0);
const today = () => new Date().toLocaleDateString('sv-SE'); // yyyy-mm-dd in lokaler Zeit
const customerName = c => c ? `${c.firstName ?? ''} ${c.lastName ?? ''}`.trim() || `Kunde #${c.id}` : '–';

async function api(path, { method = 'GET', body } = {}) {
  const res = await fetch('/api' + path, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : {},
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  if (res.status === 204) return null;
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { data = text; }
  if (!res.ok) {
    const msg = (data && typeof data === 'object' && (data.message || data.error)) || `Fehler ${res.status}`;
    throw new Error(msg);
  }
  return data;
}

function toast(msg, type = 'ok') {
  const el = document.createElement('div');
  el.className = `toast ${type}`;
  el.textContent = msg;
  $('#toasts').append(el);
  setTimeout(() => el.remove(), type === 'err' || type === 'warn' ? 6000 : 3000);
}

// Führt eine Aktion aus, zeigt Fehler als Toast und lädt danach die Ansicht neu
async function act(fn, okMsg) {
  try {
    await fn();
    if (okMsg) toast(okMsg);
    await router();
  } catch (e) {
    toast(e.message, 'err');
  }
}

const field = (label, name, value = '', type = 'text', extra = '') =>
  `<label class="field"><span>${label}</span><input name="${name}" type="${type}" value="${esc(value)}" ${extra}></label>`;
const selectField = (label, name, options, value, extra = '') =>
  `<label class="field"><span>${label}</span><select name="${name}" ${extra}>${options.map(o =>
    `<option value="${esc(o.value)}" ${String(o.value) === String(value) ? 'selected' : ''}>${esc(o.label)}</option>`).join('')}</select></label>`;
const checkField = (label, name, checked) =>
  `<label class="check"><input type="checkbox" name="${name}" ${checked ? 'checked' : ''}> ${label}</label>`;

// Modal mit Formular. onSubmit(daten, form) – gibt es false zurück, bleibt das Modal offen.
function modal({ title, body, submitLabel = 'Speichern', onSubmit, wide = false, onOpen }) {
  const bg = document.createElement('div');
  bg.className = 'modal-bg';
  bg.innerHTML = `
    <form class="modal ${wide ? 'wide' : ''}" novalidate>
      <header><h2>${esc(title)}</h2><button type="button" class="icon-btn" data-close aria-label="Schließen">✕</button></header>
      <div class="modal-body">${body}</div>
      <footer>
        <button type="button" class="btn ghost" data-close>${onSubmit ? 'Abbrechen' : 'Schließen'}</button>
        ${onSubmit ? `<button type="submit" class="btn primary">${esc(submitLabel)}</button>` : ''}
      </footer>
    </form>`;
  document.body.append(bg);
  const form = $('form', bg);
  const close = () => { bg.remove(); document.removeEventListener('keydown', onKey); };
  const onKey = e => { if (e.key === 'Escape') close(); };
  document.addEventListener('keydown', onKey);
  bg.addEventListener('mousedown', e => { if (e.target === bg) close(); });
  $$('[data-close]', bg).forEach(b => b.addEventListener('click', close));
  form.addEventListener('submit', async e => {
    e.preventDefault();
    if (!onSubmit) return close();
    const data = Object.fromEntries(new FormData(form));
    const btn = $('button[type=submit]', form);
    btn.disabled = true;
    try {
      const keep = await onSubmit(data, form);
      if (keep !== false) close();
    } catch (err) {
      toast(err.message, 'err');
    } finally {
      btn.disabled = false;
    }
  });
  onOpen?.(form, close);
  $('input:not([type=checkbox]), select, textarea', form)?.focus();
  return { form, close };
}

function confirmDelete(text, fn, okMsg) {
  modal({
    title: 'Wirklich löschen?',
    body: `<p>${esc(text)}</p>`,
    submitLabel: 'Löschen',
    onSubmit: async () => { await fn(); toast(okMsg); await router(); },
  });
}

const statusBadge = s => {
  const st = String(s || '').toUpperCase();
  const map = { OPEN: ['b-open', 'Offen'], CONFIRMED: ['b-confirmed', 'Bestätigt'], COMPLETED: ['b-completed', 'Abgeschlossen'] };
  const [cls, label] = map[st] || ['b-gray', s || '–'];
  return `<span class="badge ${cls}">${esc(label)}</span>`;
};

// ---------- Router ----------
const routes = {
  kasse: viewPos,
  bestellungen: viewOrders,
  speisekarte: viewMenu,
  lager: viewStock,
  kunden: viewCustomers,
  statistik: viewStats,
};

async function router() {
  const [name, param] = location.hash.replace(/^#\/?/, '').split('/');
  const key = routes[name] ? name : 'kasse';
  $$('.nav a').forEach(a => a.classList.toggle('active', a.dataset.view === key));
  const main = $('#main');
  if (!main.dataset.view || main.dataset.view !== key) main.innerHTML = '<div class="loading">Lädt …</div>';
  main.dataset.view = key;
  try {
    await routes[key](main, param);
  } catch (e) {
    main.innerHTML = `<div class="error-box"><h2>Daten konnten nicht geladen werden</h2>
      <p>${esc(e.message)}</p><p class="muted">Läuft die Spring-Boot-Anwendung auf Port 8080?</p></div>`;
  }
}
window.addEventListener('hashchange', router);
window.addEventListener('DOMContentLoaded', router);

// ---------- Kasse ----------
const cart = { lines: [], customerId: '' };
let posCategory = 'all';

async function viewPos(main) {
  const [products, categories, customers] = await Promise.all([api('/products'), api('/categories'), api('/kunden')]);
  const active = products.filter(p => p.active);
  const avail = {};
  // Warenkorb mit aktuellen Produktdaten abgleichen
  cart.lines = cart.lines.filter(l => active.some(p => p.id === l.product.id))
    .map(l => ({ ...l, product: active.find(p => p.id === l.product.id) }));
  if (cart.customerId && !customers.some(c => String(c.id) === String(cart.customerId))) cart.customerId = '';

  main.innerHTML = `
    <div class="page-head"><div><h1>Kasse</h1><p class="sub">Produkte antippen, Kunde wählen, Bestellung aufgeben.</p></div></div>
    <div class="pos">
      <section><div class="chips" id="chips"></div><div class="product-grid" id="grid"></div></section>
      <aside class="cart card" id="cart"></aside>
    </div>`;

  const usedCats = categories.filter(c => active.some(p => p.category?.id === c.id));
  if (posCategory !== 'all' && !usedCats.some(c => c.id === posCategory)) posCategory = 'all';

  const drawChips = () => {
    $('#chips').innerHTML = [{ id: 'all', name: 'Alle' }, ...usedCats].map(c =>
      `<button class="chip ${posCategory === c.id ? 'active' : ''}" data-cat="${c.id}">${esc(c.name)}</button>`).join('');
    $$('#chips .chip').forEach(b => b.onclick = () => {
      posCategory = b.dataset.cat === 'all' ? 'all' : Number(b.dataset.cat);
      drawChips(); drawGrid();
    });
  };

  const availBadge = p => {
    const a = avail[p.id];
    if (!a) return '';
    if (a.available) return '<span class="badge b-ok">verfügbar</span>';
    if (!a.missingIngredients?.length) return '<span class="badge b-gray" title="Für dieses Produkt ist kein Rezept hinterlegt">kein Rezept</span>';
    const tip = a.missingIngredients.map(m => `${m.ingredient}: ${num(m.available)} von ${num(m.required)} ${m.unit ?? ''}`).join('\n');
    return `<span class="badge b-bad" title="${esc(tip)}">zu wenig Lager</span>`;
  };

  const drawGrid = () => {
    const list = active.filter(p => posCategory === 'all' || p.category?.id === posCategory);
    $('#grid').innerHTML = list.length ? list.map(p => `
      <button class="product" data-id="${p.id}">
        <span class="name">${esc(p.name)}</span>
        <span class="desc">${esc(p.description || p.category?.name || '')}</span>
        <span class="foot"><span class="price">${eur(p.price)}</span>${availBadge(p)}</span>
      </button>`).join('')
      : `<div class="card empty">Noch keine aktiven Produkte. Lege sie unter <a href="#/speisekarte">Speisekarte</a> an.</div>`;
    $$('#grid .product').forEach(b => b.onclick = () => {
      const p = active.find(x => x.id === Number(b.dataset.id));
      const line = cart.lines.find(l => l.product.id === p.id && !l.note);
      if (line) line.qty++; else cart.lines.push({ key: crypto.randomUUID?.() ?? String(Math.random()), product: p, qty: 1, note: '' });
      drawCart();
    });
  };

  const drawCart = () => {
    const total = sum(cart.lines, l => l.qty * l.product.price);
    $('#cart').innerHTML = `
      <h2>Warenkorb</h2>
      <div class="row">
        ${selectField('Kunde', 'customer', [{ value: '', label: '– Kunde wählen –' },
          ...customers.map(c => ({ value: c.id, label: customerName(c) }))], cart.customerId)}
        <button class="btn ghost" id="newCust" title="Neuen Kunden anlegen">+ Neu</button>
      </div>
      <div class="cart-lines">
        ${cart.lines.length ? cart.lines.map(l => `
          <div class="cart-line" data-key="${l.key}">
            <div class="top">
              <span class="name">${esc(l.product.name)}</span>
              <span class="qty"><button data-d="-1">−</button><span>${l.qty}</span><button data-d="1">+</button></span>
            </div>
            <div class="top small muted"><span>${eur(l.product.price)} × ${l.qty}</span><strong>${eur(l.product.price * l.qty)}</strong></div>
            <input placeholder="Sonderwunsch, z. B. ohne Zwiebel" value="${esc(l.note)}">
          </div>`).join('') : '<p class="empty">Noch nichts im Warenkorb.</p>'}
      </div>
      <div class="total"><span>Summe</span><span>${eur(total)}</span></div>
      <button class="btn primary block" id="orderConfirm" ${cart.lines.length ? '' : 'disabled'}>Bestellen &amp; bestätigen</button>
      <button class="btn ghost block" id="orderOnly" ${cart.lines.length ? '' : 'disabled'}>Nur als offene Bestellung anlegen</button>`;

    $('#cart select').onchange = e => { cart.customerId = e.target.value; };
    $('#newCust').onclick = () => customerModal(null, c => { cart.customerId = String(c.id); customers.push(c); drawCart(); });
    $$('#cart .cart-line').forEach(el => {
      const line = cart.lines.find(l => l.key === el.dataset.key);
      $$('.qty button', el).forEach(b => b.onclick = () => {
        line.qty += Number(b.dataset.d);
        if (line.qty < 1) cart.lines = cart.lines.filter(l => l !== line);
        drawCart();
      });
      $('input', el).oninput = e => { line.note = e.target.value; };
    });
    $('#orderConfirm').onclick = e => placeOrder(true, e.target);
    $('#orderOnly').onclick = e => placeOrder(false, e.target);
  };

  async function placeOrder(confirmNow, btn) {
    if (!cart.customerId) return toast('Bitte zuerst einen Kunden wählen.', 'warn');
    $$('#cart .btn').forEach(b => b.disabled = true);
    let order;
    try {
      order = await api('/orders', { method: 'POST', body: { customerId: Number(cart.customerId) } });
      for (const l of cart.lines) {
        const base = { orderId: order.id, productId: l.product.id, quantity: l.qty, unitPrice: l.product.price };
        if (l.note.trim()) {
          await api('/order-item-customs', { method: 'POST', body: { ...base, specialInstructions: l.note.trim() } });
        } else {
          await api('/order-items', { method: 'POST', body: base });
        }
      }
    } catch (e) {
      toast(e.message, 'err');
      drawCart();
      return;
    }
    cart.lines = [];
    if (confirmNow) {
      try {
        await api(`/orders/${order.id}/confirm`, { method: 'POST' });
        toast(`Bestellung #${order.id} bestätigt – Zutaten wurden vom Lager abgebucht.`);
      } catch (e) {
        toast(`Bestellung #${order.id} angelegt, aber nicht bestätigt: ${e.message}`, 'warn');
      }
    } else {
      toast(`Bestellung #${order.id} angelegt.`);
    }
    location.hash = `#/bestellungen/${order.id}`;
  }

  drawChips(); drawGrid(); drawCart();
  // Verfügbarkeit im Hintergrund nachladen
  Promise.all(active.map(p => api(`/products/${p.id}/availability`).then(a => { avail[p.id] = a; }).catch(() => {})))
    .then(() => { if ($('#grid')) drawGrid(); });
}

function customerModal(customer, onSaved) {
  modal({
    title: customer ? 'Kunde bearbeiten' : 'Neuer Kunde',
    body: field('Vorname', 'firstName', customer?.firstName, 'text', 'required') +
          field('Nachname', 'lastName', customer?.lastName, 'text', 'required'),
    onSubmit: async d => {
      if (!d.firstName.trim() || !d.lastName.trim()) throw new Error('Bitte Vor- und Nachnamen eingeben.');
      const body = { firstName: d.firstName.trim(), lastName: d.lastName.trim() };
      const saved = customer
        ? await api(`/kunden/${customer.id}`, { method: 'PUT', body })
        : await api('/kunden', { method: 'POST', body });
      toast(customer ? 'Kunde gespeichert.' : `${customerName(saved)} angelegt.`);
      onSaved?.(saved);
    },
  });
}

// ---------- Bestellungen ----------
let orderFilter = 'all';

async function viewOrders(main, selected) {
  const [orders, items, customs, payments, products] = await Promise.all([
    api('/orders'), api('/order-items'), api('/order-item-customs'), api('/payments'), api('/products')]);
  const missingOrderId = [...items, ...customs].some(i => i.orderId === undefined);

  const info = orders.map(o => {
    const its = items.filter(i => i.orderId === o.id);
    const cus = customs.filter(i => i.orderId === o.id);
    const pays = payments.filter(p => p.orderId === o.id);
    const lines = [...its.map(i => ({ ...i, type: 'item' })), ...cus.map(i => ({ ...i, type: 'custom' }))];
    return {
      o, lines, pays,
      total: sum(lines, l => l.quantity * l.unitPrice),
      paid: pays.some(p => p.status === 'SUCCESSFUL'),
    };
  }).sort((a, b) => b.o.id - a.o.id);

  const filters = {
    all: ['Alle', () => true],
    OPEN: ['Offen', x => x.o.status === 'OPEN'],
    CONFIRMED: ['Bestätigt', x => x.o.status === 'CONFIRMED'],
    COMPLETED: ['Abgeschlossen', x => x.o.status === 'COMPLETED'],
    unpaid: ['Unbezahlt', x => !x.paid],
  };
  const shown = info.filter(filters[orderFilter][1]);
  const sel = info.find(x => String(x.o.id) === String(selected));

  main.innerHTML = `
    <div class="page-head">
      <div><h1>Bestellungen</h1><p class="sub">${orders.length} Bestellungen · ${info.filter(x => x.o.status === 'OPEN').length} offen</p></div>
      <a class="btn primary" href="#/kasse">+ Neue Bestellung</a>
    </div>
    ${missingOrderId ? '<div class="banner">Positionen können keiner Bestellung zugeordnet werden – das Backend liefert noch kein <code>orderId</code>. Bitte die Änderungen an Order_Item / Order_Item_Custom übernehmen und neu starten.</div>' : ''}
    <div class="chips">${Object.entries(filters).map(([k, [label]]) =>
      `<button class="chip ${orderFilter === k ? 'active' : ''}" data-f="${k}">${label}</button>`).join('')}</div>
    <div class="${sel ? 'split' : ''}">
      <div class="card" style="padding:6px 8px">
        <table>
          <thead><tr><th>#</th><th>Kunde</th><th>Datum</th><th>Status</th><th class="right">Pos.</th><th class="right">Summe</th><th>Zahlung</th></tr></thead>
          <tbody>${shown.length ? shown.map(x => `
            <tr class="clickable ${sel?.o.id === x.o.id ? 'selected' : ''}" data-id="${x.o.id}">
              <td><strong>${x.o.id}</strong></td>
              <td>${esc(customerName(x.o.kunde))}</td>
              <td class="muted">${fmtDate(x.o.orderDate)}</td>
              <td>${statusBadge(x.o.status)}</td>
              <td class="right">${sum(x.lines, l => l.quantity)}</td>
              <td class="right"><strong>${eur(x.total)}</strong></td>
              <td>${x.paid ? '<span class="badge b-ok">bezahlt</span>' : '<span class="badge b-gray">offen</span>'}</td>
            </tr>`).join('') : '<tr><td colspan="7" class="empty">Keine Bestellungen in dieser Ansicht.</td></tr>'}
          </tbody>
        </table>
      </div>
      ${sel ? orderDetail(sel) : ''}
    </div>`;

  $$('.chips .chip', main).forEach(b => b.onclick = () => { orderFilter = b.dataset.f; router(); });
  $$('tr.clickable', main).forEach(tr => tr.onclick = () => {
    location.hash = String(selected) === tr.dataset.id ? '#/bestellungen' : `#/bestellungen/${tr.dataset.id}`;
  });
  if (!sel) return;

  const id = sel.o.id;
  const on = (sel_, fn) => { const el = $(sel_, main); if (el) el.onclick = fn; };
  on('#closeDetail', () => { location.hash = '#/bestellungen'; });
  on('#confirmOrder', () => act(() => api(`/orders/${id}/confirm`, { method: 'POST' }),
    'Bestellung bestätigt – Zutaten wurden vom Lager abgebucht.'));
  on('#payOk', () => act(() => api('/payments', { method: 'POST', body: { orderId: id, successful: true } }), 'Zahlung erfolgreich.'));
  on('#payFail', () => act(async () => {
    await api('/payments', { method: 'POST', body: { orderId: id, successful: false } });
    toast('Zahlung als fehlgeschlagen gespeichert.', 'warn');
  }));
  on('#deleteOrder', () => confirmDelete(`Bestellung #${id} mit allen Positionen löschen?`,
    async () => { await api(`/orders/${id}`, { method: 'DELETE' }); location.hash = '#/bestellungen'; }, 'Bestellung gelöscht.'));
  on('#addLine', () => lineModal(id, products));
  $$('[data-del-line]', main).forEach(b => b.onclick = () => {
    const [type, lineId] = b.dataset.delLine.split(':');
    act(() => api(`/${type === 'custom' ? 'order-item-customs' : 'order-items'}/${lineId}`, { method: 'DELETE' }), 'Position entfernt.');
  });
}

function orderDetail({ o, lines, pays, total, paid }) {
  const open = o.status === 'OPEN';
  return `
    <aside class="card detail">
      <div class="detail-head">
        <h2>Bestellung #${o.id}</h2>
        <button class="icon-btn" id="closeDetail" aria-label="Schließen">✕</button>
      </div>
      <p class="meta">${esc(customerName(o.kunde))} · ${fmtDate(o.orderDate)}</p>
      <div class="actions">${statusBadge(o.status)} ${paid ? '<span class="badge b-ok">bezahlt</span>' : '<span class="badge b-gray">nicht bezahlt</span>'}</div>

      <div class="section">
        <h3>Positionen</h3>
        <table>
          <tbody>${lines.length ? lines.map(l => `
            <tr>
              <td>${l.quantity}×</td>
              <td>${esc(l.product?.name)}${l.specialInstructions ? `<div class="note">${esc(l.specialInstructions)}</div>` : ''}</td>
              <td class="right">${eur(l.quantity * l.unitPrice)}</td>
              <td class="right" style="width:34px">${open ? `<button class="icon-btn" title="Entfernen" data-del-line="${l.type}:${l.id}">✕</button>` : ''}</td>
            </tr>`).join('') : '<tr><td class="empty">Noch keine Positionen.</td></tr>'}
          </tbody>
        </table>
        <div class="sum-line"><span>Summe</span><span>${eur(total)}</span></div>
        ${open ? '<button class="btn ghost sm" id="addLine" style="margin-top:12px">+ Position hinzufügen</button>' : ''}
      </div>

      <div class="section">
        <h3>Zahlungen</h3>
        ${pays.length ? `<div class="list">${pays.map(p => `
          <div class="list-item"><span>${fmtDate(p.paymentTime)}</span><span>${eur(p.amount)}</span>
          ${p.status === 'SUCCESSFUL' ? '<span class="badge b-ok">erfolgreich</span>' : '<span class="badge b-bad">fehlgeschlagen</span>'}</div>`).join('')}</div>`
          : '<p class="muted small">Noch keine Zahlungsversuche.</p>'}
      </div>

      <div class="section actions">
        ${open ? '<button class="btn" id="confirmOrder">Bestätigen &amp; Lager abbuchen</button>' : ''}
        ${!paid ? `<button class="btn green" id="payOk" ${lines.length ? '' : 'disabled'}>Bezahlen</button>
                   <button class="btn ghost" id="payFail" ${lines.length ? '' : 'disabled'} title="Simuliert eine abgelehnte Zahlung">Zahlung ablehnen</button>` : ''}
        ${open ? '<button class="btn danger" id="deleteOrder">Löschen</button>' : ''}
      </div>
    </aside>`;
}

function lineModal(orderId, products) {
  const active = products.filter(p => p.active);
  if (!active.length) return toast('Es gibt noch keine aktiven Produkte.', 'warn');
  modal({
    title: `Position zu Bestellung #${orderId}`,
    body: selectField('Produkt', 'productId', active.map(p => ({ value: p.id, label: `${p.name} – ${eur(p.price)}` })), active[0].id) +
          field('Menge', 'quantity', 1, 'number', 'min="1" step="1"') +
          field('Sonderwunsch (optional)', 'note', '', 'text', 'placeholder="z. B. extra scharf"'),
    submitLabel: 'Hinzufügen',
    onSubmit: async d => {
      const p = active.find(x => x.id === Number(d.productId));
      const quantity = parseInt(d.quantity, 10);
      if (!(quantity >= 1)) throw new Error('Menge muss mindestens 1 sein.');
      const body = { orderId, productId: p.id, quantity, unitPrice: p.price };
      if (d.note.trim()) await api('/order-item-customs', { method: 'POST', body: { ...body, specialInstructions: d.note.trim() } });
      else await api('/order-items', { method: 'POST', body });
      toast('Position hinzugefügt.');
      await router();
    },
  });
}

// ---------- Speisekarte ----------
async function viewMenu(main) {
  const [products, categories, recipes, ingredients] = await Promise.all([
    api('/products'), api('/categories'), api('/product-ingredients'), api('/ingredients')]);
  const recipeCount = id => recipes.filter(r => r.product?.id === id).length;

  main.innerHTML = `
    <div class="page-head">
      <div><h1>Speisekarte</h1><p class="sub">${products.length} Produkte in ${categories.length} Kategorien</p></div>
      <button class="btn primary" id="newProduct">+ Neues Produkt</button>
    </div>
    <div class="grid-menu">
      <div class="card">
        <div class="detail-head" style="margin-bottom:8px"><h2>Kategorien</h2><button class="btn ghost sm" id="newCat">+ Neu</button></div>
        <div class="list">${categories.length ? categories.map(c => `
          <div class="list-item">
            <span>${esc(c.name)} <span class="muted small">(${products.filter(p => p.category?.id === c.id).length})</span></span>
            <span><button class="icon-btn" data-edit-cat="${c.id}" title="Umbenennen">✎</button><button class="icon-btn" data-del-cat="${c.id}" title="Löschen">🗑</button></span>
          </div>`).join('') : '<p class="muted small">Noch keine Kategorien.</p>'}</div>
      </div>
      <div class="card" style="padding:6px 8px">
        <table>
          <thead><tr><th>Produkt</th><th>Kategorie</th><th class="right">Preis</th><th>Rezept</th><th>Status</th><th></th></tr></thead>
          <tbody>${products.length ? products.map(p => `
            <tr>
              <td><strong>${esc(p.name)}</strong><div class="muted small">${esc(p.description || '')}</div></td>
              <td>${esc(p.category?.name || '–')}</td>
              <td class="right">${eur(p.price)}</td>
              <td>${recipeCount(p.id) ? `<span class="badge b-ok">${recipeCount(p.id)} Zutaten</span>` : '<span class="badge b-warn">fehlt</span>'}</td>
              <td>${p.active ? '<span class="badge b-ok">aktiv</span>' : '<span class="badge b-gray">inaktiv</span>'}</td>
              <td class="right"><div class="actions" style="justify-content:flex-end">
                <button class="btn ghost sm" data-recipe="${p.id}">Rezept</button>
                <button class="icon-btn" data-edit="${p.id}" title="Bearbeiten">✎</button>
                <button class="icon-btn" data-del="${p.id}" title="Löschen">🗑</button>
              </div></td>
            </tr>`).join('') : '<tr><td colspan="6" class="empty">Noch keine Produkte.</td></tr>'}
          </tbody>
        </table>
      </div>
    </div>`;

  const catModal = cat => modal({
    title: cat ? 'Kategorie umbenennen' : 'Neue Kategorie',
    body: field('Name', 'name', cat?.name, 'text', 'required'),
    onSubmit: async d => {
      if (!d.name.trim()) throw new Error('Bitte einen Namen eingeben.');
      if (cat) await api(`/categories/${cat.id}`, { method: 'PUT', body: { name: d.name.trim() } });
      else await api('/categories', { method: 'POST', body: { name: d.name.trim() } });
      toast('Kategorie gespeichert.');
      await router();
    },
  });

  const productModal = p => {
    if (!categories.length) return toast('Lege zuerst eine Kategorie an.', 'warn');
    modal({
      title: p ? 'Produkt bearbeiten' : 'Neues Produkt',
      body: field('Name', 'name', p?.name, 'text', 'required') +
            field('Beschreibung', 'description', p?.description) +
            `<div class="row">${field('Preis (€)', 'price', p?.price ?? '', 'number', 'min="0" step="0.01" required')}
            ${selectField('Kategorie', 'categoryId', categories.map(c => ({ value: c.id, label: c.name })), p?.category?.id ?? categories[0].id)}</div>` +
            checkField('Aktiv (an der Kasse sichtbar)', 'active', p ? p.active : true),
      onSubmit: async d => {
        const price = parseFloat(String(d.price).replace(',', '.'));
        if (!d.name.trim()) throw new Error('Bitte einen Namen eingeben.');
        if (!(price >= 0)) throw new Error('Bitte einen gültigen Preis eingeben.');
        const body = { name: d.name.trim(), description: d.description.trim(), price, active: !!d.active, category: { id: Number(d.categoryId) } };
        if (p) await api(`/products/${p.id}`, { method: 'PUT', body });
        else await api('/products', { method: 'POST', body });
        toast('Produkt gespeichert.');
        await router();
      },
    });
  };

  $('#newProduct').onclick = () => productModal(null);
  $('#newCat').onclick = () => catModal(null);
  $$('[data-edit-cat]').forEach(b => b.onclick = () => catModal(categories.find(c => c.id === Number(b.dataset.editCat))));
  $$('[data-del-cat]').forEach(b => b.onclick = () => {
    const c = categories.find(x => x.id === Number(b.dataset.delCat));
    confirmDelete(`Kategorie „${c.name}“ löschen? Das klappt nur, wenn keine Produkte mehr darin sind.`,
      () => api(`/categories/${c.id}`, { method: 'DELETE' }), 'Kategorie gelöscht.');
  });
  $$('[data-edit]').forEach(b => b.onclick = () => productModal(products.find(p => p.id === Number(b.dataset.edit))));
  $$('[data-del]').forEach(b => b.onclick = () => {
    const p = products.find(x => x.id === Number(b.dataset.del));
    confirmDelete(`Produkt „${p.name}“ löschen? Produkte, die schon bestellt wurden, besser auf „inaktiv“ setzen.`,
      () => api(`/products/${p.id}`, { method: 'DELETE' }), 'Produkt gelöscht.');
  });
  $$('[data-recipe]').forEach(b => b.onclick = () => recipeModal(products.find(p => p.id === Number(b.dataset.recipe)), ingredients));
}

function recipeModal(product, ingredients) {
  let changed = false;
  modal({
    title: `Rezept: ${product.name}`,
    wide: true,
    body: `<p class="sub" style="margin:0 0 14px">Welche Zutaten werden für <strong>1 Stück</strong> gebraucht? Beim Bestätigen einer Bestellung wird das vom Lager abgezogen.</p>
      <div id="recipeList" class="loading">Lädt …</div>
      <div class="row" style="margin-top:16px">
        ${selectField('Zutat', 'ingredientId', ingredients.map(i => ({ value: i.id, label: `${i.name} (${i.unit})` })), ingredients[0]?.id)}
        ${field('Menge', 'quantity', '', 'number', 'min="0" step="any" placeholder="z. B. 150"')}
        <button type="submit" class="btn">Hinzufügen</button>
      </div>`,
    submitLabel: 'Fertig',
    onOpen: (form, close) => {
      const submitBtn = $('footer button[type=submit]', form);
      submitBtn.type = 'button';
      submitBtn.onclick = () => close();
      $$('[data-close]', form.parentElement).forEach(b => b.addEventListener('click', () => { if (changed) router(); }));
      submitBtn.addEventListener('click', () => { if (changed) router(); });
      if (!ingredients.length) $('.row', form).innerHTML = '<p class="muted small">Lege zuerst unter <a href="#/lager">Lager</a> Zutaten an.</p>';
      draw(form);
    },
    onSubmit: async (d, form) => {
      const quantity = parseFloat(String(d.quantity).replace(',', '.'));
      if (!(quantity > 0)) throw new Error('Menge muss größer als 0 sein.');
      await api('/product-ingredients', { method: 'POST', body: { product: { id: product.id }, ingredient: { id: Number(d.ingredientId) }, quantity } });
      changed = true;
      $('[name=quantity]', form).value = '';
      await draw(form);
      return false;
    },
  });

  async function draw(form) {
    const list = await api(`/product-ingredients/product/${product.id}`);
    const box = $('#recipeList', form);
    box.className = '';
    box.innerHTML = list.length ? `<table><thead><tr><th>Zutat</th><th class="right">Menge</th><th></th></tr></thead><tbody>${list.map(r => `
      <tr><td>${esc(r.ingredient?.name)}</td><td class="right">${num(r.quantity)} ${esc(r.ingredient?.unit)}</td>
      <td class="right"><button type="button" class="icon-btn" data-del-ri="${r.id}" title="Entfernen">✕</button></td></tr>`).join('')}</tbody></table>`
      : '<p class="banner" style="margin:0">Noch kein Rezept – ohne Rezept kann eine Bestellung mit diesem Produkt nicht bestätigt werden.</p>';
    $$('[data-del-ri]', box).forEach(b => b.onclick = async () => {
      try { await api(`/product-ingredients/${b.dataset.delRi}`, { method: 'DELETE' }); changed = true; await draw(form); }
      catch (e) { toast(e.message, 'err'); }
    });
  }
}

// ---------- Lager ----------
async function viewStock(main) {
  const [ingredients, inventory, recipes] = await Promise.all([api('/ingredients'), api('/inventory'), api('/product-ingredients')]);
  const rows = ingredients.map(i => {
    const inv = inventory.find(x => x.ingredient?.id === i.id);
    const uses = recipes.filter(r => r.ingredient?.id === i.id);
    const perPortion = uses.length ? Math.max(...uses.map(r => r.quantity)) : 0;
    const portions = inv && perPortion ? Math.floor(inv.quantity / perPortion) : null;
    let state = ['b-ok', 'ok'];
    if (!inv) state = ['b-gray', 'kein Bestand'];
    else if (inv.quantity <= 0) state = ['b-bad', 'leer'];
    else if (portions !== null && portions < 10) state = ['b-warn', 'niedrig'];
    return { i, inv, uses, portions, state };
  });
  const empty = rows.filter(r => r.state[1] === 'leer' || r.state[1] === 'kein Bestand').length;
  const low = rows.filter(r => r.state[1] === 'niedrig').length;

  main.innerHTML = `
    <div class="page-head">
      <div><h1>Lager</h1><p class="sub">Zutaten und Bestände. „Reicht für“ bezieht sich auf das Produkt, das am meisten davon braucht.</p></div>
      <button class="btn primary" id="newIng">+ Neue Zutat</button>
    </div>
    <div class="kpis">
      <div class="card kpi"><div class="label">Zutaten</div><div class="value">${ingredients.length}</div></div>
      <div class="card kpi"><div class="label">Niedriger Bestand</div><div class="value" style="color:#a36a00">${low}</div></div>
      <div class="card kpi"><div class="label">Leer / ohne Bestand</div><div class="value" style="color:var(--red)">${empty}</div></div>
    </div>
    <div class="card" style="padding:6px 8px">
      <table>
        <thead><tr><th>Zutat</th><th class="right">Bestand</th><th>Reicht für</th><th>Verwendet in</th><th>Status</th><th></th></tr></thead>
        <tbody>${rows.length ? rows.map(r => `
          <tr>
            <td><strong>${esc(r.i.name)}</strong>${r.i.active ? '' : ' <span class="badge b-gray">inaktiv</span>'}</td>
            <td class="right"><strong>${r.inv ? num(r.inv.quantity) : '–'}</strong> <span class="muted">${esc(r.i.unit)}</span></td>
            <td class="muted">${r.portions === null ? '–' : `${r.portions} Portionen`}</td>
            <td class="small">${r.uses.length ? esc(r.uses.map(u => u.product?.name).join(', ')) : '<span class="muted">–</span>'}</td>
            <td><span class="badge ${r.state[0]}">${r.state[1]}</span></td>
            <td class="right"><div class="actions" style="justify-content:flex-end">
              ${r.inv ? `<button class="btn ghost sm" data-fill="${r.i.id}">Auffüllen</button><button class="btn ghost sm" data-set="${r.i.id}">Setzen</button>`
                      : `<button class="btn sm" data-create-inv="${r.i.id}">Bestand anlegen</button>`}
              <button class="icon-btn" data-edit="${r.i.id}" title="Bearbeiten">✎</button>
              <button class="icon-btn" data-del="${r.i.id}" title="Löschen">🗑</button>
            </div></td>
          </tr>`).join('') : '<tr><td colspan="6" class="empty">Noch keine Zutaten.</td></tr>'}
        </tbody>
      </table>
    </div>`;

  const byId = id => rows.find(r => r.i.id === Number(id));
  const qtyModal = (title, label, r, apply) => modal({
    title, body: field(`${label} (${esc(r.i.unit)})`, 'q', '', 'number', 'min="0" step="any" required'),
    onSubmit: async d => {
      const q = parseFloat(String(d.q).replace(',', '.'));
      if (!(q >= 0)) throw new Error('Bitte eine gültige Menge eingeben.');
      await apply(q);
      toast(`Bestand für ${r.i.name} gespeichert.`);
      await router();
    },
  });

  $('#newIng').onclick = () => modal({
    title: 'Neue Zutat',
    body: field('Name', 'name', '', 'text', 'required') +
          `<div class="row">${field('Einheit', 'unit', 'g', 'text', 'placeholder="g, ml, Stk"')}${field('Anfangsbestand', 'q', '0', 'number', 'min="0" step="any"')}</div>`,
    onSubmit: async d => {
      if (!d.name.trim()) throw new Error('Bitte einen Namen eingeben.');
      const ing = await api('/ingredients', { method: 'POST', body: { name: d.name.trim(), unit: d.unit.trim() || 'Stk', active: true } });
      await api('/inventory', { method: 'POST', body: { ingredient: { id: ing.id }, quantity: parseFloat(String(d.q || 0).replace(',', '.')) || 0 } });
      toast(`${ing.name} angelegt.`);
      await router();
    },
  });
  $$('[data-fill]').forEach(b => b.onclick = () => { const r = byId(b.dataset.fill);
    qtyModal(`${r.i.name} auffüllen`, 'Menge hinzufügen', r,
      q => api(`/inventory/${r.inv.id}/quantity?quantity=${r.inv.quantity + q}`, { method: 'PATCH' })); });
  $$('[data-set]').forEach(b => b.onclick = () => { const r = byId(b.dataset.set);
    qtyModal(`Bestand ${r.i.name} setzen`, 'Neuer Bestand', r,
      q => api(`/inventory/${r.inv.id}/quantity?quantity=${q}`, { method: 'PATCH' })); });
  $$('[data-create-inv]').forEach(b => b.onclick = () => { const r = byId(b.dataset.createInv);
    qtyModal(`Bestand für ${r.i.name} anlegen`, 'Anfangsbestand', r,
      q => api('/inventory', { method: 'POST', body: { ingredient: { id: r.i.id }, quantity: q } })); });
  $$('[data-edit]').forEach(b => b.onclick = () => { const r = byId(b.dataset.edit);
    modal({
      title: 'Zutat bearbeiten',
      body: field('Name', 'name', r.i.name, 'text', 'required') + field('Einheit', 'unit', r.i.unit) + checkField('Aktiv', 'active', r.i.active),
      onSubmit: async d => {
        if (!d.name.trim()) throw new Error('Bitte einen Namen eingeben.');
        await api(`/ingredients/${r.i.id}`, { method: 'PUT', body: { name: d.name.trim(), unit: d.unit.trim(), active: !!d.active } });
        toast('Zutat gespeichert.');
        await router();
      },
    }); });
  $$('[data-del]').forEach(b => b.onclick = () => { const r = byId(b.dataset.del);
    confirmDelete(`Zutat „${r.i.name}“ löschen?${r.uses.length ? ' Sie wird noch in Rezepten verwendet – entferne sie dort zuerst.' : ''}`,
      async () => {
        if (r.inv) await api(`/inventory/${r.inv.id}`, { method: 'DELETE' });
        await api(`/ingredients/${r.i.id}`, { method: 'DELETE' });
      }, 'Zutat gelöscht.'); });
}

// ---------- Kunden ----------
async function viewCustomers(main) {
  const [customers, orders, payments] = await Promise.all([api('/kunden'), api('/orders'), api('/payments')]);
  const stats = c => {
    const os = orders.filter(o => o.kunde?.id === c.id);
    const revenue = sum(payments.filter(p => p.status === 'SUCCESSFUL' && os.some(o => o.id === p.orderId)), p => p.amount);
    return { count: os.length, revenue };
  };
  main.innerHTML = `
    <div class="page-head">
      <div><h1>Kunden</h1><p class="sub">${customers.length} Kunden</p></div>
      <button class="btn primary" id="newCust">+ Neuer Kunde</button>
    </div>
    <div class="card" style="padding:6px 8px">
      <table>
        <thead><tr><th>#</th><th>Name</th><th class="right">Bestellungen</th><th class="right">Umsatz (bezahlt)</th><th></th></tr></thead>
        <tbody>${customers.length ? customers.map(c => { const s = stats(c); return `
          <tr>
            <td class="muted">${c.id}</td>
            <td><strong>${esc(customerName(c))}</strong></td>
            <td class="right">${s.count}</td>
            <td class="right">${eur(s.revenue)}</td>
            <td class="right"><div class="actions" style="justify-content:flex-end">
              <button class="icon-btn" data-edit="${c.id}" title="Bearbeiten">✎</button>
              <button class="icon-btn" data-del="${c.id}" title="Löschen">🗑</button>
            </div></td>
          </tr>`; }).join('') : '<tr><td colspan="5" class="empty">Noch keine Kunden.</td></tr>'}
        </tbody>
      </table>
    </div>`;
  $('#newCust').onclick = () => customerModal(null, () => router());
  $$('[data-edit]').forEach(b => b.onclick = () => customerModal(customers.find(c => c.id === Number(b.dataset.edit)), () => router()));
  $$('[data-del]').forEach(b => b.onclick = () => {
    const c = customers.find(x => x.id === Number(b.dataset.del));
    confirmDelete(`${customerName(c)} löschen? Kunden mit Bestellungen können nicht gelöscht werden.`,
      () => api(`/kunden/${c.id}`, { method: 'DELETE' }), 'Kunde gelöscht.');
  });
}

// ---------- Statistik ----------
const statState = { date: today(), month: today().slice(0, 7) };

async function viewStats(main) {
  const [daily, monthly, avg, best, payments, orders] = await Promise.all([
    api(`/statistics/revenue/daily?date=${statState.date}`),
    api(`/statistics/revenue/monthly?month=${statState.month}`),
    api('/statistics/average-order-value'),
    api('/statistics/best-selling-products'),
    api('/payments'),
    api('/orders'),
  ]);
  const max = Math.max(1, ...best.map(b => b.quantity));
  const ok = payments.filter(p => p.status === 'SUCCESSFUL').length;
  const failed = payments.length - ok;

  main.innerHTML = `
    <div class="page-head"><div><h1>Statistik</h1><p class="sub">Gezählt werden nur erfolgreich bezahlte Bestellungen.</p></div></div>
    <div class="kpis">
      <div class="card kpi"><div class="label">Tagesumsatz</div><div class="value">${eur(daily)}</div>
        <input type="date" id="d" value="${statState.date}"></div>
      <div class="card kpi"><div class="label">Monatsumsatz</div><div class="value">${eur(monthly)}</div>
        <input type="month" id="m" value="${statState.month}"></div>
      <div class="card kpi"><div class="label">Ø Bestellwert</div><div class="value">${eur(avg)}</div>
        <p class="muted small">über abgeschlossene Bestellungen</p></div>
      <div class="card kpi"><div class="label">Zahlungen</div><div class="value">${ok} <span class="muted small">ok</span> · ${failed} <span class="muted small">abgelehnt</span></div>
        <p class="muted small">${orders.length} Bestellungen insgesamt</p></div>
    </div>
    <div class="card">
      <h2 style="margin-bottom:16px">Meistverkaufte Produkte</h2>
      ${best.length ? `<div class="bars">${best.map(b => `
        <div class="bar-row"><span>${esc(b.name)}</span><div class="bar"><div style="width:${(b.quantity / max) * 100}%"></div></div><strong class="right">${b.quantity}</strong></div>`).join('')}</div>`
        : '<p class="empty">Noch keine bezahlten Bestellungen.</p>'}
    </div>`;
  $('#d').onchange = e => { if (e.target.value) { statState.date = e.target.value; router(); } };
  $('#m').onchange = e => { if (e.target.value) { statState.month = e.target.value; router(); } };
}
