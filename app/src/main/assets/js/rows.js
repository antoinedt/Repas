var t = document.querySelector(G);
if (!t) return '[]';
function tx(c) { return (c.textContent || '').replace(/\s+/g, ' ').trim(); }
var rs = t.querySelectorAll('tbody > tr'), out = [];
for (var i = 0; i < rs.length; i++) {
  var r = rs[i];
  if (!r.classList.contains('grid3__row')) continue;
  var td = r.children;
  if (td.length < 5) continue;
  out.push({ i: i, date: tx(td[1]), type: tx(td[2]), group: tx(td[3]), teacher: tx(td[4]) });
}
return JSON.stringify(out);
