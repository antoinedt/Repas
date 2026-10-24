// Reads every course table on the "Travaux" page (course filter = Tous).
function tx(c) { return (c.textContent || '').replace(/\s+/g, ' ').trim(); }
var tables = arr(document.querySelectorAll('table[id*="grdTravaux"]'));
var et = document.querySelector('select[id$="cboEtape"]');
var out = { etape: et ? et.value : '', courses: [] };
tables.forEach(function (t) {
  var code = t.id.replace(/^.*grdTravaux/, '');
  // Course title = the last ".ctitre" heading before this table.
  var title = '';
  arr(document.querySelectorAll('.ctitre')).forEach(function (h) {
    if (h.compareDocumentPosition(t) & Node.DOCUMENT_POSITION_FOLLOWING) title = tx(h);
  });
  var comp = '', cat = '', items = [];
  arr(t.querySelectorAll('tbody > tr')).forEach(function (r) {
    if (r.classList.contains('grid3__regr')) { comp = tx(r); cat = ''; return; }
    if (!r.classList.contains('grid3__row')) return;
    var td = r.children;
    if (td.length < 7) return;
    var c = tx(td[1]);
    if (c) cat = c;
    items.push({ comp: comp, cat: cat, title: tx(td[2]), assigned: tx(td[3]),
                 due: tx(td[4]), done: tx(td[5]), result: tx(td[6]) });
  });
  out.courses.push({ code: code, title: title, items: items });
});
return JSON.stringify(out);
