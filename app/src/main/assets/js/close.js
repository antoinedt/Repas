var t = document.querySelector(G);
var c = arr(document.querySelectorAll('a,button,input[type=button]')).filter(function (b) {
  if (!vis(b) || (t && t.contains(b)) || b.id === 'lnkSUIVI') return false;
  var s = ((b.innerText || b.value || '') + ' ' + (b.title || '') + ' ' + (b.id || '')).toLowerCase();
  return /\bclose\b|fermer|retour|arrow_back|annuler/.test(s);
});
if (c.length) { c[c.length - 1].click(); return 'closed'; }
return 'none';
