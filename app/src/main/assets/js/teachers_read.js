// '' = page not refreshed yet; '[]' = refreshed, no teacher; else JSON list.
// Names starting with '~' are "First Last"; others "Last, First".
var s = document.querySelector('select[id$="cboCours"]');
if (!s || s.value !== __VAL__) return '';
var e = document.querySelector('select[id$="cboEnseignant"]');
var acc = document.querySelector('.pageonglet[data-id="ongletAccueil"]');
var fresh = (e && !e.__cndOld) || (acc && !acc.__cndOld);
if (!fresh) return '';
var names = [];
if (e && !e.__cndOld) {
  names = arr(e.options).map(function (o) { return (o.text || '').trim(); })
    .filter(function (x) { return x && !/^tous/i.test(x); });
}
if (!names.length && acc && !acc.__cndOld) {
  arr(acc.querySelectorAll('.libelle-entete')).forEach(function (h) {
    if (/enseignant/i.test(h.textContent)) {
      var v = h.nextElementSibling;
      if (v && v.textContent.trim()) names.push('~' + v.textContent.trim());
    }
  });
}
return JSON.stringify(names);
