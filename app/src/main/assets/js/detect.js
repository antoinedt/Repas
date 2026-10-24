var snap = window.__cndSnap || {};
var t = document.querySelector(G);
var pages = arr(document.querySelectorAll('.pagecoba')).filter(function (p) {
  return vis(p) && !(t && p.contains(t)) && snap[p.id] !== p.innerText && p.innerText.trim().length > 0;
});
if (pages.length) return 'P:' + pages[pages.length - 1].innerText;
var dl = arr(document.querySelectorAll('.ui-dialog,[role=dialog],.popup,.modal,.dialog')).filter(function (e) {
  return vis(e) && !(t && e.contains(t)) && e.innerText.trim().length > 20;
});
if (dl.length) return 'D:' + dl[dl.length - 1].innerText;
if (t) {
  var rs = t.querySelectorAll('tbody > tr');
  if (rs.length > (window.__cndRows || 0)) {
    var n = rs[(window.__cndIdx || 0) + 1];
    if (n && !n.classList.contains('grid3__row')) return 'R:' + n.innerText;
  }
}
return '';
