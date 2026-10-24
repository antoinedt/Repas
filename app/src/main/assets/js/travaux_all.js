// Makes sure the course filter is "Tous". Returns 'ok', 'changed' or 'noSel'.
var s = document.querySelector('select[id$="cboCours"]');
if (!s) return 'noSel';
if (s.value === 'TOUS') return 'ok';
arr(document.querySelectorAll('table[id*="grdTravaux"]')).forEach(function (t) { t.__cndOld = true; });
s.value = 'TOUS';
s.dispatchEvent(new Event('change', { bubbles: true }));
return 'changed';
